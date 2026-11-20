import { and, eq, isNull, ne, or } from 'drizzle-orm'
import { db } from '../db'
import { refreshTokens } from '../db/schema'
import { VIDA_DO_ACESSO_S } from './jwt'
import { encerrarSessao } from './redis'
import { derrubarSessoes } from './realtime'

export function sessaoDaLinha(linha: { id: string; sessionId: string | null }): string {
  return linha.sessionId ?? linha.id
}

export async function encerrarAoVivo(userId: string, sessoes: string[]): Promise<void> {
  if (!sessoes.length) return
  await Promise.all(sessoes.map((sessao) => encerrarSessao(sessao, VIDA_DO_ACESSO_S)))
  await derrubarSessoes(userId, new Set(sessoes))
}

export function foraDaSessao(sessao: string) {
  return and(ne(refreshTokens.id, sessao), or(isNull(refreshTokens.sessionId), ne(refreshTokens.sessionId, sessao)))
}

export async function encerrarAsOutras(userId: string, sessaoAtual: string): Promise<number> {
  const derrubadas = await db
    .update(refreshTokens)
    .set({ revokedAt: new Date() })
    .where(and(eq(refreshTokens.userId, userId), isNull(refreshTokens.revokedAt), foraDaSessao(sessaoAtual)))
    .returning({ id: refreshTokens.id, sessionId: refreshTokens.sessionId })
  await encerrarAoVivo(userId, derrubadas.map(sessaoDaLinha))
  return derrubadas.length
}
