import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'

vi.mock('../db', async () => ({ db: (await import('../__tests__/bancoGravador')).bancoGravador }))
vi.mock('../lib/sessoes', async (importOriginal) => {
  const real = await importOriginal<typeof import('../lib/sessoes')>()
  const { gravador } = await import('../__tests__/bancoGravador')
  return {
    ...real,
    encerrarAoVivo: async (_userId: string, sessoes: string[]) => {
      if (sessoes.length) gravador.anotar(`encerrou ${sessoes.join(',')}`)
    },
  }
})

import authRouter from './auth'
import { generateRefreshToken, verifyAccessToken } from '../lib/jwt'
import { gravador } from '../__tests__/bancoGravador'

let servidor: Server
let base = ''

const contaAtiva = [{ apagadaEm: null, senha: 'h' }]
const TRAVA = 'tx:select User for no key update'

async function renovar() {
  const resposta = await fetch(`${base}/api/auth/refresh`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${generateRefreshToken('u1')}` },
  })
  const corpo = (await resposta.json()) as { data?: { accessToken: string } }
  return {
    status: resposta.status,
    sessao: corpo.data ? verifyAccessToken(corpo.data.accessToken).sid : undefined,
  }
}

describe('a renovacao da credencial', () => {
  beforeAll(async () => {
    const app = express()
    app.use(express.json())
    app.use('/api/auth', authRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  beforeEach(() => gravador.limpar())

  it('trava a conta antes de reivindicar e grava a linha nova na mesma transacao', async () => {
    gravador.responder('select:User', contaAtiva)
    gravador.responder('update:RefreshToken', [{ id: 'r1', sessionId: 's1' }])
    const { status } = await renovar()
    expect(status).toBe(200)
    expect(gravador.passos).toEqual(['begin', TRAVA, 'tx:update RefreshToken', 'tx:insert RefreshToken', 'commit'])
  })

  it('a linha nova e a credencial nova herdam a sessao da linha renovada', async () => {
    gravador.responder('select:User', contaAtiva)
    gravador.responder('update:RefreshToken', [{ id: 'r1', sessionId: 's1' }])
    const { sessao } = await renovar()
    expect(sessao).toBe('s1')
    expect(gravador.consulta('tx:insert RefreshToken')?.valores).toMatchObject({ userId: 'u1', sessionId: 's1' })
  })

  it('linha de antes da coluna: a sessao passa a ser o id dela, e fica fixa dali em diante', async () => {
    gravador.responder('select:User', contaAtiva)
    gravador.responder('update:RefreshToken', [{ id: 'r1', sessionId: null }])
    const { sessao } = await renovar()
    expect(sessao).toBe('r1')
    expect(gravador.consulta('tx:insert RefreshToken')?.valores).toMatchObject({ sessionId: 'r1' })
  })

  it('so reivindica a linha do dono da credencial', async () => {
    gravador.responder('select:User', contaAtiva)
    gravador.responder('update:RefreshToken', [{ id: 'r1', sessionId: 's1' }])
    await renovar()
    const reivindicacao = gravador.consulta('tx:update RefreshToken')
    expect(reivindicacao?.sql).toContain('"RefreshToken"."userId" = ')
    expect(reivindicacao?.params).toContain('u1')
  })

  it('conta apagada: recusa sem tocar em nenhuma linha', async () => {
    gravador.responder('select:User', [{ apagadaEm: new Date(), senha: null }])
    const { status } = await renovar()
    expect(status).toBe(401)
    expect(gravador.passos).toEqual(['begin', TRAVA, 'commit'])
  })

  it('reuso depois de 30 s derruba todas as sessoes dentro da trava, e so avisa depois do commit', async () => {
    gravador.responder('select:User', contaAtiva)
    gravador.responder('update:RefreshToken', [], [{ id: 'r7', sessionId: 's7' }])
    gravador.responder('select:RefreshToken', [{ revokedAt: new Date(Date.now() - 60_000) }])
    const { status } = await renovar()
    expect(status).toBe(401)
    expect(gravador.passos).toEqual([
      'begin',
      TRAVA,
      'tx:update RefreshToken',
      'tx:select RefreshToken',
      'tx:update RefreshToken',
      'commit',
      'encerrou s7',
    ])
  })

  it('credencial ja gasta ha menos de 30 s (corrida do proprio app): so recusa, nao derruba ninguem', async () => {
    gravador.responder('select:User', contaAtiva)
    gravador.responder('update:RefreshToken', [])
    gravador.responder('select:RefreshToken', [{ revokedAt: new Date(Date.now() - 5_000) }])
    const { status } = await renovar()
    expect(status).toBe(401)
    expect(gravador.passos).toEqual(['begin', TRAVA, 'tx:update RefreshToken', 'tx:select RefreshToken', 'commit'])
  })
})
