import { Router, Request, Response } from 'express'
import multer from 'multer'
import path from 'path'
import fs from 'fs'
import crypto from 'crypto'
import sharp from 'sharp'
import { and, asc, eq, inArray } from 'drizzle-orm'
import { z } from 'zod'
import { db } from '../db'
import { serverEmojis, serverMembers } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { getMemberPerms, PERMS } from '../lib/permissions'
import { UPLOAD_DIR } from './upload'

const MAX_EMOJIS = 50
const MAX_SIZE   = 512 * 1024
const EMOJI_PX   = 128

const upload = multer({
  storage: multer.memoryStorage(),
  limits:  { fileSize: MAX_SIZE, files: 1 },
  fileFilter: (_req, file, cb) => {
    const m = file.mimetype.split(';')[0].toLowerCase()
    if (!['image/png','image/jpeg','image/webp','image/avif','image/gif'].includes(m)) {
      return cb(new Error('TYPE_NOT_ALLOWED'))
    }
    cb(null, true)
  },
})

const router = Router()

const NAME_RE = /^[a-z0-9_]{2,32}$/i

async function ensureCanManage(serverId: string, userId: string): Promise<boolean> {
  const perms = await getMemberPerms(userId, serverId)
  if (!perms.memberId && !perms.isOwner) return false
  if (perms.isOwner) return true
  return perms.permissions.has(PERMS.MANAGE_CHANNELS)
}

router.get(
  '/:serverId/emojis',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params

    const [m] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, req.userId!), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (!m) return res.status(403).json({ error: 'Acesso negado' })

    const rows = await db.select().from(serverEmojis)
      .where(eq(serverEmojis.serverId, serverId))
      .orderBy(asc(serverEmojis.name))
    res.json({ data: rows })
  })
)

router.post(
  '/:serverId/emojis',
  requireAuth,
  (req: Request, res: Response, next) => {
    upload.single('file')(req, res, (err: any) => {
      if (err) {
        if (err.code === 'LIMIT_FILE_SIZE') return res.status(413).json({ error: 'Emoji maior que 512KB' })
        if (err.message === 'TYPE_NOT_ALLOWED') return res.status(415).json({ error: 'Tipo não permitido' })
        return res.status(400).json({ error: 'Falha no upload' })
      }
      next()
    })
  },
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const name = String(req.body.name ?? '').trim()
    if (!NAME_RE.test(name)) {
      return res.status(422).json({ error: 'Nome inválido (2-32 chars, alfanum + underscore)' })
    }
    if (!(await ensureCanManage(serverId, req.userId!))) {
      return res.status(403).json({ error: 'Sem permissão' })
    }
    const file = req.file
    if (!file) return res.status(400).json({ error: 'Arquivo obrigatório' })

    const existing = await db.select({ id: serverEmojis.id, name: serverEmojis.name })
      .from(serverEmojis).where(eq(serverEmojis.serverId, serverId))
    if (existing.length >= MAX_EMOJIS) {
      return res.status(429).json({ error: `Limite de ${MAX_EMOJIS} emojis por servidor atingido` })
    }
    if (existing.some((e) => e.name.toLowerCase() === name.toLowerCase())) {
      return res.status(409).json({ error: `Já existe um emoji chamado :${name}:` })
    }

    const isAnimated = file.mimetype.includes('gif')
    let buffer: Buffer, ext: string, mime: string
    if (isAnimated) {

      buffer = await sharp(file.buffer, { animated: true })
        .resize({ width: EMOJI_PX, height: EMOJI_PX, fit: 'inside' })
        .webp({ quality: 80, effort: 4 })
        .toBuffer()
      ext  = '.webp'
      mime = 'image/webp'
    } else {
      buffer = await sharp(file.buffer)
        .rotate()
        .resize({ width: EMOJI_PX, height: EMOJI_PX, fit: 'inside', withoutEnlargement: true })
        .webp({ quality: 88, effort: 4 })
        .toBuffer()
      ext  = '.webp'
      mime = 'image/webp'
    }

    const id  = crypto.randomBytes(16).toString('hex')
    const filename = `${id}${ext}`
    await fs.promises.writeFile(path.join(UPLOAD_DIR, filename), buffer)

    const [created] = await db.insert(serverEmojis).values({
      serverId,
      name,
      url:       `/uploads/${filename}`,
      createdBy: req.userId!,
    }).returning()

    res.status(201).json({ data: { ...created, mime } })
  })
)

router.delete(
  '/:serverId/emojis/:emojiId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, emojiId } = req.params
    if (!(await ensureCanManage(serverId, req.userId!))) {
      return res.status(403).json({ error: 'Sem permissão' })
    }
    const result = await db.delete(serverEmojis)
      .where(and(eq(serverEmojis.id, emojiId), eq(serverEmojis.serverId, serverId)))
      .returning({ id: serverEmojis.id, url: serverEmojis.url })
    if (result.length === 0) return res.status(404).json({ error: 'Emoji não encontrado' })

    const url = result[0].url
    if (url.startsWith('/uploads/')) {
      const p = path.join(UPLOAD_DIR, url.replace('/uploads/', ''))
      fs.promises.unlink(p).catch(() => {})
    }
    res.json({ data: { ok: true } })
  })
)

const RenameSchema = z.object({ name: z.string().regex(NAME_RE) })
router.patch(
  '/:serverId/emojis/:emojiId',
  requireAuth,
  validate(RenameSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, emojiId } = req.params
    if (!(await ensureCanManage(serverId, req.userId!))) {
      return res.status(403).json({ error: 'Sem permissão' })
    }
    const { name } = req.body as z.infer<typeof RenameSchema>
    try {
      const [updated] = await db.update(serverEmojis)
        .set({ name })
        .where(and(eq(serverEmojis.id, emojiId), eq(serverEmojis.serverId, serverId)))
        .returning()
      if (!updated) return res.status(404).json({ error: 'Emoji não encontrado' })
      res.json({ data: updated })
    } catch (e: any) {
      if (e?.code === '23505') return res.status(409).json({ error: `Já existe :${name}:` })
      throw e
    }
  })
)

export default router
