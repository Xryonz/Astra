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
vi.mock('../middleware/auth', () => ({
  requireAuth: (req: { userId?: string }, _res: unknown, next: () => void) => {
    req.userId = 'u1'
    next()
  },
}))
vi.mock('../lib/sessoes', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../lib/sessoes')>()),
  encerrarAoVivo: async () => {},
}))
vi.mock('../lib/storage', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../lib/storage')>()),
  removeAttachment: async (url: string) => {
    removidas.push(url)
  },
}))

import authRouter from './auth'
import { db } from '../db'
import { channels, messages, notifications, refreshTokens, servers, users } from '../db/schema'

const FOTO = 'https://b/u1-foto.webp'
const FOTO_GRANDE = 'https://b/u1-foto.png'
const BANNER_DIVIDIDO = 'https://b/dividido.webp'
const ANEXO = JSON.stringify([{ url: 'https://b/anexo.png', name: 'anexo.png' }])
const AVISO_DE_OUTRA_PESSOA = JSON.stringify({
  authorId: 'u1-outra',
  authorName: 'Outra Pessoa',
  authorUsername: 'outra_pessoa',
  authorAvatar: null,
  preview: 'oi',
})

let servidor: Server
let base = ''

async function semear() {
  await db.insert(users).values([
    {
      id: 'u1',
      email: 'pessoa@teste.invalid',
      username: 'pessoa',
      coordinate: 'c-u1',
      displayName: 'Pessoa',
      avatarUrl: FOTO,
      avatarFullUrl: FOTO_GRANDE,
      bannerUrl: BANNER_DIVIDIDO,
      bio: 'oi',
      pendingEmail: 'nova@teste.invalid',
      notificationPrefs: '{"mencao":true}',
    },
    {
      id: 'u2',
      email: 'outra@teste.invalid',
      username: 'outra',
      coordinate: 'c-u2',
      displayName: 'Outra',
      bannerUrl: BANNER_DIVIDIDO,
    },
  ])
  await db.insert(servers).values({ id: 's1', name: 'Sala', ownerId: 'u2' })
  await db.insert(channels).values({ id: 'ch1', name: 'geral', serverId: 's1' })
  await db.insert(messages).values({
    id: 'm1',
    content: 'olá',
    authorId: 'u1',
    channelId: 'ch1',
    authorName: 'Pessoa',
    authorAvatarUrl: FOTO,
    attachments: ANEXO,
  })
  await db.insert(notifications).values([
    {
      id: 'n1',
      userId: 'u2',
      type: 'mention',
      payload: JSON.stringify({
        authorId: 'u1',
        authorName: 'Pessoa',
        authorUsername: 'pessoa',
        authorAvatar: FOTO,
        preview: 'olá',
      }),
    },
    { id: 'n2', userId: 'u2', type: 'mention', payload: AVISO_DE_OUTRA_PESSOA },
  ])
  await db
    .insert(refreshTokens)
    .values({ id: 'r1', token: 'tk1', userId: 'u1', expiresAt: new Date(Date.now() + 86_400_000), sessionId: 's-u1' })
}

describe('apagar a conta num Postgres de verdade', () => {
  beforeAll(async () => {
    await semear()
    const app = express()
    app.use(express.json())
    app.use('/api/auth', authRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
    const resposta = await fetch(`${base}/api/auth/apagar-conta`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ confirmacao: 'pessoa' }),
    })
    expect(resposta.status).toBe(200)
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  it('a conta vira lápide sem foto, e-mail pendente nem preferências', async () => {
    const [lapide] = await db.select().from(users).where(eq(users.id, 'u1'))
    expect(lapide).toMatchObject({
      displayName: 'conta apagada',
      avatarUrl: null,
      avatarFullUrl: null,
      bannerUrl: null,
      bio: null,
      pendingEmail: null,
      notificationPrefs: null,
    })
    expect(lapide.deletedAt).not.toBeNull()
    expect(await db.select().from(refreshTokens).where(eq(refreshTokens.userId, 'u1'))).toEqual([])
  })

  it('a mensagem antiga perde o nome e a foto guardados e mantém o texto e o anexo', async () => {
    const [mensagem] = await db.select().from(messages).where(eq(messages.id, 'm1'))
    expect(mensagem).toMatchObject({ authorName: null, authorAvatarUrl: null, content: 'olá', attachments: ANEXO })
  })

  it('o aviso que cita a conta troca o autor por "conta apagada" e o de outra pessoa fica igual', async () => {
    const [citado] = await db.select().from(notifications).where(eq(notifications.id, 'n1'))
    expect(JSON.parse(citado.payload)).toEqual({
      authorId: 'u1',
      authorName: 'conta apagada',
      authorUsername: null,
      authorAvatar: null,
      preview: 'olá',
    })
    const [outro] = await db.select().from(notifications).where(eq(notifications.id, 'n2'))
    expect(outro.payload).toBe(AVISO_DE_OUTRA_PESSOA)
  })

  it('as fotos saem do bucket, menos o banner que outra conta também usa', () => {
    expect([...removidas].sort()).toEqual([FOTO_GRANDE, FOTO].sort())
  })
})
