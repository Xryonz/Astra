/**
 * Memória conversacional temporária do bot.
 *
 * Modelo:
 * - Chave Redis: `bot:mem:{userId}:{channelId}` (per-user-per-channel)
 * - ZSET com score = ms timestamp, member = JSON `{role, content, ts}`
 * - TTL: 24h, setado apenas no primeiro turn (NX). Não reseta — memória
 *   morre 24h depois da primeira mensagem, garantindo "reset diário".
 *
 * Pra controlar tamanho do contexto:
 * - WORKING_WINDOW: últimos N turnos crus que vão pro prompt
 * - SUMMARY_TRIGGER: quando atingir N turnos, comprime os antigos num
 *   resumo curto (próximo arquivo botTools/bot.ts cuida do summarize)
 *
 * Token budget:
 * - `bot:tokens:{userId}` = counter de tokens consumidos no dia (TTL 24h)
 * - Bloqueia quando passa do limite (DAILY_TOKEN_LIMIT)
 *
 * Tool calls budget:
 * - `bot:tools:{userId}` = counter de tool calls/24h (TTL 24h)
 */
import { redis } from './redis'

export const MEMORY_TTL_SECONDS  = 24 * 60 * 60
export const WORKING_WINDOW       = 12          // últimas 12 mensagens (6 turns user+bot)
export const SUMMARY_TRIGGER      = 20          // resume quando passar de 20
export const DAILY_TOKEN_LIMIT    = 100_000     // ~Sonnet 4.6 / Haiku misturado
export const DAILY_TOOL_LIMIT     = 50          // tool calls por user/dia

export type Role = 'user' | 'assistant'
export interface MemoryTurn { role: Role; content: string; ts: number }
export interface Summary    { text: string; turnsCovered: number; createdAt: number }

const memKey     = (userId: string, channelId: string) => `bot:mem:${userId}:${channelId}`
const summaryKey = (userId: string, channelId: string) => `bot:summary:${userId}:${channelId}`
const tokensKey  = (userId: string) => `bot:tokens:${userId}`
const toolsKey   = (userId: string) => `bot:tools:${userId}`

/**
 * Salva um turn. Seta TTL só na primeira escrita (Redis EXPIRE NX).
 */
export async function pushTurn(userId: string, channelId: string, turn: MemoryTurn): Promise<void> {
  const k = memKey(userId, channelId)
  // Pipeline: 1 round-trip em vez de 2. EXPIRE NX só seta se ainda não
  // tem TTL → preserva "reset diário fixo" da 1ª escrita.
  await redis.multi()
    .zadd(k, turn.ts, JSON.stringify(turn))
    .expire(k, MEMORY_TTL_SECONDS, 'NX')
    .exec()
}

/**
 * Pega últimas N mensagens (ordem cronológica ascendente).
 */
export async function getHistory(
  userId: string, channelId: string, limit = WORKING_WINDOW,
): Promise<MemoryTurn[]> {
  const k    = memKey(userId, channelId)
  const raws = await redis.zrange(k, -limit, -1) // últimas N
  return raws.map((r) => safeParseTurn(r)).filter((t): t is MemoryTurn => t !== null)
}

/**
 * Conta total de turns na memória.
 */
export async function countTurns(userId: string, channelId: string): Promise<number> {
  return redis.zcard(memKey(userId, channelId))
}

/**
 * Pega summary salvo (se houver).
 */
export async function getSummary(userId: string, channelId: string): Promise<Summary | null> {
  const raw = await redis.get(summaryKey(userId, channelId))
  if (!raw) return null
  try { return JSON.parse(raw) as Summary } catch { return null }
}

/**
 * Salva summary novo + remove turns antigos que foram comprimidos.
 */
export async function setSummary(
  userId: string, channelId: string, summary: Summary, removeBeforeTs: number,
): Promise<void> {
  const k = summaryKey(userId, channelId)
  // Pipeline: SET summary + remove turns antigos em 1 round-trip.
  await redis.multi()
    .set(k, JSON.stringify(summary), 'EX', MEMORY_TTL_SECONDS)
    .zremrangebyscore(memKey(userId, channelId), 0, removeBeforeTs - 1)
    .exec()
}

/**
 * Limpa toda memória do user num canal — comando `/astra reset`.
 */
export async function clearMemory(userId: string, channelId: string): Promise<void> {
  await redis.del(memKey(userId, channelId), summaryKey(userId, channelId))
}

// ─── Token budget ─────────────────────────────────────────────

/**
 * Tenta consumir N tokens. Retorna { allowed, remaining }.
 */
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

/**
 * Tenta consumir 1 tool call. Retorna allowed + remaining.
 */
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
