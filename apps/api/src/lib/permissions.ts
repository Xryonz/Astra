
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

export async function hasPermission(userId: string, serverId: string, perm: Permission): Promise<boolean> {
  const m = await getMemberPerms(userId, serverId)
  if (m.isOwner) return true
  return m.permissions.has(perm)
}

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

export async function filterVisibleChannels(
  userId: string,
  channelIds: string[],
): Promise<Set<string>> {
  if (channelIds.length === 0) return new Set()

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

  const serverIds = [...new Set(privateNeedingCheck.map((c) => c.serverId))]
  const memberships = await db.select({
    memberId: serverMembers.id, serverId: serverMembers.serverId,
  })
    .from(serverMembers)
    .where(and(eq(serverMembers.userId, userId), inArray(serverMembers.serverId, serverIds)))
  const memberByServer = new Map(memberships.map((m) => [m.serverId, m.memberId]))

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

export function computeChannelVisibility(input: {
  ownerId:      string | null
  userId:       string
  isMember:     boolean
  isPrivate:    boolean
  userRoleIds:  string[]
  allowedRoles: string[] | Set<string>
}): boolean {
  const { ownerId, userId, isMember, isPrivate, userRoleIds, allowedRoles } = input
  if (ownerId === userId && !!ownerId) return true
  if (!isMember) return false
  if (!isPrivate) return true
  const allowedSet = allowedRoles instanceof Set ? allowedRoles : new Set(allowedRoles)
  if (allowedSet.size === 0) return false
  return userRoleIds.some((id) => allowedSet.has(id))
}

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

