import { Router, Request, Response } from 'express'
import { z } from 'zod'
import { Server as SocketServer } from 'socket.io'
import { and, asc, eq, isNull } from 'drizzle-orm'
import { db } from '../db'
import { channels, serverMembers, messages, messageReactions, users } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { notify } from '../lib/notifications'

const EmojiSchema = z.object({
  emoji: z.string().min(1).max(8),
})

export function createReactionsRouter(io: SocketServer) {
  const router = Router({ mergeParams: true })

  router.post(
    '/',
    requireAuth,
    validate(EmojiSchema),
    asyncHandler(async (req: Request, res: Response) => {
      const { channelId, messageId } = req.params
      const { emoji } = req.body

      const [channel] = await db.select({ serverId: channels.serverId }).from(channels)
        .where(eq(channels.id, channelId)).limit(1)
      if (!channel) return res.status(403).json({ error: 'Acesso negado' })

      const [member] = await db.select({ id: serverMembers.id }).from(serverMembers)
        .where(and(eq(serverMembers.userId, req.userId!), eq(serverMembers.serverId, channel.serverId)))
        .limit(1)
      if (!member) return res.status(403).json({ error: 'Acesso negado' })

      const [message] = await db.select({
        id: messages.id, authorId: messages.authorId, content: messages.content,
      }).from(messages)
        .where(and(
          eq(messages.id, messageId),
          eq(messages.channelId, channelId),
          isNull(messages.deletedAt),
        ))
        .limit(1)
      if (!message) return res.status(404).json({ error: 'Mensagem não encontrada' })

      const [existing] = await db.select({ id: messageReactions.id }).from(messageReactions)
        .where(and(
          eq(messageReactions.messageId, messageId),
          eq(messageReactions.userId,    req.userId!),
          eq(messageReactions.emoji,     emoji),
        ))
        .limit(1)

      let action: 'added' | 'removed'
      if (existing) {
        await db.delete(messageReactions).where(eq(messageReactions.id, existing.id))
        action = 'removed'
      } else {
        await db.insert(messageReactions).values({ messageId, userId: req.userId!, emoji })
        action = 'added'
      }

      const reactions = await getReactionSummary(messageId)

      io.to(`channel:${channelId}`).emit('reaction_update', { messageId, channelId, reactions })

      if (action === 'added' && message.authorId !== req.userId) {
        const [actor] = await db.select({
          displayName: users.displayName, avatarUrl: users.avatarUrl,
        }).from(users).where(eq(users.id, req.userId!)).limit(1)
        notify({
          io, userId: message.authorId, actorId: req.userId!, type: 'reaction',
          payload: {
            messageId, channelId, serverId: channel.serverId,
            emoji,
            authorId:    req.userId!,
            authorName:  actor?.displayName ?? 'Alguém',
            authorAvatar: actor?.avatarUrl ?? null,
            preview:     (message.content ?? '').slice(0, 80),
          },

        }).catch(() => {})
      }

      res.json({ data: { action, reactions } })
    })
  )

  router.get(
    '/',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const { messageId } = req.params
      const reactions = await getReactionSummary(messageId)
      res.json({ data: reactions })
    })
  )

  return router
}

export async function getReactionSummary(messageId: string) {
  const raw = await db.select({
    emoji:  messageReactions.emoji,
    userId: messageReactions.userId,
  })
    .from(messageReactions)
    .where(eq(messageReactions.messageId, messageId))
    .orderBy(asc(messageReactions.createdAt))

  const map = new Map<string, string[]>()
  for (const r of raw) {
    if (!map.has(r.emoji)) map.set(r.emoji, [])
    map.get(r.emoji)!.push(r.userId)
  }

  return Array.from(map.entries()).map(([emoji, users]) => ({
    emoji,
    count: users.length,
    users,
  }))
}
