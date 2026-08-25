import { S3Client, PutObjectCommand, DeleteObjectCommand } from '@aws-sdk/client-s3'
import path from 'path'
import fs from 'fs'
import crypto from 'crypto'
import sharp from 'sharp'
import { logger } from './logger'
import { HttpError } from './errors'

const {
  R2_ACCOUNT_ID,
  R2_ACCESS_KEY_ID,
  R2_SECRET_ACCESS_KEY,
  R2_BUCKET,
  R2_PUBLIC_URL,
  S3_ENDPOINT,
  S3_REGION,
} = process.env

const ENDPOINT = S3_ENDPOINT || (R2_ACCOUNT_ID ? `https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com` : null)
const R2_READY = !!(ENDPOINT && R2_ACCESS_KEY_ID && R2_SECRET_ACCESS_KEY && R2_BUCKET && R2_PUBLIC_URL)

const UPLOAD_DIR = path.resolve(process.cwd(), 'uploads')

const s3 = R2_READY
  ? new S3Client({
      region: S3_REGION || 'auto',
      endpoint: ENDPOINT!,
      forcePathStyle: !!S3_ENDPOINT,
      credentials: {
        accessKeyId:     R2_ACCESS_KEY_ID!,
        secretAccessKey: R2_SECRET_ACCESS_KEY!,
      },
    })
  : null

export const storageMode = !R2_READY ? 'local' : (S3_ENDPOINT ? 's3' : 'r2')

export const storageFalta: string[] = (() => {
  if (R2_READY) return []
  const falta: string[] = []
  if (!ENDPOINT)             falta.push('S3_ENDPOINT')
  if (!R2_ACCESS_KEY_ID)     falta.push('R2_ACCESS_KEY_ID')
  if (!R2_SECRET_ACCESS_KEY) falta.push('R2_SECRET_ACCESS_KEY')
  if (!R2_BUCKET)            falta.push('R2_BUCKET')
  if (!R2_PUBLIC_URL)        falta.push('R2_PUBLIC_URL')
  return falta
})()

const R2_HOST = (() => {
  try { return R2_PUBLIC_URL ? new URL(R2_PUBLIC_URL).hostname : null } catch { return null }
})()

export function isOwnStorageUrl(url: string | null | undefined): boolean {
  if (!url) return false
  if (url.startsWith('/uploads/')) return true
  if (!R2_HOST) return false
  try { const { hostname } = new URL(url); return hostname === R2_HOST || hostname.endsWith(`.${R2_HOST}`) }
  catch { return false }
}

const CDN_DE_GIF = ['giphy.com', 'media.giphy.com']

export function urlDeAnexoPermitida(url: string | null | undefined): boolean {
  if (!url) return false
  if (isOwnStorageUrl(url)) return true
  try {
    const { hostname, protocol } = new URL(url)
    if (protocol !== 'https:') return false
    return CDN_DE_GIF.some((h) => hostname === h || hostname.endsWith(`.${h}`))
  } catch { return false }
}

export function primeiroAnexoNaoPermitido(
  anexos: ReadonlyArray<{ url?: string; thumbUrl?: string }> | undefined,
): string | null {
  for (const a of anexos ?? []) {
    if (!urlDeAnexoPermitida(a.url)) return a.url ?? '(vazia)'
    if (a.thumbUrl && !urlDeAnexoPermitida(a.thumbUrl)) return a.thumbUrl
  }
  return null
}

function abreInline(mime: string): boolean {
  const base = mime.split(';')[0].trim().toLowerCase()
  return base.startsWith('image/') || base.startsWith('video/') || base.startsWith('audio/')
}

const EXIGE_BUCKET = process.env.NODE_ENV === 'production'

export async function putAttachment(key: string, body: Buffer, mime: string): Promise<string> {
  if (!s3 && EXIGE_BUCKET) {
    logger.error(
      'storage',
      `RECUSANDO upload: em produção e sem bucket. Falta no ambiente: ${storageFalta.join(', ') || '(não sei dizer)'}. ` +
        'Gravar em disco aqui criaria URLs /uploads/ que morrem no próximo deploy.',
    )
    throw new HttpError(503, 'Armazenamento de imagens indisponível. Tente novamente em alguns minutos.', 'storage_indisponivel')
  }

  if (s3) {
    await s3.send(new PutObjectCommand({
      Bucket: R2_BUCKET,
      Key: key,
      Body: body,
      ContentType: mime,
      ...(abreInline(mime) ? {} : { ContentDisposition: 'attachment' }),

      CacheControl: 'public, max-age=31536000, immutable',
    }))
    return `${R2_PUBLIC_URL!.replace(/\/$/, '')}/${key}`
  }

  if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true })
  await fs.promises.writeFile(path.join(UPLOAD_DIR, key), body)
  return `/uploads/${key}`
}

export async function removeAttachment(url: string | null | undefined): Promise<void> {
  if (!url) return
  try {
    if (url.startsWith('/uploads/')) {
      await fs.promises.unlink(path.join(UPLOAD_DIR, url.slice('/uploads/'.length)))
      return
    }
    if (!s3 || !R2_PUBLIC_URL) return
    const raiz = R2_PUBLIC_URL.replace(/\/$/, '') + '/'
    if (!url.startsWith(raiz)) return  
    const key = url.slice(raiz.length)
    if (!key) return
    await s3.send(new DeleteObjectCommand({ Bucket: R2_BUCKET, Key: key }))
  } catch {  }
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
      body = await sharp(input)
        .webp({ quality: 92, effort: 6, alphaQuality: 100, smartSubsample: true })
        .toBuffer()
      outMime = 'image/webp'
      ext = 'webp'
    } catch {  }
  }

  const key = `${crypto.randomBytes(16).toString('hex')}.${ext}`
  return putAttachment(key, body, outMime)
}

const LADO_DE_EXIBICAO = 256

export async function persistImagemDeExibicao(
  value: string | null | undefined,
): Promise<{ url: string | null; original: string | null }> {
  if (!value) return { url: value ?? null, original: null }

  const original = await persistDataUri(value)
  if (typeof original !== 'string') return { url: null, original: null }

  if (!value.startsWith('data:')) return { url: original, original: null }
  if (original.endsWith('.gif')) return { url: original, original: null }

  try {
    const m = DATA_URI_RE.exec(value)
    if (!m) return { url: original, original: null }
    const bruto = m[2] ? Buffer.from(m[3], 'base64') : Buffer.from(decodeURIComponent(m[3]))

    const pequeno = await sharp(bruto)
      .resize({ width: LADO_DE_EXIBICAO, height: LADO_DE_EXIBICAO, fit: 'inside', withoutEnlargement: true })
      .webp({ quality: 90, effort: 6, alphaQuality: 100, smartSubsample: true })
      .toBuffer()

    if (pequeno.length >= bruto.length) return { url: original, original: null }

    const key = `${crypto.randomBytes(16).toString('hex')}.webp`
    const url = await putAttachment(key, pequeno, 'image/webp')
    return { url, original }
  } catch {
    return { url: original, original: null }
  }
}
