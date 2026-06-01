/**
 * Banimentos. BAN remove o member e cria um registro em ServerBan
 * que sobrevive ao kick → impede rejoin via convite.
 * Gated por BAN_MEMBERS (owner ganha implícito).
 */
import { Router, Request, Response } from 'express'
import { z } from 'zod'
import { and, desc, eq } from 'drizzle-orm'
import { db } from '../db'
import { serverBans, serverMembers, users, servers } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { PERMS, getMemberPerms } from '../lib/permissions'
import { AUDIT, audit } from '../lib/audit'

export const bansRouter = Router()

const BanSchema = z.object({
  userId: z.string().min(1),
  reason: z.string().max(500).optional().nullable(),
})

// GET /api/servers/:serverId/bans
bansRouter.get(
  '/:serverId/bans',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.BAN_MEMBERS))
      return res.status(403).json({ error: 'Sem permissão' })

    const rows = await db.select({
      id:         serverBans.id,
      userId:     serverBans.userId,
      bannedById: serverBans.bannedById,
      reason:     serverBans.reason,
      createdAt:  serverBans.createdAt,
      user: {
        id:          users.id,
        username:    users.username,
        displayName: users.displayName,
        avatarUrl:   users.avatarUrl,
      },
    })
      .from(serverBans)
      .innerJoin(users, eq(users.id, serverBans.userId))
      .where(eq(serverBans.serverId, serverId))
      .orderBy(desc(serverBans.createdAt))

    res.json({ data: rows })
  })
)

// POST /api/servers/:serverId/bans
bansRouter.post(
  '/:serverId/bans',
  requireAuth,
  validate(BanSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const { userId, reason } = req.body as z.infer<typeof BanSchema>

    const requester = await getMemberPerms(req.userId!, serverId)
    if (!requester.memberId && !requester.isOwner) return res.status(403).json({ error: 'Você não é membro' })
    if (!requester.isOwner && !requester.permissions.has(PERMS.BAN_MEMBERS))
      return res.status(403).json({ error: 'Sem permissão pra banir' })

    if (userId === req.userId) return res.status(400).json({ error: 'Você não pode se banir' })

    const [srv] = await db.select({ ownerId: servers.ownerId }).from(servers)
      .where(eq(servers.id, serverId)).limit(1)
    if (!srv) return res.status(404).json({ error: 'Servidor não encontrado' })
    if (srv.ownerId === userId) return res.status(400).json({ error: 'Não é possível banir o dono' })

    // Hierarquia: não-owner não pode banir outro com BAN_MEMBERS
    if (!requester.isOwner) {
      const targetPerms = await getMemberPerms(userId, serverId)
      if (targetPerms.isOwner || targetPerms.permissions.has(PERMS.BAN_MEMBERS))
        return res.status(403).json({ error: 'Não pode banir alguém com mesma permissão' })
    }

    await db.transaction(async (tx) => {
      // upsert do ban
      const [existing] = await tx.select({ id: serverBans.id }).from(serverBans)
        .where(and(eq(serverBans.serverId, serverId), eq(serverBans.userId, userId))).limit(1)
      if (!existing) {
        await tx.insert(serverBans).values({
          serverId, userId, bannedById: req.userId!, reason: reason ?? null,
        })
      }
      // remove o member se existir
      await tx.delete(serverMembers).where(and(
        eq(serverMembers.userId, userId),
        eq(serverMembers.serverId, serverId),
      ))
    })

    void audit({
      serverId, actorId: req.userId!, action: AUDIT.MEMBER_BAN,
      targetId: userId, metadata: { reason: reason ?? null },
    })

    res.json({ data: { ok: true } })
  })
)

// DELETE /api/servers/:serverId/bans/:userId
bansRouter.delete(
  '/:serverId/bans/:userId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, userId } = req.params

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.BAN_MEMBERS))
      return res.status(403).json({ error: 'Sem permissão' })

    const r = await db.delete(serverBans)
      .where(and(eq(serverBans.serverId, serverId), eq(serverBans.userId, userId)))
      .returning({ id: serverBans.id })
    if (r.length === 0) return res.status(404).json({ error: 'Ban não encontrado' })

    void audit({
      serverId, actorId: req.userId!, action: AUDIT.MEMBER_UNBAN, targetId: userId,
    })

    res.json({ data: { ok: true } })
  })
)
