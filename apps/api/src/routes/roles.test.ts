import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'

const banco = vi.hoisted(() => ({
  alteracao: null as Record<string, unknown> | null,
  insercao: null as Record<string, unknown> | null,
}))

vi.mock('../db', () => ({
  db: {
    select: () => ({
      from: () => ({
        where: () => Object.assign(Promise.resolve([{ max: 0 }]), { limit: async () => [{ id: 'constelacao_1' }] }),
      }),
    }),
    insert: () => ({
      values: (valores: Record<string, unknown>) => {
        banco.insercao = valores
        return { returning: async () => [{ id: 'cargo_2', ...valores }] }
      },
    }),
    update: () => ({
      set: (alteracao: Record<string, unknown>) => {
        banco.alteracao = alteracao
        return { where: () => ({ returning: async () => [{ id: 'cargo_1', name: 'novo', permissions: '[]' }] }) }
      },
    }),
  },
}))
vi.mock('../middleware/auth', () => ({
  requireAuth: (req: { userId?: string }, _res: unknown, next: () => void) => {
    req.userId = 'dono_1'
    next()
  },
}))
vi.mock('../lib/permissions', async (importOriginal) => {
  const real = await importOriginal<typeof import('../lib/permissions')>()
  return {
    ...real,
    getMemberPerms: vi.fn(async () => ({ isOwner: true, isAdmin: true, permissions: new Set(), memberId: 'membro_1' })),
  }
})
vi.mock('../lib/audit', async (importOriginal) => {
  const real = await importOriginal<typeof import('../lib/audit')>()
  return { ...real, audit: vi.fn() }
})
vi.mock('../lib/membros', () => ({ cargosDoServidorMudaram: vi.fn(), cargosDoMembroMudaram: vi.fn() }))
vi.mock('../lib/storage', () => ({ persistImagemDeExibicao: vi.fn(async () => ({ url: null, original: null })) }))

import { rolesRouter } from './roles'

let servidor: Server
let base = ''

async function pedir(metodo: 'POST' | 'PATCH', caminho: string, corpo: unknown) {
  const resposta = await fetch(`${base}/api/servers/constelacao_1${caminho}`, {
    method: metodo,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(corpo),
  })
  return resposta.status
}

describe('cargos: campos que o pedido não manda', () => {
  beforeAll(async () => {
    const app = express()
    app.use(express.json())
    app.use('/api/servers', rolesRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  beforeEach(() => {
    banco.alteracao = null
    banco.insercao = null
  })

  it('renomear um cargo grava só o nome, sem zerar permissões nem destaque', async () => {
    expect(await pedir('PATCH', '/roles/cargo_1', { name: 'novo' })).toBe(200)
    expect(banco.alteracao).toEqual({ name: 'novo' })
  })

  it('criar um cargo sem permissões nem destaque grava a lista vazia e o destaque desligado', async () => {
    expect(await pedir('POST', '/roles', { name: 'cargo novo' })).toBe(201)
    expect(banco.insercao).toMatchObject({ name: 'cargo novo', permissions: '[]', hoist: false })
  })
})
