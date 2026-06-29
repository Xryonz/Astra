
import { Router, Request, Response } from 'express'
import { Server as SocketServer } from 'socket.io'
import { z } from 'zod'
import { and, eq, isNull } from 'drizzle-orm'
import { db } from '../db'
import { channels, servers, serverMembers, messages, users } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { messageLimiter } from '../middleware/rateLimiter'
import { asyncHandler } from '../lib/asyncHandler'
import { PERMS, getMemberPerms } from '../lib/permissions'
import { createId } from '../db/cuid'

const CreatePollSchema = z.object({
  question:      z.string().min(3).max(300),
  options:       z.array(z.string().min(1).max(80)).min(2).max(8),
  allowMultiple: z.boolean().optional().default(false),

  durationHours: z.number().int().min(1).max(168).optional().nullable(),
})

interface PollData {
  question:      string
  options:       Array<{ id: string; text: string; votes: string[] }>
  allowMultiple: boolean
  expiresAt:     string | null
  closed:        boolean
}

function safeParsePoll(raw: unknown): PollData | null {
  if (typeof raw !== 'string') return null
  try {
    const v = JSON.parse(raw) as Partial<PollData>
    if (!v.question || !Array.isArray(v.options)) return null
    return {
      question:      v.question,
      options:       v.options.map((o) => ({
        id:    String((o as any)?.id ?? createId()),
        text:  String((o as any)?.text ?? ''),
        votes: Array.isArray((o as any)?.votes) ? (o as any).votes.filter((u: unknown) => typeof u === 'string') : [],
      })),
      allowMultiple: !!v.allowMultiple,
      expiresAt:     typeof v.expiresAt === 'string' ? v.expiresAt : null,
      closed:        !!v.closed,
    }
  } catch { return null }
}

async function assertChannelAccess(userId: string, channelId: string) {
  const [row] = await db.select({
    channelId:   channels.id,
    serverId:    channels.serverId,
    membershipId: serverMembers.id,
    serverName:  servers.name,
    channelName: channels.name,
  })
    .from(channels)
    .innerJoin(servers, eq(servers.id, channels.serverId))
    .leftJoin(serverMembers, and(
      eq(serverMembers.serverId, channels.serverId),
      eq(serverMembers.userId,   userId),
    ))
    .where(eq(channels.id, channelId))
    .limit(1)
  if (!row || !row.membershipId) return null
  return row
}

export function createPollsRouter(io: SocketServer) {
  const router = Router({ mergeParams: true })

  router.post(
    '/',
    requireAuth,
    messageLimiter,
    validate(CreatePollSchema),
    asyncHandler(async (req: Request, res: Response) => {
      const { channelId } = req.params
      const body = req.body as z.infer<typeof CreatePollSchema>

      const channel = await assertChannelAccess(req.userId!, channelId)
      if (!channel) return res.status(403).json({ error: 'Acesso negado' })

      const expiresAt = body.durationHours
        ? new Date(Date.now() + body.durationHours * 3600_000).toISOString()
        : null

      const poll: PollData = {
        question:      body.question,
        options:       body.options.map((text) => ({ id: createId(), text, votes: [] })),
        allowMultiple: body.allowMultiple,
        expiresAt,
        closed:        false,
      }

      const [inserted] = await db.insert(messages).values({
        content:   body.question,
        channelId,
        authorId:  req.userId!,
        poll:      JSON.stringify(poll),
      }).returning()

      const [author] = await db.select({
        id: users.id, username: users.username,
        displayName: users.displayName, avatarUrl: users.avatarUrl,
      }).from(users).where(eq(users.id, req.userId!)).limit(1)

      const payload = {
        ...inserted,
        author,
        reactions:   [],
        mentions:    [],
        attachments: [],
        poll,
      }

      io.to(`channel:${channelId}`).emit('new_message', payload)
      res.status(201).json({ data: payload })
    })
  )

  router.post(
    '/:messageId/vote',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const { channelId, messageId } = req.params
      const optionId = String((req.body as { optionId?: string })?.optionId ?? '')
      if (!optionId) return res.status(400).json({ error: 'optionId obrigatório' })

      const channel = await assertChannelAccess(req.userId!, channelId)
      if (!channel) return res.status(403).json({ error: 'Acesso negado' })

      for (let attempt = 0; attempt < 3; attempt++) {
        const [msg] = await db.select({
          id: messages.id, poll: messages.poll, updatedAt: messages.updatedAt,
        })
          .from(messages)
          .where(and(eq(messages.id, messageId), eq(messages.channelId, channelId), isNull(messages.deletedAt)))
          .limit(1)
        if (!msg || !msg.poll) return res.status(404).json({ error: 'Poll não encontrada' })

        const poll = safeParsePoll(msg.poll)
        if (!poll) return res.status(500).json({ error: 'Poll corrompida' })
        if (poll.closed) return res.status(400).json({ error: 'Poll encerrada' })
        if (poll.expiresAt && new Date(poll.expiresAt).getTime() < Date.now())
          return res.status(400).json({ error: 'Poll expirada' })

        const targetOption = poll.options.find((o) => o.id === optionId)
        if (!targetOption) return res.status(400).json({ error: 'Opção inválida' })

        const alreadyVoted = targetOption.votes.includes(req.userId!)
        if (alreadyVoted) {
          targetOption.votes = targetOption.votes.filter((u) => u !== req.userId)
        } else {
          if (!poll.allowMultiple) {

            for (const o of poll.options) o.votes = o.votes.filter((u) => u !== req.userId)
          }
          targetOption.votes.push(req.userId!)
        }

        const r = await db.update(messages)
          .set({ poll: JSON.stringify(poll) })
          .where(and(eq(messages.id, messageId), eq(messages.updatedAt, msg.updatedAt)))
          .returning({ id: messages.id })

        if (r.length > 0) {
          io.to(`channel:${channelId}`).emit('poll_updated', { messageId, channelId, poll })
          return res.json({ data: { messageId, poll } })
        }

      }

      return res.status(409).json({ error: 'Voto não pôde ser registrado (conflito de concorrência). Tente de novo.' })
    })
  )

  router.post(
    '/:messageId/close',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const { channelId, messageId } = req.params
      const channel = await assertChannelAccess(req.userId!, channelId)
      if (!channel) return res.status(403).json({ error: 'Acesso negado' })

      const [msg] = await db.select({
        authorId: messages.authorId, poll: messages.poll,
      })
        .from(messages)
        .where(and(eq(messages.id, messageId), eq(messages.channelId, channelId), isNull(messages.deletedAt)))
        .limit(1)
      if (!msg || !msg.poll) return res.status(404).json({ error: 'Poll não encontrada' })

      const m = await getMemberPerms(req.userId!, channel.serverId)
      const canClose = msg.authorId === req.userId || m.isOwner || m.permissions.has(PERMS.MANAGE_MESSAGES)
      if (!canClose) return res.status(403).json({ error: 'Sem permissão' })

      const poll = safeParsePoll(msg.poll)
      if (!poll) return res.status(500).json({ error: 'Poll corrompida' })
      poll.closed = true

      await db.update(messages).set({ poll: JSON.stringify(poll) }).where(eq(messages.id, messageId))
      io.to(`channel:${channelId}`).emit('poll_updated', { messageId, channelId, poll })
      res.json({ data: { messageId, poll } })
    })
  )

  return router
}

export { safeParsePoll }
export type { PollData }
