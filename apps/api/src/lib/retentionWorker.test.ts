import { describe, it, expect, beforeEach, vi } from 'vitest'

vi.mock('../db', async () => ({ db: (await import('../__tests__/bancoGravador')).bancoGravador }))

import { apagarRenovacoesVencidas } from './retentionWorker'
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
