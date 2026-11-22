import { describe, it, expect, beforeEach, vi } from 'vitest'
import { PgDialect } from 'drizzle-orm/pg-core'

const h = vi.hoisted(() => ({
  ordem: [] as string[],
}))

vi.mock('../db', async () => ({ db: (await import('../__tests__/bancoGravador')).bancoGravador }))
vi.mock('./redis', () => ({
  encerrarSessao: vi.fn(async (sessao: string, segundos: number) => {
    h.ordem.push(`marcou ${sessao} por ${segundos} s`)
  }),
}))
vi.mock('./realtime', () => ({
  derrubarSessoes: vi.fn(async (_userId: string, sessoes: ReadonlySet<string>) => {
    h.ordem.push(`derrubou ${[...sessoes].join(',')}`)
  }),
}))

import { comAContaTravada, encerrarAoVivo, foraDaSessao, revogarAsOutras, sessaoDaLinha } from './sessoes'
import { derrubarSessoes } from './realtime'
import { gravador } from '../__tests__/bancoGravador'

const TRAVA = 'tx:select User for no key update'

describe('encerrar sessao ao vivo', () => {
  beforeEach(() => {
    h.ordem.length = 0
    vi.mocked(derrubarSessoes).mockClear()
  })

  it('marca cada sessao pelo tempo de vida da credencial ANTES de derrubar, para a reconexao ja ser recusada', async () => {
    await encerrarAoVivo('u1', ['s1', 's2'])
    expect(h.ordem).toEqual(['marcou s1 por 900 s', 'marcou s2 por 900 s', 'derrubou s1,s2'])
  })

  it('lista vazia nao faz nada', async () => {
    await encerrarAoVivo('u1', [])
    expect(h.ordem).toEqual([])
    expect(derrubarSessoes).not.toHaveBeenCalled()
  })
})

describe('a sessao de cada linha', () => {
  it('linha nova carrega a sessao em que nasceu', () => {
    expect(sessaoDaLinha({ id: 'r2', sessionId: 's1' })).toBe('s1')
  })

  it('linha de antes da coluna vira a propria sessao, e a renovacao herda esse id', () => {
    expect(sessaoDaLinha({ id: 'r1', sessionId: null })).toBe('r1')
  })
})

describe('a trava da conta', () => {
  beforeEach(() => gravador.limpar())

  it('e o primeiro comando da transacao, na linha da pessoa, em modo que nao barra as chaves estrangeiras', async () => {
    gravador.responder('select:User', [{ apagadaEm: null, senha: 'h' }])
    await comAContaTravada('u1', (tx) => revogarAsOutras(tx, 'u1', 's1'))
    expect(gravador.passos).toEqual(['begin', TRAVA, 'tx:update RefreshToken', 'commit'])
    expect(gravador.consulta(TRAVA)?.params).toEqual(['u1'])
  })

  it('trava mesmo a conta apagada, e avisa que ela nao esta ativa', async () => {
    gravador.responder('select:User', [{ apagadaEm: new Date(), senha: null }])
    await expect(comAContaTravada('u1', async (_tx, conta) => conta)).resolves.toEqual({ ativa: false, senha: null })
  })

  it('conta que nao existe nao esta ativa', async () => {
    await expect(comAContaTravada('u1', async (_tx, conta) => conta.ativa)).resolves.toBe(false)
  })
})

describe('revogar as outras sessoes', () => {
  beforeEach(() => gravador.limpar())

  it('revoga pelo executor recebido e devolve as linhas, para derrubar depois do commit', async () => {
    gravador.responder('update:RefreshToken', [{ id: 'r5', sessionId: 's5' }])
    await expect(comAContaTravada('u1', (tx) => revogarAsOutras(tx, 'u1', 's1'))).resolves.toEqual([
      { id: 'r5', sessionId: 's5' },
    ])
  })

  it('a condicao poupa a sessao atual e NAO deixa escapar a linha antiga sem sessao (NULL)', () => {
    const { sql, params } = new PgDialect().sqlToQuery(
      foraDaSessao('s1') as NonNullable<ReturnType<typeof foraDaSessao>>,
    )
    expect(sql).toContain('"RefreshToken"."id" <> $1')
    expect(sql).toContain('("RefreshToken"."sessionId" is null or "RefreshToken"."sessionId" <> $2)')
    expect(params).toEqual(['s1', 's1'])
  })
})
