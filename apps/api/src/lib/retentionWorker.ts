import path from 'path'
import fs from 'fs'
import { and, eq, inArray, isNotNull, lt, sql } from 'drizzle-orm'
import { db } from '../db'
import { servers, channels, messages, notifications } from '../db/schema'
import { UPLOAD_DIR } from '../routes/upload'
import { logger } from './logger'

const NOTIFICATION_TTL_DAYS = 30

// Bumpa de 1h pra 5min pra que mensagens efêmeras de 1h+ sumam com precisão razoável.
// Custo: 12 ticks/h em vez de 1. Cada tick é barato (queries indexadas).
const RUN_INTERVAL_MS = 5 * 60 * 1000 // 5min
let intervalRef: ReturnType<typeof setInterval> | null = null

/**
 * Apaga (soft + hard) mensagens cujo servidor tem `messageRetentionDays` setado
 * e estão mais velhas que isso. Limpa arquivos órfãos do disco quando dá pra.
 *
 * Não roda em test (NODE_ENV=test).
 */
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

        // Coleta mensagens a apagar (e os attachments pra remover do disco)
        const stale = await db.select({
          id: messages.id, attachments: messages.attachments,
        })
          .from(messages)
          .innerJoin(channels, eq(channels.id, messages.channelId))
          .where(and(eq(channels.serverId, s.id), lt(messages.createdAt, cutoff)))
          .limit(500) // batch

        if (stale.length === 0) continue

        // Remove arquivos locais (best-effort)
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
        // `inArray` gera `id IN ($1,$2,...)` — funciona com Drizzle. Antes
        // usávamos `ANY(${ids})` que vira `ANY(($1,$2,...))` (row constructor)
        // → Postgres rejeita silenciosamente.
        await db.delete(messages).where(inArray(messages.id, ids))
        logger.info('Retention', `server=${s.id} apagou ${ids.length} mensagens (cutoff ${cutoff.toISOString()})`)
      }

      // Mensagens efêmeras expiradas — DELETE direto (não soft).
      const delEph = await db.delete(messages)
        .where(sql`${messages.expiresAt} IS NOT NULL AND ${messages.expiresAt} <= now()`)
        .returning({ id: messages.id })
      if (delEph.length > 0) {
        logger.info('Retention', `apagou ${delEph.length} msgs efêmeras expiradas`)
      }

      // Notifications lidas mais antigas que NOTIFICATION_TTL_DAYS → DELETE.
      // Não-lidas ficam (user pode ter ficado offline por dias).
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

  // Roda 1x logo após start, depois no intervalo
  setTimeout(run, 30_000)
  intervalRef = setInterval(run, RUN_INTERVAL_MS)
}
