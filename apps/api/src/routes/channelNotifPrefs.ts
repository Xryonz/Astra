
import { Router, Request, Response } from 'express'
import { and, eq, inArray } from 'drizzle-orm'
import { z } from 'zod'
import { db } from '../db'
import { channelNotifPrefs, channels, serverMembers } from '../db/schema'
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

export async function getNotifModesFor(
  channelId: string,
  userIds: string[],
): Promise<Map<string, 'all' | 'mentions' | 'mute'>> {
  const out = new Map<string, 'all' | 'mentions' | 'mute'>()
  for (const id of userIds) out.set(id, 'all')
  if (userIds.length === 0) return out
  const rows = await db.select({ userId: channelNotifPrefs.userId, mode: channelNotifPrefs.mode })
    .from(channelNotifPrefs)
    .where(and(
      eq(channelNotifPrefs.channelId, channelId),
      inArray(channelNotifPrefs.userId, userIds),
    ))
  for (const r of rows) {
    if (r.mode === 'mentions' || r.mode === 'mute') out.set(r.userId, r.mode)
  }
  return out
}

export default router

void channels; void serverMembers;
