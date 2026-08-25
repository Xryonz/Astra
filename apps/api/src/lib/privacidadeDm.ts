import { and, eq, inArray } from 'drizzle-orm'
import { db } from './../db'
import { dmConversations, friendships, serverMembers, users } from '../db/schema'

export type NivelDeSussurro = 'all' | 'shared' | 'friends'

export function nivelDeSussurro(cru: string | null | undefined): NivelDeSussurro {
  return cru === 'shared' || cru === 'friends' ? cru : 'all'
}

export async function aceitaSussurroNovo(deId: string, paraId: string): Promise<boolean> {
  if (deId === paraId) return true

  const [alvo] = await db.select({ nivel: users.dmPrivacy }).from(users)
    .where(eq(users.id, paraId))
    .limit(1)
  const nivel = nivelDeSussurro(alvo?.nivel)
  if (nivel === 'all') return true

  const [a, b] = [deId, paraId].sort()
  const [conversa] = await db.select({ id: dmConversations.id }).from(dmConversations)
    .where(and(eq(dmConversations.userAId, a), eq(dmConversations.userBId, b)))
    .limit(1)
  if (conversa) return true

  if (await saoAmigos(deId, paraId)) return true
  if (nivel === 'friends') return false

  return await dividemConstelacao(deId, paraId)
}

async function saoAmigos(x: string, y: string): Promise<boolean> {
  const [a, b] = [x, y].sort()
  const [linha] = await db.select({ id: friendships.id }).from(friendships)
    .where(and(
      eq(friendships.userAId, a),
      eq(friendships.userBId, b),
      eq(friendships.status, 'accepted'),
    ))
    .limit(1)
  return !!linha
}

async function dividemConstelacao(x: string, y: string): Promise<boolean> {
  const dele = await db.select({ id: serverMembers.serverId }).from(serverMembers)
    .where(eq(serverMembers.userId, x))
  if (dele.length === 0) return false

  const [linha] = await db.select({ id: serverMembers.id }).from(serverMembers)
    .where(and(
      eq(serverMembers.userId, y),
      inArray(serverMembers.serverId, dele.map((d) => d.id)),
    ))
    .limit(1)
  return !!linha
}

export const RECUSA_DE_SUSSURRO = 'Não é possível conversar com essa pessoa'
