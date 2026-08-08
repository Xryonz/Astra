
import { Router, Request, Response } from 'express'
import { and, desc, eq, inArray, isNull, lt, sql } from 'drizzle-orm'
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

    const payloads = items.map((n) => safeJson(n.payload))

    // CONSERTO DO HISTORICO: notificação antiga foi gravada sem o nome de quem
    // mandou (ou com o literal 'Alguém'), e payload gravado não se corrige
    // sozinho. Como o authorId esta la, da pra resolver o nome na leitura — UMA
    // consulta por pagina, so pelos ids que faltam. Sem isto o conserto so
    // valeria pra notificação nova e a lista seguiria cheia de "alguém".
    const faltando = [...new Set(
      payloads
        .filter((p) => !p.authorName || p.authorName === 'Alguém')
        .map((p) => p.authorId)
        .filter((id): id is string => typeof id === 'string'),
    )]
    if (faltando.length > 0) {
      const autores = await db.select({
        id: users.id, username: users.username, displayName: users.displayName, avatarUrl: users.avatarUrl,
      }).from(users).where(inArray(users.id, faltando))
      const porId = new Map(autores.map((a) => [a.id, a]))
      for (const p of payloads) {
        if (p.authorName && p.authorName !== 'Alguém') continue
        const a = typeof p.authorId === 'string' ? porId.get(p.authorId) : undefined
        if (!a) continue
        p.authorName     = a.displayName || a.username
        p.authorUsername = a.username
        p.authorAvatar   = p.authorAvatar ?? a.avatarUrl ?? null
      }
    }

    res.json({
      data: {
        items: items.map((n, i) => ({
          id:      n.id,
          type:    n.type,
          payload: payloads[i],
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

// LIMPAR: apaga de verdade, nao marca como lida. Sao duas intencoes diferentes —
// "read-all" zera o sino e mantem o historico; isto e a pessoa dizendo que nao
// quer mais ver aquilo. Apagar so as do proprio usuario, obviamente.
router.delete(
  '/notifications',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    await db.delete(notifications).where(eq(notifications.userId, req.userId!))
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

function safeJson(s: string): Record<string, unknown> {
  try {
    const v = JSON.parse(s)
    return v && typeof v === 'object' && !Array.isArray(v) ? v as Record<string, unknown> : {}
  } catch { return {} }
}

export default router
