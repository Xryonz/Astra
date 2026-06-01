/**
 * Read receipts:
 *  - POST /api/channels/:channelId/read       → marca canal como lido agora
 *  - GET  /api/reads/channels                 → lastReadAt por canal do user atual
 *  - POST /api/dm/:conversationId/read        → marca conv DM como lida + emite socket
 *  - GET  /api/reads/dm                       → lastReadByOther (+seu) por conv
 *
 * Frontend usa pra:
 *  - sidebar: dot quando channel.lastMsgAt > read.lastReadAt
 *  - DM: "Visto" no último envio quando other.lastRead >= msg.createdAt
 */
import { Router, Request, Response } from 'express'
import { Server as SocketServer } from 'socket.io'
import { and, eq, or } from 'drizzle-orm'
import { db } from '../db'
import { channels, serverMembers, channelReads, dmConversations } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'

export function createReadsRouter(io: SocketServer) {
  const router = Router()

  // POST /api/channels/:channelId/read
  router.post(
    '/channels/:channelId/read',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const { channelId } = req.params

      // Confirma membership antes de gravar (não permite "marcar lido" canal que não tem acesso)
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
      // Upsert: insert ou update lastReadAt no unique (userId, channelId)
      await db.insert(channelReads)
        .values({ userId: req.userId!, channelId, lastReadAt: now })
        .onConflictDoUpdate({
          target: [channelReads.userId, channelReads.channelId],
          set:    { lastReadAt: now },
        })

      res.json({ data: { channelId, lastReadAt: now.toISOString() } })
    })
  )

  // GET /api/reads/channels — pega tudo de uma vez (sidebar precisa)
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

  // POST /api/dm/:conversationId/read
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

      // Notifica o outro lado (pra atualizar "Visto" no chat dele)
      const otherId = isA ? conv.userBId : conv.userAId
      io.to(`user:${otherId}`).emit('dm_read', {
        conversationId,
        readerId:   req.userId,
        readerSide: isA ? 'A' : 'B',
        lastReadAt: now.toISOString(),
      })

      res.json({ data: { conversationId, lastReadAt: now.toISOString() } })
    })
  )

  // GET /api/reads/dm — { [conversationId]: { mine, other } }
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
