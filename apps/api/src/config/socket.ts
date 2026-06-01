import { Server, Socket } from 'socket.io'
import { and, eq, or } from 'drizzle-orm'
import { db } from '../db'
import { users, dmConversations } from '../db/schema'
import { verifyAccessToken } from '../lib/jwt'
import { isTokenBlacklisted, setUserOnline, setUserOffline, refreshPresence } from '../lib/redis'
import { trackMessage, isUserMuted, muteUser, getMuteExpiry } from '../lib/spamDetector'
import { getBotId, askBot, handleBotCommand } from '../lib/bot'
import { socketConnections, socketEventsTotal } from '../lib/metrics'

const userSockets = new Map<string, Set<string>>()

// Reutiliza helper central (respeita canais privados por role)
import { userCanSeeChannel } from '../lib/permissions'
async function userCanAccessChannel(userId: string, channelId: string): Promise<boolean> {
  return userCanSeeChannel(userId, channelId)
}

/**
 * Confere se o user participa da DM conversation.
 */
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
    // INVISIBLE → broadcast como offline pra não revelar presença
    const broadcastStatus = chosenStatus === 'INVISIBLE' ? 'OFFLINE' : chosenStatus
    socket.broadcast.emit('presence_update', { userId, status: broadcastStatus })

    // Cliente pode mudar status em runtime
    socket.on('set_status', async (newStatus: 'ONLINE'|'IDLE'|'DND'|'INVISIBLE') => {
      if (!['ONLINE','IDLE','DND','INVISIBLE'].includes(newStatus)) return
      socket.data.status = newStatus
      await setUserOnline(userId, newStatus)
      try { await db.update(users).set({ status: newStatus }).where(eq(users.id, userId)) } catch {}
      const out = newStatus === 'INVISIBLE' ? 'OFFLINE' : newStatus
      socket.broadcast.emit('presence_update', { userId, status: out })
      socket.emit('presence_update', { userId, status: newStatus }) // self vê o real
    })

    // ── Personal room for mention notifications ───────────────
    // Every connected user joins "user:<id>" so we can target them directly
    socket.join(`user:${userId}`)

    socket.on('heartbeat', () => {
      socketEventsTotal.inc({ event: 'heartbeat', direction: 'in' })
      refreshPresence(userId)
    })

    // ── Channel rooms ─────────────────────────────────────────
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

    // ── DM rooms ──────────────────────────────────────────────
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

    // ── DM call signaling (voice/video) ───────────────────────
    // Convida outro user pra entrar numa chamada LiveKit. Server só faz
    // signaling — o handshake real (token, conexão SFU) acontece via REST + WebRTC.
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

    // ── Typing ────────────────────────────────────────────────
    // Só emite pros rooms onde o user já entrou (joinedRoom previne broadcast spoof).
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

    // ── Spam check ────────────────────────────────────────────
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
            id: `bot-mute-${Date.now()}`,
            content: `🔇 **@${socket.data.username}** foi silenciado por **5 minutos** por spam.`,
            channelId, edited: false, createdAt: new Date().toISOString(),
            authorColor: null, reactions: [], mentions: [],
            author: { id: botId, username: 'umbra_bot', displayName: 'Umbra', avatarUrl: null },
          }
          io.to(`channel:${channelId}`).emit('new_message', botMsg)
        }
        const secs = await getMuteExpiry(userId, serverId)
        socket.emit('message_blocked', { channelId, reason: 'spam', secondsLeft: secs })
        return
      }

      socket.emit('message_allowed', { channelId })
    })

    // ── Bot command ───────────────────────────────────────────
    socket.on('bot_command', async (payload: { channelId: string; serverId: string; content: string }) => {
      const { channelId, serverId, content } = payload ?? {}
      if (typeof channelId !== 'string' || typeof serverId !== 'string' || typeof content !== 'string') return
      if (!content.toLowerCase().startsWith('/umbra')) return
      // Confirma membership pra impedir injeção de mensagem do bot em canal alheio
      const canAccess = await userCanAccessChannel(userId, channelId)
      if (!canAccess) return

      const botId = await getBotId()
      if (!botId) return

      const muted           = await isUserMuted(userId, serverId)
      const muteSecondsLeft = muted ? await getMuteExpiry(userId, serverId) : 0

      const commandResponse = await handleBotCommand(content, {
        username: socket.data.username,
        isMuted:  muted,
        muteSecondsLeft,
        userId,
        channelId,
      })

      let reply: string
      if (commandResponse) {
        reply = commandResponse
      } else {
        const userMessage = content.replace(/^\/umbra\s*/i, '').trim()
        if (!userMessage) {
          reply = 'Como posso ajudar? Tente `/umbra help` pra ver comandos.'
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
        id: `bot-${Date.now()}`,
        content: reply, channelId,
        edited: false, createdAt: new Date().toISOString(),
        authorColor: null, reactions: [], mentions: [],
        author: { id: botId, username: 'umbra_bot', displayName: 'Umbra', avatarUrl: null },
      }
      io.to(`channel:${channelId}`).emit('new_message', botMsg)
    })

    // ── Disconnect ────────────────────────────────────────────
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