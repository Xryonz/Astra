import { and, eq, inArray } from 'drizzle-orm'
import { db } from './../db'
import { dmConversations, friendships, serverMembers, users } from '../db/schema'

// QUEM PODE TE MANDAR SUSSURRO.
//
// Antes disto, qualquer pessoa com o seu ID (ou o seu nome de usuário) abria uma
// conversa com você. Numa constelação pública isso é um convite aberto a quem
// chegar — e o bloqueio, que era a única defesa, só serve DEPOIS que a mensagem
// chegou.
//
// Três níveis, e não um interruptor, porque o caso do meio é o mais comum de
// todos: alguém da sua constelação que você ainda não adicionou querendo falar
// com você. Um binário "amigo ou nada" mataria justamente esse.
export type NivelDeSussurro = 'all' | 'shared' | 'friends'

export function nivelDeSussurro(cru: string | null | undefined): NivelDeSussurro {
  return cru === 'shared' || cru === 'friends' ? cru : 'all'
}

// CONVERSA QUE JÁ EXISTE PASSA SEMPRE, e isso é regra e não descuido: apertar o
// ajuste não pode calar quem você já estava respondendo. O filtro decide quem
// consegue CHEGAR até você, não quem já chegou. (O bloqueio é que corta os dois
// casos — ele continua sendo checado à parte, e antes deste.)
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

// Duas consultas e não um JOIN de propósito: a primeira costuma devolver poucas
// dezenas de linhas (as constelações de UMA pessoa), e a segunda vira uma busca
// por índice dentro dessa lista curta. Um JOIN aqui faria o banco cruzar as duas
// listas inteiras de participação pra descobrir a mesma coisa.
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

// A recusa é a MESMA do bloqueio, palavra por palavra, e isso é de propósito:
// respostas diferentes contariam ao outro lado qual é o seu ajuste — e "esta
// pessoa só aceita amigos" já é informação sobre você que ninguém pediu pra dar.
export const RECUSA_DE_SUSSURRO = 'Não é possível conversar com essa pessoa'
