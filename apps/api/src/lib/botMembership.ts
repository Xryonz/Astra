import { and, eq } from 'drizzle-orm'
import { db } from '../db'
import { serverMembers, roles, memberRoles, servers } from '../db/schema'
import { getBotId } from './bot'
import { invalidateMembersCache } from './membersCache'
import { logger } from './logger'

const NOME_CARGO = 'BOT'
const COR_CARGO = '#c9a96e'

export async function garantirBotNaConstelacao(serverId: string): Promise<void> {
  const botId = await getBotId()
  if (!botId) return

  let [membro] = await db.select({ id: serverMembers.id })
    .from(serverMembers)
    .where(and(eq(serverMembers.serverId, serverId), eq(serverMembers.userId, botId)))
    .limit(1)

  if (!membro) {
    const [novo] = await db.insert(serverMembers)
      .values({ userId: botId, serverId, role: 'MEMBER' })
      .returning({ id: serverMembers.id })
    membro = novo
  }
  if (!membro) return

  let [cargo] = await db.select({ id: roles.id })
    .from(roles)
    .where(and(eq(roles.serverId, serverId), eq(roles.name, NOME_CARGO)))
    .limit(1)

  if (!cargo) {
    const [novo] = await db.insert(roles).values({
      serverId,
      name: NOME_CARGO,
      color: COR_CARGO,
      hoist: true,
      position: 0,
      permissions: '[]',
    }).returning({ id: roles.id })
    cargo = novo
  }
  if (!cargo) return

  const [temCargo] = await db.select({ id: memberRoles.id })
    .from(memberRoles)
    .where(and(eq(memberRoles.memberId, membro.id), eq(memberRoles.roleId, cargo.id)))
    .limit(1)

  if (!temCargo) {
    await db.insert(memberRoles).values({ memberId: membro.id, roleId: cargo.id })
  }

  void invalidateMembersCache(serverId)
}

export async function garantirBotEmTodas(): Promise<void> {
  try {
    const todas = await db.select({ id: servers.id }).from(servers)
    let feitas = 0
    for (const s of todas) {
      try {
        await garantirBotNaConstelacao(s.id)
        feitas++
      } catch {  }
    }
    logger.info('Bot', `Bot garantida em ${feitas}/${todas.length} constelações.`)
  } catch (e) {
    logger.error('Bot', 'Falha ao garantir a bot nas constelações', e as Error)
  }
}
