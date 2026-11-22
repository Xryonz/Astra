import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import bcrypt from 'bcryptjs'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'

vi.mock('../db', async () => ({ db: (await import('../__tests__/bancoGravador')).bancoGravador }))

import authRouter from './auth'
import { gravador } from '../__tests__/bancoGravador'

let servidor: Server
let base = ''
let hashDaSenha = ''

const TRAVA = 'tx:select User for no key update'

async function entrar() {
  const resposta = await fetch(`${base}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-device-id': 'aparelho-1' },
    body: JSON.stringify({ email: 'pessoa@exemplo.com', password: 'Senha1234' }),
  })
  return resposta.status
}

describe('entrar com e-mail e senha', () => {
  beforeAll(async () => {
    hashDaSenha = await bcrypt.hash('Senha1234', 4)
    const app = express()
    app.use(express.json())
    app.use('/api/auth', authRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  beforeEach(() => gravador.limpar())

  it('cria a sessao dentro da trava da conta, depois de conferir que a senha ainda e a mesma', async () => {
    gravador.responder(
      'select:User',
      [{ id: 'u1', passwordHash: hashDaSenha }],
      [{ apagadaEm: null, senha: hashDaSenha }],
    )
    expect(await entrar()).toBe(200)
    expect(gravador.passos).toEqual([
      'db:select User',
      'begin',
      TRAVA,
      'tx:update RefreshToken',
      'tx:insert RefreshToken',
      'commit',
    ])
  })

  it('a senha mudou enquanto o bcrypt conferia: 401 e nenhuma sessao nasce', async () => {
    gravador.responder(
      'select:User',
      [{ id: 'u1', passwordHash: hashDaSenha }],
      [{ apagadaEm: null, senha: 'outro-hash' }],
    )
    expect(await entrar()).toBe(401)
    expect(gravador.passos).toEqual(['db:select User', 'begin', TRAVA, 'commit'])
  })

  it('a conta foi apagada nesse meio-tempo: 401 e nenhuma sessao nasce', async () => {
    gravador.responder(
      'select:User',
      [{ id: 'u1', passwordHash: hashDaSenha }],
      [{ apagadaEm: new Date(), senha: hashDaSenha }],
    )
    expect(await entrar()).toBe(401)
    expect(gravador.passos).not.toContain('tx:insert RefreshToken')
  })
})
