import { Router, Request, Response } from 'express'
import { z } from 'zod'
import { and, eq } from 'drizzle-orm'
import { db } from '../db'
import { pushSubscriptions, fcmTokens } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { getVapidPublicKey, isPushEnabled, sendPush } from '../lib/push'

const router = Router()

// GET /api/push/vapid-public-key
router.get(
  '/vapid-public-key',
  asyncHandler(async (_req: Request, res: Response) => {
    res.json({ data: { publicKey: getVapidPublicKey(), enabled: isPushEnabled() } })
  })
)

// POST /api/push/subscribe — body: { endpoint, keys: { p256dh, auth } }
const SubscribeSchema = z.object({
  endpoint: z.string().url(),
  keys: z.object({
    p256dh: z.string().min(1),
    auth:   z.string().min(1),
  }),
})

router.post(
  '/subscribe',
  requireAuth,
  validate(SubscribeSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { endpoint, keys } = req.body as z.infer<typeof SubscribeSchema>
    const userAgent = req.headers['user-agent']?.slice(0, 200) ?? null

    // Upsert manual: se já existe esse endpoint, atualiza o user
    const [existing] = await db.select({ id: pushSubscriptions.id })
      .from(pushSubscriptions).where(eq(pushSubscriptions.endpoint, endpoint)).limit(1)

    if (existing) {
      await db.update(pushSubscriptions)
        .set({ userId: req.userId!, p256dh: keys.p256dh, auth: keys.auth, userAgent })
        .where(eq(pushSubscriptions.id, existing.id))
    } else {
      await db.insert(pushSubscriptions).values({
        userId: req.userId!, endpoint, p256dh: keys.p256dh, auth: keys.auth, userAgent,
      })
    }

    res.status(201).json({ data: { ok: true } })
  })
)

// DELETE /api/push/subscribe?endpoint=...
router.delete(
  '/subscribe',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const endpoint = String(req.query.endpoint ?? '')
    if (!endpoint) return res.status(400).json({ error: 'endpoint obrigatório' })

    await db.delete(pushSubscriptions).where(and(
      eq(pushSubscriptions.userId, req.userId!),
      eq(pushSubscriptions.endpoint, endpoint),
    ))
    res.json({ data: { ok: true } })
  })
)

// ── FCM (app nativo) ──────────────────────────────────────────
// POST /api/push/fcm-token — registra/renova token do device
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

    // Upsert por token: device pode trocar de user (logout/login)
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

// DELETE /api/push/fcm-token — remove no logout
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

// POST /api/push/test — dispara um push de teste pro próprio user
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
