
import { Router, Request, Response } from 'express'
import { z } from 'zod'
import { and, desc, eq, gt, isNull, or } from 'drizzle-orm'
import { db } from '../db'
import { reminders, users } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { badRequest } from '../lib/errors'

const router = Router()

const CreateSchema = z.object({
  content:      z.string().min(1).max(500),
  durationMs:   z.number().int().min(60_000).max(365 * 86_400_000).optional(),
  dueAt:        z.string().datetime().optional(),
  channelId:    z.string().min(1).max(64).optional(),
  targetUserId: z.string().min(1).max(64).optional(),
}).refine((d) => d.durationMs || d.dueAt, { message: 'durationMs ou dueAt obrigatório' })

router.post('/', requireAuth, validate(CreateSchema), asyncHandler(async (req: Request, res: Response) => {
  const { content, durationMs, dueAt, channelId, targetUserId } = req.body as z.infer<typeof CreateSchema>

  const due = dueAt ? new Date(dueAt) : new Date(Date.now() + durationMs!)
  if (isNaN(due.getTime()) || due.getTime() < Date.now() + 30_000) {
    throw badRequest('dueAt precisa ser pelo menos 30s no futuro')
  }

  const target = targetUserId ?? req.userId!
  if (target !== req.userId) {

    const [u] = await db.select({ id: users.id }).from(users).where(eq(users.id, target)).limit(1)
    if (!u) return res.status(404).json({ error: 'Usuário-alvo não encontrado' })
  }

  const [created] = await db.insert(reminders).values({
    creatorId: req.userId!, targetUserId: target, content, channelId: channelId ?? null, dueAt: due,
  }).returning()

  res.status(201).json({ data: created })
}))

router.get('/', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const cutoff = new Date(Date.now() - 7 * 86_400_000)
  const rows = await db.select().from(reminders)
    .where(and(
      eq(reminders.targetUserId, req.userId!),
      or(isNull(reminders.deliveredAt), gt(reminders.deliveredAt, cutoff)),
    ))
    .orderBy(desc(reminders.dueAt))
    .limit(100)
  res.json({ data: rows })
}))

router.delete('/:id', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const { id } = req.params

  await db.delete(reminders).where(and(
    eq(reminders.id, id),
    eq(reminders.creatorId, req.userId!),
    isNull(reminders.deliveredAt),
  ))
  res.json({ data: { ok: true } })
}))

export default router
