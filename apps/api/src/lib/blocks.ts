import { and, eq, or } from 'drizzle-orm'
import { db } from './../db'
import { userBlocks } from '../db/schema'

// Bloqueio entre pessoas.
//
// A regra que importa: o EFEITO vale nos dois sentidos. Se A bloqueou B, nem A
// manda pra B nem B manda pra A. Bloqueio de mao unica seria uma porta trancada
// com a chave do lado de fora — quem bloqueou continuaria recebendo.
//
// O registro, por outro lado, e direcional (quem bloqueou / quem foi bloqueado),
// porque so quem bloqueou pode desfazer, e so ele ve isso na interface.

/** Existe bloqueio entre os dois, em qualquer direcao? */
export async function haBloqueio(a: string, b: string): Promise<boolean> {
  if (a === b) return false
  const [linha] = await db.select({ id: userBlocks.id }).from(userBlocks)
    .where(or(
      and(eq(userBlocks.blockerId, a), eq(userBlocks.blockedId, b)),
      and(eq(userBlocks.blockerId, b), eq(userBlocks.blockedId, a)),
    ))
    .limit(1)
  return !!linha
}

/** Quem EU bloqueei (pra interface mostrar "Desbloquear" no lugar de "Bloquear"). */
export async function quemEuBloqueei(userId: string): Promise<string[]> {
  const linhas = await db.select({ id: userBlocks.blockedId }).from(userBlocks)
    .where(eq(userBlocks.blockerId, userId))
  return linhas.map((l) => l.id)
}
