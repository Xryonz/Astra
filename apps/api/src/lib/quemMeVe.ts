import { and, eq, or } from 'drizzle-orm'
import { db } from '../db'
import { dmConversations, friendships, serverMembers } from '../db/schema'
import { logger } from './logger'

export function montarSalas(
  euId: string,
  constelacoes: ReadonlyArray<string>,
  pessoas: ReadonlyArray<string>,
): string[] {
  const salas = new Set<string>([`user:${euId}`])
  for (const id of constelacoes) if (id) salas.add(`server:${id}`)
  for (const id of pessoas) if (id && id !== euId) salas.add(`user:${id}`)
  return [...salas]
}

export async function salasQueMeVeem(euId: string): Promise<string[] | null> {
  try {
    const [constelacoes, amizades, sussurros] = await Promise.all([
      db.select({ serverId: serverMembers.serverId })
        .from(serverMembers)
        .where(eq(serverMembers.userId, euId)),
      db.select({ a: friendships.userAId, b: friendships.userBId })
        .from(friendships)
        .where(and(
          eq(friendships.status, 'accepted'),
          or(eq(friendships.userAId, euId), eq(friendships.userBId, euId)),
        )),
      db.select({ a: dmConversations.userAId, b: dmConversations.userBId })
        .from(dmConversations)
        .where(or(eq(dmConversations.userAId, euId), eq(dmConversations.userBId, euId))),
    ])
    const doOutroLado = (par: { a: string; b: string }) => (par.a === euId ? par.b : par.a)
    return montarSalas(
      euId,
      constelacoes.map((c) => c.serverId),
      [...amizades.map(doOutroLado), ...sussurros.map(doOutroLado)],
    )
  } catch (e) {
    logger.error('Realtime', 'Falha ao descobrir quem ve a pessoa', e as Error)
    return null
  }
}
