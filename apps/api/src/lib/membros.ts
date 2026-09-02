import { and, asc, eq } from 'drizzle-orm'
import { db } from '../db'
import { memberRoles, roles, serverMembers, users } from '../db/schema'
import { servidorDeSocket } from './realtime'
import { redis, presenceKeys } from './redis'

async function presencaDe(userId: string): Promise<string> {
  try {
    const s = await redis.get(presenceKeys.user(userId))
    return !s || s === 'INVISIBLE' ? 'OFFLINE' : s
  } catch {
    return 'OFFLINE'
  }
}

export interface CargoDoMembro {
  id: string
  name: string
  color: string | null
  iconUrl: string | null
  position: number
  hoist: boolean
}

export function corQueVence(cargos: CargoDoMembro[]): string | null {
  return cargos.find((r) => r.color)?.color ?? null
}

async function cargosPorMembro(serverId: string): Promise<Map<string, CargoDoMembro[]>> {
  const atribuicoes = await db.select({
    memberId: memberRoles.memberId,
    id:       roles.id,
    name:     roles.name,
    color:    roles.color,
    iconUrl:  roles.iconUrl,
    position: roles.position,
    hoist:    roles.hoist,
  })
    .from(memberRoles)
    .innerJoin(roles, eq(roles.id, memberRoles.roleId))
    .where(eq(roles.serverId, serverId))

  const porMembro = new Map<string, CargoDoMembro[]>()
  for (const { memberId, ...cargo } of atribuicoes) {
    if (!porMembro.has(memberId)) porMembro.set(memberId, [])
    porMembro.get(memberId)!.push(cargo)
  }
  for (const lista of porMembro.values()) lista.sort((a, b) => b.position - a.position)
  return porMembro
}

export async function listarMembros(serverId: string) {
  const membros = await db.select({
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
      displayFont: users.displayFont,
    },
  })
    .from(serverMembers)
    .innerJoin(users, eq(users.id, serverMembers.userId))
    .where(eq(serverMembers.serverId, serverId))
    .orderBy(asc(serverMembers.joinedAt))

  if (membros.length === 0) return []

  const porMembro = await cargosPorMembro(serverId)
  return membros.map((m) => {
    const cargos = porMembro.get(m.id) ?? []
    return { ...m, roles: cargos, topColor: corQueVence(cargos) }
  })
}

export async function cargosDoServidorMudaram(serverId: string): Promise<void> {
  const io = servidorDeSocket()
  if (!io) return
  io.to(`server:${serverId}`).emit('server_members_reset', {
    serverId,
    membros: await listarMembros(serverId),
  })
}

export async function cargosDoMembroMudaram(serverId: string, memberId: string): Promise<void> {
  const io = servidorDeSocket()
  if (!io) return
  const cargos = (await cargosPorMembro(serverId)).get(memberId) ?? []
  io.to(`server:${serverId}`).emit('server_member_roles', {
    serverId,
    memberId,
    roles: cargos,
    topColor: corQueVence(cargos),
  })
}

export async function membroEntrou(serverId: string, userId: string): Promise<void> {
  const io = servidorDeSocket()
  if (!io) return

  const [membro] = await db.select({
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
      displayFont: users.displayFont,
    },
  })
    .from(serverMembers)
    .innerJoin(users, eq(users.id, serverMembers.userId))
    .where(and(eq(serverMembers.serverId, serverId), eq(serverMembers.userId, userId)))
    .limit(1)

  if (!membro) return

  io.to(`server:${serverId}`).emit('server_member_added', {
    serverId,
    membro: { ...membro, roles: [], topColor: null },
    presenca: await presencaDe(userId),
  })
}

export function membroSaiu(serverId: string, userId: string): void {
  servidorDeSocket()?.to(`server:${serverId}`).emit('server_member_removed', { serverId, userId })
}

export function membroMudouDeCargo(serverId: string, memberId: string, role: string): void {
  servidorDeSocket()?.to(`server:${serverId}`).emit('server_member_role', { serverId, memberId, role })
}
