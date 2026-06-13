/**
 * Descoberta (Discover) — diretório de constelações públicas. Donos/admins
 * ligam "Listar na Descoberta" nas configs (Server.isPublic). Qualquer um
 * pode entrar direto, sem convite.
 */
import { Router, Request, Response } from 'express'
import { and, desc, eq, ilike, sql } from 'drizzle-orm'
import { db } from '../db'
import { servers, serverMembers, serverBans } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { authLimiter } from '../middleware/rateLimiter'
import { invalidateMembersCache } from '../lib/membersCache'

const router = Router()

// GET /api/discover?q=texto — lista servidores públicos (não-grupos),
// ordenados por nº de membros. q filtra por nome (opcional).
router.get(
  '/',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const q = String(req.query.q ?? '').trim()
    const memberCount = sql<number>`(select count(*)::int from "ServerMember" where "ServerMember"."serverId" = ${servers.id})`

    const rows = await db.select({
      id:          servers.id,
      name:        servers.name,
      iconUrl:     servers.iconUrl,
      bannerUrl:   servers.bannerUrl,
      description: servers.description,
      members:     memberCount,
    })
      .from(servers)
      .where(q
        ? and(eq(servers.isPublic, true), eq(servers.isGroup, false), ilike(servers.name, `%${q}%`))
        : and(eq(servers.isPublic, true), eq(servers.isGroup, false)))
      .orderBy(desc(memberCount))
      .limit(60)

    res.json({ data: rows })
  })
)

// POST /api/discover/:serverId/join — entra direto num servidor público.
router.post(
  '/:serverId/join',
  authLimiter,
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const [server] = await db.select().from(servers).where(eq(servers.id, serverId)).limit(1)
    if (!server || !server.isPublic || server.isGroup) {
      return res.status(404).json({ error: 'Constelação não encontrada na Descoberta' })
    }

    const [banned] = await db.select({ id: serverBans.id }).from(serverBans)
      .where(and(eq(serverBans.userId, req.userId!), eq(serverBans.serverId, serverId)))
      .limit(1)
    if (banned) return res.status(403).json({ error: 'Você está banido deste servidor' })

    const [already] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, req.userId!), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (already) return res.status(409).json({ error: 'Você já é membro' })

    await db.insert(serverMembers).values({ userId: req.userId!, serverId, role: 'MEMBER' })
    void invalidateMembersCache(serverId)

    res.status(201).json({ data: { ok: true, serverId } })
  })
)

export default router
