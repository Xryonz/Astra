import { Router, Request, Response } from 'express'
import { and, desc, eq, inArray, ne } from 'drizzle-orm'
import { z } from 'zod'
import { db } from '../db'
import { users, servers, serverMembers, profileNotes, friendships } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { UpdateProfileSchema, ProfileNoteSchema } from '@astra/types'
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

router.patch(
  '/',
  requireAuth,
  validate(UpdateProfileSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const {
      displayName, username, bio, avatarUrl, bannerUrl, bannerColor, profileTheme,
      bannerPositionY, bannerScale, bannerBorder, bannerTextColor,
      pronouns, statusEmoji, displayFont,
    } = req.body

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
    if (bannerPositionY !== undefined) update.bannerPositionY = bannerPositionY
    if (bannerScale     !== undefined) update.bannerScale     = bannerScale
    if (bannerBorder    !== undefined) update.bannerBorder    = bannerBorder
    if (bannerTextColor !== undefined) update.bannerTextColor = bannerTextColor
    if (pronouns         !== undefined) update.pronouns         = pronouns
    if (statusEmoji      !== undefined) update.statusEmoji      = statusEmoji
    if (displayFont      !== undefined) update.displayFont      = displayFont

    const [user] = await db.update(users).set(update)
      .where(eq(users.id, req.userId!))
      .returning({
        id: users.id, email: users.email, username: users.username,
        displayName: users.displayName, avatarUrl: users.avatarUrl,
        bio: users.bio, bannerUrl: users.bannerUrl, bannerColor: users.bannerColor,
        profileTheme: users.profileTheme,
        bannerPositionY: users.bannerPositionY,
        bannerScale:     users.bannerScale,
        bannerBorder:    users.bannerBorder,
        bannerTextColor: users.bannerTextColor,
        pronouns: users.pronouns, statusEmoji: users.statusEmoji,
        displayFont: users.displayFont,
      })

    res.json({ data: { user } })
  })
)

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

const PreferencesSchema = z.object({
  preferences: z.record(z.unknown()),
})

router.get(
  '/preferences',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const [row] = await db.select({ preferences: users.preferences })
      .from(users).where(eq(users.id, req.userId!)).limit(1)
    if (!row) return res.status(404).json({ error: 'Usuário não encontrado' })
    let parsed: Record<string, unknown> = {}
    if (row.preferences) {
      try { parsed = JSON.parse(row.preferences) } catch {}
    }
    res.json({ data: { preferences: parsed } })
  })
)

router.patch(
  '/preferences',
  requireAuth,
  validate(PreferencesSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const payload = (req.body as { preferences: Record<string, unknown> }).preferences
    const serialized = JSON.stringify(payload)
    if (serialized.length > 4096) {
      return res.status(413).json({ error: 'Preferências excedem 4KB' })
    }
    await db.update(users).set({ preferences: serialized })
      .where(eq(users.id, req.userId!))
    res.json({ data: { preferences: payload } })
  })
)

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
      bannerPositionY: users.bannerPositionY,
      bannerScale:     users.bannerScale,
      bannerBorder:    users.bannerBorder,
      bannerTextColor: users.bannerTextColor,
      pronouns: users.pronouns, statusEmoji: users.statusEmoji,
      customStatus: users.customStatus,
      displayFont: users.displayFont,
      isBot: users.isBot,
      status: users.status,
      createdAt: users.createdAt,
    }).from(users).where(eq(users.id, targetId)).limit(1)
    if (!user) return res.status(404).json({ error: 'Usuário não encontrado' })

    const liveStatus = await getUserStatus(targetId)
    const isSelf = targetId === req.userId
    let effectiveStatus: 'ONLINE'|'IDLE'|'DND'|'INVISIBLE'|'OFFLINE'
    if (!liveStatus) effectiveStatus = 'OFFLINE'
    else if (liveStatus === 'INVISIBLE' && !isSelf) effectiveStatus = 'OFFLINE'
    else effectiveStatus = liveStatus
    ;(user as any).effectiveStatus = effectiveStatus

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

router.get(
  '/:userId/notes',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const targetId = req.params.userId
    const rows = await db.select({
      id:        profileNotes.id,
      content:   profileNotes.content,
      pinned:    profileNotes.pinned,
      createdAt: profileNotes.createdAt,
      authorId:        users.id,
      authorUsername:  users.username,
      authorDisplay:   users.displayName,
      authorAvatar:    users.avatarUrl,
    })
      .from(profileNotes)
      .innerJoin(users, eq(users.id, profileNotes.authorId))
      .where(eq(profileNotes.profileUserId, targetId))
      .orderBy(desc(profileNotes.pinned), desc(profileNotes.createdAt))
      .limit(40)

    const items = rows.map((r) => ({
      id:        r.id,
      content:   r.content,
      pinned:    r.pinned,
      createdAt: r.createdAt.toISOString(),
      author: {
        id: r.authorId, username: r.authorUsername,
        displayName: r.authorDisplay, avatarUrl: r.authorAvatar,
      },
    }))
    res.json({ data: items })
  })
)

router.post(
  '/:userId/notes',
  requireAuth,
  validate(ProfileNoteSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const targetId = req.params.userId
    const me       = req.userId!
    if (targetId === me) return res.status(400).json({ error: 'Não dá pra escrever no próprio mural' })

    const [a, b] = me < targetId ? [me, targetId] : [targetId, me]
    const [friendRow] = await db.select({ id: friendships.id })
      .from(friendships)
      .where(and(
        eq(friendships.userAId, a), eq(friendships.userBId, b),
        eq(friendships.status, 'accepted'),
      )).limit(1)
    if (!friendRow) return res.status(403).json({ error: 'Apenas amigos podem deixar nota' })

    const { content } = req.body as { content: string }
    const trimmed = content.trim()
    if (!trimmed) return res.status(400).json({ error: 'Nota vazia' })

    const [existing] = await db.select({ id: profileNotes.id })
      .from(profileNotes)
      .where(and(eq(profileNotes.profileUserId, targetId), eq(profileNotes.authorId, me)))
      .limit(1)

    if (existing) {
      await db.update(profileNotes)
        .set({ content: trimmed, createdAt: new Date() })
        .where(eq(profileNotes.id, existing.id))
      return res.json({ data: { id: existing.id, content: trimmed, updated: true } })
    }

    const [inserted] = await db.insert(profileNotes).values({
      profileUserId: targetId,
      authorId:      me,
      content:       trimmed,
    }).returning({ id: profileNotes.id })
    res.json({ data: { id: inserted.id, content: trimmed, updated: false } })
  })
)

router.delete(
  '/notes/:noteId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const noteId = req.params.noteId
    const me     = req.userId!
    const [note] = await db.select({
      id: profileNotes.id, authorId: profileNotes.authorId, profileUserId: profileNotes.profileUserId,
    }).from(profileNotes).where(eq(profileNotes.id, noteId)).limit(1)
    if (!note) return res.status(404).json({ error: 'Nota não encontrada' })
    if (note.authorId !== me && note.profileUserId !== me) {
      return res.status(403).json({ error: 'Sem permissão' })
    }
    await db.delete(profileNotes).where(eq(profileNotes.id, noteId))
    res.json({ data: { ok: true } })
  })
)

router.patch(
  '/notes/:noteId/pin',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const noteId = req.params.noteId
    const me     = req.userId!
    const [note] = await db.select({
      id: profileNotes.id, pinned: profileNotes.pinned, profileUserId: profileNotes.profileUserId,
    }).from(profileNotes).where(eq(profileNotes.id, noteId)).limit(1)
    if (!note) return res.status(404).json({ error: 'Nota não encontrada' })
    if (note.profileUserId !== me) return res.status(403).json({ error: 'Apenas o dono do mural' })

    if (!note.pinned) {
      await db.update(profileNotes).set({ pinned: false })
        .where(eq(profileNotes.profileUserId, me))
    }
    await db.update(profileNotes).set({ pinned: !note.pinned })
      .where(eq(profileNotes.id, noteId))
    res.json({ data: { pinned: !note.pinned } })
  })
)

export default router
