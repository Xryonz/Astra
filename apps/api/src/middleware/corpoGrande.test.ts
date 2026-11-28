import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import express from 'express'
import jwt from 'jsonwebtoken'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { corpoGrandeSoComLogin } from './corpoGrande'
import { requireAuth } from './auth'
import { generateAccessToken } from '../lib/jwt'
import { env } from '../lib/env'

let servidor: Server
let base = ''

const DOIS_MB = JSON.stringify({ texto: 'a'.repeat(2 * 1024 * 1024) })

const credencial = (segredo: string, exp: number) => jwt.sign({ userId: 'u1', jti: 'j1', sid: 's1', exp }, segredo)
const agoraEmSegundos = () => Math.floor(Date.now() / 1000)

beforeAll(async () => {
  const app = express()
  app.use('/api/profile', corpoGrandeSoComLogin)
  app.use(express.json({ limit: '1mb' }))
  app.post('/api/profile', requireAuth, (req, res) => {
    res.json({ tamanho: String(req.body?.texto ?? '').length })
  })
  await new Promise<void>((pronto) => {
    servidor = app.listen(0, () => pronto())
  })
  base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
})

afterAll(() => {
  servidor.close()
})

function enviar(corpo: string, autorizacao?: string) {
  const headers: Record<string, string> = { 'content-type': 'application/json' }
  if (autorizacao) headers.authorization = autorizacao
  return fetch(`${base}/api/profile`, { method: 'POST', headers, body: corpo })
}

describe('corpo grande só para quem apresenta credencial', () => {
  it('sem credencial, um corpo acima de 1 MB é recusado antes de ser lido inteiro', async () => {
    const resposta = await enviar(DOIS_MB)
    expect(resposta.status).toBe(413)
  })

  it('com credencial falsa ou assinada por outro segredo, vale o mesmo limite de 1 MB', async () => {
    expect((await enviar(DOIS_MB, 'Bearer credencial-falsa')).status).toBe(413)
    const deOutroSegredo = credencial('outro-segredo-com-mais-de-32-caracteres!!', agoraEmSegundos() + 600)
    expect((await enviar(DOIS_MB, `Bearer ${deOutroSegredo}`)).status).toBe(413)
  })

  it('com credencial válida, o corpo de 2 MB passa, como a foto em data-URI precisa', async () => {
    const { token } = generateAccessToken('u1', 's1')
    const resposta = await enviar(DOIS_MB, `Bearer ${token}`)
    expect(resposta.status).toBe(200)
    expect(await resposta.json()).toEqual({ tamanho: 2 * 1024 * 1024 })
  })

  it('com credencial vencida do próprio servidor, o corpo passa e a rota responde 401, para o app renovar', async () => {
    const vencida = credencial(env.JWT_ACCESS_SECRET, agoraEmSegundos() - 60)
    const resposta = await enviar(DOIS_MB, `Bearer ${vencida}`)
    expect(resposta.status).toBe(401)
    expect(await resposta.json()).toMatchObject({ code: 'TOKEN_EXPIRED' })
  })
})
