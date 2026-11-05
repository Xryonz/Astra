import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import type { Server } from 'http'
import type { AddressInfo } from 'net'

const banco = vi.hoisted(() => ({ respostas: [] as unknown[][] }))

vi.mock('../db', () => {
  const consulta = () => {
    const passo = {
      from: () => passo,
      where: () => passo,
      orderBy: () => passo,
      limit: async () => banco.respostas.shift() ?? [],
    }
    return passo
  }
  return { db: { select: consulta } }
})
vi.mock('../lib/redis', () => ({
  getUserStatus: vi.fn(),
  setUserOnline: vi.fn(),
  redis: {},
  presenceKeys: {},
  activityKeys: {},
  leAtividade: vi.fn(),
}))
vi.mock('../lib/realtime', () => ({ presenceChanged: vi.fn(), profileChanged: vi.fn() }))
vi.mock('../middleware/auth', () => ({
  requireAuth: (req: { userId?: string }, _res: unknown, next: () => void) => {
    req.userId = 'usuario_1'
    next()
  },
}))

import profileRouter from './profile'

let servidor: Server
let base = ''

async function fotoPerdida(): Promise<string | null> {
  const resposta = await fetch(`${base}/api/profile/me/foto-perdida`)
  return ((await resposta.json()) as { data: { endereco: string | null } }).data.endereco
}

describe('GET /api/profile/me/foto-perdida', () => {
  beforeAll(async () => {
    const app = express()
    app.use('/api/profile', profileRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  beforeEach(() => {
    banco.respostas = []
  })

  it('sem foto no banco, procura a última foto nas mensagens', async () => {
    banco.respostas = [[{ avatarUrl: null }], [{ endereco: '/uploads/antiga.webp' }]]

    expect(await fotoPerdida()).toBe('/uploads/antiga.webp')
  })

  it('foto removida de propósito não é foto perdida', async () => {
    banco.respostas = [[{ avatarUrl: '' }], [{ endereco: '/uploads/antiga.webp' }]]

    expect(await fotoPerdida()).toBeNull()
  })

  it('quem tem foto não tem nada a recuperar', async () => {
    banco.respostas = [[{ avatarUrl: 'https://armazem.exemplo/foto.webp' }]]

    expect(await fotoPerdida()).toBeNull()
  })
})
