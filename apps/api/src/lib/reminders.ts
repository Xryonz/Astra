
import { and, inArray, isNull, lte } from 'drizzle-orm'
import { db } from '../db'
import { reminders, users } from '../db/schema'
import { logger } from './logger'
import { notify } from './notifications'
import type { Server as SocketServer } from 'socket.io'

const TICK_MS = 30_000
const MAX_DURATION_MS = 365 * 24 * 60 * 60 * 1000
const MIN_DURATION_MS = 60 * 1000
let intervalRef: ReturnType<typeof setInterval> | null = null

export function parseDuration(input: string): number | null {
  if (!input) return null
  const re = /(\d+)\s*(d|h|m|min|s)/gi
  let total = 0
  let m: RegExpExecArray | null
  let matched = false
  while ((m = re.exec(input.toLowerCase())) !== null) {
    matched = true
    const n = parseInt(m[1], 10)
    const u = m[2]
    if (!Number.isFinite(n) || n < 0) return null
    if      (u === 'd')                total += n * 86_400_000
    else if (u === 'h')                total += n * 3_600_000
    else if (u === 'm' || u === 'min') total += n * 60_000
    else if (u === 's')                total += n * 1000
  }
  if (!matched) return null
  if (total < MIN_DURATION_MS || total > MAX_DURATION_MS) return null
  return total
}

export function startReminderWorker(io: SocketServer) {
  if (process.env.NODE_ENV === 'test') return
  if (intervalRef) clearInterval(intervalRef)

  const tick = async () => {
    try {
      const now = new Date()
      const due = await db.select({
        id: reminders.id, targetUserId: reminders.targetUserId,
        creatorId: reminders.creatorId, content: reminders.content,
        channelId: reminders.channelId, dueAt: reminders.dueAt,
      })
        .from(reminders)
        .where(and(isNull(reminders.deliveredAt), lte(reminders.dueAt, now)))
        .limit(100)

      if (due.length === 0) return

      const meus = await db.update(reminders)
        .set({ deliveredAt: now })
        .where(and(inArray(reminders.id, due.map(r => r.id)), isNull(reminders.deliveredAt)))
        .returning({ id: reminders.id })

      const pegos = new Set(meus.map(r => r.id))
      const claimed = due.filter(r => pegos.has(r.id))
      if (claimed.length === 0) return

      const autores = await db.select({
        id: users.id, displayName: users.displayName, avatarUrl: users.avatarUrl,
      }).from(users).where(inArray(users.id, [...new Set(claimed.map(r => r.creatorId))]))

      const autorPorId = new Map(autores.map(a => [a.id, a]))

      for (const r of claimed) {
        const creator = autorPorId.get(r.creatorId)

        const isSelf  = r.creatorId === r.targetUserId
        const title   = isSelf ? '⏰ Lembrete' : `⏰ Lembrete de ${creator?.displayName ?? 'alguém'}`

        await notify({
          io, userId: r.targetUserId, actorId: r.creatorId, type: 'reply',
          payload: {
            isReminder: true,
            authorId:   r.creatorId,
            authorName: creator?.displayName ?? 'Astra',
            authorAvatar: creator?.avatarUrl ?? null,
            preview:    r.content,
            channelId:  r.channelId ?? undefined,
          },
          push: {
            title,
            body:  r.content,
            url:   '/app',
            tag:   `reminder-${r.id}`,
          },
        })
      }

      logger.info('Reminders', `delivered ${claimed.length}`)
    } catch (e: any) {
      logger.error('Reminders', 'tick falhou', e)
    }
  }

  setTimeout(tick, 5_000)
  intervalRef = setInterval(tick, TICK_MS)
}
