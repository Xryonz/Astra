import path from 'path'
import fs from 'fs'
import { and, eq, inArray, isNotNull, lt, sql } from 'drizzle-orm'
import { db } from '../db'
import { servers, channels, messages, notifications } from '../db/schema'
import { UPLOAD_DIR } from '../routes/upload'
import { logger } from './logger'

const NOTIFICATION_TTL_DAYS = 30

const RUN_INTERVAL_MS = 5 * 60 * 1000
let intervalRef: ReturnType<typeof setInterval> | null = null

export function startRetentionWorker() {
  if (process.env.NODE_ENV === 'test') return
  if (intervalRef) clearInterval(intervalRef)

  const run = async () => {
    try {
      const servs = await db.select({ id: servers.id, days: servers.messageRetentionDays })
        .from(servers).where(isNotNull(servers.messageRetentionDays))

      for (const s of servs) {
        if (!s.days || s.days <= 0) continue
        const cutoff = new Date(Date.now() - s.days * 24 * 60 * 60 * 1000)

        const stale = await db.select({
          id: messages.id, attachments: messages.attachments,
        })
          .from(messages)
          .innerJoin(channels, eq(channels.id, messages.channelId))
          .where(and(eq(channels.serverId, s.id), lt(messages.createdAt, cutoff)))
          .limit(500)

        if (stale.length === 0) continue

        for (const m of stale) {
          try {
            const arr = JSON.parse(m.attachments || '[]') as Array<{ url: string }>
            for (const a of arr) {
              if (a.url?.startsWith('/uploads/')) {
                const fname = a.url.replace('/uploads/', '')
                const p = path.join(UPLOAD_DIR, fname)
                fs.unlink(p, () => {})
              }
            }
          } catch {}
        }

        const ids = stale.map((m) => m.id)

        await db.delete(messages).where(inArray(messages.id, ids))
        logger.info('Retention', `server=${s.id} apagou ${ids.length} mensagens (cutoff ${cutoff.toISOString()})`)
      }

      const delEph = await db.delete(messages)
        .where(sql`${messages.expiresAt} IS NOT NULL AND ${messages.expiresAt} <= now()`)
        .returning({ id: messages.id })
      if (delEph.length > 0) {
        logger.info('Retention', `apagou ${delEph.length} msgs efêmeras expiradas`)
      }

      const notifCutoff = new Date(Date.now() - NOTIFICATION_TTL_DAYS * 24 * 60 * 60 * 1000)
      const delNotifs = await db.delete(notifications)
        .where(and(isNotNull(notifications.readAt), lt(notifications.createdAt, notifCutoff)))
        .returning({ id: notifications.id })
      if (delNotifs.length > 0) {
        logger.info('Retention', `apagou ${delNotifs.length} notifs lidas > ${NOTIFICATION_TTL_DAYS}d`)
      }
    } catch (err: any) {
      const cause = err?.cause ?? err
      logger.error('Retention', err?.message ?? 'sem mensagem', {
        code: cause?.code, constraint: cause?.constraint, detail: cause?.detail,
        table: cause?.table, column: cause?.column,
      })
    }
  }

  setTimeout(run, 30_000)
  intervalRef = setInterval(run, RUN_INTERVAL_MS)
}
