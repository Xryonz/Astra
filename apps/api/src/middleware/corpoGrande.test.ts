import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import express from 'express'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { corpoGrandeSoComLogin } from './corpoGrande'
import { generateAccessToken } from '../lib/jwt'

let servidor: Server
let base = ''

const DOIS_MB = JSON.stringify({ texto: 'a'.repeat(2 * 1024 * 1024) })

beforeAll(async () => {
  const app = express()
  app.use('/api/profile', corpoGrandeSoComLogin)
  app.use(express.json({ limit: '1mb' }))
  app.post('/api/profile', (req, res) => {
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

  it('com credencial inválida, vale o mesmo limite de 1 MB', async () => {
    const resposta = await enviar(DOIS_MB, 'Bearer credencial-falsa')
    expect(resposta.status).toBe(413)
  })

  it('com credencial válida, o corpo de 2 MB passa, como a foto em data-URI precisa', async () => {
    const { token } = generateAccessToken('u1', 's1')
    const resposta = await enviar(DOIS_MB, `Bearer ${token}`)
    expect(resposta.status).toBe(200)
    expect(await resposta.json()).toEqual({ tamanho: 2 * 1024 * 1024 })
  })
})
