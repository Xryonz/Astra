
import { Router, Request, Response } from 'express'
import { and, desc, eq, isNull, lt, sql } from 'drizzle-orm'
import { z } from 'zod'
import { db } from '../db'
import { users, notifications } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { DEFAULT_PREFS, parsePrefs } from '../lib/notifications'

const router = Router()

router.get(
  '/notifications',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const limit  = Math.min(Number(req.query.limit ?? 30), 100)
    const cursor = req.query.cursor as string | undefined

    let cursorDate: Date | null = null
    if (cursor) {
      try {
        const parsed = JSON.parse(Buffer.from(cursor, 'base64url').toString('utf8'))
        cursorDate = new Date(parsed.createdAt)
      } catch {}
    }

    const where = cursorDate
      ? and(eq(notifications.userId, req.userId!), lt(notifications.createdAt, cursorDate))
      : eq(notifications.userId, req.userId!)

    const rows = await db.select().from(notifications).where(where!)
      .orderBy(desc(notifications.createdAt)).limit(limit + 1)

    const hasMore    = rows.length > limit
    const items      = hasMore ? rows.slice(0, limit) : rows
    const last       = items[items.length - 1]
    const nextCursor = hasMore && last
      ? Buffer.from(JSON.stringify({ createdAt: last.createdAt.toISOString() })).toString('base64url')
      : null

    res.json({
      data: {
        items: items.map((n) => ({
          id:      n.id,
          type:    n.type,
          payload: safeJson(n.payload),
          readAt:  n.readAt?.toISOString() ?? null,
          createdAt: n.createdAt.toISOString(),
        })),
        nextCursor,
      },
    })
  })
)

router.get(
  '/notifications/unread',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const [row] = await db.select({ count: sql<number>`count(*)::int` })
      .from(notifications)
      .where(and(eq(notifications.userId, req.userId!), isNull(notifications.readAt)))
    res.json({ data: { count: row?.count ?? 0 } })
  })
)

router.post(
  '/notifications/:id/read',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { id } = req.params
    await db.update(notifications).set({ readAt: new Date() })
      .where(and(eq(notifications.id, id), eq(notifications.userId, req.userId!)))
    res.json({ data: { ok: true } })
  })
)

router.post(
  '/notifications/read-all',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    await db.update(notifications).set({ readAt: new Date() })
      .where(and(eq(notifications.userId, req.userId!), isNull(notifications.readAt)))
    res.json({ data: { ok: true } })
  })
)

const PrefsSchema = z.object({
  mentions:   z.boolean().optional(),
  dms:        z.boolean().optional(),
  reactions:  z.boolean().optional(),
  replies:    z.boolean().optional(),
  sounds:     z.boolean().optional(),
  desktop:    z.boolean().optional(),
  quietStart: z.number().int().min(0).max(23).nullable().optional(),
  quietEnd:   z.number().int().min(0).max(23).nullable().optional(),
})

router.get(
  '/notifications/prefs',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const [row] = await db.select({ raw: users.notificationPrefs })
      .from(users).where(eq(users.id, req.userId!)).limit(1)
    const prefs = parsePrefs(row?.raw ?? null)
    res.json({ data: { prefs, defaults: DEFAULT_PREFS } })
  })
)

router.patch(
  '/notifications/prefs',
  requireAuth,
  validate(PrefsSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const patch = req.body as z.infer<typeof PrefsSchema>
    const [row] = await db.select({ raw: users.notificationPrefs })
      .from(users).where(eq(users.id, req.userId!)).limit(1)
    const current = parsePrefs(row?.raw ?? null)
    const next    = { ...current, ...patch }
    await db.update(users).set({ notificationPrefs: JSON.stringify(next) })
      .where(eq(users.id, req.userId!))
    res.json({ data: { prefs: next } })
  })
)

function safeJson(s: string): unknown {
  try { return JSON.parse(s) } catch { return {} }
}

export default router
