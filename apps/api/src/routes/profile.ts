import { Router, Request, Response } from 'express'
import { and, eq, inArray, ne } from 'drizzle-orm'
import { z } from 'zod'
import { db } from '../db'
import { users, servers, serverMembers } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { UpdateProfileSchema } from '@umbra/types'
import { getUserStatus, setUserOnline } from '../lib/redis'

const router = Router()

const ALLOWED_HOSTS = [
  'i.imgur.com',
  'media.giphy.com',
  'cdn.discordapp.com',
  'media.tenor.com',
  'i.postimg.cc',
  'images.unsplash.com',
  'lh3.googleusercontent.com',
  'pbs.twimg.com',
  'media.discordapp.net',
  'cdn.jsdelivr.net',
  'raw.githubusercontent.com',
]

function isAllowedImageUrl(url: string | null | undefined): boolean {
  if (!url) return true
  if (url.startsWith('data:image/')) return true
  try {
    const { hostname } = new URL(url)
    return ALLOWED_HOSTS.some((h) => hostname === h || hostname.endsWith(`.${h}`))
  } catch {
    return false
  }
}

function isDataUriTooLarge(url: string | null | undefined): boolean {
  if (!url || !url.startsWith('data:')) return false
  const bytes = url.length * 0.75
  return bytes > 6 * 1024 * 1024
}

// GET /api/profile/lookup?ids=a,b,c — batch lookup pra UIs (voice panel, etc)
router.get(
  '/lookup',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const raw = String(req.query.ids ?? '')
    const ids = raw.split(',').map((s) => s.trim()).filter(Boolean).slice(0, 50)
    if (ids.length === 0) return res.json({ data: [] })
    const rows = await db.select({
      id:          users.id,
      username:    users.username,
      displayName: users.displayName,
      avatarUrl:   users.avatarUrl,
      bannerColor: users.bannerColor,
    }).from(users).where(inArray(users.id, ids))
    res.json({ data: rows })
  })
)

// PATCH /api/profile
router.patch(
  '/',
  requireAuth,
  validate(UpdateProfileSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { displayName, username, bio, avatarUrl, bannerUrl, bannerColor, profileTheme } = req.body

    if (bannerUrl && !isAllowedImageUrl(bannerUrl)) {
      return res.status(422).json({
        error: 'URL do banner não é de um host permitido. Use Imgur, Giphy, Tenor ou envie um arquivo.',
      })
    }
    if (isDataUriTooLarge(bannerUrl)) {
      return res.status(413).json({ error: 'Arquivo muito grande. Máximo 5MB.' })
    }
    if (isDataUriTooLarge(avatarUrl)) {
      return res.status(413).json({ error: 'Avatar muito grande. Máximo 5MB.' })
    }

    // Username uniqueness
    if (username) {
      const [conflict] = await db.select({ id: users.id }).from(users)
        .where(and(eq(users.username, username), ne(users.id, req.userId!)))
        .limit(1)
      if (conflict) return res.status(409).json({ error: 'Username já está em uso' })
    }

    const update: Record<string, unknown> = {}
    if (displayName !== undefined) update.displayName = displayName
    if (username    !== undefined) update.username    = username
    if (bio         !== undefined) update.bio         = bio
    if (avatarUrl   !== undefined) update.avatarUrl   = avatarUrl
    if (bannerUrl   !== undefined) update.bannerUrl   = bannerUrl
    if (bannerColor  !== undefined) update.bannerColor  = bannerColor
    if (profileTheme !== undefined) update.profileTheme = profileTheme

    const [user] = await db.update(users).set(update)
      .where(eq(users.id, req.userId!))
      .returning({
        id: users.id, email: users.email, username: users.username,
        displayName: users.displayName, avatarUrl: users.avatarUrl,
        bio: users.bio, bannerUrl: users.bannerUrl, bannerColor: users.bannerColor,
        profileTheme: users.profileTheme,
      })

    res.json({ data: { user } })
  })
)

// GET /api/profile/presence?ids=a,b,c — bulk status pra members lists
router.get(
  '/presence',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const ids = String(req.query.ids ?? '').split(',').map((s) => s.trim()).filter(Boolean).slice(0, 200)
    if (ids.length === 0) return res.json({ data: {} })

    const out: Record<string, 'ONLINE'|'IDLE'|'DND'|'OFFLINE'> = {}
    const live = await Promise.all(ids.map((id) => getUserStatus(id)))
    ids.forEach((id, i) => {
      const s = live[i]
      if (!s) out[id] = 'OFFLINE'
      else if (s === 'INVISIBLE') out[id] = 'OFFLINE'
      else out[id] = s
    })
    res.json({ data: out })
  })
)

// PATCH /api/profile/status — set chosen presence status
const StatusSchema = z.object({ status: z.enum(['ONLINE','IDLE','DND','INVISIBLE']) })

router.patch(
  '/status',
  requireAuth,
  validate(StatusSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { status } = req.body as { status: 'ONLINE'|'IDLE'|'DND'|'INVISIBLE' }
    await db.update(users).set({ status }).where(eq(users.id, req.userId!))
    await setUserOnline(req.userId!, status)
    res.json({ data: { status } })
  })
)

// GET /api/profile/:userId
router.get(
  '/:userId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const targetId = req.params.userId
    const [user] = await db.select({
      id: users.id, username: users.username, displayName: users.displayName,
      avatarUrl: users.avatarUrl, bio: users.bio,
      bannerUrl: users.bannerUrl, bannerColor: users.bannerColor,
      profileTheme: users.profileTheme,
      isBot: users.isBot,
      status: users.status,
      createdAt: users.createdAt,
    }).from(users).where(eq(users.id, targetId)).limit(1)
    if (!user) return res.status(404).json({ error: 'Usuário não encontrado' })

    // Status efetivo: combina socket vivo (Redis) com status preferido (DB).
    // Se INVISIBLE e não for o próprio user pedindo, devolve OFFLINE.
    const liveStatus = await getUserStatus(targetId)
    const isSelf = targetId === req.userId
    let effectiveStatus: 'ONLINE'|'IDLE'|'DND'|'INVISIBLE'|'OFFLINE'
    if (!liveStatus) effectiveStatus = 'OFFLINE'
    else if (liveStatus === 'INVISIBLE' && !isSelf) effectiveStatus = 'OFFLINE'
    else effectiveStatus = liveStatus
    ;(user as any).effectiveStatus = effectiveStatus

    // Mutual servers: intersect requester's memberships with target's memberships
    const [myMems, theirMems] = await Promise.all([
      db.select({ serverId: serverMembers.serverId }).from(serverMembers).where(eq(serverMembers.userId, req.userId!)),
      db.select({ serverId: serverMembers.serverId, role: serverMembers.role }).from(serverMembers).where(eq(serverMembers.userId, targetId)),
    ])
    const mySet = new Set(myMems.map((m) => m.serverId))
    const mutualIds = theirMems.map((m) => m.serverId).filter((id) => mySet.has(id))

    let mutualServers: { id: string; name: string; iconUrl: string|null; isGroup: boolean; role: string }[] = []
    if (mutualIds.length > 0) {
      const srvs = await db.select({ id: servers.id, name: servers.name, iconUrl: servers.iconUrl, isGroup: servers.isGroup })
        .from(servers).where(inArray(servers.id, mutualIds))
      const roleByServer = new Map(theirMems.map((m) => [m.serverId, m.role]))
      mutualServers = srvs.map((s) => ({ ...s, role: roleByServer.get(s.id) ?? 'MEMBER' }))
    }

    res.json({ data: { user, mutualServers } })
  })
)

// GET /api/profile/by-username/:username — compact card pra hover preview
router.get(
  '/by-username/:username',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { username } = req.params
    const [u] = await db.select({
      id: users.id, username: users.username, displayName: users.displayName,
      avatarUrl: users.avatarUrl, bio: users.bio,
      bannerUrl: users.bannerUrl, bannerColor: users.bannerColor,
      isBot: users.isBot,
      customStatus: users.customStatus,
    }).from(users).where(eq(users.username, username)).limit(1)
    if (!u) return res.status(404).json({ error: 'Usuário não encontrado' })

    const liveStatus = await getUserStatus(u.id)
    const effectiveStatus =
      !liveStatus ? 'OFFLINE'
      : liveStatus === 'INVISIBLE' && u.id !== req.userId ? 'OFFLINE'
      : liveStatus
    res.json({ data: { ...u, effectiveStatus } })
  })
)

export default router
