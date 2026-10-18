import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest'
import express from 'express'
import type { Server } from 'http'
import type { AddressInfo } from 'net'

const COTA_DO_LOGIN = 10

let servidor: Server
let base = ''
const avisos: string[] = []

async function pedir(ip: string) {
  const resposta = await fetch(base, { headers: { 'X-Forwarded-For': ip } })
  return resposta.status
}

async function esgotar(ip: string) {
  for (let i = 0; i < COTA_DO_LOGIN; i++) expect(await pedir(ip)).toBe(200)
}

describe('limitador por endereco', () => {
  beforeAll(async () => {
    const guardar = (...partes: unknown[]) => { avisos.push(partes.map(String).join(' ')) }
    vi.spyOn(console, 'error').mockImplementation(guardar)
    vi.spyOn(console, 'warn').mockImplementation(guardar)
    const { authLimiter } = await import('./rateLimiter')
    const app = express()
    app.set('trust proxy', 1)
    app.use(authLimiter)
    app.get('/', (_req, res) => { res.json({ ok: true }) })
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(async () => {
    vi.restoreAllMocks()
    await new Promise<void>((fechado) => servidor.close(() => fechado()))
  })

  it('enderecos IPv6 da mesma rede /56 dividem a cota', async () => {
    await esgotar('2001:db8:aaaa:1100::1')

    expect(await pedir('2001:db8:aaaa:11ff::2')).toBe(429)
    expect(await pedir('2001:db8:aaaa:1200::1')).toBe(200)
  })

  it('enderecos IPv4 seguem separados, inclusive escritos como IPv6', async () => {
    await esgotar('203.0.113.7')
    await esgotar('::ffff:198.51.100.1')

    expect(await pedir('203.0.113.7')).toBe(429)
    expect(await pedir('203.0.113.8')).toBe(200)
    expect(await pedir('::ffff:198.51.100.2')).toBe(200)
  })

  it('a biblioteca aceita a chave sem reclamar', () => {
    expect(avisos.filter((aviso) => aviso.includes('ERR_ERL'))).toEqual([])
  })
})
