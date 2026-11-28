import { Router, type Response } from 'express'
import type { Request } from '../lib/requisicao'
import multer from 'multer'
import path from 'path'
import os from 'os'
import fs from 'fs'
import crypto from 'crypto'
import sharp from 'sharp'
import { encode as encodeBlurhash } from 'blurhash'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { uploadLimiter } from '../middleware/rateLimiter'
import { guardarSeNovo, storageMode } from '../lib/storage'
import { env } from '../lib/env'

const UPLOAD_DIR = path.resolve(process.cwd(), 'uploads')
if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true })

if (process.env.NODE_ENV === 'production' && storageMode === 'local' && !process.env.UPLOAD_PERSISTENT) {
  console.warn(
    '[uploads] ⚠ Storage em filesystem local. Arquivos serão perdidos em cada redeploy.\n' +
      '          Configure R2 (R2_ACCOUNT_ID/R2_ACCESS_KEY_ID/R2_SECRET_ACCESS_KEY/R2_BUCKET/R2_PUBLIC_URL)\n' +
      '          ou seta UPLOAD_PERSISTENT=1 se tem volume montado.',
  )
}

const ALLOWED_MIMES = new Set([
  'image/png',
  'image/jpeg',
  'image/gif',
  'image/webp',
  'image/avif',
  'video/mp4',
  'video/webm',
  'video/quicktime',
  'audio/mpeg',
  'audio/wav',
  'audio/ogg',
  'audio/webm',
  'audio/mp4',
  'audio/x-m4a',
  'audio/aac',
  'application/pdf',
  'text/plain',
  'application/zip',
  'application/json',
])

function isMimeAllowed(raw: string): boolean {
  const base = raw.split(';')[0].trim().toLowerCase()
  return ALLOWED_MIMES.has(base)
}

const MIME_EXT: Record<string, string> = {
  'image/png': '.png',
  'image/jpeg': '.jpg',
  'image/gif': '.gif',
  'image/webp': '.webp',
  'image/avif': '.avif',
  'video/mp4': '.mp4',
  'video/webm': '.webm',
  'video/quicktime': '.mov',
  'audio/mpeg': '.mp3',
  'audio/wav': '.wav',
  'audio/ogg': '.ogg',
  'audio/webm': '.weba',
  'audio/mp4': '.m4a',
  'audio/x-m4a': '.m4a',
  'audio/aac': '.aac',
  'application/pdf': '.pdf',
  'text/plain': '.txt',
  'application/zip': '.zip',
  'application/json': '.json',
}
function extForMime(mime: string): string {
  return MIME_EXT[mime.split(';')[0].trim().toLowerCase()] ?? '.bin'
}

const MAX_FILE_SIZE = 25 * 1024 * 1024
const MAX_PER_REQUEST = 10

const CHAVE_DOS_NOMES = crypto.createHmac('sha256', env.JWT_REFRESH_SECRET).update('nome-de-anexo').digest()

const PASTA_DOS_ENVIOS = path.join(os.tmpdir(), 'astra-envios')

const upload = multer({
  dest: PASTA_DOS_ENVIOS,
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

const THUMB_PX = 1280
const THUMB_MIN_PX = 1400

async function maybeTranscode(
  buffer: Buffer,
  tipo: string,
): Promise<{
  buffer: Buffer
  mime: string
  ext: string
  width?: number
  height?: number
  blurhash?: string
  thumb?: Buffer
}> {
  const mime = tipo.split(';')[0].toLowerCase()

  if (!mime.startsWith('image/') || mime === 'image/gif' || mime === 'image/svg+xml') {
    return { buffer, mime, ext: extForMime(mime) }
  }

  try {
    const img = sharp(buffer, { failOn: 'none' })
    const meta = await img.metadata()
    const maiorLado = Math.max(meta.width ?? 0, meta.height ?? 0)
    let thumb: Buffer | undefined
    if (maiorLado >= THUMB_MIN_PX) {
      thumb = await sharp(buffer, { failOn: 'none' })
        .rotate()
        .resize({ width: THUMB_PX, height: THUMB_PX, fit: 'inside', withoutEnlargement: true })
        .webp({ quality: 88, effort: 4, smartSubsample: true })
        .toBuffer()
    }

    return {
      buffer,
      mime,
      ext: extForMime(mime),
      width: meta.width,
      height: meta.height,
      blurhash: await makeBlurhash(buffer),
      thumb,
    }
  } catch (e) {
    console.warn('[upload] sharp falhou, fallback p/ original:', (e as Error).message)
    return { buffer, mime, ext: extForMime(mime) }
  }
}

async function comecoDoArquivo(caminho: string): Promise<string> {
  const arquivo = await fs.promises.open(caminho, 'r')
  try {
    const { buffer, bytesRead } = await arquivo.read(Buffer.alloc(64), 0, 64, 0)
    return buffer.subarray(0, bytesRead).toString('latin1').toLowerCase().trimStart()
  } finally {
    await arquivo.close()
  }
}

function pareceHtml(comeco: string): boolean {
  return (
    comeco.startsWith('<!doctype html') ||
    comeco.startsWith('<html') ||
    comeco.startsWith('<svg') ||
    comeco.includes('<script')
  )
}

async function guardarAnexo(f: Express.Multer.File) {
  const processed = await maybeTranscode(await fs.promises.readFile(f.path), f.mimetype)
  const id = crypto.createHmac('sha256', CHAVE_DOS_NOMES).update(processed.buffer).digest('hex')
  const filename = `${id}${processed.ext}`
  const [url, thumbUrl] = await Promise.all([
    guardarSeNovo(filename, processed.buffer, processed.mime),
    processed.thumb ? guardarSeNovo(`${id}_t.webp`, processed.thumb, 'image/webp') : Promise.resolve(undefined),
  ])
  return {
    url,
    thumbUrl,
    type: processed.mime,
    name: f.originalname,
    size: processed.buffer.length,
    width: processed.width,
    height: processed.height,
    blurhash: processed.blurhash,
  }
}

async function processarEnvio(files: Express.Multer.File[]): Promise<{ status: number; corpo: unknown }> {
  if (files.length === 0) return { status: 400, corpo: { error: 'Nenhum arquivo enviado' } }
  for (const f of files) {
    if (pareceHtml(await comecoDoArquivo(f.path))) return { status: 415, corpo: { error: 'Conteúdo não permitido' } }
  }
  const attachments = []
  for (const f of files) attachments.push(await guardarAnexo(f))
  return { status: 200, corpo: { data: { attachments } } }
}

async function apagarTemporarios(files: Express.Multer.File[]): Promise<void> {
  await Promise.all(files.map((f) => fs.promises.rm(f.path, { force: true })))
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
        if (err.code === 'LIMIT_FILE_COUNT')
          return res.status(413).json({ error: `Máximo ${MAX_PER_REQUEST} arquivos` })
        if (err.message === 'TYPE_NOT_ALLOWED') return res.status(415).json({ error: 'Tipo não permitido' })
        return res.status(400).json({ error: 'Falha no upload' })
      }
      next()
    })
  },
  asyncHandler(async (req: Request, res: Response) => {
    const files = (req.files as Express.Multer.File[] | undefined) ?? []
    const resposta = await processarEnvio(files).finally(() => apagarTemporarios(files))
    res.status(resposta.status).json(resposta.corpo)
  }),
)

export default router
export { UPLOAD_DIR }
