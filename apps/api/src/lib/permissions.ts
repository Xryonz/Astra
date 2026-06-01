/**
 * Sistema central de permissões.
 *
 * Cascata:
 *   1) Dono do servidor → todas perms implícitas
 *   2) Membro com legacy enum role 'ADMIN' → set base de admin
 *   3) Cargos customizados atribuídos via ServerMemberRole → união das perms
 *
 * Permissões são strings em JSON array dentro de ServerRole.permissions.
 * Frontend (RoleEditor.tsx) usa estes mesmos identificadores.
 */
import { and, eq, inArray } from 'drizzle-orm'
import { db } from '../db'
import { servers, serverMembers, roles, memberRoles, channels, channelRolePerms } from '../db/schema'

export const PERMS = {
  MANAGE_SERVER:    'MANAGE_SERVER',
  MANAGE_ROLES:     'MANAGE_ROLES',
  MANAGE_CHANNELS:  'MANAGE_CHANNELS',
  KICK_MEMBERS:     'KICK_MEMBERS',
  BAN_MEMBERS:      'BAN_MEMBERS',
  MANAGE_MESSAGES:  'MANAGE_MESSAGES',
  MENTION_EVERYONE: 'MENTION_EVERYONE',
} as const
export type Permission = typeof PERMS[keyof typeof PERMS]

// Set base que legacy ADMIN ganha sem precisar de cargo customizado
const LEGACY_ADMIN_PERMS: Permission[] = [
  PERMS.MANAGE_SERVER,
  PERMS.MANAGE_CHANNELS,
  PERMS.KICK_MEMBERS,
  PERMS.MANAGE_MESSAGES,
]

export interface MemberPerms {
  isOwner:     boolean
  isAdmin:     boolean
  permissions: Set<Permission>
  memberId:    string | null
}

/**
 * Carrega todas as permissões efetivas de um user num server.
 * Owner → isOwner=true e Set vazio (use isOwner pra short-circuit).
 * Não-membro → memberId=null, permissions vazias.
 */
export async function getMemberPerms(userId: string, serverId: string): Promise<MemberPerms> {
  const [srv] = await db.select({ ownerId: servers.ownerId }).from(servers)
    .where(eq(servers.id, serverId)).limit(1)
  if (!srv) return { isOwner: false, isAdmin: false, permissions: new Set(), memberId: null }

  const [member] = await db.select({ id: serverMembers.id, role: serverMembers.role }).from(serverMembers)
    .where(and(eq(serverMembers.userId, userId), eq(serverMembers.serverId, serverId)))
    .limit(1)

  let rolesPermsRaw: string[] = []
  if (member) {
    const assigned = await db.select({ permissions: roles.permissions })
      .from(memberRoles)
      .innerJoin(roles, eq(roles.id, memberRoles.roleId))
      .where(and(eq(memberRoles.memberId, member.id), eq(roles.serverId, serverId)))
    rolesPermsRaw = assigned.map((a) => a.permissions)
  }

  return computeMemberPerms(srv.ownerId, userId, member ?? null, rolesPermsRaw)
}

/**
 * Atalho pra checagem direta. Owner sempre true.
 */
export async function hasPermission(userId: string, serverId: string, perm: Permission): Promise<boolean> {
  const m = await getMemberPerms(userId, serverId)
  if (m.isOwner) return true
  return m.permissions.has(perm)
}

/**
 * Parser puro — útil em testes e externamente. Aceita JSON string, retorna
 * só permissões válidas conhecidas, descarta lixo silenciosamente.
 */
export function parsePermissionsJson(raw: unknown): Permission[] {
  if (typeof raw !== 'string') return []
  try {
    const v = JSON.parse(raw)
    if (!Array.isArray(v)) return []
    return v.filter((x): x is Permission => typeof x === 'string' && (Object.values(PERMS) as string[]).includes(x))
  } catch {
    return []
  }
}

/**
 * Pode esse user ver/acessar esse canal?
 *  - Canal público (isPrivate=false): qualquer membro do server vê.
 *  - Canal privado: owner do server sempre vê; senão precisa ter pelo menos
 *    1 role atribuída que esteja em ChannelRolePerm desse canal.
 * Retorna false se não-membro do server, canal inexistente, ou sem perm.
 */
export async function userCanSeeChannel(userId: string, channelId: string): Promise<boolean> {
  const [row] = await db.select({
    serverId:  channels.serverId,
    isPrivate: channels.isPrivate,
    ownerId:   servers.ownerId,
  })
    .from(channels)
    .innerJoin(servers, eq(servers.id, channels.serverId))
    .where(eq(channels.id, channelId))
    .limit(1)
  if (!row) return false

  if (row.ownerId === userId) return true

  const [member] = await db.select({ id: serverMembers.id })
    .from(serverMembers)
    .where(and(eq(serverMembers.userId, userId), eq(serverMembers.serverId, row.serverId)))
    .limit(1)
  if (!member) return false

  if (!row.isPrivate) return true

  // Privado → user precisa ter role que esteja em ChannelRolePerm
  const userRoleRows = await db.select({ roleId: memberRoles.roleId })
    .from(memberRoles)
    .where(eq(memberRoles.memberId, member.id))
  if (userRoleRows.length === 0) return false

  const userRoleIds = userRoleRows.map((r) => r.roleId)
  const [match] = await db.select({ id: channelRolePerms.id })
    .from(channelRolePerms)
    .where(and(eq(channelRolePerms.channelId, channelId), inArray(channelRolePerms.roleId, userRoleIds)))
    .limit(1)
  return !!match
}

/**
 * Filtra uma lista de canais retornando só os que o user pode ver. Single
 * query — útil pra `listServersForUser`. Em vez de N queries, agrega tudo.
 */
export async function filterVisibleChannels(
  userId: string,
  channelIds: string[],
): Promise<Set<string>> {
  if (channelIds.length === 0) return new Set()

  // Pega channel info + ownerId do server
  const rows = await db.select({
    channelId: channels.id,
    serverId:  channels.serverId,
    isPrivate: channels.isPrivate,
    ownerId:   servers.ownerId,
  })
    .from(channels)
    .innerJoin(servers, eq(servers.id, channels.serverId))
    .where(inArray(channels.id, channelIds))

  const visible = new Set<string>()
  const privateNeedingCheck: { channelId: string; serverId: string }[] = []

  for (const r of rows) {
    if (r.ownerId === userId) { visible.add(r.channelId); continue }
    if (!r.isPrivate) { visible.add(r.channelId); continue }
    privateNeedingCheck.push({ channelId: r.channelId, serverId: r.serverId })
  }

  if (privateNeedingCheck.length === 0) return visible

  // Pega membership do user em todos os servers relevantes
  const serverIds = [...new Set(privateNeedingCheck.map((c) => c.serverId))]
  const memberships = await db.select({
    memberId: serverMembers.id, serverId: serverMembers.serverId,
  })
    .from(serverMembers)
    .where(and(eq(serverMembers.userId, userId), inArray(serverMembers.serverId, serverIds)))
  const memberByServer = new Map(memberships.map((m) => [m.serverId, m.memberId]))

  // Pega role IDs do user nos servers relevantes
  const memberIds = memberships.map((m) => m.memberId)
  const userRoles = memberIds.length > 0
    ? await db.select({ memberId: memberRoles.memberId, roleId: memberRoles.roleId })
        .from(memberRoles).where(inArray(memberRoles.memberId, memberIds))
    : []
  const rolesByMember = new Map<string, string[]>()
  for (const r of userRoles) {
    const arr = rolesByMember.get(r.memberId) ?? []
    arr.push(r.roleId)
    rolesByMember.set(r.memberId, arr)
  }

  // Pega channel→role mapping
  const privateChannelIds = privateNeedingCheck.map((c) => c.channelId)
  const channelRoles = await db.select({
    channelId: channelRolePerms.channelId, roleId: channelRolePerms.roleId,
  }).from(channelRolePerms).where(inArray(channelRolePerms.channelId, privateChannelIds))
  const rolesByChannel = new Map<string, Set<string>>()
  for (const c of channelRoles) {
    const s = rolesByChannel.get(c.channelId) ?? new Set()
    s.add(c.roleId)
    rolesByChannel.set(c.channelId, s)
  }

  // Match
  for (const pc of privateNeedingCheck) {
    const memberId = memberByServer.get(pc.serverId)
    if (!memberId) continue
    const userRoleIds = rolesByMember.get(memberId) ?? []
    const allowedRoles = rolesByChannel.get(pc.channelId)
    if (!allowedRoles) continue
    if (userRoleIds.some((rid) => allowedRoles.has(rid))) visible.add(pc.channelId)
  }

  return visible
}

/**
 * Lógica pura da cascata Owner → Admin legacy → Cargos. Testável sem DB.
 *
 * @param ownerId   id do user dono do server
 * @param userId    id do user sendo avaliado
 * @param member    null se não-membro; senão { id, role }
 * @param rolesPermsRaw  array de strings JSON (raw permissions de cada cargo do user)
 */
export function computeMemberPerms(
  ownerId:       string | null,
  userId:        string,
  member:        { id: string; role: string } | null,
  rolesPermsRaw: string[],
): MemberPerms {
  const isOwner = !!ownerId && ownerId === userId
  if (!member) {
    return { isOwner, isAdmin: false, permissions: new Set(), memberId: null }
  }
  const isAdmin = member.role === 'ADMIN'
  const perms = new Set<Permission>()
  if (isAdmin) for (const p of LEGACY_ADMIN_PERMS) perms.add(p)
  for (const raw of rolesPermsRaw) {
    for (const p of parsePermissionsJson(raw)) perms.add(p)
  }
  return { isOwner, isAdmin, permissions: perms, memberId: member.id }
}

