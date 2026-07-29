
import { S3Client, PutObjectCommand } from '@aws-sdk/client-s3'
import path from 'path'
import fs from 'fs'
import crypto from 'crypto'
import sharp from 'sharp'

const {
  R2_ACCOUNT_ID,
  R2_ACCESS_KEY_ID,
  R2_SECRET_ACCESS_KEY,
  R2_BUCKET,
  R2_PUBLIC_URL,
} = process.env

const R2_READY = !!(R2_ACCOUNT_ID && R2_ACCESS_KEY_ID && R2_SECRET_ACCESS_KEY && R2_BUCKET && R2_PUBLIC_URL)

const UPLOAD_DIR = path.resolve(process.cwd(), 'uploads')

const s3 = R2_READY
  ? new S3Client({
      region: 'auto',
      endpoint: `https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com`,
      credentials: {
        accessKeyId:     R2_ACCESS_KEY_ID!,
        secretAccessKey: R2_SECRET_ACCESS_KEY!,
      },
    })
  : null

export const storageMode = R2_READY ? 'r2' : 'local'

// Host publico do R2 (quando configurado), derivado uma vez.
const R2_HOST = (() => {
  try { return R2_PUBLIC_URL ? new URL(R2_PUBLIC_URL).hostname : null } catch { return null }
})()

// URL que o PROPRIO app gerou (persistDataUri/putAttachment): path local /uploads/
// ou o host publico do R2. A validacao de host das rotas (isAllowedIcon do server,
// isAllowedImageUrl do perfil) precisa trata-las como confiaveis — senao reenviar um
// icone/banner JA salvo (o cliente reenvia o campo inalterado na proxima edicao) cai
// como "URL nao permitida": /uploads/x quebra o `new URL()` e o host do R2 nao esta
// no allowlist de terceiros. Bug do "troquei o icone, ai o banner nao troca mais".
export function isOwnStorageUrl(url: string | null | undefined): boolean {
  if (!url) return false
  if (url.startsWith('/uploads/')) return true
  if (!R2_HOST) return false
  try { const { hostname } = new URL(url); return hostname === R2_HOST || hostname.endsWith(`.${R2_HOST}`) }
  catch { return false }
}

export async function putAttachment(key: string, body: Buffer, mime: string): Promise<string> {
  if (s3) {
    await s3.send(new PutObjectCommand({
      Bucket: R2_BUCKET,
      Key: key,
      Body: body,
      ContentType: mime,

      CacheControl: 'public, max-age=31536000, immutable',
    }))
    return `${R2_PUBLIC_URL!.replace(/\/$/, '')}/${key}`
  }

  if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true })
  await fs.promises.writeFile(path.join(UPLOAD_DIR, key), body)
  return `/uploads/${key}`
}

function mimeExt(mime: string): string {
  switch (mime) {
    case 'image/png':  return 'png'
    case 'image/jpeg': return 'jpg'
    case 'image/webp': return 'webp'
    case 'image/gif':  return 'gif'
    default:           return 'bin'
  }
}

const DATA_URI_RE = /^data:([\w.+-]+\/[\w.+-]+)?(;base64)?,(.*)$/s

// Tira data-URIs do banco: decodifica, joga o binario no R2 (ou disco no
// fallback) e devolve a URL. Se ja for URL (ou vazio/null), passa direto.
// PNG/JPEG viram WebP (igual upload.ts, encolhe MUITO); GIF/WebP ficam como
// estao pra preservar animacao. Falha de decode -> guarda o original.
export async function persistDataUri<T extends string | null | undefined>(value: T): Promise<T | string> {
  if (!value || !value.startsWith('data:')) return value
  const m = DATA_URI_RE.exec(value)
  if (!m) return value

  const mime = m[1] || 'image/png'
  const input = m[2] ? Buffer.from(m[3], 'base64') : Buffer.from(decodeURIComponent(m[3]))

  let body: Buffer = input
  let outMime = mime
  let ext = mimeExt(mime)
  if (mime.startsWith('image/') && mime !== 'image/gif' && mime !== 'image/webp') {
    try {
      body = await sharp(input).webp({ quality: 82 }).toBuffer()
      outMime = 'image/webp'
      ext = 'webp'
    } catch { /* imagem estranha: guarda o original */ }
  }

  const key = `${crypto.randomBytes(16).toString('hex')}.${ext}`
  return putAttachment(key, body, outMime)
}
