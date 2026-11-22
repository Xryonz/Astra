import { and, eq, isNull, ne, or } from 'drizzle-orm'
import { db } from '../db'
import { refreshTokens, users } from '../db/schema'
import { VIDA_DO_ACESSO_S } from './jwt'
import { encerrarSessao } from './redis'
import { derrubarSessoes } from './realtime'

export type Transacao = Parameters<Parameters<typeof db.transaction>[0]>[0]
export type Executor = Pick<Transacao, 'select' | 'update' | 'insert' | 'delete'>
export type LinhaRevogada = { id: string; sessionId: string | null }
export type ContaTravada = { ativa: boolean; senha: string | null }

export function sessaoDaLinha(linha: LinhaRevogada): string {
  return linha.sessionId ?? linha.id
}

export async function encerrarAoVivo(userId: string, sessoes: string[]): Promise<void> {
  if (!sessoes.length) return
  await Promise.all(sessoes.map((sessao) => encerrarSessao(sessao, VIDA_DO_ACESSO_S)))
  await derrubarSessoes(userId, new Set(sessoes))
}

export function comAContaTravada<T>(
  userId: string,
  trabalho: (tx: Transacao, conta: ContaTravada) => Promise<T>,
): Promise<T> {
  return db.transaction(async (tx) => {
    const [linha] = await tx
      .select({ apagadaEm: users.deletedAt, senha: users.passwordHash })
      .from(users)
      .where(eq(users.id, userId))
      .for('no key update')
    return trabalho(tx, { ativa: linha !== undefined && linha.apagadaEm === null, senha: linha?.senha ?? null })
  })
}

export function foraDaSessao(sessao: string) {
  return and(ne(refreshTokens.id, sessao), or(isNull(refreshTokens.sessionId), ne(refreshTokens.sessionId, sessao)))
}

export async function revogarAsOutras(tx: Executor, userId: string, sessaoAtual: string): Promise<LinhaRevogada[]> {
  return tx
    .update(refreshTokens)
    .set({ revokedAt: new Date() })
    .where(and(eq(refreshTokens.userId, userId), isNull(refreshTokens.revokedAt), foraDaSessao(sessaoAtual)))
    .returning({ id: refreshTokens.id, sessionId: refreshTokens.sessionId })
}
