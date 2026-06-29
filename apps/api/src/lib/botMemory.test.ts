import { describe, it, expect, beforeEach, vi } from 'vitest'
import { redis } from './redis'
import {
  pushTurn, getHistory, countTurns, clearMemory,
  setSummary, getSummary,
  consumeTokens, consumeToolCall,
  MEMORY_TTL_SECONDS, DAILY_TOKEN_LIMIT, DAILY_TOOL_LIMIT,
} from './botMemory'

const U = 'user-test'
const C = 'channel-test'

beforeEach(async () => {

  await (redis as any).flushall()
})

describe('pushTurn + getHistory', () => {
  it('salva e recupera turns em ordem cronológica', async () => {
    await pushTurn(U, C, { role: 'user',      content: 'oi',     ts: 1000 })
    await pushTurn(U, C, { role: 'assistant', content: 'olá!',   ts: 2000 })
    await pushTurn(U, C, { role: 'user',      content: 'tudo?',  ts: 3000 })

    const h = await getHistory(U, C, 10)
    expect(h.map((t) => t.content)).toEqual(['oi', 'olá!', 'tudo?'])
  })

  it('limit retorna apenas últimos N', async () => {
    for (let i = 0; i < 5; i++) {
      await pushTurn(U, C, { role: 'user', content: `msg-${i}`, ts: 1000 + i })
    }
    const h = await getHistory(U, C, 2)
    expect(h.map((t) => t.content)).toEqual(['msg-3', 'msg-4'])
  })

  it('countTurns reflete total', async () => {
    expect(await countTurns(U, C)).toBe(0)
    await pushTurn(U, C, { role: 'user', content: 'a', ts: 1 })
    await pushTurn(U, C, { role: 'user', content: 'b', ts: 2 })
    expect(await countTurns(U, C)).toBe(2)
  })

  it('chaves por user × channel não vazam entre si', async () => {
    await pushTurn('u1', 'c1', { role: 'user', content: 'segredo-u1', ts: 1 })
    await pushTurn('u2', 'c1', { role: 'user', content: 'segredo-u2', ts: 1 })

    const h1 = await getHistory('u1', 'c1')
    const h2 = await getHistory('u2', 'c1')
    expect(h1[0].content).toBe('segredo-u1')
    expect(h2[0].content).toBe('segredo-u2')
  })

  it('clearMemory apaga turns + summary', async () => {
    await pushTurn(U, C, { role: 'user', content: 'x', ts: 1 })
    await setSummary(U, C, { text: 'resumo', turnsCovered: 5, createdAt: 1 }, 0)
    expect(await countTurns(U, C)).toBe(1)
    expect(await getSummary(U, C)).not.toBeNull()

    await clearMemory(U, C)
    expect(await countTurns(U, C)).toBe(0)
    expect(await getSummary(U, C)).toBeNull()
  })
})

describe('TTL — fixo, não reseta com novo turn', () => {
  it('primeiro turn seta TTL de 24h', async () => {
    await pushTurn(U, C, { role: 'user', content: 'a', ts: 1 })
    const ttl = await redis.ttl(`bot:mem:${U}:${C}`)

    if (ttl !== -1) {
      expect(ttl).toBeGreaterThan(MEMORY_TTL_SECONDS - 5)
      expect(ttl).toBeLessThanOrEqual(MEMORY_TTL_SECONDS)
    }
  })

  it('código usa EXPIRE NX no segundo turn (não tenta resetar)', async () => {

    await pushTurn(U, C, { role: 'user', content: 'a', ts: 1 })

    const expireCalls: unknown[][] = []
    const originalMulti = redis.multi.bind(redis)
    const multiSpy = vi.spyOn(redis, 'multi').mockImplementation((...args: any[]) => {
      const pipe = originalMulti(...(args as []))
      const origExpire = pipe.expire.bind(pipe)
      pipe.expire = ((...exArgs: any[]) => {
        expireCalls.push(exArgs)
        return origExpire(...(exArgs as Parameters<typeof origExpire>))
      }) as typeof pipe.expire
      return pipe
    })

    await pushTurn(U, C, { role: 'user', content: 'b', ts: 2 })

    expect(expireCalls).toContainEqual([`bot:mem:${U}:${C}`, MEMORY_TTL_SECONDS, 'NX'])
    multiSpy.mockRestore()
  })
})

describe('summary', () => {
  it('setSummary + getSummary roundtrip', async () => {
    await setSummary(U, C, { text: 'resumo X', turnsCovered: 10, createdAt: 1234 }, 100)
    const s = await getSummary(U, C)
    expect(s).not.toBeNull()
    expect(s!.text).toBe('resumo X')
    expect(s!.turnsCovered).toBe(10)
  })

  it('setSummary remove turns antigos (score < cutoff)', async () => {
    await pushTurn(U, C, { role: 'user', content: 'antigo-1', ts: 100 })
    await pushTurn(U, C, { role: 'user', content: 'antigo-2', ts: 200 })
    await pushTurn(U, C, { role: 'user', content: 'recente',  ts: 500 })

    await setSummary(U, C, { text: 'sum', turnsCovered: 2, createdAt: 1 }, 300)

    const h = await getHistory(U, C, 10)
    expect(h.map((t) => t.content)).toEqual(['recente'])
  })
})

describe('budget — tokens', () => {
  it('consumo dentro do limite é permitido', async () => {
    const r = await consumeTokens(U, 100)
    expect(r.allowed).toBe(true)
    expect(r.remaining).toBe(DAILY_TOKEN_LIMIT - 100)
  })

  it('estoura limite → allowed=false', async () => {
    await consumeTokens(U, DAILY_TOKEN_LIMIT - 1)
    const r = await consumeTokens(U, 50)
    expect(r.allowed).toBe(false)
  })

  it('é per-user', async () => {
    await consumeTokens('u1', DAILY_TOKEN_LIMIT)
    const r = await consumeTokens('u2', 100)
    expect(r.allowed).toBe(true)
  })
})

describe('budget — tool calls', () => {
  it('cada call decrementa remaining', async () => {
    const a = await consumeToolCall(U)
    const b = await consumeToolCall(U)
    expect(a.remaining).toBe(DAILY_TOOL_LIMIT - 1)
    expect(b.remaining).toBe(DAILY_TOOL_LIMIT - 2)
  })

  it('estoura o limite', async () => {
    for (let i = 0; i < DAILY_TOOL_LIMIT; i++) await consumeToolCall(U)
    const over = await consumeToolCall(U)
    expect(over.allowed).toBe(false)
  })
})
