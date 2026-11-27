import { describe, it, expect, beforeEach, vi } from 'vitest'

vi.mock('../db', async () => ({ db: (await import('../__tests__/bancoGravador')).bancoGravador }))

import { apagarRelatosVencidos, apagarRenovacoesVencidas } from './retentionWorker'
import { gravador } from '../__tests__/bancoGravador'

const UM_DIA_MS = 24 * 60 * 60 * 1000

describe('limpeza das renovacoes de sessao', () => {
  beforeEach(() => gravador.limpar())

  it('apaga so as linhas vencidas ha mais de 1 dia, e conta quantas', async () => {
    const agora = Date.UTC(2026, 10, 21, 12)
    gravador.responder('delete:RefreshToken', { rowCount: 3 })
    await expect(apagarRenovacoesVencidas(agora)).resolves.toBe(3)
    const limpeza = gravador.consulta('db:delete RefreshToken')
    expect(limpeza?.sql).toBe('"RefreshToken"."expiresAt" < $1')
    expect(limpeza?.params).toEqual([new Date(agora - UM_DIA_MS).toISOString()])
  })
})

describe('limpeza dos relatos de falha', () => {
  beforeEach(() => gravador.limpar())

  it('apaga so os relatos com mais de 60 dias, e conta quantos', async () => {
    const agora = Date.UTC(2026, 10, 27, 12)
    gravador.responder('delete:CrashReport', { rowCount: 2 })
    await expect(apagarRelatosVencidos(agora)).resolves.toBe(2)
    const limpeza = gravador.consulta('db:delete CrashReport')
    expect(limpeza?.sql).toBe('"CrashReport"."createdAt" < $1')
    expect(limpeza?.params).toEqual([new Date(agora - 60 * UM_DIA_MS).toISOString()])
  })
})
