import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import bcrypt from 'bcryptjs'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'

vi.mock('../db', async () => ({ db: (await import('../__tests__/bancoGravador')).bancoGravador }))
vi.mock('../middleware/auth', () => ({
  requireAuth: (req: { userId?: string }, _res: unknown, next: () => void) => {
    req.userId = 'u1'
    next()
  },
}))
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
import { gravador } from '../__tests__/bancoGravador'

let servidor: Server
let base = ''
let hashAtual = ''

async function apagar(corpo: { confirmacao: string; password?: string }) {
  const resposta = await fetch(`${base}/api/auth/apagar-conta`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(corpo),
  })
  return resposta.status
}

describe('apagar a conta', () => {
  beforeAll(async () => {
    hashAtual = await bcrypt.hash('Antiga123', 4)
    const app = express()
    app.use(express.json())
    app.use('/api/auth', authRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  beforeEach(() => gravador.limpar())

  it('derruba ao vivo as sessoes que ainda estavam valendo, depois de apagar as linhas, e so elas', async () => {
    gravador.responder('select:User', [{ username: 'pessoa', passwordHash: null }])
    gravador.responder('update:User', [{ id: 'u1' }])
    gravador.responder('delete:RefreshToken', [
      { id: 'r1', sessionId: 's1', revokedAt: null },
      { id: 'r2', sessionId: 's1', revokedAt: new Date() },
      { id: 'r3', sessionId: 's2', revokedAt: new Date() },
      { id: 'r4', sessionId: null, revokedAt: null },
    ])
    expect(await apagar({ confirmacao: 'pessoa' })).toBe(200)
    expect(gravador.consulta('db:update User')?.sql).toContain('"User"."passwordHash" is null')
    expect(gravador.passos.indexOf('db:delete RefreshToken')).toBeLessThan(gravador.passos.indexOf('encerrou s1,r4'))
    expect(gravador.passos[gravador.passos.length - 1]).toBe('encerrou s1,r4')
  })

  it('a senha mudou entre a conferencia e a lapide: nao apaga nada', async () => {
    gravador.responder('select:User', [{ username: 'pessoa', passwordHash: hashAtual }])
    expect(await apagar({ confirmacao: 'pessoa', password: 'Antiga123' })).toBe(401)
    const lapide = gravador.consulta('db:update User')
    expect(lapide?.sql).toContain('"User"."passwordHash" = ')
    expect(lapide?.params).toContain(hashAtual)
    expect(gravador.passos.filter((passo) => passo.startsWith('db:delete'))).toEqual([])
  })
})
