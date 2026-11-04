import { Router, Response } from 'express'
import type { Request } from '../lib/requisicao'
import { z } from 'zod'
import { and, eq } from 'drizzle-orm'
import { db } from '../db'
import { fcmTokens } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { sendPush } from '../lib/push'

const router = Router()

const FcmTokenSchema = z.object({
  token:    z.string().min(10),
  platform: z.enum(['android', 'ios']).default('android'),
})

router.post(
  '/fcm-token',
  requireAuth,
  validate(FcmTokenSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { token, platform } = req.body as z.infer<typeof FcmTokenSchema>

    const [existing] = await db.select({ id: fcmTokens.id })
      .from(fcmTokens).where(eq(fcmTokens.token, token)).limit(1)

    if (existing) {
      await db.update(fcmTokens)
        .set({ userId: req.userId!, platform, lastSeenAt: new Date() })
        .where(eq(fcmTokens.id, existing.id))
    } else {
      await db.insert(fcmTokens).values({ userId: req.userId!, token, platform })
    }
    res.status(201).json({ data: { ok: true } })
  })
)

router.delete(
  '/fcm-token',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const token = String(req.query.token ?? '')
    if (!token) return res.status(400).json({ error: 'token obrigatório' })
    await db.delete(fcmTokens).where(and(
      eq(fcmTokens.userId, req.userId!),
      eq(fcmTokens.token, token),
    ))
    res.json({ data: { ok: true } })
  })
)

router.post(
  '/test',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    await sendPush(req.userId!, {
      title: 'Astra · Teste',
      body:  'Notificações estão funcionando!',
      url:   '/app',
      tag:   'astra-test',
    })
    res.json({ data: { ok: true } })
  })
)

export default router
