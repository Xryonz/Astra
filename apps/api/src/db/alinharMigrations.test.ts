import { describe, it, expect, beforeEach, vi } from 'vitest'
import { alinharMigrations } from './alinharMigrations'
import { migrate } from 'drizzle-orm/node-postgres/migrator'

const consultas: { sql: string; params?: unknown[] }[] = []
let temUser = true
let ultimoCarimbo: number | null = null

const query = vi.fn(async (sql: string, params?: unknown[]) => {
  consultas.push({ sql, params })
  if (sql.includes('to_regclass')) return { rows: [{ alvo: temUser ? 'User' : null }] }
  if (sql.includes('order by created_at desc')) {
    return { rows: ultimoCarimbo == null ? [] : [{ created_at: ultimoCarimbo }] }
  }
  return { rows: [] }
})

vi.mock('./index', () => ({ pool: { query: (...a: unknown[]) => query(...(a as [string])) } }))
vi.mock('../lib/logger', () => ({ logger: { info: vi.fn(), warn: vi.fn(), error: vi.fn() } }))
vi.mock('drizzle-orm/node-postgres', () => ({ drizzle: vi.fn(() => ({})) }))
vi.mock('drizzle-orm/node-postgres/migrator', () => ({ migrate: vi.fn(async () => undefined) }))
vi.mock('drizzle-orm/migrator', () => ({
  readMigrationFiles: () => [
    { sql: ['a'], folderMillis: 100, hash: 'hash-a' },
    { sql: ['b'], folderMillis: 200, hash: 'hash-b' },
    { sql: ['c'], folderMillis: 300, hash: 'hash-c' },
  ],
}))

const carimbos = () => consultas.filter((c) => c.sql.includes('insert into'))

beforeEach(() => {
  consultas.length = 0
  query.mockClear()
  vi.mocked(migrate).mockClear()
  temUser = true
  ultimoCarimbo = null
})

describe('alinharMigrations', () => {
  it('banco novo nao carimba nada e deixa o migrator criar tudo', async () => {
    temUser = false
    await alinharMigrations()
    expect(carimbos()).toHaveLength(0)
    expect(migrate).toHaveBeenCalledOnce()
  })

  it('banco com esquema e sem registro carimba todas sem executar', async () => {
    await alinharMigrations()
    expect(carimbos()).toHaveLength(3)
    expect(carimbos().map((c) => c.params?.[0])).toEqual(['hash-a', 'hash-b', 'hash-c'])
    expect(carimbos().map((c) => c.params?.[1])).toEqual([100, 200, 300])
  })

  it('carimba so o que falta quando o registro esta atrasado', async () => {
    ultimoCarimbo = 100
    await alinharMigrations()
    expect(carimbos().map((c) => c.params?.[0])).toEqual(['hash-b', 'hash-c'])
  })

  it('nao carimba nada quando o registro ja esta em dia', async () => {
    ultimoCarimbo = 300
    await alinharMigrations()
    expect(carimbos()).toHaveLength(0)
    expect(migrate).toHaveBeenCalledOnce()
  })

  it('cria o schema de controle antes de carimbar', async () => {
    await alinharMigrations()
    const ordem = consultas.map((c) => c.sql)
    const criouSchema = ordem.findIndex((s) => s.includes('create schema'))
    const primeiroCarimbo = ordem.findIndex((s) => s.includes('insert into'))
    expect(criouSchema).toBeGreaterThanOrEqual(0)
    expect(criouSchema).toBeLessThan(primeiroCarimbo)
  })
})
