/**
 * Badges (insígnias).
 *  - Servidor: o dono (ou MANAGE_SERVER) cria badges e concede a membros.
 *  - Global: derivadas em runtime (Pioneiro, Bot) — não vivem no banco.
 *
 * Dois routers: serverBadgesRouter (/api/servers) e userBadgesRouter
 * (/api/users) — o perfil busca badges de um usuário por aqui.
 */
import { Router, Request, Response } from 'express'
import { z } from 'zod'
import { and, desc, eq } from 'drizzle-orm'
import { db } from '../db'
import { badges, badgeGrants, users, servers } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { PERMS, getMemberPerms } from '../lib/permissions'

export const serverBadgesRouter = Router()
export const userBadgesRouter = Router()

const CreateBadgeSchema = z.object({
  name:        z.string().min(1, 'Nome obrigatório').max(40),
  icon:        z.string().min(1, 'Emoji obrigatório').max(16),
  color:       z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Use hex #RRGGBB').optional().nullable(),
  description: z.string().max(120).optional().nullable(),
})
const GrantSchema = z.object({ userId: z.string().min(1) })

// Badges globais derivadas (sem concessão manual).
function deriveGlobalBadges(u: { isBot: boolean; createdAt: Date | null }) {
  const out: Array<{ id: string; name: string; icon: string; color: string; description: string }> = []
  if (u.isBot) {
    out.push({ id: 'bot', name: 'Bot', icon: '🤖', color: '#6e6b95', description: 'Conta automatizada' })
  }
  // Pioneiro: contas dos primeiros tempos da Astra.
  const PIONEER_CUTOFF = new Date('2027-01-01')
  if (u.createdAt && u.createdAt < PIONEER_CUTOFF) {
    out.push({ id: 'pioneer', name: 'Pioneiro', icon: '✦', color: '#c9a96e', description: 'Esteve aqui desde o começo da Astra' })
  }
  return out
}

async function requireManage(userId: string, serverId: string): Promise<boolean> {
  const m = await getMemberPerms(userId, serverId)
  return m.isOwner || m.permissions.has(PERMS.MANAGE_SERVER)
}

// ── GET /api/users/:userId/badges ─────────────────────────────
userBadgesRouter.get(
  '/:userId/badges',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { userId } = req.params
    const [u] = await db.select({ isBot: users.isBot, createdAt: users.createdAt })
      .from(users).where(eq(users.id, userId)).limit(1)
    if (!u) return res.status(404).json({ error: 'Usuário não encontrado' })

    const server = await db.select({
      badgeId:     badges.id,
      name:        badges.name,
      icon:        badges.icon,
      color:       badges.color,
      description: badges.description,
      serverId:    badges.serverId,
      serverName:  servers.name,
    })
      .from(badgeGrants)
      .innerJoin(badges,  eq(badges.id, badgeGrants.badgeId))
      .innerJoin(servers, eq(servers.id, badges.serverId))
      .where(eq(badgeGrants.userId, userId))
      .orderBy(desc(badgeGrants.grantedAt))

    res.json({ data: { global: deriveGlobalBadges(u), server } })
  })
)

// ── GET /api/servers/:serverId/badges ─────────────────────────
serverBadgesRouter.get(
  '/:serverId/badges',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId && !m.isOwner) return res.status(403).json({ error: 'Você não é membro' })
    const rows = await db.select().from(badges)
      .where(eq(badges.serverId, serverId))
      .orderBy(desc(badges.createdAt))
    res.json({ data: rows })
  })
)

// ── POST /api/servers/:serverId/badges ────────────────────────
serverBadgesRouter.post(
  '/:serverId/badges',
  requireAuth,
  validate(CreateBadgeSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    if (!(await requireManage(req.userId!, serverId))) return res.status(403).json({ error: 'Sem permissão' })
    const { name, icon, color, description } = req.body as z.infer<typeof CreateBadgeSchema>
    const [b] = await db.insert(badges)
      .values({ serverId, name, icon, color: color ?? null, description: description ?? null })
      .returning()
    res.status(201).json({ data: b })
  })
)

// ── DELETE /api/servers/:serverId/badges/:badgeId ─────────────
serverBadgesRouter.delete(
  '/:serverId/badges/:badgeId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, badgeId } = req.params
    if (!(await requireManage(req.userId!, serverId))) return res.status(403).json({ error: 'Sem permissão' })
    const r = await db.delete(badges)
      .where(and(eq(badges.id, badgeId), eq(badges.serverId, serverId)))
      .returning({ id: badges.id })
    if (!r.length) return res.status(404).json({ error: 'Badge não encontrada' })
    res.json({ data: { ok: true } })
  })
)

// ── POST /api/servers/:serverId/badges/:badgeId/grants ────────
serverBadgesRouter.post(
  '/:serverId/badges/:badgeId/grants',
  requireAuth,
  validate(GrantSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, badgeId } = req.params
    if (!(await requireManage(req.userId!, serverId))) return res.status(403).json({ error: 'Sem permissão' })
    const [b] = await db.select({ id: badges.id }).from(badges)
      .where(and(eq(badges.id, badgeId), eq(badges.serverId, serverId))).limit(1)
    if (!b) return res.status(404).json({ error: 'Badge não encontrada' })
    await db.insert(badgeGrants)
      .values({ badgeId, userId: req.body.userId, grantedBy: req.userId! })
      .onConflictDoNothing()
    res.json({ data: { ok: true } })
  })
)

// ── DELETE /api/servers/:serverId/badges/:badgeId/grants/:userId ──
serverBadgesRouter.delete(
  '/:serverId/badges/:badgeId/grants/:userId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, badgeId, userId } = req.params
    if (!(await requireManage(req.userId!, serverId))) return res.status(403).json({ error: 'Sem permissão' })
    await db.delete(badgeGrants)
      .where(and(eq(badgeGrants.badgeId, badgeId), eq(badgeGrants.userId, userId)))
    res.json({ data: { ok: true } })
  })
)
