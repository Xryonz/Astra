import { Server, Socket } from 'socket.io'
import { randomUUID } from 'node:crypto'
import { and, eq, or } from 'drizzle-orm'
import { db } from '../db'
import { users, dmConversations, directMessages, channels, messages, serverMembers, servers } from '../db/schema'
import { notify, resumoDaMensagem } from '../lib/notifications'
import { verifyAccessToken } from '../lib/jwt'
import { isTokenBlacklisted, setUserOnline, setUserOffline, refreshPresence, setUserActivity, clearUserActivity, redis, vozKeys, VOZ_TTL_SEGUNDOS } from '../lib/redis'
import { trackMessage, isUserMuted, muteUser, getMuteExpiry } from '../lib/spamDetector'
import { getBotId, askBot, handleBotCommand, prefixoUsado, semPrefixo, sincronizaPersona, personaDoDia } from '../lib/bot'
import { responderNoSussurro } from '../lib/botSussurro'
import { socketConnections, socketEventsTotal, messagesSentTotal } from '../lib/metrics'
import { parseMentions } from '../lib/mentions'
import { xpPorMensagem } from '../lib/xp'
import { comemorarNivel } from '../lib/botAvisos'
import { eventoDeMissao } from '../lib/missoes'
import { selectAuthorById, selectMemberColor } from '../db/prepared'
import { haBloqueio } from '../lib/blocks'
import { botNaOrbita } from '../lib/botScope'
import { registrarChamadasDeSussurro } from '../lib/dmCalls'
import { entregarSussurro } from '../lib/realtime'

const userSockets = new Map<string, Set<string>>()

const STATUS_DB_TTL_MS = 5_000
const lastStatusDbWrite = new Map<string, { at: number; status: string }>()
function shouldPersistStatus(userId: string, status: string): boolean {
  const prev = lastStatusDbWrite.get(userId)
  const now  = Date.now()

  if (!prev || prev.status !== status || now - prev.at > STATUS_DB_TTL_MS) {
    lastStatusDbWrite.set(userId, { at: now, status })
    return true
  }
  return false
}

import { membrosQueVeemCanal, userCanSeeChannel } from '../lib/permissions'
async function userCanAccessChannel(userId: string, channelId: string): Promise<boolean> {
  return userCanSeeChannel(userId, channelId)
}

async function userCanAccessDM(userId: string, conversationId: string): Promise<boolean> {
  const [row] = await db.select({ id: dmConversations.id }).from(dmConversations)
    .where(and(
      eq(dmConversations.id, conversationId),
      or(eq(dmConversations.userAId, userId), eq(dmConversations.userBId, userId)),
    ))
    .limit(1)
  return !!row
}

export function setupSocket(io: Server) {
  io.use(async (socket, next) => {
    const token = socket.handshake.auth?.token
    if (!token) return next(new Error('AUTH_REQUIRED'))

    try {
      const payload = verifyAccessToken(token)
      const revoked = await isTokenBlacklisted(payload.jti)
      if (revoked) return next(new Error('TOKEN_REVOKED'))

      const [user] = await db.select({
        username:    users.username,
        displayName: users.displayName,
        status:      users.status,
      }).from(users).where(eq(users.id, payload.userId)).limit(1)

      socket.data.userId      = payload.userId
      socket.data.username    = user?.username    ?? 'usuario'
      socket.data.displayName = user?.displayName ?? 'Usuário'
      socket.data.status      = (user?.status as 'ONLINE'|'IDLE'|'DND'|'INVISIBLE') ?? 'ONLINE'
      next()
    } catch {
      next(new Error('INVALID_TOKEN'))
    }
  })

  io.on('connection', async (socket: Socket) => {
    const userId: string = socket.data.userId

    if (!userSockets.has(userId)) userSockets.set(userId, new Set())
    userSockets.get(userId)!.add(socket.id)
    socketConnections.inc()

    const chosenStatus = (socket.data.status as 'ONLINE'|'IDLE'|'DND'|'INVISIBLE') ?? 'ONLINE'
    await setUserOnline(userId, chosenStatus)

    const broadcastStatus = chosenStatus === 'INVISIBLE' ? 'OFFLINE' : chosenStatus
    socket.broadcast.emit('presence_update', { userId, status: broadcastStatus })

    socket.on('set_status', async (newStatus: 'ONLINE'|'IDLE'|'DND'|'INVISIBLE') => {
      if (!['ONLINE','IDLE','DND','INVISIBLE'].includes(newStatus)) return
      socket.data.status = newStatus
      await setUserOnline(userId, newStatus)

      if (shouldPersistStatus(userId, newStatus)) {
        try { await db.update(users).set({ status: newStatus }).where(eq(users.id, userId)) } catch {}
      }
      const out = newStatus === 'INVISIBLE' ? 'OFFLINE' : newStatus
      socket.broadcast.emit('presence_update', { userId, status: out })
      socket.emit('presence_update', { userId, status: newStatus })
    })

    socket.join(`user:${userId}`)

    try {
      const mine = await db.select({ serverId: serverMembers.serverId })
        .from(serverMembers).where(eq(serverMembers.userId, userId))
      for (const s of mine) socket.join(`server:${s.serverId}`)
    } catch {}

    socket.on('join_server', async (serverId: string) => {
      if (typeof serverId !== 'string' || !serverId) return
      const [row] = await db.select({ userId: serverMembers.userId })
        .from(serverMembers)
        .where(and(eq(serverMembers.serverId, serverId), eq(serverMembers.userId, userId)))
        .limit(1)
      if (row) socket.join(`server:${serverId}`)
    })

    socket.on('heartbeat', () => {
      socketEventsTotal.inc({ event: 'heartbeat', direction: 'in' })
      refreshPresence(userId)
    })

    socket.on('set_activity', async (texto: unknown) => {
      if (typeof texto !== 'string') return
      const limpo = texto.replace(/[\r\n\t]/g, ' ').trim().slice(0, 64)
      const salas = [...socket.rooms].filter((r) => r.startsWith('server:'))
      if (!limpo) {
        await clearUserActivity(userId)
        if (salas.length) socket.to(salas).emit('activity_update', { userId, activity: null, since: null })
        return
      }
      const viva = await setUserActivity(userId, limpo)
      if (salas.length) {
        socket.to(salas).emit('activity_update', {
          userId, activity: limpo, since: viva?.desde ?? Date.now(),
        })
      }
    })

    socket.on('join_channel', async (channelId: string) => {
      if (typeof channelId !== 'string' || !channelId) return
      const ok = await userCanAccessChannel(userId, channelId)
      if (!ok) { socket.emit('join_denied', { channelId, reason: 'not_a_member' }); return }
      socket.join(`channel:${channelId}`)
    })
    socket.on('leave_channel', (channelId: string) => {
      if (typeof channelId !== 'string' || !channelId) return
      socket.leave(`channel:${channelId}`)
      socket.to(`channel:${channelId}`).emit('user_stopped_typing', { userId, channelId })
    })

    socket.on('join_dm', async (conversationId: string) => {
      if (typeof conversationId !== 'string' || !conversationId) return
      const ok = await userCanAccessDM(userId, conversationId)
      if (!ok) { socket.emit('join_denied', { conversationId, reason: 'not_a_participant' }); return }
      socket.join(`dm:${conversationId}`)
    })
    socket.on('leave_dm', (conversationId: string) => {
      if (typeof conversationId !== 'string' || !conversationId) return
      socket.leave(`dm:${conversationId}`)
    })

    registrarChamadasDeSussurro(io, socket, userId)

    socket.on('typing_start', (channelId: string) => {
      if (typeof channelId !== 'string' || !channelId) return
      const room = `channel:${channelId}`
      if (!socket.rooms.has(room)) return
      socket.to(room).emit('user_typing', { userId, username: socket.data.username, channelId })
    })
    socket.on('typing_stop', (channelId: string) => {
      if (typeof channelId !== 'string' || !channelId) return
      const room = `channel:${channelId}`
      if (!socket.rooms.has(room)) return
      socket.to(room).emit('user_stopped_typing', { userId, channelId })
    })

    socket.on('dm_typing_start', (conversationId: string) => {
      if (typeof conversationId !== 'string' || !conversationId) return
      const room = `dm:${conversationId}`
      if (!socket.rooms.has(room)) return
      socket.to(room).emit('dm_user_typing', { userId, username: socket.data.username, conversationId })
    })
    socket.on('dm_typing_stop', (conversationId: string) => {
      if (typeof conversationId !== 'string' || !conversationId) return
      const room = `dm:${conversationId}`
      if (!socket.rooms.has(room)) return
      socket.to(room).emit('dm_user_stopped_typing', { userId, conversationId })
    })

    socket.on('check_message', async (payload: { channelId: string; serverId: string }) => {
      const { channelId, serverId } = payload
      if (!channelId || !serverId) return

      const muted = await isUserMuted(userId, serverId)
      if (muted) {
        const secs = await getMuteExpiry(userId, serverId)
        socket.emit('message_blocked', { channelId, reason: 'muted', secondsLeft: secs })
        return
      }

      const { spamDetected } = await trackMessage(userId, channelId)
      if (spamDetected) {
        const botId = await getBotId()
        if (botId) {
          await muteUser(userId, serverId, botId)
          const botMsg = {
            id: `bot-mute-${randomUUID()}`,
            content: `🔇 **@${socket.data.username}** foi silenciado por **5 minutos** por spam.`,
            channelId, edited: false, createdAt: new Date().toISOString(),

            authorId: botId,

            authorColor: null, reactions: [], mentions: [],
            author: { id: botId, username: 'astra_bot', displayName: personaDoDia().nome, avatarUrl: personaDoDia().avatar },
          }
          io.to(`channel:${channelId}`).emit('new_message', botMsg)
        }
        const secs = await getMuteExpiry(userId, serverId)
        socket.emit('message_blocked', { channelId, reason: 'spam', secondsLeft: secs })
        return
      }

      socket.emit('message_allowed', { channelId })
    })

    socket.on('fast_send_text', async (
      payload: { channelId: string; content: string; clientNonce?: string },
      ack?: (r: { ok: boolean; error?: string; code?: string; msg?: unknown }) => void,
    ) => {
      const safeAck = typeof ack === 'function' ? ack : () => {}
      try {
        const { channelId, content, clientNonce } = payload ?? {}
        if (typeof channelId !== 'string' || typeof content !== 'string') {
          return safeAck({ ok: false, error: 'Payload inválido' })
        }
        const trimmed = content.trim()
        if (trimmed.length === 0 || trimmed.length > 4000) {
          return safeAck({ ok: false, error: 'Conteúdo inválido' })
        }

        const [ch] = await db.select({
          id:         channels.id,
          name:       channels.name,
          serverId:   channels.serverId,
          serverName: servers.name,
          isPrivate:  channels.isPrivate,
          ownerId:    servers.ownerId,
        })
          .from(channels)
          .innerJoin(servers, eq(servers.id, channels.serverId))
          .where(eq(channels.id, channelId)).limit(1)
        if (!ch) return safeAck({ ok: false, error: 'Canal não encontrado' })
        const canAccess = await userCanAccessChannel(userId, channelId)
        if (!canAccess) return safeAck({ ok: false, error: 'Acesso negado' })

        const muted = await isUserMuted(userId, ch.serverId)
        if (muted) {
          const secondsLeft = await getMuteExpiry(userId, ch.serverId)
          return safeAck({ ok: false, error: 'Silenciado', code: 'MUTED', ...{ secondsLeft } } as any)
        }

        const { spamDetected } = await trackMessage(userId, channelId)
        if (spamDetected) {
          const botId = await getBotId()
          if (botId) await muteUser(userId, ch.serverId, botId)
          return safeAck({ ok: false, error: 'Spam detectado', code: 'SPAM_MUTED' })
        }

        const [membership, mentionedIds, author] = await Promise.all([
          selectMemberColor.execute({ userId, serverId: ch.serverId }).then((rows) => rows[0]),
          parseMentions(trimmed, ch.serverId),
          selectAuthorById.execute({ userId }).then((rows) => rows[0]),
        ])

        const [inserted] = await db.insert(messages).values({
          content:     trimmed,
          channelId,
          authorId:    userId,
          authorColor: membership?.nameColor ?? null,
          mentions:    mentionedIds.join(','),
          attachments: '[]',
        }).returning()

        const payload2 = {
          ...inserted,
          author,
          reactions:   [],
          mentions:    mentionedIds,
          attachments: [],
          replyTo:     null,
          clientNonce: clientNonce ?? null,
        }

        io.to(`channel:${channelId}`).emit('new_message', payload2)
        messagesSentTotal.inc({ kind: 'channel' })
        safeAck({ ok: true, msg: payload2 })

        void xpPorMensagem(userId).then((g) => {
          if (g?.subiuDeNivel) void comemorarNivel(userId, channelId, g.progresso.nivel)
        })
        void eventoDeMissao(userId, 'mensagem', { channelId })

        setImmediate(() => {
          void (async () => {
            try {
              const allMembers = await db.select({ userId: serverMembers.userId })
                .from(serverMembers).where(eq(serverMembers.serverId, ch.serverId))
              const now = (inserted.createdAt instanceof Date ? inserted.createdAt : new Date()).toISOString()
              const outros = allMembers.map((m) => m.userId).filter((id) => id !== userId)
              const veem = await membrosQueVeemCanal(channelId, ch.isPrivate, ch.ownerId, outros)
              const aviso = {
                channelId,
                lastMessageAt: now,
                channelName:  ch.name,
                serverName:   ch.serverName,
                authorName:   author?.displayName || author?.username,
                authorAvatar: author?.avatarUrl ?? null,
                preview:      resumoDaMensagem(trimmed, 0),
              }
              for (const id of outros) {
                if (!veem.has(id)) continue
                io.to(`user:${id}`).emit('channel_activity', aviso)
              }
              for (const targetId of mentionedIds) {
                if (targetId === userId) continue
                io.to(`user:${targetId}`).emit('mention', {
                  channelId, messageId: inserted.id, from: socket.data.username,
                })
              }
            } catch (e) {
              console.error('[fast_send_text/background]', e)
            }
          })()
        })
      } catch (e) {
        console.error('[fast_send_text]', e)
        safeAck({ ok: false, error: 'Erro interno' })
      }
    })

    const emitVoicePresence = async (channelId: unknown, joined: boolean) => {
      if (typeof channelId !== 'string' || !channelId) return
      if (!(await userCanAccessChannel(userId, channelId))) return
      const [ch] = await db.select({ serverId: channels.serverId })
        .from(channels).where(eq(channels.id, channelId)).limit(1)
      if (!ch) return
      const members = await db.select({ userId: serverMembers.userId })
        .from(serverMembers).where(eq(serverMembers.serverId, ch.serverId))
      for (const m of members) {
        io.to(`user:${m.userId}`).emit('voice_presence', { channelId, userId, joined })
      }
    }
    const salasDeVoz = new Set<string>()

    const marcarNaVoz = async (channelId: string) => {
      await redis.set(vozKeys.membro(channelId, userId), '1', 'EX', VOZ_TTL_SEGUNDOS)
    }
    const tirarDaVoz = async (channelId: string) => {
      await redis.del(vozKeys.membro(channelId, userId))
    }

    socket.on('voice_join', (channelId: string) => {
      if (typeof channelId !== 'string' || !channelId) return
      salasDeVoz.add(channelId)
      void marcarNaVoz(channelId)
      void emitVoicePresence(channelId, true)
    })
    socket.on('voice_leave', (channelId: string) => {
      if (typeof channelId !== 'string' || !channelId) return
      salasDeVoz.delete(channelId)
      void tirarDaVoz(channelId)
      void emitVoicePresence(channelId, false)
    })

    socket.on('voice_keepalive', (channelId: string) => {
      if (typeof channelId !== 'string' || !channelId) return
      if (!salasDeVoz.has(channelId)) return
      void marcarNaVoz(channelId)
    })

    const TETO_SINAL = 64 * 1024

    socket.on('rtc_signal', (payload: unknown) => {
      if (typeof payload !== 'object' || payload === null) return
      const { para, tipo, dados } = payload as Record<string, unknown>
      if (typeof para !== 'string' || !para) return
      if (tipo !== 'oferta' && tipo !== 'resposta' && tipo !== 'candidato' && tipo !== 'tchau') return
      if (typeof dados !== 'string' || dados.length > TETO_SINAL) return
      if (para === userId) return
      io.to(`user:${para}`).emit('rtc_signal', { de: userId, tipo, dados })
    })

    socket.on('fast_send_dm', async (
      payload: { conversationId: string; content: string; clientNonce?: string },
      ack?: (r: { ok: boolean; error?: string; msg?: unknown }) => void,
    ) => {
      const safeAck = typeof ack === 'function' ? ack : () => {}
      try {
        const { conversationId, content, clientNonce } = payload ?? {}
        if (typeof conversationId !== 'string' || typeof content !== 'string') {
          return safeAck({ ok: false, error: 'Payload inválido' })
        }
        const trimmed = content.trim()
        if (trimmed.length === 0 || trimmed.length > 4000) {
          return safeAck({ ok: false, error: 'Conteúdo inválido' })
        }

        const [conv] = await db.select().from(dmConversations)
          .where(and(
            eq(dmConversations.id, conversationId),
            or(eq(dmConversations.userAId, userId), eq(dmConversations.userBId, userId)),
          ))
          .limit(1)
        if (!conv) return safeAck({ ok: false, error: 'Acesso negado' })

        const receiverId = conv.userAId === userId ? conv.userBId : conv.userAId
        if (await haBloqueio(userId, receiverId)) {
          return safeAck({ ok: false, error: 'Não é possível conversar com essa pessoa' })
        }

        const [insertedRows, authorRows] = await Promise.all([
          db.insert(directMessages).values({
            content: trimmed, senderId: userId, receiverId, conversationId,
            attachments: '[]', replyToId: null,
          }).returning(),
          selectAuthorById.execute({ userId }),
        ])
        const inserted = insertedRows[0]
        const author   = authorRows[0]

        const message = {
          ...inserted,
          attachments: [],
          replyTo:     null,
          author,
          clientNonce: clientNonce ?? null,
        }

        entregarSussurro(io, conversationId, [userId, receiverId], 'new_dm', message)
        messagesSentTotal.inc({ kind: 'dm' })
        safeAck({ ok: true, msg: message })

        setImmediate(() => {
          void (async () => {
            try {
              await db.update(dmConversations).set({ updatedAt: new Date() })
                .where(eq(dmConversations.id, conversationId))
              const receiverMuted =
                (conv.userAId === receiverId ? conv.mutedByA : conv.mutedByB) != null
              if (receiverMuted) return
              await notify({
                io, userId: receiverId, actorId: userId, type: 'dm',
                payload: {
                  messageId:    inserted.id,
                  conversationId,
                  authorId:     author?.id,
                  authorName:   author?.displayName || author?.username,
                  authorAvatar: author?.avatarUrl ?? null,
                  preview:      trimmed.slice(0, 140),
                },
                push: {
                  title: `Nova DM de ${author?.displayName ?? 'Alguém'}`,
                  body:  trimmed.slice(0, 140),
                  url:   `/app/dm/${conversationId}`,
                  tag:   `dm-${conversationId}`,
                },
              })
            } catch (e) {
              console.error('[fast_send_dm/background]', e)
            }
          })()
        })

        if (receiverId === (await getBotId())) {
          setImmediate(() => {
            void responderNoSussurro({
              io, conversationId, userId, receiverId,
              content: trimmed, username: socket.data.username,
            })
          })
        }
      } catch (e) {
        console.error('[fast_send_dm]', e)
        safeAck({ ok: false, error: 'Erro interno' })
      }
    })

    socket.on('bot_command', async (payload: { channelId: string; serverId: string; content: string }) => {
      const { channelId, serverId, content } = payload ?? {}
      if (typeof channelId !== 'string' || typeof serverId !== 'string' || typeof content !== 'string') return
      if (!prefixoUsado(content)) return

      const canAccess = await userCanAccessChannel(userId, channelId)
      if (!canAccess) return
      const regra = await botNaOrbita(channelId)
      if (!regra.fala) return

      const botId = await getBotId()
      if (!botId) return
      const persona = await sincronizaPersona(botId)

      const muted           = await isUserMuted(userId, serverId)
      const muteSecondsLeft = muted ? await getMuteExpiry(userId, serverId) : 0

      const commandResponse = await handleBotCommand(content, {
        username: socket.data.username,
        isMuted:  muted,
        muteSecondsLeft,
        userId,
        channelId,
        serverId,
      })

      let reply: string
      if (commandResponse) {
        reply = commandResponse
      } else {
        const userMessage = semPrefixo(content)
        if (!userMessage) {
          reply = `Como posso ajudar? Tente \`${persona.prefixo} ajuda\` pra ver os comandos de hoje.`
        } else {
          const result = await askBot({
            userMessage,
            ctx: { userId, channelId, serverId, username: socket.data.username },
          })
          reply = result.text
          if (result.truncated === 'tokens') reply += '\n\n_(seu limite diário foi atingido)_'
          if (result.truncated === 'tools')  reply += '\n\n_(limite diário de ferramentas atingido)_'
        }
      }

      const autorBot = {
        id: botId, username: 'astra_bot',
        displayName: persona.nome, avatarUrl: persona.avatar,
      }

      if (regra.guarda) {
        const [autor, membership] = await Promise.all([
          selectAuthorById.execute({ userId }).then((r) => r[0]),
          selectMemberColor.execute({ userId, serverId }).then((r) => r[0]),
        ])

        const [msgComando] = await db.insert(messages).values({
          content, channelId, authorId: userId,
          authorColor: membership?.nameColor ?? null,
        }).returning()

        const depois = new Date(
          (msgComando.createdAt instanceof Date ? msgComando.createdAt : new Date()).getTime() + 1,
        )
        const [msgResposta] = await db.insert(messages).values({
          content: reply, channelId, authorId: botId, createdAt: depois,
        }).returning()

        const enfeita = (m: typeof msgComando, author: unknown) => ({
          ...m, author, reactions: [], mentions: [], attachments: [], replyTo: null,
        })
        io.to(`channel:${channelId}`).emit('new_message', enfeita(msgComando, autor))
        io.to(`channel:${channelId}`).emit('new_message', enfeita(msgResposta, autorBot))
        return
      }

      const botMsg = {
        id: `bot-${randomUUID()}`,
        content: reply, channelId,
        edited: false, createdAt: new Date().toISOString(),

        authorId: botId,

        authorColor: null, reactions: [], mentions: [],
        author: autorBot,
      }
      io.to(`channel:${channelId}`).emit('new_message', botMsg)
    })

    socket.on('disconnect', async () => {
      for (const channelId of salasDeVoz) {
        await tirarDaVoz(channelId)
        void emitVoicePresence(channelId, false)
      }
      salasDeVoz.clear()

      const sockets = userSockets.get(userId)
      sockets?.delete(socket.id)
      if (!sockets?.size) {
        userSockets.delete(userId)
        await setUserOffline(userId)
        socket.broadcast.emit('presence_update', { userId, status: 'OFFLINE' })
        await clearUserActivity(userId)
        socket.broadcast.emit('activity_update', { userId, activity: null, since: null })
      }
      socketConnections.dec()
    })
  })
}

export { userSockets }
