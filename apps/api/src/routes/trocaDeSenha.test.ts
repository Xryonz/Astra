import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import bcrypt from 'bcryptjs'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'

vi.mock('../db', async () => ({ db: (await import('../__tests__/bancoGravador')).bancoGravador }))
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
  const { gravador } = await import('../__tests__/bancoGravador')
  return {
    ...real,
    encerrarAoVivo: async (_userId: string, sessoes: string[]) => {
      if (sessoes.length) gravador.anotar(`encerrou ${sessoes.join(',')}`)
    },
  }
})

import authRouter from './auth'
import { gravador } from '../__tests__/bancoGravador'

let servidor: Server
let base = ''
let hashAtual = ''

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
    hashAtual = await bcrypt.hash('Antiga123', 4)
    const app = express()
    app.use(express.json())
    app.use('/api/auth', authRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  beforeEach(() => {
    gravador.limpar()
    gravador.responder('select:User', [{ passwordHash: hashAtual }], [{ apagadaEm: null, senha: hashAtual }])
  })

  it('a senha nova e a revogacao das outras saem na mesma transacao, com a conta travada', async () => {
    gravador.responder('update:RefreshToken', [{ id: 'r5', sessionId: 's5' }])
    expect(await trocarSenha('s-atual')).toBe(200)
    expect(gravador.passos).toEqual([
      'db:select User',
      'begin',
      'tx:select User for no key update',
      'tx:update User',
      'tx:update RefreshToken',
      'commit',
      'encerrou s5',
    ])
  })

  it('poupa a sessao de quem trocou', async () => {
    await trocarSenha('s-atual')
    const revogacao = gravador.consulta('tx:update RefreshToken')
    expect(revogacao?.sql).toContain('"RefreshToken"."sessionId" <> ')
    expect(revogacao?.params).toContain('s-atual')
  })

  it('a senha mudou entre a conferencia e a trava: recusa e nao grava nada', async () => {
    gravador.responder('select:User', [{ passwordHash: hashAtual }], [{ apagadaEm: null, senha: 'outro-hash' }])
    expect(await trocarSenha('s-atual')).toBe(401)
    expect(gravador.passos).toEqual(['db:select User', 'begin', 'tx:select User for no key update', 'commit'])
  })

  it('a conta foi apagada entre a conferencia e a trava: recusa e nao grava nada', async () => {
    gravador.responder('select:User', [{ passwordHash: hashAtual }], [{ apagadaEm: new Date(), senha: hashAtual }])
    expect(await trocarSenha('s-atual')).toBe(401)
    expect(gravador.passos).toEqual(['db:select User', 'begin', 'tx:select User for no key update', 'commit'])
  })

  it('credencial antiga, sem sessao dentro: troca a senha e nao derruba ninguem as cegas', async () => {
    expect(await trocarSenha()).toBe(200)
    expect(gravador.passos).toEqual([
      'db:select User',
      'begin',
      'tx:select User for no key update',
      'tx:update User',
      'commit',
    ])
  })
})
