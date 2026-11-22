import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'

vi.mock('../db', async () => ({ db: (await import('../__tests__/bancoGravador')).bancoGravador }))
vi.mock('../middleware/auth', () => ({
  requireAuth: (req: { userId?: string }, _res: unknown, next: () => void) => {
    req.userId = 'u1'
    next()
  },
}))
vi.mock('../lib/sessoes', async (importOriginal) => {
  const real = await importOriginal<typeof import('../lib/sessoes')>()
  const { gravador } = await import('../__tests__/bancoGravador')
  return {
    ...real,
    encerrarAoVivo: async (_userId: string, sessoes: string[]) => {
      if (sessoes.length) gravador.anotar(`encerrou ${sessoes.join(',')}`)
    },
  }
})

import sessionsRouter from './sessions'
import { gravador } from '../__tests__/bancoGravador'

let servidor: Server
let base = ''

const TRAVA = 'tx:select User for no key update'

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
    gravador.limpar()
    gravador.responder('select:User', [{ apagadaEm: null, senha: 'h' }])
  })

  it('encerrar uma sessao trava a conta, revoga a sessao inteira e so derruba depois do commit', async () => {
    gravador.responder('select:RefreshToken', [{ id: 'r2', sessionId: 's1' }])
    gravador.responder('update:RefreshToken', [{ id: 'r2' }])
    const { status, corpo } = await pedir('DELETE', '/r2')
    expect(status).toBe(200)
    expect(corpo.data).toEqual({ revoked: 'r2' })
    expect(gravador.passos).toEqual([
      'db:select RefreshToken',
      'begin',
      TRAVA,
      'tx:update RefreshToken',
      'commit',
      'encerrou s1',
    ])
  })

  it('a revogacao pega a sessao inteira, nao so a linha da lista', async () => {
    gravador.responder('select:RefreshToken', [{ id: 'r2', sessionId: 's1' }])
    gravador.responder('update:RefreshToken', [{ id: 'r9' }])
    await pedir('DELETE', '/r2')
    const revogacao = gravador.consulta('tx:update RefreshToken')
    expect(revogacao?.sql).toContain('("RefreshToken"."id" = ')
    expect(revogacao?.sql).toContain(' or "RefreshToken"."sessionId" = ')
    expect(revogacao?.params).toEqual(expect.arrayContaining(['u1', 's1']))
  })

  it('linha de antes da coluna: a sessao e a propria linha', async () => {
    gravador.responder('select:RefreshToken', [{ id: 'r1', sessionId: null }])
    gravador.responder('update:RefreshToken', [{ id: 'r1' }])
    await pedir('DELETE', '/r1')
    expect(gravador.passos[gravador.passos.length - 1]).toBe('encerrou r1')
  })

  it('sessao de outra conta ou inexistente: 404 e ninguem cai', async () => {
    const { status } = await pedir('DELETE', '/r9')
    expect(status).toBe(404)
    expect(gravador.passos).toEqual(['db:select RefreshToken'])
  })

  it('sessao ja encerrada: 404 e ninguem cai de novo', async () => {
    gravador.responder('select:RefreshToken', [{ id: 'r2', sessionId: 's1' }])
    const { status } = await pedir('DELETE', '/r2')
    expect(status).toBe(404)
    expect(gravador.passos).toEqual(['db:select RefreshToken', 'begin', TRAVA, 'tx:update RefreshToken', 'commit'])
  })

  it('encerrar as outras trava a conta e derruba cada sessao revogada, depois do commit', async () => {
    gravador.responder('update:RefreshToken', [
      { id: 'r5', sessionId: 's5' },
      { id: 'r6', sessionId: null },
    ])
    const { status, corpo } = await pedir('POST', '/revoke-others', { refreshToken: 'atual' })
    expect(status).toBe(200)
    expect(corpo.data).toEqual({ revokedCount: 2 })
    expect(gravador.passos).toEqual(['begin', TRAVA, 'tx:update RefreshToken', 'commit', 'encerrou s5,r6'])
  })
})
