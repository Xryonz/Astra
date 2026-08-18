import Redis from 'ioredis'

export const redis = new Redis(process.env.REDIS_URL ?? 'redis://localhost:6379', {
  maxRetriesPerRequest: 3,
  enableReadyCheck: true,
  retryStrategy(times) {
    if (times > 10) {
      console.error('[Redis] Falha ao reconectar após 10 tentativas')
      return null
    }
    return Math.min(times * 100, 3000)
  },
})

redis.on('connect', () => console.log('[Redis] Conectado'))
redis.on('error', (err) => console.error('[Redis] Erro:', err.message))

const PRESENCE_TTL = 60

export type PresenceStatus = 'ONLINE' | 'IDLE' | 'DND' | 'INVISIBLE'

export const presenceKeys = {
  user: (userId: string) => `presence:user:${userId}`,
}

// ATIVIDADE ("o que a pessoa está usando agora"). Mesma vida da presença: 60s de
// TTL, renovada pelo cliente enquanto ele estiver aberto e com o recurso ligado.
//
// SÓ NO REDIS, nunca no Postgres. Isto é estado de sessão, não histórico — e a
// diferença não é de arquitetura, é de promessa: quem liga isto está dizendo "pode
// mostrar o que estou fazendo AGORA", não "pode guardar o que eu fiz". Uma tabela
// transformaria um recurso de presença num registro de hábitos, que é outra coisa
// e precisaria de outro consentimento.
//
// O TTL também é a rede de segurança: app fechado à força, queda de luz, processo
// morto — em 60s a linha some sozinha, sem depender de ninguém avisar.
export const activityKeys = {
  user: (userId: string) => `activity:user:${userId}`,
}

// QUEM ESTÁ EM CALL. Mesma ideia da presença e da atividade, e pelo mesmo motivo.
//
// Antes isto era perguntado ao LiveKit (`listParticipants`), e o LiveKit sabia
// porque a mídia passava por ele. Com a call em ponto a ponto, ninguém no meio
// enxerga quem está lá — a mídia vai direto de uma pessoa para outra. Então a
// lista passa a ser mantida aqui.
//
// UMA CHAVE POR PESSOA POR SALA, com TTL, em vez de um conjunto por sala. A
// diferença importa: com um conjunto, quem cai sem avisar (queda de luz, processo
// morto, internet caindo) fica no conjunto para sempre, e a sala aparece cheia de
// gente que não está lá. Com uma chave por pessoa, a ausência do aviso de saída
// vira simplesmente uma chave que expira — o fantasma some sozinho.
//
// O cliente renova enquanto estiver na call. Se parar de renovar, sai da lista em
// menos de um minuto sem ninguém precisar fazer nada.
export const vozKeys = {
  membro: (channelId: string, userId: string) => `voz:${channelId}:${userId}`,
  daSala: (channelId: string) => `voz:${channelId}:*`,
}

// Vida da marca de "estou em call". Três vezes o intervalo de renovação do
// cliente (20s), para que uma renovação perdida por engasgo de rede não derrube
// alguém da lista — só duas seguidas.
export const VOZ_TTL_SEGUNDOS = 60

// A atividade guarda DESDE QUANDO, pro cartão poder dizer "há 2h 14min".
export type Atividade = { texto: string; desde: number }

// Formato guardado: "<epochMs>|<texto>". O separador é o PRIMEIRO "|" — nome de
// programa pode conter o caractere, e o texto é todo o resto da linha.
// Linha sem "|" é do formato antigo (chave viva de antes deste deploy): vale como
// texto e começa a contar agora, em vez de virar lixo na tela por um minuto.
export function leAtividade(cru: string | null | undefined): Atividade | null {
  if (!cru) return null
  const corte = cru.indexOf('|')
  if (corte < 0) return { texto: cru, desde: Date.now() }
  const texto = cru.slice(corte + 1)
  if (!texto) return null
  const desde = Number(cru.slice(0, corte))
  return { texto, desde: Number.isFinite(desde) && desde > 0 ? desde : Date.now() }
}

// O "DESDE" NÃO PODE REINICIAR A CADA RENOVAÇÃO.
//
// O publicador reenvia a MESMA atividade a cada 45s só pra segurar o TTL de 60s.
// Se cada reenvio gravasse um instante novo, o contador voltaria pra "agora mesmo"
// três vezes por minuto e nunca passaria de um minuto — um cronômetro que só sabe
// dizer zero. Por isso lê o que está lá antes: mesmo texto, mesmo início.
//
// Custa um GET a mais por renovação. É o preço de o número significar algo.
export async function setUserActivity(userId: string, texto: string): Promise<Atividade | null> {
  try {
    const chave = activityKeys.user(userId)
    const anterior = leAtividade(await redis.get(chave))
    const desde = anterior?.texto === texto ? anterior.desde : Date.now()
    await redis.setex(chave, PRESENCE_TTL, `${desde}|${texto}`)
    return { texto, desde }
  } catch {
    // Redis fora: a atividade não persiste, mas quem está com o app aberto ainda
    // recebe o evento ao vivo. Melhor isso do que sumir com o recurso inteiro.
    return { texto, desde: Date.now() }
  }
}

export async function clearUserActivity(userId: string): Promise<void> {
  try { await redis.del(activityKeys.user(userId)) } catch { /* cache off */ }
}

// Redis aqui é cache + presença — NUNCA crítico. Se o servidor estiver fora ou
// capado (ex.: limite de requests do plano free do Upstash), o comando rejeita
// com um ReplyError POR-COMANDO — que o `redis.on('error')` (só nível de conexão)
// NÃO captura. Uma dessas rejeições sem catch (ex.: o refreshPresence fire-and-
// forget do heartbeat do socket) virava unhandledRejection -> process.exit(1) ->
// crash-loop, e a API INTEIRA caía por causa do cache. Por isso todo helper aqui
// é à prova de falha: swallow + fallback seguro. Redis fora = presença/cache off,
// mas a API segue no ar. O /health ainda reporta redis:{ok:false} (visibilidade).
export async function setUserOnline(userId: string, status: PresenceStatus = 'ONLINE'): Promise<void> {
  try { await redis.setex(presenceKeys.user(userId), PRESENCE_TTL, status) } catch { /* cache off */ }
}

export async function setUserOffline(userId: string): Promise<void> {
  try { await redis.del(presenceKeys.user(userId)) } catch { /* cache off */ }
}

export async function refreshPresence(userId: string): Promise<void> {
  try { await redis.expire(presenceKeys.user(userId), PRESENCE_TTL) } catch { /* cache off */ }
}

export async function getUserStatus(userId: string): Promise<PresenceStatus | null> {
  try {
    const v = await redis.get(presenceKeys.user(userId))
    return (v as PresenceStatus | null) ?? null
  } catch {
    return null
  }
}

export async function isUserOnline(userId: string): Promise<boolean> {
  try {
    return (await redis.exists(presenceKeys.user(userId))) === 1
  } catch {
    return false
  }
}

// Cache EM PROCESSO do resultado de isTokenBlacklisted. Esse check rodava um
// comando Redis em TODA request autenticada (auth.ts) — de longe o maior
// consumidor da quota do Upstash (o que estourou os 500k). Com um TTL curto,
// leituras repetidas do mesmo jti (o caso comum: um usuário faz N requests com o
// mesmo token) viram 1 comando a cada BLACKLIST_CACHE_MS. blacklistToken atualiza
// o cache local na hora -> logout instantâneo nesta instância (no free tier é 1
// instância só). Miss propaga a revogação em no máx BLACKLIST_CACHE_MS (aceitável:
// o access token já tem exp curto próprio).
const BLACKLIST_CACHE_MS = 30_000
const BLACKLIST_CACHE_MAX = 10_000
const blacklistCache = new Map<string, { revoked: boolean; exp: number }>()

function rememberBlacklist(jti: string, revoked: boolean, ttlMs: number): void {
  // Poda preguiçosa: só quando o mapa cresce, varre e tira os expirados (evita
  // vazamento de memória em uptime longo sem precisar de um timer).
  if (blacklistCache.size > BLACKLIST_CACHE_MAX) {
    const now = Date.now()
    for (const [k, v] of blacklistCache) if (v.exp <= now) blacklistCache.delete(k)
  }
  blacklistCache.set(jti, { revoked, exp: Date.now() + ttlMs })
}

export async function blacklistToken(jti: string, expiresInSeconds: number): Promise<void> {
  rememberBlacklist(jti, true, expiresInSeconds * 1000) // logout instantâneo local
  try { await redis.setex(`blacklist:token:${jti}`, expiresInSeconds, '1') } catch { /* best-effort */ }
}

// FAIL-OPEN (decisão do dono): sem Redis, trata o token como NÃO-revogado -> a API
// segue no ar em vez de deslogar todo mundo quando o cache cai. Trade-off aceito:
// um token revogado ainda vale até expirar sozinho (TTL curto). Blacklist é
// best-effort por design (o token já tem exp curto próprio).
export async function isTokenBlacklisted(jti: string): Promise<boolean> {
  const cached = blacklistCache.get(jti)
  if (cached && cached.exp > Date.now()) return cached.revoked
  try {
    const revoked = (await redis.exists(`blacklist:token:${jti}`)) === 1
    rememberBlacklist(jti, revoked, BLACKLIST_CACHE_MS)
    return revoked
  } catch {
    return false
  }
}
