import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import path from 'path'
import { promises as fs } from 'fs'
import sharp from 'sharp'
import type { Server } from 'http'
import type { AddressInfo } from 'net'

const gravadas = vi.hoisted(() => ({ fotos: [] as string[] }))

vi.mock('../db', () => ({ db: {} }))
vi.mock('../lib/redis', () => ({
  getUserStatus: vi.fn(), setUserOnline: vi.fn(), redis: {}, presenceKeys: {}, activityKeys: {}, leAtividade: vi.fn(),
}))
vi.mock('../lib/realtime', () => ({ presenceChanged: vi.fn(), profileChanged: vi.fn() }))
vi.mock('../middleware/auth', () => ({
  requireAuth: (req: { userId?: string }, _res: unknown, next: () => void) => {
    req.userId = 'usuario_1'
    next()
  },
}))
vi.mock('../lib/storage', async (importOriginal) => {
  const real = await importOriginal<typeof import('../lib/storage')>()
  return {
    ...real,
    persistImagemDeExibicao: vi.fn(async (valor: string) => {
      gravadas.fotos.push(valor)
      return { url: 'https://armazem.exemplo/foto.webp', original: null }
    }),
  }
})

import profileRouter from './profile'

const GIF_DA_SPARKLE = path.join(__dirname, '../../public/bot/sparkle.gif')

let servidor: Server
let base = ''

async function recortar(corpo: unknown) {
  const resposta = await fetch(`${base}/api/profile/foto`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(corpo),
  })
  return { status: resposta.status, json: (await resposta.json()) as { data?: { url: string }; error?: string } }
}

describe('POST /api/profile/foto', () => {
  beforeAll(async () => {
    const app = express()
    app.use('/api/profile', express.json({ limit: '16mb' }))
    app.use('/api/profile', profileRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  beforeEach(() => {
    gravadas.fotos = []
  })

  it('recorta o GIF, grava o WebP animado e devolve a URL', async () => {
    const gif = await fs.readFile(GIF_DA_SPARKLE)
    const { status, json } = await recortar({
      imagem: `data:image/gif;base64,${gif.toString('base64')}`,
      recorte: { x: 95, y: 0, lado: 307 },
    })

    expect(status).toBe(200)
    expect(json.data?.url).toBe('https://armazem.exemplo/foto.webp')
    expect(gravadas.fotos).toHaveLength(1)
    expect(gravadas.fotos[0]).toMatch(/^data:image\/webp;base64,/)
    const bytes = Buffer.from(gravadas.fotos[0].slice(gravadas.fotos[0].indexOf(',') + 1), 'base64')
    const meta = await sharp(bytes, { animated: true }).metadata()
    expect(meta.pages).toBe(69)
    expect(meta.width).toBe(256)
  })

  it('enquadramento fora da imagem dá 422 e não grava nada', async () => {
    const png = await sharp({ create: { width: 50, height: 50, channels: 3, background: { r: 0, g: 0, b: 0 } } }).png().toBuffer()
    const { status, json } = await recortar({
      imagem: `data:image/png;base64,${png.toString('base64')}`,
      recorte: { x: 60, y: 0, lado: 10 },
    })

    expect(status).toBe(422)
    expect(json.error).toContain('fora da imagem')
    expect(gravadas.fotos).toHaveLength(0)
  })

  it('imagem que não é data URI é recusada na validação', async () => {
    const { status } = await recortar({ imagem: 'https://exemplo.com/x.gif', recorte: { x: 0, y: 0, lado: 10 } })

    expect(status).toBe(400)
    expect(gravadas.fotos).toHaveLength(0)
  })

  it('recorte com número negativo é recusado na validação', async () => {
    const { status } = await recortar({ imagem: 'data:image/png;base64,AAAA', recorte: { x: -1, y: 0, lado: 10 } })

    expect(status).toBe(400)
  })

  it('arquivo que não é imagem dá 422', async () => {
    const { status, json } = await recortar({
      imagem: `data:image/gif;base64,${Buffer.from('isto nao e uma imagem').toString('base64')}`,
      recorte: { x: 0, y: 0, lado: 10 },
    })

    expect(status).toBe(422)
    expect(json.error).toContain('Não foi possível ler')
  })
})
