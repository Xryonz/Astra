import { Router, Request, Response } from 'express'
import multer from 'multer'
import path from 'path'
import fs from 'fs'
import crypto from 'crypto'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'

const UPLOAD_DIR = path.resolve(process.cwd(), 'uploads')
if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true })

// SVG removido propositalmente: pode conter <script> e dispara XSS quando
// servido com Content-Type image/svg+xml e renderizado inline. Pra suportar
// SVG no futuro: sanitizar via dompurify server-side OU servir com
// Content-Disposition: attachment.
const ALLOWED_MIMES = new Set([
  'image/png','image/jpeg','image/gif','image/webp','image/avif',
  'video/mp4','video/webm','video/quicktime',
  'audio/mpeg','audio/wav','audio/ogg','audio/webm','audio/mp4','audio/x-m4a','audio/aac',
  'application/pdf','text/plain','application/zip','application/json',
])

// Browser pode mandar mime com codec suffix (ex: 'audio/webm;codecs=opus').
// Normalizamos comparando só a parte antes do ';'.
function isMimeAllowed(raw: string): boolean {
  const base = raw.split(';')[0].trim().toLowerCase()
  return ALLOWED_MIMES.has(base)
}

const MAX_FILE_SIZE  = 25 * 1024 * 1024 // 25 MB
const MAX_PER_REQUEST = 10

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, UPLOAD_DIR),
  filename:    (_req, file, cb) => {
    const ext = path.extname(file.originalname).slice(0, 16).toLowerCase().replace(/[^a-z0-9.]/g, '')
    const id  = crypto.randomBytes(16).toString('hex')
    cb(null, `${id}${ext}`)
  },
})

const upload = multer({
  storage,
  limits: { fileSize: MAX_FILE_SIZE, files: MAX_PER_REQUEST },
  fileFilter: (_req, file, cb) => {
    if (!isMimeAllowed(file.mimetype)) return cb(new Error('TYPE_NOT_ALLOWED'))
    cb(null, true)
  },
})

const router = Router()

// POST /api/upload — multipart, campo "files"
router.post(
  '/',
  requireAuth,
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

    const attachments = files.map((f) => ({
      url:  `/uploads/${f.filename}`,
      type: f.mimetype,
      name: f.originalname,
      size: f.size,
    }))
    res.json({ data: { attachments } })
  })
)

export default router
export { UPLOAD_DIR }
