import type { Server as SocketServer } from 'socket.io'
import { eq } from 'drizzle-orm'
import { db } from '../db'
import { dmConversations, directMessages } from '../db/schema'
import { askBot, getBotId, handleBotCommand, sincronizaPersona } from './bot'
import { messagesSentTotal } from './metrics'
import { entregarSussurro } from './realtime'

interface Pedido {
  io:             SocketServer
  conversationId: string
  userId:         string
  receiverId:     string
  content:        string
  username:       string
}

export async function responderNoSussurro({
  io, conversationId, userId, receiverId, content, username,
}: Pedido): Promise<void> {
  try {
    const botId = await getBotId()
    if (!botId || botId !== receiverId) return

    const persona = await sincronizaPersona(botId)

    const sala = `dm:${conversationId}`
    io.to(sala).emit('dm_user_typing', {
      userId: botId, username: 'astra_bot', conversationId,
    })
    let texto: string
    try {
      const comando = await handleBotCommand(content, {
        username, isMuted: false, muteSecondsLeft: 0, userId, channelId: conversationId,
      })

      if (comando) {
        texto = comando
      } else {
        const pergunta = content.trim()
        if (!pergunta) return
        const r = await askBot({
          userMessage: pergunta,
          ctx: { userId, channelId: conversationId, serverId: null, username },
        })
        texto = r.text
        if (r.truncated === 'tokens') texto += '\n\n_(seu limite diário foi atingido)_'
        if (r.truncated === 'tools')  texto += '\n\n_(limite diário de ferramentas atingido)_'
      }
    } finally {
      io.to(sala).emit('dm_user_stopped_typing', { userId: botId, conversationId })
    }
    if (!texto.trim()) return

    const [linha] = await db.insert(directMessages).values({
      content: texto, senderId: botId, receiverId: userId, conversationId,
      attachments: '[]',
    }).returning()

    const autor = {
      id: botId, username: 'astra_bot',
      displayName: persona.nome, avatarUrl: persona.avatar, displayFont: null,
    }

    entregarSussurro(io, conversationId, [botId, userId], 'new_dm', {
      ...linha, attachments: [], replyTo: null, author: autor,
    })
    messagesSentTotal.inc({ kind: 'dm' })

    await db.update(dmConversations).set({ updatedAt: new Date() })
      .where(eq(dmConversations.id, conversationId))
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error('[bot/sussurro]', err)
  }
}
