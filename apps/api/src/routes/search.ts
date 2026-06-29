import { Router, Request, Response } from 'express'
import { and, desc, eq, ilike, inArray, isNull, or } from 'drizzle-orm'
import { db } from '../db'
import { messages, channels, servers, serverMembers, users } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'

const router = Router()

router.get(
  '/',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const q = String(req.query.q ?? '').trim()
    const scope = String(req.query.scope ?? 'all') as 'all'|'messages'|'channels'|'users'
    if (!q || q.length < 2) return res.json({ data: { messages: [], channels: [], users: [], servers: [] } })

    const like = `%${q}%`

    const myMemberships = await db.select({ serverId: serverMembers.serverId })
      .from(serverMembers).where(eq(serverMembers.userId, req.userId!))
    const serverIds = myMemberships.map((m) => m.serverId)
    if (serverIds.length === 0) return res.json({ data: { messages: [], channels: [], users: [], servers: [] } })

    const wantMsgs   = scope === 'all' || scope === 'messages'
    const wantChans  = scope === 'all' || scope === 'channels'
    const wantUsers  = scope === 'all' || scope === 'users'

    const [msgs, chans, usrs, srvs] = await Promise.all([
      wantMsgs
        ? db.select({
            id: messages.id, content: messages.content, channelId: messages.channelId, createdAt: messages.createdAt,
            channelName: channels.name, serverId: channels.serverId, serverName: servers.name,
            author: { id: users.id, username: users.username, displayName: users.displayName, avatarUrl: users.avatarUrl },
          })
            .from(messages)
            .innerJoin(channels, eq(channels.id, messages.channelId))
            .innerJoin(servers, eq(servers.id, channels.serverId))
            .innerJoin(users, eq(users.id, messages.authorId))
            .where(and(
              ilike(messages.content, like),
              inArray(channels.serverId, serverIds),
              isNull(messages.deletedAt),
              isNull(messages.threadId),
            ))
            .orderBy(desc(messages.createdAt))
            .limit(20)
        : Promise.resolve([] as any[]),
      wantChans
        ? db.select({
            id: channels.id, name: channels.name, type: channels.type,
            serverId: channels.serverId, serverName: servers.name,
          })
            .from(channels)
            .innerJoin(servers, eq(servers.id, channels.serverId))
            .where(and(ilike(channels.name, like), inArray(channels.serverId, serverIds)))
            .limit(20)
        : Promise.resolve([] as any[]),
      wantUsers
        ? db.select({
            id: users.id, username: users.username, displayName: users.displayName, avatarUrl: users.avatarUrl,
          })
            .from(users)
            .innerJoin(serverMembers, eq(serverMembers.userId, users.id))
            .where(and(
              or(ilike(users.displayName, like), ilike(users.username, like))!,
              inArray(serverMembers.serverId, serverIds),
            ))
            .limit(20)
        : Promise.resolve([] as any[]),

      db.select({ id: servers.id, name: servers.name, iconUrl: servers.iconUrl, isGroup: servers.isGroup })
        .from(servers)
        .where(and(ilike(servers.name, like), inArray(servers.id, serverIds)))
        .limit(10),
    ])

    const seenU = new Set<string>()
    const dedupUsers = usrs.filter((u) => seenU.has(u.id) ? false : (seenU.add(u.id), true))

    res.json({ data: { messages: msgs, channels: chans, users: dedupUsers, servers: srvs } })
  })
)

export default router
