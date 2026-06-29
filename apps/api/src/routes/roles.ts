import { Router, Request, Response } from 'express'
import { z } from 'zod'
import { and, asc, desc, eq, sql } from 'drizzle-orm'
import { db } from '../db'
import { roles, memberRoles, serverMembers, servers } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { PERMS, getMemberPerms } from '../lib/permissions'
import { AUDIT, audit } from '../lib/audit'

const HEX = /^#[0-9a-fA-F]{6}$/

const CreateRoleSchema = z.object({
  name:        z.string().min(1).max(50),
  color:       z.string().regex(HEX, 'Cor inválida (hex #RRGGBB)').optional().nullable(),
  permissions: z.array(z.string().max(40)).max(20).optional().default([]),
  hoist:       z.boolean().optional().default(false),
})

const UpdateRoleSchema = CreateRoleSchema.partial()

const PositionSchema = z.object({
  positions: z.array(z.object({ id: z.string(), position: z.number().int().min(0) })).max(50),
})

async function canManageRoles(userId: string, serverId: string) {
  const m = await getMemberPerms(userId, serverId)
  return m.isOwner || m.permissions.has(PERMS.MANAGE_ROLES)
}

export const rolesRouter = Router()

rolesRouter.get(
  '/:serverId/roles',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params

    const [member] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, req.userId!), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (!member) return res.status(403).json({ error: 'Acesso negado' })

    const rows = await db.select().from(roles)
      .where(eq(roles.serverId, serverId))
      .orderBy(desc(roles.position), asc(roles.createdAt))

    res.json({ data: rows.map((r) => ({ ...r, permissions: safeParseArr(r.permissions) })) })
  })
)

rolesRouter.post(
  '/:serverId/roles',
  requireAuth,
  validate(CreateRoleSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const [srv] = await db.select({ id: servers.id }).from(servers).where(eq(servers.id, serverId)).limit(1)
    if (!srv) return res.status(404).json({ error: 'Servidor não encontrado' })
    if (!(await canManageRoles(req.userId!, serverId)))
      return res.status(403).json({ error: 'Sem permissão pra gerenciar cargos' })

    const body = req.body as z.infer<typeof CreateRoleSchema>

    const [{ max } = { max: 0 }] = await db
      .select({ max: sql<number>`COALESCE(MAX(${roles.position}), 0)::int` })
      .from(roles)
      .where(eq(roles.serverId, serverId))

    const [created] = await db.insert(roles).values({
      serverId,
      name:        body.name,
      color:       body.color ?? null,
      position:    (max ?? 0) + 1,
      permissions: JSON.stringify(body.permissions ?? []),
      hoist:       body.hoist ?? false,
    }).returning()

    void audit({
      serverId, actorId: req.userId!, action: AUDIT.ROLE_CREATE,
      targetId: created.id, metadata: { name: created.name, color: created.color },
    })

    res.status(201).json({ data: { ...created, permissions: safeParseArr(created.permissions) } })
  })
)

rolesRouter.patch(
  '/:serverId/roles/:roleId',
  requireAuth,
  validate(UpdateRoleSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, roleId } = req.params
    if (!(await canManageRoles(req.userId!, serverId)))
      return res.status(403).json({ error: 'Sem permissão pra editar cargos' })

    const body = req.body as z.infer<typeof UpdateRoleSchema>
    const patch: Record<string, unknown> = {}
    if (body.name !== undefined)  patch.name = body.name
    if (body.color !== undefined) patch.color = body.color
    if (body.hoist !== undefined) patch.hoist = body.hoist
    if (body.permissions !== undefined) patch.permissions = JSON.stringify(body.permissions)
    if (Object.keys(patch).length === 0) return res.status(400).json({ error: 'Nada para atualizar' })

    const [updated] = await db.update(roles).set(patch)
      .where(and(eq(roles.id, roleId), eq(roles.serverId, serverId)))
      .returning()
    if (!updated) return res.status(404).json({ error: 'Cargo não encontrado' })

    void audit({
      serverId, actorId: req.userId!, action: AUDIT.ROLE_UPDATE,
      targetId: updated.id, metadata: { name: updated.name, patch },
    })

    res.json({ data: { ...updated, permissions: safeParseArr(updated.permissions) } })
  })
)

rolesRouter.patch(
  '/:serverId/roles/positions/batch',
  requireAuth,
  validate(PositionSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    if (!(await canManageRoles(req.userId!, serverId)))
      return res.status(403).json({ error: 'Sem permissão pra reordenar cargos' })

    const { positions } = req.body as z.infer<typeof PositionSchema>
    await db.transaction(async (tx) => {
      for (const p of positions) {
        await tx.update(roles).set({ position: p.position })
          .where(and(eq(roles.id, p.id), eq(roles.serverId, serverId)))
      }
    })
    res.json({ data: { ok: true } })
  })
)

rolesRouter.delete(
  '/:serverId/roles/:roleId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, roleId } = req.params
    if (!(await canManageRoles(req.userId!, serverId)))
      return res.status(403).json({ error: 'Sem permissão pra excluir cargos' })

    const r = await db.delete(roles)
      .where(and(eq(roles.id, roleId), eq(roles.serverId, serverId)))
      .returning({ id: roles.id })
    if (r.length === 0) return res.status(404).json({ error: 'Cargo não encontrado' })

    void audit({ serverId, actorId: req.userId!, action: AUDIT.ROLE_DELETE, targetId: roleId })

    res.json({ message: 'Cargo excluído' })
  })
)

rolesRouter.post(
  '/:serverId/members/:memberId/roles/:roleId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, memberId, roleId } = req.params
    if (!(await canManageRoles(req.userId!, serverId)))
      return res.status(403).json({ error: 'Sem permissão pra atribuir cargos' })

    const [[m], [r]] = await Promise.all([
      db.select({ id: serverMembers.id }).from(serverMembers)
        .where(and(eq(serverMembers.id, memberId), eq(serverMembers.serverId, serverId))).limit(1),
      db.select({ id: roles.id }).from(roles)
        .where(and(eq(roles.id, roleId), eq(roles.serverId, serverId))).limit(1),
    ])
    if (!m) return res.status(404).json({ error: 'Membro não encontrado' })
    if (!r) return res.status(404).json({ error: 'Cargo não encontrado' })

    const [existing] = await db.select({ id: memberRoles.id }).from(memberRoles)
      .where(and(eq(memberRoles.memberId, memberId), eq(memberRoles.roleId, roleId))).limit(1)
    if (!existing) await db.insert(memberRoles).values({ memberId, roleId })

    void audit({
      serverId, actorId: req.userId!, action: AUDIT.ROLE_ASSIGN,
      targetId: memberId, metadata: { roleId },
    })

    res.status(201).json({ data: { ok: true } })
  })
)

rolesRouter.delete(
  '/:serverId/members/:memberId/roles/:roleId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, memberId, roleId } = req.params
    if (!(await canManageRoles(req.userId!, serverId)))
      return res.status(403).json({ error: 'Sem permissão pra remover cargos' })

    await db.delete(memberRoles).where(and(
      eq(memberRoles.memberId, memberId),
      eq(memberRoles.roleId, roleId),
    ))

    void audit({
      serverId, actorId: req.userId!, action: AUDIT.ROLE_UNASSIGN,
      targetId: memberId, metadata: { roleId },
    })

    res.json({ data: { ok: true } })
  })
)

function safeParseArr(raw: unknown): string[] {
  if (typeof raw !== 'string') return []
  try { const v = JSON.parse(raw); return Array.isArray(v) ? v : [] } catch { return [] }
}
