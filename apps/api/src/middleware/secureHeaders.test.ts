import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import express from 'express'
import type { Server } from 'http'
import type { AddressInfo } from 'net'
import { env } from '../lib/env'
import { secureHeaders } from './secureHeaders'

let servidor: Server
let base = ''

describe('cabecalhos de seguranca', () => {
  beforeAll(async () => {
    const app = express()
    app.use(secureHeaders)
    app.get('/', (_req, res) => { res.json({ ok: true }) })
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  it('mantem a politica de conteudo e as protecoes de quadro, tipo e origem', async () => {
    const cabecalhos = (await fetch(base)).headers
    const politica = cabecalhos.get('content-security-policy') ?? ''

    expect(politica).toContain("default-src 'self'")
    expect(politica).toContain("frame-src 'none'")
    expect(politica).toContain("object-src 'none'")
    expect(politica).toContain(`connect-src 'self' ${env.CLIENT_URL}`)
    expect(cabecalhos.get('x-frame-options')).toBe('DENY')
    expect(cabecalhos.get('x-content-type-options')).toBe('nosniff')
    expect(cabecalhos.get('x-xss-protection')).toBe('0')
    expect(cabecalhos.get('referrer-policy')).toBe('strict-origin-when-cross-origin')
    expect(cabecalhos.get('cross-origin-opener-policy')).toBe('same-origin-allow-popups')
    expect(cabecalhos.get('cross-origin-resource-policy')).toBe('cross-origin')
    expect(cabecalhos.get('cross-origin-embedder-policy')).toBeNull()
  })

  it('fora de producao nao manda HSTS', async () => {
    expect((await fetch(base)).headers.get('strict-transport-security')).toBeNull()
  })
})
