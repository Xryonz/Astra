
import { Router, Request, Response } from 'express'
import { Server as SocketServer } from 'socket.io'
import { and, eq, isNull, or, sql } from 'drizzle-orm'
import { db } from '../db'
import { channels, serverMembers, channelReads, dmConversations, notifications } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'

export function createReadsRouter(io: SocketServer) {
  const router = Router()

  router.post(
    '/channels/:channelId/read',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const { channelId } = req.params

      const [row] = await db.select({ membershipId: serverMembers.id })
        .from(channels)
        .innerJoin(serverMembers, and(
          eq(serverMembers.serverId, channels.serverId),
          eq(serverMembers.userId,   req.userId!),
        ))
        .where(eq(channels.id, channelId))
        .limit(1)
      if (!row) return res.status(403).json({ error: 'Acesso negado' })

      const now = new Date()

      await db.insert(channelReads)
        .values({ userId: req.userId!, channelId, lastReadAt: now })
        .onConflictDoUpdate({
          target: [channelReads.userId, channelReads.channelId],
          set:    { lastReadAt: now },
        })

      const marked = await db.update(notifications)
        .set({ readAt: now })
        .where(and(
          eq(notifications.userId, req.userId!),
          isNull(notifications.readAt),
          sql`(${notifications.payload}::jsonb ->> 'channelId') = ${channelId}`,
        ))
        .returning({ id: notifications.id })

      if (marked.length > 0) {
        io.to(`user:${req.userId}`).emit('notifications_read', {
          ids:   marked.map((m) => m.id),
          scope: { channelId },
        })
      }

      res.json({ data: { channelId, lastReadAt: now.toISOString(), notifsMarked: marked.length } })
    })
  )

  router.get(
    '/reads/channels',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const rows = await db.select({
        channelId:  channelReads.channelId,
        lastReadAt: channelReads.lastReadAt,
      })
        .from(channelReads)
        .where(eq(channelReads.userId, req.userId!))

      const map: Record<string, string> = {}
      for (const r of rows) map[r.channelId] = r.lastReadAt.toISOString()
      res.json({ data: map })
    })
  )

  router.post(
    '/dm/:conversationId/read',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const { conversationId } = req.params

      const [conv] = await db.select().from(dmConversations)
        .where(and(
          eq(dmConversations.id, conversationId),
          or(eq(dmConversations.userAId, req.userId!), eq(dmConversations.userBId, req.userId!)),
        ))
        .limit(1)
      if (!conv) return res.status(403).json({ error: 'Acesso negado' })

      const now = new Date()
      const isA = conv.userAId === req.userId
      await db.update(dmConversations)
        .set(isA ? { lastReadByA: now } : { lastReadByB: now })
        .where(eq(dmConversations.id, conversationId))

      const otherId = isA ? conv.userBId : conv.userAId
      io.to(`user:${otherId}`).emit('dm_read', {
        conversationId,
        readerId:   req.userId,
        readerSide: isA ? 'A' : 'B',
        lastReadAt: now.toISOString(),
      })

      const marked = await db.update(notifications)
        .set({ readAt: now })
        .where(and(
          eq(notifications.userId, req.userId!),
          isNull(notifications.readAt),
          sql`(${notifications.payload}::jsonb ->> 'conversationId') = ${conversationId}`,
        ))
        .returning({ id: notifications.id })

      if (marked.length > 0) {
        io.to(`user:${req.userId}`).emit('notifications_read', {
          ids:   marked.map((m) => m.id),
          scope: { conversationId },
        })
      }

      res.json({ data: { conversationId, lastReadAt: now.toISOString(), notifsMarked: marked.length } })
    })
  )

  router.get(
    '/reads/dm',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const convs = await db.select({
        id:          dmConversations.id,
        userAId:     dmConversations.userAId,
        userBId:     dmConversations.userBId,
        lastReadByA: dmConversations.lastReadByA,
        lastReadByB: dmConversations.lastReadByB,
      })
        .from(dmConversations)
        .where(or(eq(dmConversations.userAId, req.userId!), eq(dmConversations.userBId, req.userId!)))

      const map: Record<string, { mine: string | null; other: string | null }> = {}
      for (const c of convs) {
        const isA = c.userAId === req.userId
        map[c.id] = {
          mine:  (isA ? c.lastReadByA : c.lastReadByB)?.toISOString() ?? null,
          other: (isA ? c.lastReadByB : c.lastReadByA)?.toISOString() ?? null,
        }
      }
      res.json({ data: map })
    })
  )

  return router
}
