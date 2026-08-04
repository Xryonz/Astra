import { Server, Socket } from 'socket.io'
import { randomUUID } from 'node:crypto'
import { and, eq, or } from 'drizzle-orm'
import { db } from '../db'
import { users, dmConversations, directMessages, channels, messages, serverMembers } from '../db/schema'
import { notify } from '../lib/notifications'
import { verifyAccessToken } from '../lib/jwt'
import { isTokenBlacklisted, setUserOnline, setUserOffline, refreshPresence } from '../lib/redis'
import { trackMessage, isUserMuted, muteUser, getMuteExpiry } from '../lib/spamDetector'
import { getBotId, askBot, handleBotCommand, prefixoUsado, semPrefixo, sincronizaPersona, personaDoDia } from '../lib/bot'
import { socketConnections, socketEventsTotal, messagesSentTotal } from '../lib/metrics'
import { parseMentions } from '../lib/mentions'
import { xpPorMensagem } from '../lib/xp'
import { eventoDeMissao } from '../lib/missoes'
import { selectAuthorById, selectMemberColor } from '../db/prepared'
import { haBloqueio } from '../lib/blocks'
import { botPodeFalar } from '../lib/botScope'

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

import { userCanSeeChannel } from '../lib/permissions'
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

    // Salas de CONSTELACAO. Ate agora so existiam salas de canal e de DM, ou seja,
    // nada que valha pra constelacao inteira tinha por onde ser transmitido — e por
    // isso "canal novo" so aparecia pros outros no proximo boot do app. Uma consulta
    // indexada no connect resolve pra sessao toda.
    try {
      const mine = await db.select({ serverId: serverMembers.serverId })
        .from(serverMembers).where(eq(serverMembers.userId, userId))
      for (const s of mine) socket.join(`server:${s.serverId}`)
    } catch {}

    // Entrou numa constelacao AGORA (convite/descoberta): a sala do connect nao a
    // inclui, entao o cliente pede a entrada em vez de reconectar o socket inteiro.
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

    socket.on('dm_call_invite', async (p: { conversationId: string; toUserId: string }) => {
      if (!p || typeof p.conversationId !== 'string' || typeof p.toUserId !== 'string') return
      const ok = await userCanAccessDM(userId, p.conversationId)
      if (!ok) return
      io.to(`user:${p.toUserId}`).emit('dm_call_invite', {
        conversationId: p.conversationId,
        fromUserId:     userId,
        fromUsername:   socket.data.username,
        fromDisplayName: socket.data.displayName,
      })
    })

    socket.on('dm_call_accept', async (p: { conversationId: string; toUserId: string }) => {
      if (!p || typeof p.conversationId !== 'string' || typeof p.toUserId !== 'string') return
      const ok = await userCanAccessDM(userId, p.conversationId)
      if (!ok) return
      io.to(`user:${p.toUserId}`).emit('dm_call_accept', {
        conversationId: p.conversationId,
        byUserId:       userId,
      })
    })

    socket.on('dm_call_reject', async (p: { conversationId: string; toUserId: string }) => {
      if (!p || typeof p.conversationId !== 'string' || typeof p.toUserId !== 'string') return
      const ok = await userCanAccessDM(userId, p.conversationId)
      if (!ok) return
      io.to(`user:${p.toUserId}`).emit('dm_call_reject', {
        conversationId: p.conversationId,
        byUserId:       userId,
      })
    })

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
            // authorId ALEM do author: o cliente Kotlin exige o campo plano (todas as

            // outras mensagens vem do banco, que tem authorId). Sem ele a desserializacao

            // falha e o desktop DESCARTA a mensagem da bot em silencio.

            authorId: botId,

            authorColor: null, reactions: [], mentions: [],
            // Aviso de moderacao sai com o nome de quem esta de plantao hoje.
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

        const [ch] = await db.select({ id: channels.id, serverId: channels.serverId })
          .from(channels).where(eq(channels.id, channelId)).limit(1)
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

        // XP DEPOIS do ack, sem await: a bolha da mensagem nao espera progressao.
        // A propria funcao decide se conta (trava de 1 min, teto do dia) e ela
        // engole os proprios erros — nao ha caso em que XP derrube uma mensagem.
        void xpPorMensagem(userId)
        void eventoDeMissao(userId, 'mensagem', { channelId })

        setImmediate(() => {
          void (async () => {
            try {
              const allMembers = await db.select({ userId: serverMembers.userId })
                .from(serverMembers).where(eq(serverMembers.serverId, ch.serverId))
              const now = (inserted.createdAt instanceof Date ? inserted.createdAt : new Date()).toISOString()
              for (const m of allMembers) {
                if (m.userId === userId) continue
                io.to(`user:${m.userId}`).emit('channel_activity', { channelId, lastMessageAt: now })
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

    // Presenca de VOZ ao vivo. A fonte da verdade continua sendo o LiveKit (rota
    // /voice/presence, que o cliente ainda consulta de tempos em tempos), mas quem
    // entra/sai AVISA na hora — senao "fulano entrou na call" so aparecia no proximo
    // poll (ate ~10s de atraso, contando o cache do servidor).
    // Por que confiar no cliente aqui: o dado e cosmetico (bolinha na barra lateral),
    // o acesso ao canal e VALIDADO abaixo, e o poll corrige qualquer mentira ou
    // fantasma (queda de rede/crash, que nao emitem 'leave') em segundos.
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
    socket.on('voice_join', (channelId: string) => { void emitVoicePresence(channelId, true) })
    socket.on('voice_leave', (channelId: string) => { void emitVoicePresence(channelId, false) })

    // Irmao do fast_send_text pro SUSSURRO: mesma ideia (texto puro, sem anexo nem
    // resposta) pra a bolha aparecer na hora em vez de esperar o POST. Espelha a rota
    // HTTP de dm.ts — inclusive o notify em background, senao DM por este caminho
    // deixaria de gerar feed/push/badge (regressao silenciosa).
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
        // O caminho rapido precisa da MESMA regra da rota HTTP. Sem isto, bloquear
        // alguem funcionaria so quando a mensagem tivesse anexo ou resposta — que e
        // o tipo de meia-regra pior que regra nenhuma.
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

        io.to(`dm:${conversationId}`).emit('new_dm', message)
        messagesSentTotal.inc({ kind: 'dm' })
        safeAck({ ok: true, msg: message })

        setImmediate(() => {
          void (async () => {
            try {
              await db.update(dmConversations).set({ updatedAt: new Date() })
                .where(eq(dmConversations.id, conversationId))
              // Silenciada pelo receptor: a mensagem entra (o socket ja emitiu),
              // mas sem feed/push/badge — mesma regra da rota HTTP.
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
      } catch (e) {
        console.error('[fast_send_dm]', e)
        safeAck({ ok: false, error: 'Erro interno' })
      }
    })

    socket.on('bot_command', async (payload: { channelId: string; serverId: string; content: string }) => {
      const { channelId, serverId, content } = payload ?? {}
      if (typeof channelId !== 'string' || typeof serverId !== 'string' || typeof content !== 'string') return
      // /sparkle (dias uteis), /sparxie (fim de semana) e /astra (o que o app
      // mobile ja manda). Todos entram; quem responde e quem esta de plantao.
      if (!prefixoUsado(content)) return

      const canAccess = await userCanAccessChannel(userId, channelId)
      if (!canAccess) return
      // Bot desligada aqui: sai calada. Responder "estou desligada" seria falar
      // justamente onde pediram silencio — e o cliente ja nem mostra a caixinha
      // de comandos nestas orbitas, entao ninguem chega aqui por engano.
      if (!(await botPodeFalar(channelId))) return

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

      const botMsg = {
        id: `bot-${randomUUID()}`,
        content: reply, channelId,
        edited: false, createdAt: new Date().toISOString(),
        // authorId ALEM do author: o cliente Kotlin exige o campo plano (todas as

        // outras mensagens vem do banco, que tem authorId). Sem ele a desserializacao

        // falha e o desktop DESCARTA a mensagem da bot em silencio.

        authorId: botId,

        authorColor: null, reactions: [], mentions: [],
        // A foto TEM que vir da persona aqui. Esta mensagem nao passa pelo banco (e
        // sintetica, id `bot-...`), entao ela nao herda nada do User como as outras —
        // com null cravado, a bot aparecia com foto no cartao de perfil e sem foto na
        // propria mensagem que acabou de mandar.
        author: { id: botId, username: 'astra_bot', displayName: persona.nome, avatarUrl: persona.avatar },
      }
      io.to(`channel:${channelId}`).emit('new_message', botMsg)
    })

    socket.on('disconnect', async () => {
      const sockets = userSockets.get(userId)
      sockets?.delete(socket.id)
      if (!sockets?.size) {
        userSockets.delete(userId)
        await setUserOffline(userId)
        socket.broadcast.emit('presence_update', { userId, status: 'OFFLINE' })
      }
      socketConnections.dec()
    })
  })
}

export { userSockets }