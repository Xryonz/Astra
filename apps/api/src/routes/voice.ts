
import { Router, Request, Response } from 'express'
import { z } from 'zod'
import { and, eq, or } from 'drizzle-orm'
import { AccessToken } from 'livekit-server-sdk'
import { db } from '../db'
import { dmConversations, channels, users } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { env } from '../lib/env'
import { userCanSeeChannel } from '../lib/permissions'
import { redis } from '../lib/redis'
import { forbidden, badRequest } from '../lib/errors'

const router = Router()

const PRESENCE_TTL_MS = 5_000
const presenceCache = new Map<string, { at: number; ids: string[] }>()

const TokenSchema = z.object({
  roomKind: z.enum(['channel', 'dm']),
  roomId:   z.string().min(1).max(64),
})

router.get('/config', requireAuth, asyncHandler(async (_req: Request, res: Response) => {

  res.json({
    data: {
      enabled: !!(env.LIVEKIT_URL && env.LIVEKIT_API_KEY && env.LIVEKIT_API_SECRET),
      url:     env.LIVEKIT_URL ?? null,
    },
  })
}))

router.post('/token', requireAuth, validate(TokenSchema), asyncHandler(async (req: Request, res: Response) => {
  if (!env.LIVEKIT_URL || !env.LIVEKIT_API_KEY || !env.LIVEKIT_API_SECRET) {
    return res.status(503).json({ error: 'Chamadas não configuradas no servidor' })
  }

  const { roomKind, roomId } = req.body as z.infer<typeof TokenSchema>

  if (roomKind === 'channel') {

    const [ch] = await db.select({ type: channels.type }).from(channels)
      .where(eq(channels.id, roomId)).limit(1)
    if (!ch) throw badRequest('Canal não encontrado')
    if (ch.type !== 'VOICE') throw badRequest('Canal não é de voz')
    const ok = await userCanSeeChannel(req.userId!, roomId)
    if (!ok) throw forbidden('Sem acesso a esse canal')
  } else {

    const [conv] = await db.select({ id: dmConversations.id }).from(dmConversations)
      .where(and(
        eq(dmConversations.id, roomId),
        or(eq(dmConversations.userAId, req.userId!), eq(dmConversations.userBId, req.userId!)),
      ))
      .limit(1)
    if (!conv) throw forbidden('Sem acesso à DM')
  }

  const roomName = `${roomKind}:${roomId}`
  const identity = req.userId!

  // Nome + avatar viajam no token pro LiveKit: assim os participantes carregam
  // identidade humana (nome real + foto) em vez do id cru — funciona em call de
  // servidor E de DM, no web e no nativo (que le participant.name/metadata).
  const [u] = await db.select({
    username:    users.username,
    displayName: users.displayName,
    avatarUrl:   users.avatarUrl,
  }).from(users).where(eq(users.id, identity)).limit(1)

  const at = new AccessToken(env.LIVEKIT_API_KEY, env.LIVEKIT_API_SECRET, {
    identity,
    name: u?.displayName || u?.username || identity,
    metadata: JSON.stringify({ avatarUrl: u?.avatarUrl ?? null, username: u?.username ?? null }),
    ttl: '6h',
  })
  at.addGrant({
    room:        roomName,
    roomJoin:    true,
    canPublish:  true,
    canSubscribe: true,
    canPublishData: true,
  })

  const token = await at.toJwt()
  res.json({
    data: {
      token,
      url:      env.LIVEKIT_URL,
      roomName,
      identity,
    },
  })
}))

const PresenceSchema = z.object({
  channelIds: z.string().min(1).max(2000),
})
router.get('/presence', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const parsed = PresenceSchema.safeParse(req.query)
  if (!parsed.success) {
    return res.json({ data: {} })
  }
  const ids = parsed.data.channelIds.split(',').map((s) => s.trim()).filter(Boolean).slice(0, 64)
  if (ids.length === 0) return res.json({ data: {} })

  const out: Record<string, string[]> = {}
  const now = Date.now()

  await Promise.all(ids.map(async (channelId) => {
    const cached = presenceCache.get(channelId)
    if (cached && now - cached.at < PRESENCE_TTL_MS) {
      if (await userCanSeeChannel(req.userId!, channelId)) out[channelId] = cached.ids
      return
    }
    if (!(await userCanSeeChannel(req.userId!, channelId))) return

    // A LISTA SAI DO REDIS, não mais do LiveKit.
    //
    // Com a call em ponto a ponto não há servidor de mídia por onde perguntar:
    // o áudio vai direto de uma pessoa para outra. Cada cliente marca presença
    // enquanto está na sala, e a marca expira sozinha (ver vozKeys) — o que
    // resolve o fantasma melhor do que o LiveKit resolvia, porque não depende de
    // uma chamada de rede a um terceiro que pode estar fora do ar.
    //
    // `scanStream` e não `keys`: `keys` varre o banco inteiro e trava o Redis
    // enquanto varre. Num banco compartilhado como o nosso isso é problema de
    // todo mundo, não só nosso.
    try {
      const prefixo = `voz:${channelId}:`
      const encontrados: string[] = []
      const fluxo = redis.scanStream({ match: `${prefixo}*`, count: 200 })
      for await (const lote of fluxo as AsyncIterable<string[]>) {
        for (const chave of lote) encontrados.push(chave.slice(prefixo.length))
      }
      presenceCache.set(channelId, { at: now, ids: encontrados })
      out[channelId] = encontrados
    } catch {
      // Redis fora do ar MANTÉM o que se sabia, em vez de esvaziar a sala. Uma
      // falha de rede não pode fazer todo mundo sumir da call na barra lateral.
      out[channelId] = cached?.ids ?? []
    }
  }))

  res.json({ data: out })
}))

export default router
