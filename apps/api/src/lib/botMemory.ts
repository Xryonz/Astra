
import { redis } from './redis'

export const MEMORY_TTL_SECONDS  = 24 * 60 * 60
export const WORKING_WINDOW       = 12
export const SUMMARY_TRIGGER      = 20
export const DAILY_TOKEN_LIMIT    = 100_000
export const DAILY_TOOL_LIMIT     = 50

export type Role = 'user' | 'assistant'
export interface MemoryTurn { role: Role; content: string; ts: number }
export interface Summary    { text: string; turnsCovered: number; createdAt: number }

const memKey     = (userId: string, channelId: string) => `bot:mem:${userId}:${channelId}`
const summaryKey = (userId: string, channelId: string) => `bot:summary:${userId}:${channelId}`
const tokensKey  = (userId: string) => `bot:tokens:${userId}`
const toolsKey   = (userId: string) => `bot:tools:${userId}`

export async function pushTurn(userId: string, channelId: string, turn: MemoryTurn): Promise<void> {
  const k = memKey(userId, channelId)

  await redis.multi()
    .zadd(k, turn.ts, JSON.stringify(turn))
    .expire(k, MEMORY_TTL_SECONDS, 'NX')
    .exec()
}

export async function getHistory(
  userId: string, channelId: string, limit = WORKING_WINDOW,
): Promise<MemoryTurn[]> {
  const k    = memKey(userId, channelId)
  const raws = await redis.zrange(k, -limit, -1)
  return raws.map((r) => safeParseTurn(r)).filter((t): t is MemoryTurn => t !== null)
}

export async function countTurns(userId: string, channelId: string): Promise<number> {
  return redis.zcard(memKey(userId, channelId))
}

export async function getSummary(userId: string, channelId: string): Promise<Summary | null> {
  const raw = await redis.get(summaryKey(userId, channelId))
  if (!raw) return null
  try { return JSON.parse(raw) as Summary } catch { return null }
}

export async function setSummary(
  userId: string, channelId: string, summary: Summary, removeBeforeTs: number,
): Promise<void> {
  const k = summaryKey(userId, channelId)

  await redis.multi()
    .set(k, JSON.stringify(summary), 'EX', MEMORY_TTL_SECONDS)
    .zremrangebyscore(memKey(userId, channelId), 0, removeBeforeTs - 1)
    .exec()
}

export async function clearMemory(userId: string, channelId: string): Promise<void> {
  await redis.del(memKey(userId, channelId), summaryKey(userId, channelId))
}

export async function consumeTokens(userId: string, n: number): Promise<{ allowed: boolean; remaining: number }> {
  const k = tokensKey(userId)
  const res = await redis.multi()
    .incrby(k, n)
    .expire(k, MEMORY_TTL_SECONDS, 'NX')
    .exec()
  const newVal = Number(res?.[0]?.[1] ?? 0)
  const remaining = Math.max(0, DAILY_TOKEN_LIMIT - newVal)
  return { allowed: newVal <= DAILY_TOKEN_LIMIT, remaining }
}

export async function consumeToolCall(userId: string): Promise<{ allowed: boolean; remaining: number }> {
  const k = toolsKey(userId)
  const res = await redis.multi()
    .incr(k)
    .expire(k, MEMORY_TTL_SECONDS, 'NX')
    .exec()
  const newVal = Number(res?.[0]?.[1] ?? 0)
  const remaining = Math.max(0, DAILY_TOOL_LIMIT - newVal)
  return { allowed: newVal <= DAILY_TOOL_LIMIT, remaining }
}

function safeParseTurn(raw: string): MemoryTurn | null {
  try {
    const v = JSON.parse(raw) as Partial<MemoryTurn>
    if (typeof v.content !== 'string') return null
    if (v.role !== 'user' && v.role !== 'assistant') return null
    if (typeof v.ts !== 'number') return null
    return { role: v.role, content: v.content, ts: v.ts }
  } catch { return null }
}
