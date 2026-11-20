import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'

const h = vi.hoisted(() => ({
  reivindicadas: [] as Array<{ id: string; userId: string; sessionId: string | null }>,
  inseridas: [] as Array<Record<string, unknown>>,
}))

vi.mock('../db', () => ({
  db: {
    update: () => ({ set: () => ({ where: () => ({ returning: async () => h.reivindicadas }) }) }),
    insert: () => ({
      values: async (linha: Record<string, unknown>) => {
        h.inseridas.push(linha)
      },
    }),
    select: () => ({ from: () => ({ where: () => ({ limit: async () => [] }) }) }),
  },
}))

import authRouter from './auth'
import { generateRefreshToken, verifyAccessToken } from '../lib/jwt'

let servidor: Server
let base = ''

async function renovar() {
  const resposta = await fetch(`${base}/api/auth/refresh`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${generateRefreshToken('u1')}` },
  })
  const corpo = (await resposta.json()) as { data: { accessToken: string } }
  return { status: resposta.status, sessao: verifyAccessToken(corpo.data.accessToken).sid }
}

describe('a renovacao da credencial mantem a sessao', () => {
  beforeAll(async () => {
    const app = express()
    app.use(express.json())
    app.use('/api/auth', authRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  beforeEach(() => {
    h.inseridas.length = 0
  })

  it('a linha nova e a credencial nova herdam a sessao da linha renovada', async () => {
    h.reivindicadas = [{ id: 'r1', userId: 'u1', sessionId: 's1' }]
    const { status, sessao } = await renovar()
    expect(status).toBe(200)
    expect(sessao).toBe('s1')
    expect(h.inseridas[0]?.sessionId).toBe('s1')
  })

  it('linha de antes da coluna: a sessao passa a ser o id dela, e fica fixa dali em diante', async () => {
    h.reivindicadas = [{ id: 'r1', userId: 'u1', sessionId: null }]
    const { sessao } = await renovar()
    expect(sessao).toBe('r1')
    expect(h.inseridas[0]?.sessionId).toBe('r1')
  })
})
