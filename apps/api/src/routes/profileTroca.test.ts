import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'

vi.mock('../db', async () => ({ db: (await import('../__tests__/bancoGravador')).bancoGravador }))
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
    req.userId = 'u1'
    next()
  },
}))
vi.mock('../lib/storage', async (importOriginal) => {
  const real = await importOriginal<typeof import('../lib/storage')>()
  const { gravador } = await import('../__tests__/bancoGravador')
  return {
    ...real,
    persistImagemDeExibicao: async (valor: string | null) => ({ url: valor, original: null }),
    removeAttachment: async (url: string) => {
      gravador.anotar(`removeu ${url}`)
    },
  }
})

import profileRouter from './profile'
import { gravador } from '../__tests__/bancoGravador'

let servidor: Server
let base = ''

async function trocar(corpo: Record<string, unknown>) {
  const resposta = await fetch(`${base}/api/profile`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(corpo),
  })
  return resposta.status
}

function contaDepois(avatarUrl: string | null) {
  return [{ id: 'u1', username: 'pessoa', displayName: 'Pessoa', avatarUrl, displayFont: 'serif' }]
}

function removidas() {
  return gravador.passos.filter((passo) => passo.startsWith('removeu'))
}

describe('PATCH /api/profile, troca de foto', () => {
  beforeAll(async () => {
    const app = express()
    app.use(express.json())
    app.use('/api/profile', profileRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  beforeEach(() => gravador.limpar())

  it('a foto anterior sai do bucket e as mensagens que a guardavam passam a mostrar a nova', async () => {
    gravador.responder('select:User', [
      {
        avatarUrl: 'https://b/velha.webp',
        avatarFullUrl: 'https://b/velha.png',
        bannerUrl: 'https://b/banner.webp',
        bannerFullUrl: null,
      },
    ])
    gravador.responder('update:User', contaDepois('https://b/nova.webp'))
    expect(await trocar({ avatarUrl: 'https://b/nova.webp' })).toBe(200)
    expect(gravador.consulta('db:update User')?.valores).toMatchObject({
      avatarUrl: 'https://b/nova.webp',
      avatarFullUrl: null,
    })
    const copias = gravador.consulta('db:update Message')
    expect(copias?.valores).toEqual({ authorAvatarUrl: null })
    expect(copias?.params).toEqual(['u1', 'https://b/velha.webp'])
    expect(removidas()).toEqual(['removeu https://b/velha.webp', 'removeu https://b/velha.png'])
  })

  it('trocar só o banner não mexe na foto nem nas mensagens', async () => {
    gravador.responder('select:User', [
      {
        avatarUrl: 'https://b/foto.webp',
        avatarFullUrl: null,
        bannerUrl: 'https://b/banner-velho.webp',
        bannerFullUrl: 'https://b/banner-velho.png',
      },
    ])
    gravador.responder('update:User', contaDepois('https://b/foto.webp'))
    expect(await trocar({ bannerUrl: 'https://i.imgur.com/novo.png' })).toBe(200)
    expect(gravador.consulta('db:update Message')).toBeUndefined()
    expect(removidas()).toEqual(['removeu https://b/banner-velho.webp', 'removeu https://b/banner-velho.png'])
  })

  it('reenviar a mesma foto guarda a original e não mexe nas mensagens', async () => {
    gravador.responder(
      'select:User',
      [{ avatarUrl: 'https://b/foto.webp', avatarFullUrl: 'https://b/foto.png', bannerUrl: null, bannerFullUrl: null }],
      [{ id: 'u1' }],
      [{ id: 'u1' }],
    )
    gravador.responder('update:User', contaDepois('https://b/foto.webp'))
    expect(await trocar({ avatarUrl: 'https://b/foto.webp' })).toBe(200)
    expect(gravador.consulta('db:update User')?.valores).not.toHaveProperty('avatarFullUrl')
    expect(gravador.consulta('db:update Message')).toBeUndefined()
    expect(removidas()).toEqual([])
  })

  it('a foto anterior que outra conta usa fica no bucket', async () => {
    gravador.responder(
      'select:User',
      [{ avatarUrl: 'https://b/velha.webp', avatarFullUrl: null, bannerUrl: null, bannerFullUrl: null }],
      [{ id: 'u2' }],
    )
    gravador.responder('update:User', contaDepois('https://b/nova.webp'))
    expect(await trocar({ avatarUrl: 'https://b/nova.webp' })).toBe(200)
    expect(removidas()).toEqual([])
  })
})
