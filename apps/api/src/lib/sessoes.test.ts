import { describe, it, expect, beforeEach, vi } from 'vitest'

const h = vi.hoisted(() => ({ ordem: [] as string[] }))

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

import { encerrarAoVivo, sessaoDaLinha } from './sessoes'
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
