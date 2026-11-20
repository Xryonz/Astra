import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import bcrypt from 'bcryptjs'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'

const h = vi.hoisted(() => ({
  hashAtual: '',
  encerrarAsOutras: vi.fn(async (_userId: string, _sessao: string) => 0),
}))

vi.mock('../db', () => ({
  db: {
    select: () => ({ from: () => ({ where: () => ({ limit: async () => [{ passwordHash: h.hashAtual }] }) }) }),
    update: () => ({ set: () => ({ where: async () => undefined }) }),
  },
}))
vi.mock('../middleware/auth', () => ({
  requireAuth: (
    req: { userId?: string; sid?: string; header: (n: string) => string | undefined },
    _res: unknown,
    next: () => void,
  ) => {
    req.userId = 'u1'
    req.sid = req.header('x-sessao-de-teste')
    next()
  },
}))
vi.mock('../lib/sessoes', async (importOriginal) => {
  const real = await importOriginal<typeof import('../lib/sessoes')>()
  return { ...real, encerrarAsOutras: h.encerrarAsOutras }
})

import authRouter from './auth'

let servidor: Server
let base = ''

async function trocarSenha(sessao?: string) {
  const cabecalhos: Record<string, string> = { 'Content-Type': 'application/json' }
  if (sessao) cabecalhos['x-sessao-de-teste'] = sessao
  const resposta = await fetch(`${base}/api/auth/password`, {
    method: 'POST',
    headers: cabecalhos,
    body: JSON.stringify({ currentPassword: 'Antiga123', newPassword: 'Nova12345' }),
  })
  return resposta.status
}

describe('trocar a senha encerra as outras sessoes', () => {
  beforeAll(async () => {
    h.hashAtual = await bcrypt.hash('Antiga123', 4)
    const app = express()
    app.use(express.json())
    app.use('/api/auth', authRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  beforeEach(() => h.encerrarAsOutras.mockClear())

  it('encerra todas menos a sessao de quem trocou', async () => {
    expect(await trocarSenha('s-atual')).toBe(200)
    expect(h.encerrarAsOutras).toHaveBeenCalledWith('u1', 's-atual')
  })

  it('credencial antiga, sem sessao dentro: troca a senha e nao derruba ninguem as cegas', async () => {
    expect(await trocarSenha()).toBe(200)
    expect(h.encerrarAsOutras).not.toHaveBeenCalled()
  })
})
