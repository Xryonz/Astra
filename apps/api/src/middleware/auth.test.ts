import { describe, it, expect, beforeEach, vi } from 'vitest'
import jwt from 'jsonwebtoken'
import type { Request, Response } from 'express'

const h = vi.hoisted(() => ({ encerradas: new Set<string>() }))

vi.mock('../lib/redis', () => ({
  isTokenBlacklisted: async () => false,
  isSessaoEncerrada: async (sessao: string) => h.encerradas.has(sessao),
}))

import { requireAuth } from './auth'
import { generateAccessToken } from '../lib/jwt'

async function pedirCom(token: string) {
  const req = { headers: { authorization: `Bearer ${token}` } } as Request
  const resposta = { status: 200, corpo: undefined as unknown }
  const res = {
    status(codigo: number) {
      resposta.status = codigo
      return this
    },
    json(corpo: unknown) {
      resposta.corpo = corpo
      return this
    },
  } as unknown as Response
  const next = vi.fn()
  await requireAuth(req, res, next)
  return { ...resposta, next, req }
}

describe('credencial de uma sessao encerrada', () => {
  beforeEach(() => h.encerradas.clear())

  it('e recusada na hora, mesmo antes de vencer', async () => {
    h.encerradas.add('s1')
    const { status, corpo, next } = await pedirCom(generateAccessToken('u1', 's1').token)
    expect(status).toBe(401)
    expect(corpo).toMatchObject({ code: 'TOKEN_REVOKED' })
    expect(next).not.toHaveBeenCalled()
  })

  it('a de outra sessao da mesma conta continua valendo', async () => {
    h.encerradas.add('s1')
    const { next, req } = await pedirCom(generateAccessToken('u1', 's2').token)
    expect(next).toHaveBeenCalled()
    expect(req.userId).toBe('u1')
  })

  it('credencial emitida antes da mudanca, sem sessao dentro, vale ate vencer', async () => {
    const antiga = jwt.sign({ userId: 'u1', jti: 'j1' }, process.env.JWT_ACCESS_SECRET as string, { expiresIn: 900 })
    const { next } = await pedirCom(antiga)
    expect(next).toHaveBeenCalled()
  })
})
