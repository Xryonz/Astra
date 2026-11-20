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
