import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest'
import express from 'express'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { eq } from 'drizzle-orm'

const removidas = vi.hoisted(() => [] as string[])

vi.mock('../db', async () => {
  const { abrirBancoEmMemoria } = await import('../__tests__/bancoEmMemoria')
  return { db: await abrirBancoEmMemoria(), schema: await import('../db/schema') }
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
    req.userId = 'u3'
    next()
  },
}))
vi.mock('../lib/storage', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../lib/storage')>()),
  persistImagemDeExibicao: async (valor: string | null) => ({ url: valor, original: null }),
  removeAttachment: async (url: string) => {
    removidas.push(url)
  },
}))

import profileRouter from './profile'
import { db } from '../db'
import { channels, messages, servers, users } from '../db/schema'

const VELHA = 'https://b/velha.webp'
const VELHA_GRANDE = 'https://b/velha.png'
const MAIS_VELHA = 'https://b/mais-velha.webp'
const NOVA = 'https://b/nova.webp'

let servidor: Server

async function semear() {
  await db.insert(users).values([
    {
      id: 'u3',
      email: 'troca@teste.invalid',
      username: 'troca',
      coordinate: 'c-u3',
      displayName: 'Troca',
      avatarUrl: VELHA,
      avatarFullUrl: VELHA_GRANDE,
    },
    {
      id: 'u4',
      email: 'vizinha@teste.invalid',
      username: 'vizinha',
      coordinate: 'c-u4',
      displayName: 'Vizinha',
      bannerUrl: VELHA_GRANDE,
    },
  ])
  await db.insert(servers).values({ id: 's1', name: 'Sala', ownerId: 'u4' })
  await db.insert(channels).values({ id: 'ch1', name: 'geral', serverId: 's1' })
  await db.insert(messages).values([
    {
      id: 'm1',
      content: 'com a foto de agora',
      authorId: 'u3',
      channelId: 'ch1',
      authorName: 'Troca',
      authorAvatarUrl: VELHA,
    },
    { id: 'm2', content: 'com uma foto mais antiga', authorId: 'u3', channelId: 'ch1', authorAvatarUrl: MAIS_VELHA },
  ])
}

describe('trocar a foto num Postgres de verdade', () => {
  beforeAll(async () => {
    await semear()
    const app = express()
    app.use(express.json())
    app.use('/api/profile', profileRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    const resposta = await fetch(`http://127.0.0.1:${(servidor.address() as AddressInfo).port}/api/profile`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ avatarUrl: NOVA }),
    })
    expect(resposta.status).toBe(200)
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  it('a conta fica com a foto nova e sem a original da anterior', async () => {
    const [conta] = await db.select().from(users).where(eq(users.id, 'u3'))
    expect(conta).toMatchObject({ avatarUrl: NOVA, avatarFullUrl: null })
  })

  it('só as mensagens que guardavam a foto anterior passam a mostrar a atual', async () => {
    const [comAAnterior] = await db.select().from(messages).where(eq(messages.id, 'm1'))
    expect(comAAnterior).toMatchObject({ authorAvatarUrl: null, authorName: 'Troca' })
    const [comUmaMaisAntiga] = await db.select().from(messages).where(eq(messages.id, 'm2'))
    expect(comUmaMaisAntiga.authorAvatarUrl).toBe(MAIS_VELHA)
  })

  it('a foto anterior sai do bucket, e a original que outra conta usa como banner fica', () => {
    expect(removidas).toEqual([VELHA])
  })
})
