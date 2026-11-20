import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest'
import express from 'express'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'

vi.mock('../db', () => ({
  db: {
    select: () => ({
      from: () => ({
        where: () => ({
          limit: async () => [{ type: 'VOICE', username: 'ana', displayName: 'Ana', avatarUrl: null }],
        }),
      }),
    }),
  },
}))
vi.mock('../middleware/auth', () => ({
  requireAuth: (req: { userId?: string }, _res: unknown, next: () => void) => {
    req.userId = 'u1'
    next()
  },
}))
vi.mock('../lib/permissions', () => ({ userCanSeeChannel: async () => true }))
vi.mock('../lib/env', async (importOriginal) => {
  const real = await importOriginal<typeof import('../lib/env')>()
  return {
    env: {
      ...real.env,
      LIVEKIT_URL: 'wss://astra-teste.livekit.cloud',
      LIVEKIT_API_KEY: 'chave',
      LIVEKIT_API_SECRET: 'segredo-de-teste-com-mais-de-32-caracteres',
    },
  }
})

import voiceRouter from './voice'

let servidor: Server
let base = ''

function corpoDoToken(token: string): { nbf: number; exp: number; sub: string } {
  return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'))
}

describe('credencial de voz', () => {
  beforeAll(async () => {
    const app = express()
    app.use(express.json())
    app.use('/api/voice', voiceRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  it('vale 10 minutos: so serve para entrar, e o LiveKit renova a de quem ja esta na sala', async () => {
    const resposta = await fetch(`${base}/api/voice/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ roomKind: 'channel', roomId: 'v1' }),
    })
    expect(resposta.status).toBe(200)
    const { data } = (await resposta.json()) as { data: { token: string; roomName: string } }
    const corpo = corpoDoToken(data.token)

    expect(data.roomName).toBe('channel:v1')
    expect(corpo.sub).toBe('u1')
    expect(corpo.exp - corpo.nbf).toBe(600)
  })
})
