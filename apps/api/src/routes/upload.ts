import { Router, Request, Response } from 'express'
import multer from 'multer'
import path from 'path'
import fs from 'fs'
import crypto from 'crypto'
import sharp from 'sharp'
import { encode as encodeBlurhash } from 'blurhash'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { uploadLimiter } from '../middleware/rateLimiter'
import { putAttachment, storageMode } from '../lib/storage'

const UPLOAD_DIR = path.resolve(process.cwd(), 'uploads')
if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true })

if (process.env.NODE_ENV === 'production' && storageMode === 'local' && !process.env.UPLOAD_PERSISTENT) {
  // eslint-disable-next-line no-console
  console.warn(
    '[uploads] ⚠ Storage em filesystem local. Arquivos serão perdidos em cada redeploy.\n' +
    '          Configure R2 (R2_ACCOUNT_ID/R2_ACCESS_KEY_ID/R2_SECRET_ACCESS_KEY/R2_BUCKET/R2_PUBLIC_URL)\n' +
    '          ou seta UPLOAD_PERSISTENT=1 se tem volume montado.',
  )
}

const ALLOWED_MIMES = new Set([
  'image/png','image/jpeg','image/gif','image/webp','image/avif',
  'video/mp4','video/webm','video/quicktime',
  'audio/mpeg','audio/wav','audio/ogg','audio/webm','audio/mp4','audio/x-m4a','audio/aac',
  'application/pdf','text/plain','application/zip','application/json',
])

function isMimeAllowed(raw: string): boolean {
  const base = raw.split(';')[0].trim().toLowerCase()
  return ALLOWED_MIMES.has(base)
}

// Extensao derivada do MIME (nunca do originalname do cliente): um upload com nome
// "x.html" nao consegue mais virar um arquivo .html servido pela API.
const MIME_EXT: Record<string, string> = {
  'image/png': '.png', 'image/jpeg': '.jpg', 'image/gif': '.gif',
  'image/webp': '.webp', 'image/avif': '.avif',
  'video/mp4': '.mp4', 'video/webm': '.webm', 'video/quicktime': '.mov',
  'audio/mpeg': '.mp3', 'audio/wav': '.wav', 'audio/ogg': '.ogg',
  'audio/webm': '.weba', 'audio/mp4': '.m4a', 'audio/x-m4a': '.m4a', 'audio/aac': '.aac',
  'application/pdf': '.pdf', 'text/plain': '.txt', 'application/zip': '.zip', 'application/json': '.json',
}
function extForMime(mime: string): string {
  return MIME_EXT[mime.split(';')[0].trim().toLowerCase()] ?? '.bin'
}

const MAX_FILE_SIZE  = 25 * 1024 * 1024
const MAX_PER_REQUEST = 10

const memoryStorage = multer.memoryStorage()

const upload = multer({
  storage: memoryStorage,
  limits: { fileSize: MAX_FILE_SIZE, files: MAX_PER_REQUEST },
  fileFilter: (_req, file, cb) => {
    if (!isMimeAllowed(file.mimetype)) return cb(new Error('TYPE_NOT_ALLOWED'))
    cb(null, true)
  },
})

async function makeBlurhash(input: Buffer): Promise<string | undefined> {
  try {
    const { data, info } = await sharp(input, { failOn: 'none' })
      .rotate()
      .resize(32, 32, { fit: 'inside' })
      .ensureAlpha()
      .raw()
      .toBuffer({ resolveWithObject: true })
    return encodeBlurhash(new Uint8ClampedArray(data), info.width, info.height, 4, 4)
  } catch {
    return undefined
  }
}

async function maybeTranscode(file: Express.Multer.File): Promise<{
  buffer:   Buffer
  mime:     string
  ext:      string
  width?:   number
  height?:  number
  blurhash?: string
}> {
  const mime = file.mimetype.split(';')[0].toLowerCase()

  if (!mime.startsWith('image/') || mime === 'image/gif' || mime === 'image/svg+xml') {
    return { buffer: file.buffer, mime, ext: extForMime(mime) }
  }

  try {
    const img = sharp(file.buffer, { failOn: 'none' })
    const meta = await img.metadata()
    const buffer = await img
      .rotate()
      .resize({ width: 2048, height: 2048, fit: 'inside', withoutEnlargement: true })
      .webp({ quality: 82, effort: 4 })
      .toBuffer()
    return {
      buffer,
      mime:     'image/webp',
      ext:      '.webp',
      width:    meta.width,
      height:   meta.height,
      blurhash: await makeBlurhash(file.buffer),
    }
  } catch (e) {
    console.warn('[upload] sharp falhou, fallback p/ original:', (e as Error).message)
    return { buffer: file.buffer, mime, ext: extForMime(mime) }
  }
}

const router = Router()

router.post(
  '/',
  requireAuth,
  uploadLimiter,
  (req: Request, res: Response, next) => {
    upload.array('files', MAX_PER_REQUEST)(req, res, (err: any) => {
      if (err) {
        if (err.code === 'LIMIT_FILE_SIZE') return res.status(413).json({ error: 'Arquivo maior que 25MB' })
        if (err.code === 'LIMIT_FILE_COUNT') return res.status(413).json({ error: `Máximo ${MAX_PER_REQUEST} arquivos` })
        if (err.message === 'TYPE_NOT_ALLOWED') return res.status(415).json({ error: 'Tipo não permitido' })
        return res.status(400).json({ error: 'Falha no upload' })
      }
      next()
    })
  },
  asyncHandler(async (req: Request, res: Response) => {
    const files = (req.files as Express.Multer.File[] | undefined) ?? []
    if (files.length === 0) return res.status(400).json({ error: 'Nenhum arquivo enviado' })

    // Sniff anti-HTML: recusa conteudo que abre como pagina (mesmo declarado como
    // txt/json/pdf) -> reforca o Content-Disposition do serving estatico.
    for (const f of files) {
      const head = f.buffer.subarray(0, 64).toString('latin1').toLowerCase().trimStart()
      if (head.startsWith('<!doctype html') || head.startsWith('<html') || head.startsWith('<svg') || head.includes('<script')) {
        return res.status(415).json({ error: 'Conteúdo não permitido' })
      }
    }

    const attachments = await Promise.all(files.map(async (f) => {
      const processed = await maybeTranscode(f)
      const id = crypto.randomBytes(16).toString('hex')
      const filename = `${id}${processed.ext}`
      const url = await putAttachment(filename, processed.buffer, processed.mime)
      return {
        url,
        type:     processed.mime,
        name:     f.originalname,
        size:     processed.buffer.length,
        width:    processed.width,
        height:   processed.height,
        blurhash: processed.blurhash,
      }
    }))
    res.json({ data: { attachments } })
  })
)

export default router
export { UPLOAD_DIR }
