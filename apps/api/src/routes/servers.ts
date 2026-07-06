import { Router, Request, Response } from 'express'
import { z } from 'zod'
import { and, asc, eq, inArray, sql } from 'drizzle-orm'
import { db } from '../db'
import { servers, serverMembers, channels, channelCategories, channelRolePerms, users, roles, memberRoles, serverBans, auditLogs, messages, friendships, notifications } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { CreateServerSchema, CreateChannelSchema } from '@astra/types'
import { PERMS, getMemberPerms, filterVisibleChannels } from '../lib/permissions'
import { AUDIT, audit } from '../lib/audit'
import { createId } from '../db/cuid'
import { invalidateMembersCache } from '../lib/membersCache'

export const serversRouter = Router()

// Le categorias sem derrubar a request se a tabela ChannelCategory ainda nao
// existir no banco (o boot ensureCategorySchema deve cria-la, mas nao dependemos
// disso: categorias sao opcionais, uma constelacao sem elas so mostra canais soltos).
async function safeCategoryRows(serverIds: string[]) {
  if (serverIds.length === 0) return []
  try {
    return await db.select().from(channelCategories)
      .where(inArray(channelCategories.serverId, serverIds))
      .orderBy(asc(channelCategories.position))
  } catch (e) {
    console.warn('[servers] categorias indisponiveis:', (e as Error).message)
    return []
  }
}

async function listServersForUser(userId: string) {

  const myMemberships = await db.select({ serverId: serverMembers.serverId })
    .from(serverMembers)
    .where(eq(serverMembers.userId, userId))

  const serverIds = myMemberships.map((m) => m.serverId)
  if (serverIds.length === 0) return []

  const [srvRows, chRows, countRows, catRows] = await Promise.all([
    db.select().from(servers).where(inArray(servers.id, serverIds)).orderBy(asc(servers.createdAt)),
    db.select().from(channels).where(inArray(channels.serverId, serverIds)).orderBy(asc(channels.position), asc(channels.createdAt)),
    db.select({ serverId: serverMembers.serverId, count: sql<number>`count(*)::int` })
      .from(serverMembers)
      .where(inArray(serverMembers.serverId, serverIds))
      .groupBy(serverMembers.serverId),
    safeCategoryRows(serverIds),
  ])

  const channelIds = chRows.map((c) => c.id)
  let lastByChannel = new Map<string, Date>()
  if (channelIds.length > 0) {
    const lastRows = await db.select({
      channelId: messages.channelId,
      lastAt:    sql<Date>`MAX(${messages.createdAt})`.as('lastAt'),
    })
      .from(messages)
      .where(inArray(messages.channelId, channelIds))
      .groupBy(messages.channelId)
    lastByChannel = new Map(lastRows.map((r) => [r.channelId, r.lastAt]))
  }

  const visible = await filterVisibleChannels(userId, channelIds)
  const channelsByServer = new Map<string, Array<typeof chRows[number] & { lastMessageAt: Date | null }>>()
  for (const c of chRows) {
    if (!visible.has(c.id)) continue
    const enriched = { ...c, lastMessageAt: lastByChannel.get(c.id) ?? null }
    const arr = channelsByServer.get(c.serverId) ?? []
    arr.push(enriched)
    channelsByServer.set(c.serverId, arr)
  }
  const countByServer = new Map(countRows.map((r) => [r.serverId, r.count]))
  const catsByServer = new Map<string, Array<typeof catRows[number]>>()
  for (const c of catRows) {
    const arr = catsByServer.get(c.serverId) ?? []
    arr.push(c)
    catsByServer.set(c.serverId, arr)
  }

  return srvRows.map((s) => ({
    ...s,
    channels:   channelsByServer.get(s.id) ?? [],
    categories: catsByServer.get(s.id) ?? [],
    _count:     { members: countByServer.get(s.id) ?? 0 },
  }))
}

async function serverWithChannelsAndCount(serverId: string) {
  const [srv] = await db.select().from(servers).where(eq(servers.id, serverId)).limit(1)
  if (!srv) return null
  const [chRows, [countRow], catRows] = await Promise.all([
    db.select().from(channels).where(eq(channels.serverId, serverId)).orderBy(asc(channels.position), asc(channels.createdAt)),
    db.select({ count: sql<number>`count(*)::int` })
      .from(serverMembers)
      .where(eq(serverMembers.serverId, serverId)),
    safeCategoryRows([serverId]),
  ])
  return { ...srv, channels: chRows, categories: catRows, _count: { members: countRow?.count ?? 0 } }
}

serversRouter.get(
  '/',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const list = await listServersForUser(req.userId!)
    res.json({ data: list })
  })
)

serversRouter.post(
  '/',
  requireAuth,
  validate(CreateServerSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { name, iconUrl, isGroup = false } = req.body

    const server = await db.transaction(async (tx) => {
      const [s] = await tx.insert(servers).values({
        name, iconUrl, isGroup, ownerId: req.userId!,
      }).returning()
      await tx.insert(serverMembers).values({ userId: req.userId!, serverId: s.id, role: 'OWNER' })
      await tx.insert(channels).values({ name: 'geral', type: 'TEXT', serverId: s.id, position: 0 })
      return s
    })

    // Categoria "Geral" e best-effort, FORA da transacao: se a tabela
    // ChannelCategory ainda nao existir, criar a constelacao NAO pode falhar.
    try {
      const [cat] = await db.insert(channelCategories).values({ name: 'Geral', serverId: server.id, position: 0 }).returning()
      await db.update(channels).set({ categoryId: cat.id }).where(eq(channels.serverId, server.id))
    } catch (e) {
      console.warn('[servers] categoria default pulada:', (e as Error).message)
    }

    const full = await serverWithChannelsAndCount(server.id)
    res.status(201).json({ data: full })
  })
)

const ALLOWED_ICON_HOSTS = [
  'i.imgur.com','media.giphy.com','cdn.discordapp.com','media.tenor.com',
  'i.postimg.cc','images.unsplash.com','lh3.googleusercontent.com',
  'pbs.twimg.com','media.discordapp.net','cdn.jsdelivr.net','raw.githubusercontent.com',
]
function isAllowedIcon(url: string | null | undefined): boolean {
  if (!url) return true
  if (url.startsWith('data:image/')) return true
  try { const { hostname } = new URL(url); return ALLOWED_ICON_HOSTS.some((h) => hostname === h || hostname.endsWith(`.${h}`)) }
  catch { return false }
}
function isIconTooBig(url: string | null | undefined): boolean {
  if (!url || !url.startsWith('data:')) return false
  return url.length * 0.75 > 5 * 1024 * 1024
}

function isBannerTooBig(url: string | null | undefined): boolean {
  if (!url || !url.startsWith('data:')) return false
  return url.length * 0.75 > 8 * 1024 * 1024
}

const UpdateServerSchema = z.object({
  name:      z.string().min(1).max(100).optional(),
  iconUrl:   z.string().optional().nullable(),
  bannerUrl: z.string().optional().nullable(),
  messageRetentionDays: z.number().int().min(0).max(365).optional().nullable(),
  isPublic:    z.boolean().optional(),
  description: z.string().max(200).optional().nullable(),
})

serversRouter.patch(
  '/:serverId',
  requireAuth,
  validate(UpdateServerSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const { name, iconUrl, bannerUrl, messageRetentionDays, isPublic, description } = req.body as {
      name?: string; iconUrl?: string | null; bannerUrl?: string | null
      messageRetentionDays?: number | null; isPublic?: boolean; description?: string | null
    }

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId) return res.status(403).json({ error: 'Você não é membro' })
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_SERVER)) {
      return res.status(403).json({ error: 'Sem permissão pra editar o servidor' })
    }

    if (iconUrl && !isAllowedIcon(iconUrl)) return res.status(422).json({ error: 'URL de ícone não permitida' })
    if (isIconTooBig(iconUrl)) return res.status(413).json({ error: 'Ícone muito grande (max 5MB)' })
    if (bannerUrl && !isAllowedIcon(bannerUrl)) return res.status(422).json({ error: 'URL de banner não permitida' })
    if (isBannerTooBig(bannerUrl)) return res.status(413).json({ error: 'Banner muito grande (max 8MB)' })

    const patch: Record<string, unknown> = {}
    if (name      !== undefined) patch.name      = name
    if (iconUrl   !== undefined) patch.iconUrl   = iconUrl
    if (bannerUrl !== undefined) patch.bannerUrl = bannerUrl
    if (messageRetentionDays !== undefined)
      patch.messageRetentionDays = messageRetentionDays === 0 ? null : messageRetentionDays
    if (isPublic    !== undefined) patch.isPublic    = isPublic
    if (description !== undefined) patch.description = description?.trim() || null
    if (Object.keys(patch).length === 0) return res.status(400).json({ error: 'Nada para atualizar' })

    await db.update(servers).set(patch).where(eq(servers.id, serverId))
    void audit({
      serverId, actorId: req.userId!, action: AUDIT.SERVER_UPDATE,
      targetId: serverId, metadata: { fields: Object.keys(patch) },
    })
    const updated = await serverWithChannelsAndCount(serverId)
    res.json({ data: updated })
  })
)

serversRouter.post(
  '/:serverId/regenerate-invite',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId) return res.status(403).json({ error: 'Você não é membro' })
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_SERVER)) {
      return res.status(403).json({ error: 'Sem permissão pra regenerar o convite' })
    }

    const newCode = createId()
    await db.update(servers).set({ inviteCode: newCode }).where(eq(servers.id, serverId))
    void audit({
      serverId, actorId: req.userId!, action: AUDIT.SERVER_UPDATE,
      targetId: serverId, metadata: { fields: ['inviteCode'] },
    })

    res.json({ data: { inviteCode: newCode } })
  })
)

const AddFriendSchema = z.object({
  friendUserId: z.string().min(1, 'friendUserId obrigatório'),
})

serversRouter.post(
  '/:serverId/add-friend',
  requireAuth,
  validate(AddFriendSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const { friendUserId } = req.body as { friendUserId: string }
    const callerId = req.userId!

    const [callerMember] = await db.select({ id: serverMembers.id })
      .from(serverMembers)
      .where(and(eq(serverMembers.serverId, serverId), eq(serverMembers.userId, callerId)))
      .limit(1)
    if (!callerMember) return res.status(403).json({ error: 'Você não é membro deste servidor' })

    const [a, b] = callerId < friendUserId ? [callerId, friendUserId] : [friendUserId, callerId]
    const [friendship] = await db.select({ id: friendships.id, status: friendships.status })
      .from(friendships)
      .where(and(eq(friendships.userAId, a), eq(friendships.userBId, b)))
      .limit(1)
    if (!friendship || friendship.status !== 'accepted') {
      return res.status(403).json({ error: 'Você só pode adicionar amigos aceitos' })
    }

    const [banned] = await db.select({ id: serverBans.id }).from(serverBans)
      .where(and(eq(serverBans.serverId, serverId), eq(serverBans.userId, friendUserId)))
      .limit(1)
    if (banned) return res.status(403).json({ error: 'Esse amigo está banido do servidor' })

    const [already] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.serverId, serverId), eq(serverMembers.userId, friendUserId)))
      .limit(1)
    if (already) return res.status(409).json({ error: 'Esse amigo já é membro' })

    const [server] = await db.select({ id: servers.id, name: servers.name, isGroup: servers.isGroup })
      .from(servers).where(eq(servers.id, serverId)).limit(1)
    if (!server) return res.status(404).json({ error: 'Servidor não encontrado' })

    await db.insert(serverMembers).values({ userId: friendUserId, serverId })
    void invalidateMembersCache(serverId)
    await db.insert(notifications).values({
      userId: friendUserId,
      type:   'server_invite',
      payload: JSON.stringify({
        serverId,
        serverName: server.name,
        isGroup:    server.isGroup,
        addedBy:    callerId,
      }),
    })
    void audit({
      serverId, actorId: callerId, action: AUDIT.SERVER_UPDATE,
      targetId: friendUserId, metadata: { kind: 'add_friend' },
    })

    res.json({ data: { ok: true, friendUserId } })
  })
)

const RoleSchema = z.object({ role: z.enum(['ADMIN', 'MEMBER']) })

serversRouter.patch(
  '/:serverId/members/:memberId',
  requireAuth,
  validate(RoleSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, memberId } = req.params
    const { role } = req.body as { role: 'ADMIN' | 'MEMBER' }

    const [server] = await db.select({ ownerId: servers.ownerId }).from(servers)
      .where(eq(servers.id, serverId)).limit(1)
    if (!server) return res.status(404).json({ error: 'Servidor não encontrado' })
    if (server.ownerId !== req.userId) return res.status(403).json({ error: 'Apenas o dono pode mudar cargos' })

    const [target] = await db.select({ id: serverMembers.id, role: serverMembers.role, userId: serverMembers.userId })
      .from(serverMembers)
      .where(and(eq(serverMembers.id, memberId), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (!target) return res.status(404).json({ error: 'Membro não encontrado' })
    if (target.role === 'OWNER') return res.status(400).json({ error: 'Não é possível alterar o cargo do dono' })

    await db.update(serverMembers).set({ role }).where(eq(serverMembers.id, memberId))
    res.json({ data: { id: memberId, role } })
  })
)

serversRouter.delete(
  '/:serverId/members/:memberId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, memberId } = req.params

    const requester = await getMemberPerms(req.userId!, serverId)
    if (!requester.memberId) return res.status(403).json({ error: 'Você não é membro' })
    if (!requester.isOwner && !requester.permissions.has(PERMS.KICK_MEMBERS))
      return res.status(403).json({ error: 'Sem permissão para remover membros' })

    const [target] = await db.select({ id: serverMembers.id, role: serverMembers.role, userId: serverMembers.userId })
      .from(serverMembers)
      .where(and(eq(serverMembers.id, memberId), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (!target) return res.status(404).json({ error: 'Membro não encontrado' })
    if (target.role === 'OWNER') return res.status(400).json({ error: 'Não é possível remover o dono' })
    if (target.userId === req.userId) return res.status(400).json({ error: 'Use sair do servidor para se remover' })

    if (!requester.isOwner) {
      const targetPerms = await getMemberPerms(target.userId, serverId)
      if (targetPerms.isOwner || targetPerms.permissions.has(PERMS.KICK_MEMBERS))
        return res.status(403).json({ error: 'Não pode remover alguém com mesma permissão' })
    }

    await db.delete(serverMembers).where(eq(serverMembers.id, memberId))
    void invalidateMembersCache(serverId)
    void audit({
      serverId, actorId: req.userId!, action: AUDIT.MEMBER_KICK,
      targetId: target.userId,
    })
    res.json({ message: 'Membro removido' })
  })
)

serversRouter.delete(
  '/:serverId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params

    const [server] = await db.select({ ownerId: servers.ownerId }).from(servers)
      .where(eq(servers.id, serverId)).limit(1)
    if (!server) return res.status(404).json({ error: 'Servidor não encontrado' })

    if (server.ownerId !== req.userId) {
      return res.status(403).json({ error: 'Apenas o dono pode excluir o servidor' })
    }

    await db.delete(servers).where(eq(servers.id, serverId))
    res.json({ message: 'Servidor excluído com sucesso' })
  })
)

serversRouter.delete(
  '/:serverId/leave',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params

    const [server] = await db.select({ ownerId: servers.ownerId }).from(servers)
      .where(eq(servers.id, serverId)).limit(1)
    if (!server) return res.status(404).json({ error: 'Servidor não encontrado' })

    if (server.ownerId === req.userId) {
      return res.status(400).json({
        error: 'O dono não pode sair do servidor. Exclua-o ou transfira a propriedade.',
      })
    }

    const [membership] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, req.userId!), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (!membership) return res.status(404).json({ error: 'Você não é membro deste servidor' })

    await db.delete(serverMembers).where(eq(serverMembers.id, membership.id))
    void invalidateMembersCache(serverId)
    res.json({ message: 'Você saiu do servidor' })
  })
)

serversRouter.post(
  '/join/:inviteCode',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const [server] = await db.select().from(servers)
      .where(eq(servers.inviteCode, req.params.inviteCode)).limit(1)
    if (!server) return res.status(404).json({ error: 'Convite inválido' })

    const [banned] = await db.select({ id: serverBans.id }).from(serverBans)
      .where(and(eq(serverBans.userId, req.userId!), eq(serverBans.serverId, server.id)))
      .limit(1)
    if (banned) return res.status(403).json({ error: 'Você está banido deste servidor' })

    const [already] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, req.userId!), eq(serverMembers.serverId, server.id)))
      .limit(1)
    if (already) return res.status(409).json({ error: 'Você já é membro deste servidor' })

    await db.insert(serverMembers).values({ userId: req.userId!, serverId: server.id })
    void invalidateMembersCache(server.id)
    res.json({ data: server })
  })
)

serversRouter.get(
  '/:serverId/members',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const [me] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, req.userId!), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (!me) return res.status(403).json({ error: 'Acesso negado' })

    const members = await db.select({
      id:        serverMembers.id,
      userId:    serverMembers.userId,
      serverId:  serverMembers.serverId,
      role:      serverMembers.role,
      nameColor: serverMembers.nameColor,
      joinedAt:  serverMembers.joinedAt,
      user: {
        id:          users.id,
        username:    users.username,
        displayName: users.displayName,
        avatarUrl:   users.avatarUrl,
        bio:         users.bio,
      },
    })
      .from(serverMembers)
      .innerJoin(users, eq(users.id, serverMembers.userId))
      .where(eq(serverMembers.serverId, serverId))
      .orderBy(asc(serverMembers.joinedAt))

    const memberIds = members.map((m) => m.id)
    let rolesByMember = new Map<string, Array<{ id: string; name: string; color: string|null; position: number; hoist: boolean }>>()
    if (memberIds.length > 0) {
      const assignments = await db.select({
        memberId: memberRoles.memberId,
        roleId:   roles.id,
        name:     roles.name,
        color:    roles.color,
        position: roles.position,
        hoist:    roles.hoist,
      })
        .from(memberRoles)
        .innerJoin(roles, eq(roles.id, memberRoles.roleId))
        .where(eq(roles.serverId, serverId))

      for (const a of assignments) {
        if (!rolesByMember.has(a.memberId)) rolesByMember.set(a.memberId, [])
        rolesByMember.get(a.memberId)!.push({
          id: a.roleId, name: a.name, color: a.color, position: a.position, hoist: a.hoist,
        })
      }

      for (const arr of rolesByMember.values()) arr.sort((a, b) => b.position - a.position)
    }

    const enriched = members.map((m) => {
      const rs = rolesByMember.get(m.id) ?? []
      const topColored = rs.find((r) => r.color)
      return { ...m, roles: rs, topColor: topColored?.color ?? null }
    })

    res.json({ data: enriched })
  })
)

serversRouter.post(
  '/:serverId/invite/:username',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, username } = req.params

    const [server] = await db.select({ id: servers.id }).from(servers)
      .where(eq(servers.id, serverId)).limit(1)
    if (!server) return res.status(404).json({ error: 'Servidor não encontrado' })

    const requester = await getMemberPerms(req.userId!, serverId)
    if (!requester.memberId) return res.status(403).json({ error: 'Você não é membro' })
    if (!requester.isOwner && !requester.permissions.has(PERMS.MANAGE_SERVER))
      return res.status(403).json({ error: 'Sem permissão pra adicionar membros' })

    const [target] = await db.select({ id: users.id, displayName: users.displayName }).from(users)
      .where(eq(users.username, username)).limit(1)
    if (!target) return res.status(404).json({ error: 'Usuário não encontrado' })

    const [tBan] = await db.select({ id: serverBans.id }).from(serverBans)
      .where(and(eq(serverBans.userId, target.id), eq(serverBans.serverId, serverId)))
      .limit(1)
    if (tBan) return res.status(403).json({ error: 'Usuário está banido deste servidor' })

    const [already] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, target.id), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (already) return res.status(409).json({ error: 'Usuário já é membro' })

    await db.insert(serverMembers).values({ userId: target.id, serverId, role: 'MEMBER' })
    void invalidateMembersCache(serverId)
    res.json({ message: `${target.displayName} adicionado com sucesso` })
  })
)

const NameColorSchema = z.object({
  nameColor: z.string().regex(/^(#[0-9a-fA-F]{6}|gradient:\d+:#[0-9a-fA-F]{6}:#[0-9a-fA-F]{6})$/, 'Formato inválido').nullable(),
})

serversRouter.patch(
  '/:serverId/my-color',
  requireAuth,
  validate(NameColorSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params

    const [member] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, req.userId!), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (!member) return res.status(403).json({ error: 'Você não é membro deste servidor' })

    const [updated] = await db.update(serverMembers)
      .set({ nameColor: req.body.nameColor })
      .where(eq(serverMembers.id, member.id))
      .returning({ nameColor: serverMembers.nameColor })

    res.json({ data: { nameColor: updated.nameColor } })
  })
)

serversRouter.get(
  '/:serverId/audit',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const limit = Math.min(Number(req.query.limit) || 50, 200)

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_SERVER))
      return res.status(403).json({ error: 'Sem permissão pra ver audit log' })

    const rows = await db.select({
      id:        auditLogs.id,
      action:    auditLogs.action,
      actorId:   auditLogs.actorId,
      targetId:  auditLogs.targetId,
      metadata:  auditLogs.metadata,
      createdAt: auditLogs.createdAt,
      actor: {
        id:          users.id,
        username:    users.username,
        displayName: users.displayName,
        avatarUrl:   users.avatarUrl,
      },
    })
      .from(auditLogs)
      .innerJoin(users, eq(users.id, auditLogs.actorId))
      .where(eq(auditLogs.serverId, serverId))
      .orderBy(sql`${auditLogs.createdAt} DESC`)
      .limit(limit)

    const shaped = rows.map((r) => ({
      ...r,
      metadata: safeParseObj(r.metadata),
    }))
    res.json({ data: shaped })
  })
)

function safeParseObj(raw: unknown): Record<string, unknown> {
  if (typeof raw !== 'string') return {}
  try { const v = JSON.parse(raw); return v && typeof v === 'object' ? v as Record<string, unknown> : {} } catch { return {} }
}

serversRouter.get(
  '/:serverId/me',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId && !m.isOwner) return res.status(403).json({ error: 'Você não é membro' })
    res.json({ data: {
      isOwner:     m.isOwner,
      isAdmin:     m.isAdmin,
      permissions: Array.from(m.permissions),
    } })
  })
)

export const channelsRouter = Router()

channelsRouter.post(
  '/:serverId/channels',
  requireAuth,
  validate(CreateChannelSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const { name, type } = req.body

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId) return res.status(403).json({ error: 'Você não é membro' })
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão pra criar canais' })

    const [channel] = await db.insert(channels).values({ name, type, serverId }).returning()
    void audit({
      serverId, actorId: req.userId!, action: AUDIT.CHANNEL_CREATE,
      targetId: channel.id, metadata: { name, type },
    })
    res.status(201).json({ data: channel })
  })
)

channelsRouter.get(
  '/:serverId/channels/:channelId/visibility',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, channelId } = req.params
    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão' })

    const [ch] = await db.select({ id: channels.id, isPrivate: channels.isPrivate })
      .from(channels)
      .where(and(eq(channels.id, channelId), eq(channels.serverId, serverId)))
      .limit(1)
    if (!ch) return res.status(404).json({ error: 'Canal não encontrado' })

    const perms = await db.select({ roleId: channelRolePerms.roleId })
      .from(channelRolePerms).where(eq(channelRolePerms.channelId, channelId))
    res.json({ data: { isPrivate: ch.isPrivate, roleIds: perms.map((p) => p.roleId) } })
  })
)

const VisibilitySchema = z.object({
  isPrivate: z.boolean(),
  roleIds:   z.array(z.string()).max(50).optional(),
})

channelsRouter.patch(
  '/:serverId/channels/:channelId/visibility',
  requireAuth,
  validate(VisibilitySchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, channelId } = req.params
    const { isPrivate, roleIds = [] } = req.body as z.infer<typeof VisibilitySchema>

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão' })

    const [ch] = await db.select({ id: channels.id })
      .from(channels)
      .where(and(eq(channels.id, channelId), eq(channels.serverId, serverId)))
      .limit(1)
    if (!ch) return res.status(404).json({ error: 'Canal não encontrado' })

    let validRoleIds: string[] = []
    if (roleIds.length > 0) {
      const validRoles = await db.select({ id: roles.id }).from(roles)
        .where(and(eq(roles.serverId, serverId), inArray(roles.id, roleIds)))
      validRoleIds = validRoles.map((r) => r.id)
    }

    await db.transaction(async (tx) => {
      await tx.update(channels).set({ isPrivate }).where(eq(channels.id, channelId))
      await tx.delete(channelRolePerms).where(eq(channelRolePerms.channelId, channelId))
      if (validRoleIds.length > 0) {
        await tx.insert(channelRolePerms).values(
          validRoleIds.map((roleId) => ({ channelId, roleId })),
        )
      }
    })

    void audit({
      serverId, actorId: req.userId!, action: AUDIT.CHANNEL_UPDATE,
      targetId: channelId, metadata: { isPrivate, roleIds: validRoleIds },
    })
    res.json({ data: { isPrivate, roleIds: validRoleIds } })
  })
)

const UpdateChannelSchema = z.object({
  name:       z.string().min(1).max(50).optional(),
  categoryId: z.string().nullable().optional(),
  position:   z.number().int().min(0).optional(),
})
channelsRouter.patch(
  '/:serverId/channels/:channelId',
  requireAuth,
  validate(UpdateChannelSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, channelId } = req.params
    const { name, categoryId, position } = req.body as z.infer<typeof UpdateChannelSchema>

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão' })

    if (categoryId) {
      const [cat] = await db.select({ id: channelCategories.id })
        .from(channelCategories)
        .where(and(eq(channelCategories.id, categoryId), eq(channelCategories.serverId, serverId)))
        .limit(1)
      if (!cat) return res.status(400).json({ error: 'Categoria inválida' })
    }

    const set: Partial<{ name: string; categoryId: string | null; position: number }> = {}
    if (name !== undefined) set.name = name
    if (categoryId !== undefined) set.categoryId = categoryId
    if (position !== undefined) set.position = position
    if (Object.keys(set).length === 0) return res.status(400).json({ error: 'Nada pra atualizar' })

    const r = await db.update(channels)
      .set(set)
      .where(and(eq(channels.id, channelId), eq(channels.serverId, serverId)))
      .returning({ id: channels.id, name: channels.name, categoryId: channels.categoryId, position: channels.position })
    if (r.length === 0) return res.status(404).json({ error: 'Canal não encontrado' })

    void audit({
      serverId, actorId: req.userId!, action: AUDIT.CHANNEL_UPDATE,
      targetId: channelId, metadata: { name, categoryId, position },
    })
    res.json({ data: r[0] })
  })
)

channelsRouter.delete(
  '/:serverId/channels/:channelId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, channelId } = req.params

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId && !m.isOwner) return res.status(403).json({ error: 'Você não é membro' })
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão pra excluir canais' })

    const r = await db.delete(channels)
      .where(and(eq(channels.id, channelId), eq(channels.serverId, serverId)))
      .returning({ id: channels.id, name: channels.name })
    if (r.length === 0) return res.status(404).json({ error: 'Canal não encontrado' })

    void audit({
      serverId, actorId: req.userId!, action: AUDIT.CHANNEL_DELETE,
      targetId: channelId, metadata: { name: r[0].name },
    })
    res.json({ message: 'Canal excluído' })
  })
)

const CreateCategorySchema = z.object({ name: z.string().min(1).max(50) })
channelsRouter.post(
  '/:serverId/categories',
  requireAuth,
  validate(CreateCategorySchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const { name } = req.body as z.infer<typeof CreateCategorySchema>

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId) return res.status(403).json({ error: 'Você não é membro' })
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão pra criar categorias' })

    const existing = await db.select({ position: channelCategories.position })
      .from(channelCategories).where(eq(channelCategories.serverId, serverId))
    const nextPos = existing.length ? Math.max(...existing.map((e) => e.position)) + 1 : 0

    const [cat] = await db.insert(channelCategories)
      .values({ name, serverId, position: nextPos }).returning()
    res.status(201).json({ data: cat })
  })
)

const UpdateCategorySchema = z.object({
  name:     z.string().min(1).max(50).optional(),
  position: z.number().int().min(0).optional(),
})
channelsRouter.patch(
  '/:serverId/categories/:categoryId',
  requireAuth,
  validate(UpdateCategorySchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, categoryId } = req.params
    const { name, position } = req.body as z.infer<typeof UpdateCategorySchema>

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão' })

    const set: Partial<{ name: string; position: number }> = {}
    if (name !== undefined) set.name = name
    if (position !== undefined) set.position = position
    if (Object.keys(set).length === 0) return res.status(400).json({ error: 'Nada pra atualizar' })

    const r = await db.update(channelCategories)
      .set(set)
      .where(and(eq(channelCategories.id, categoryId), eq(channelCategories.serverId, serverId)))
      .returning()
    if (r.length === 0) return res.status(404).json({ error: 'Categoria não encontrada' })
    res.json({ data: r[0] })
  })
)

channelsRouter.delete(
  '/:serverId/categories/:categoryId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, categoryId } = req.params

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão' })

    const r = await db.delete(channelCategories)
      .where(and(eq(channelCategories.id, categoryId), eq(channelCategories.serverId, serverId)))
      .returning({ id: channelCategories.id })
    if (r.length === 0) return res.status(404).json({ error: 'Categoria não encontrada' })
    res.json({ message: 'Categoria excluída' })
  })
)
