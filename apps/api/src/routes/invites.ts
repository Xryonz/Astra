import { Router, Request, Response } from 'express'
import crypto from 'crypto'
import { and, asc, eq, sql } from 'drizzle-orm'
import { db } from '../db'
import { servers, serverMembers, channels, serverBans } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { authLimiter } from '../middleware/rateLimiter'
import { PERMS, getMemberPerms } from '../lib/permissions'
import { invalidateMembersCache } from '../lib/membersCache'

const router = Router()

// GET /api/invites/:code — preview público
router.get(
  '/:code',
  asyncHandler(async (req: Request, res: Response) => {
    const [server] = await db.select({
      id:         servers.id,
      name:       servers.name,
      iconUrl:    servers.iconUrl,
      bannerUrl:  servers.bannerUrl,
      isGroup:    servers.isGroup,
      inviteCode: servers.inviteCode,
    }).from(servers).where(eq(servers.inviteCode, req.params.code)).limit(1)

    if (!server) return res.status(404).json({ error: 'Convite inválido ou expirado' })

    const [{ count }] = await db.select({ count: sql<number>`count(*)::int` })
      .from(serverMembers).where(eq(serverMembers.serverId, server.id))

    res.json({ data: { ...server, _count: { members: count } } })
  })
)

// POST /api/invites/:code/join
router.post(
  '/:code/join',
  authLimiter,
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const [server] = await db.select().from(servers)
      .where(eq(servers.inviteCode, req.params.code)).limit(1)
    if (!server) return res.status(404).json({ error: 'Convite inválido ou expirado' })

    if (server.isGroup) {
      return res.status(403).json({
        error: 'Este grupo é privado. Peça ao administrador para te adicionar diretamente.',
      })
    }

    // Banimento por user sobrevive a kicks → impede rejoin via convite
    const [banned] = await db.select({ id: serverBans.id }).from(serverBans)
      .where(and(eq(serverBans.userId, req.userId!), eq(serverBans.serverId, server.id)))
      .limit(1)
    if (banned) return res.status(403).json({ error: 'Você está banido deste servidor' })

    const [already] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, req.userId!), eq(serverMembers.serverId, server.id)))
      .limit(1)
    if (already) return res.status(409).json({ error: 'Você já é membro deste servidor' })

    await db.insert(serverMembers).values({ userId: req.userId!, serverId: server.id, role: 'MEMBER' })
    void invalidateMembersCache(server.id)

    // Retorna shape com channels + _count.members (como Prisma include fazia)
    const [chRows, [countRow]] = await Promise.all([
      db.select().from(channels).where(eq(channels.serverId, server.id)).orderBy(asc(channels.createdAt)),
      db.select({ count: sql<number>`count(*)::int` }).from(serverMembers).where(eq(serverMembers.serverId, server.id)),
    ])

    res.json({ data: { ...server, channels: chRows, _count: { members: countRow?.count ?? 0 } } })
  })
)

// POST /api/invites/:serverId/regenerate
router.post(
  '/:serverId/regenerate',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId && !m.isOwner) return res.status(403).json({ error: 'Você não é membro' })
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_SERVER)) {
      return res.status(403).json({ error: 'Sem permissão pra regenerar o convite' })
    }

    const newCode = crypto.randomUUID()
    const [updated] = await db.update(servers).set({ inviteCode: newCode })
      .where(eq(servers.id, serverId))
      .returning({ inviteCode: servers.inviteCode })

    res.json({ data: { inviteCode: updated.inviteCode } })
  })
)

export default router
