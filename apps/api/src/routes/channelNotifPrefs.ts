
import { Router, Request, Response } from 'express'
import { and, eq, inArray } from 'drizzle-orm'
import { z } from 'zod'
import { db } from '../db'
import { channelNotifPrefs, serverNotifPrefs, channels, serverMembers } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { userCanSeeChannel } from '../lib/permissions'

const router = Router()

const MODES = ['all', 'mentions', 'mute'] as const
const PrefSchema = z.object({ mode: z.enum(MODES) })

router.get(
  '/channels/notification-prefs',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const rows = await db.select({
      channelId: channelNotifPrefs.channelId,
      mode:      channelNotifPrefs.mode,
    }).from(channelNotifPrefs).where(eq(channelNotifPrefs.userId, req.userId!))
    res.json({ data: rows })
  }),
)

router.put(
  '/channels/:channelId/notification-pref',
  requireAuth,
  validate(PrefSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { channelId } = req.params
    const { mode } = req.body as z.infer<typeof PrefSchema>

    const allowed = await userCanSeeChannel(req.userId!, channelId)
    if (!allowed) return res.status(403).json({ error: 'Acesso negado' })

    await db.insert(channelNotifPrefs).values({
      userId:    req.userId!,
      channelId,
      mode,
    }).onConflictDoUpdate({
      target: [channelNotifPrefs.userId, channelNotifPrefs.channelId],
      set:    { mode },
    })

    res.json({ data: { channelId, mode } })
  }),
)

router.delete(
  '/channels/:channelId/notification-pref',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { channelId } = req.params
    await db.delete(channelNotifPrefs).where(and(
      eq(channelNotifPrefs.userId,    req.userId!),
      eq(channelNotifPrefs.channelId, channelId),
    ))
    res.json({ data: { channelId, mode: 'all' } })
  }),
)

// ---- Pref por SERVIDOR (default dos canais sem pref propria) ----

router.get(
  '/servers/notification-prefs',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const rows = await db.select({
      serverId: serverNotifPrefs.serverId,
      mode:     serverNotifPrefs.mode,
    }).from(serverNotifPrefs).where(eq(serverNotifPrefs.userId, req.userId!))
    res.json({ data: rows })
  }),
)

router.put(
  '/servers/:serverId/notification-pref',
  requireAuth,
  validate(PrefSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const { mode } = req.body as z.infer<typeof PrefSchema>

    const [member] = await db.select({ userId: serverMembers.userId }).from(serverMembers)
      .where(and(eq(serverMembers.serverId, serverId), eq(serverMembers.userId, req.userId!)))
      .limit(1)
    if (!member) return res.status(403).json({ error: 'Acesso negado' })

    await db.insert(serverNotifPrefs).values({
      userId:   req.userId!,
      serverId,
      mode,
    }).onConflictDoUpdate({
      target: [serverNotifPrefs.userId, serverNotifPrefs.serverId],
      set:    { mode },
    })

    res.json({ data: { serverId, mode } })
  }),
)

router.delete(
  '/servers/:serverId/notification-pref',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    await db.delete(serverNotifPrefs).where(and(
      eq(serverNotifPrefs.userId,   req.userId!),
      eq(serverNotifPrefs.serverId, serverId),
    ))
    res.json({ data: { serverId, mode: 'all' } })
  }),
)

// Resolucao em camadas: pref explicita do CANAL (mesmo 'all') vence a do
// SERVIDOR, que vence o default 'all'.
export async function getNotifModesFor(
  channelId: string,
  userIds: string[],
): Promise<Map<string, 'all' | 'mentions' | 'mute'>> {
  const out = new Map<string, 'all' | 'mentions' | 'mute'>()
  for (const id of userIds) out.set(id, 'all')
  if (userIds.length === 0) return out

  const [ch] = await db.select({ serverId: channels.serverId })
    .from(channels).where(eq(channels.id, channelId)).limit(1)

  const [chRows, svRows] = await Promise.all([
    db.select({ userId: channelNotifPrefs.userId, mode: channelNotifPrefs.mode })
      .from(channelNotifPrefs)
      .where(and(
        eq(channelNotifPrefs.channelId, channelId),
        inArray(channelNotifPrefs.userId, userIds),
      )),
    ch
      ? db.select({ userId: serverNotifPrefs.userId, mode: serverNotifPrefs.mode })
          .from(serverNotifPrefs)
          .where(and(
            eq(serverNotifPrefs.serverId, ch.serverId),
            inArray(serverNotifPrefs.userId, userIds),
          ))
      : Promise.resolve([] as { userId: string; mode: string }[]),
  ])

  const explicit = new Set<string>()
  for (const r of chRows) {
    explicit.add(r.userId)
    if (r.mode === 'mentions' || r.mode === 'mute') out.set(r.userId, r.mode)
  }
  for (const r of svRows) {
    if (explicit.has(r.userId)) continue
    if (r.mode === 'mentions' || r.mode === 'mute') out.set(r.userId, r.mode)
  }
  return out
}

export default router
