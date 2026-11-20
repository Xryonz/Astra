import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'

const h = vi.hoisted(() => ({
  alvo: [] as Array<{ id: string; sessionId: string | null }>,
  revogadas: [] as Array<{ id: string; sessionId?: string | null }>,
  encerrar: vi.fn(async (_userId: string, _sessoes: string[]) => {}),
}))

vi.mock('../db', () => ({
  db: {
    select: () => ({ from: () => ({ where: () => ({ limit: async () => h.alvo }) }) }),
    update: () => ({ set: () => ({ where: () => ({ returning: async () => h.revogadas }) }) }),
  },
}))
vi.mock('../middleware/auth', () => ({
  requireAuth: (req: { userId?: string }, _res: unknown, next: () => void) => {
    req.userId = 'u1'
    next()
  },
}))
vi.mock('../lib/sessoes', async (importOriginal) => {
  const real = await importOriginal<typeof import('../lib/sessoes')>()
  return { ...real, encerrarAoVivo: h.encerrar }
})

import sessionsRouter from './sessions'

let servidor: Server
let base = ''

async function pedir(metodo: 'DELETE' | 'POST', caminho: string, corpo?: unknown) {
  const resposta = await fetch(`${base}/api/sessions${caminho}`, {
    method: metodo,
    headers: { 'Content-Type': 'application/json' },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  })
  return { status: resposta.status, corpo: (await resposta.json()) as { data?: Record<string, unknown> } }
}

describe('encerrar sessoes derruba os aparelhos ao vivo', () => {
  beforeAll(async () => {
    const app = express()
    app.use(express.json())
    app.use('/api/sessions', sessionsRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  beforeEach(() => {
    h.alvo = []
    h.revogadas = []
    h.encerrar.mockClear()
  })

  it('encerrar uma sessao derruba a sessao inteira, nao so a linha da lista', async () => {
    h.alvo = [{ id: 'r2', sessionId: 's1' }]
    h.revogadas = [{ id: 'r2' }]
    const { status, corpo } = await pedir('DELETE', '/r2')
    expect(status).toBe(200)
    expect(corpo.data).toEqual({ revoked: 'r2' })
    expect(h.encerrar).toHaveBeenCalledWith('u1', ['s1'])
  })

  it('linha de antes da coluna: a sessao e a propria linha', async () => {
    h.alvo = [{ id: 'r1', sessionId: null }]
    h.revogadas = [{ id: 'r3' }]
    await pedir('DELETE', '/r1')
    expect(h.encerrar).toHaveBeenCalledWith('u1', ['r1'])
  })

  it('sessao de outra conta ou inexistente: 404 e ninguem cai', async () => {
    const { status } = await pedir('DELETE', '/r9')
    expect(status).toBe(404)
    expect(h.encerrar).not.toHaveBeenCalled()
  })

  it('sessao ja encerrada: 404 e ninguem cai de novo', async () => {
    h.alvo = [{ id: 'r2', sessionId: 's1' }]
    const { status } = await pedir('DELETE', '/r2')
    expect(status).toBe(404)
    expect(h.encerrar).not.toHaveBeenCalled()
  })

  it('encerrar as outras derruba cada sessao revogada, e so elas', async () => {
    h.revogadas = [
      { id: 'r5', sessionId: 's5' },
      { id: 'r6', sessionId: null },
    ]
    const { status, corpo } = await pedir('POST', '/revoke-others', { refreshToken: 'atual' })
    expect(status).toBe(200)
    expect(corpo.data).toEqual({ revokedCount: 2 })
    expect(h.encerrar).toHaveBeenCalledWith('u1', ['s5', 'r6'])
  })
})
