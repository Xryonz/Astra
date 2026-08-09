import type { Server as SocketServer } from 'socket.io'
import { eq } from 'drizzle-orm'
import { db } from '../db'
import { dmConversations, directMessages } from '../db/schema'
import { askBot, getBotId, handleBotCommand, sincronizaPersona } from './bot'
import { messagesSentTotal } from './metrics'

interface Pedido {
  io:             SocketServer
  conversationId: string
  userId:         string
  receiverId:     string
  content:        string
  username:       string
}

// RESPOSTA DA BOT DENTRO DO SUSSURRO.
//
// Irmã do `bot_command` do socket, com duas diferenças que vêm do lugar:
//
// 1. Sem prefixo. No canal ele separa "falo com a bot" de "falo com a sala"; numa
//    conversa de duas pessoas onde a outra é ela, não há o que separar.
// 2. Sem constelação. O `serverId` vai nulo, e as ferramentas já sabiam lidar com
//    isso desde sempre ("Você está em uma DM, não em servidor") — era só o caminho
//    até elas que não existia.
//
// A memória usa o id da CONVERSA no lugar do canal: o histórico do papo com ela
// fica por conversa, que é exatamente o recorte certo aqui.
export async function responderNoSussurro({
  io, conversationId, userId, receiverId, content, username,
}: Pedido): Promise<void> {
  try {
    const botId = await getBotId()
    if (!botId || botId !== receiverId) return

    const persona = await sincronizaPersona(botId)

    // "Digitando…" enquanto pensa. A IA leva alguns segundos, e sem sinal nenhum
    // a conversa fica parada de um jeito que se le como "ela nao recebeu". O
    // evento e o MESMO que uma pessoa dispara — o cliente ja sabe desenhar.
    const sala = `dm:${conversationId}`
    io.to(sala).emit('dm_user_typing', {
      userId: botId, username: 'astra_bot', conversationId,
    })
    // O finally e obrigatorio: se a IA cair no meio, o "digitando…" ficaria aceso
    // pra sempre e a bot pareceria travada escrevendo uma resposta que nunca vem.
    let texto: string
    try {
      // Comandos continuam valendo (`/sparkle ajuda`, dado, moeda…) — quem já sabe
      // usar não precisa desaprender só porque mudou de lugar. Os que dependem de
      // constelação (ranking, sorteio) se viram sozinhos sem o serverId.
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

    io.to(`dm:${conversationId}`).emit('new_dm', {
      ...linha, attachments: [], replyTo: null, author: autor,
    })
    messagesSentTotal.inc({ kind: 'dm' })

    // Bumpar a conversa: sem isto a resposta existe mas a conversa não sobe na
    // lista, e quem estiver noutra tela não vê que ela respondeu.
    await db.update(dmConversations).set({ updatedAt: new Date() })
      .where(eq(dmConversations.id, conversationId))
  } catch (err) {
    // Falar é melhor-esforço: se a IA cair, a mensagem da pessoa continua enviada.
    // eslint-disable-next-line no-console
    console.error('[bot/sussurro]', err)
  }
}
