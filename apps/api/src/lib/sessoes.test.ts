import { describe, it, expect, beforeEach, vi } from 'vitest'
import { PgDialect } from 'drizzle-orm/pg-core'

const h = vi.hoisted(() => ({
  ordem: [] as string[],
  derrubadas: [] as Array<{ id: string; sessionId: string | null }>,
}))

vi.mock('../db', () => ({
  db: {
    update: () => ({ set: () => ({ where: () => ({ returning: async () => h.derrubadas }) }) }),
  },
}))

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

import { encerrarAoVivo, encerrarAsOutras, foraDaSessao, sessaoDaLinha } from './sessoes'
import { derrubarSessoes } from './realtime'

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

describe('encerrar as outras sessoes (troca de senha)', () => {
  beforeEach(() => {
    h.ordem.length = 0
  })

  it('derruba a sessao de cada linha revogada e conta quantas', async () => {
    h.derrubadas = [
      { id: 'r5', sessionId: 's5' },
      { id: 'r6', sessionId: null },
    ]
    await expect(encerrarAsOutras('u1', 's1')).resolves.toBe(2)
    expect(h.ordem).toEqual(['marcou s5 por 900 s', 'marcou r6 por 900 s', 'derrubou s5,r6'])
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
