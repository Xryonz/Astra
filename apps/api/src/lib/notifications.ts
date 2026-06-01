/**
 * Notification pipeline central.
 *
 * Toda notificação (mention, dm, reaction, reply) passa por `notify()`:
 *  1. Carrega prefs do receiver
 *  2. Checa se tipo tá habilitado, se receiver tá em DND, se tá em quiet hours
 *  3. Insere row em Notification (pra feed in-app)
 *  4. Emite socket 'notification' no user-room
 *  5. Dispara push (se push tipo permitido + não em quiet hours)
 *
 * Self-notification é ignorada (autor não recebe notif de si mesmo).
 */
import { eq } from 'drizzle-orm'
import { db } from '../db'
import { users, notifications } from '../db/schema'
import { sendPush, type PushPayload } from './push'
import { logger } from './logger'
import type { Server as SocketServer } from 'socket.io'

export type NotificationType = 'mention' | 'dm' | 'reaction' | 'reply'

export interface NotificationPrefs {
  /** Aceita @mention em canais */
  mentions: boolean
  /** Aceita DMs */
  dms: boolean
  /** Aceita quando alguém reage à sua msg */
  reactions: boolean
  /** Aceita quando alguém responde sua msg */
  replies: boolean
  /** Tocar som ao receber notif */
  sounds: boolean
  /** Mostrar badge/popup desktop (push) */
  desktop: boolean
  /** Hora local (0-23) que começa "modo silencioso" — sem som/push, feed continua */
  quietStart: number | null
  /** Hora local (0-23) que termina */
  quietEnd: number | null
}

export const DEFAULT_PREFS: NotificationPrefs = {
  mentions:   true,
  dms:        true,
  reactions:  true,
  replies:    true,
  sounds:     true,
  desktop:    true,
  quietStart: null,
  quietEnd:   null,
}

export function parsePrefs(raw: string | null | undefined): NotificationPrefs {
  if (!raw) return DEFAULT_PREFS
  try {
    const parsed = JSON.parse(raw)
    return { ...DEFAULT_PREFS, ...parsed }
  } catch { return DEFAULT_PREFS }
}

/** Hora atual em 24h (assume server timezone = user timezone — simplificação por enquanto). */
function isInQuietHours(prefs: NotificationPrefs, now = new Date()): boolean {
  if (prefs.quietStart == null || prefs.quietEnd == null) return false
  const h = now.getHours()
  const s = prefs.quietStart
  const e = prefs.quietEnd
  // Janela normal (ex: 22→23 = 22h às 23h)
  if (s < e) return h >= s && h < e
  // Janela atravessando meia-noite (ex: 22→8 = 22h até 8h)
  return h >= s || h < e
}

function typeEnabled(type: NotificationType, prefs: NotificationPrefs): boolean {
  switch (type) {
    case 'mention':  return prefs.mentions
    case 'dm':       return prefs.dms
    case 'reaction': return prefs.reactions
    case 'reply':    return prefs.replies
  }
}

export interface NotifyOpts {
  io:         SocketServer
  userId:     string                // receiver
  actorId?:   string                // autor (pra skip self-notif)
  type:       NotificationType
  payload:    Record<string, unknown>
  push?:      PushPayload           // opcional — sem isso skip push, só feed
}

export interface NotifyResult {
  notificationId: string | null
  pushed:         boolean
  skipped:        'self' | 'pref' | 'dnd' | null
}

/**
 * Main entry. Retorna metadata útil pra teste/log; never throws (best-effort).
 */
export async function notify(opts: NotifyOpts): Promise<NotifyResult> {
  const { io, userId, actorId, type, payload, push } = opts

  // Self-notif: skip
  if (actorId && actorId === userId) {
    return { notificationId: null, pushed: false, skipped: 'self' }
  }

  try {
    const [user] = await db.select({
      notificationPrefs: users.notificationPrefs,
      status:            users.status,
    }).from(users).where(eq(users.id, userId)).limit(1)

    if (!user) return { notificationId: null, pushed: false, skipped: null }

    const prefs = parsePrefs(user.notificationPrefs)

    // Pref check
    if (!typeEnabled(type, prefs)) {
      return { notificationId: null, pushed: false, skipped: 'pref' }
    }

    // DND status: sem push e sem som; feed in-app ainda recebe
    const isDnd  = user.status === 'DND'
    const quiet  = isInQuietHours(prefs)
    const muted  = isDnd || quiet

    // Sempre cria entry no feed (mesmo em quiet/DND — user vê quando voltar)
    const [created] = await db.insert(notifications).values({
      userId, type, payload: JSON.stringify(payload),
    }).returning({ id: notifications.id, createdAt: notifications.createdAt })

    // Socket emit pro user-room (cliente filtra/show)
    io.to(`user:${userId}`).emit('notification', {
      id:        created.id,
      type,
      payload,
      createdAt: created.createdAt.toISOString(),
      silent:    muted, // cliente sabe se deve tocar som / piscar badge
    })

    // Push: só se push pref liga, não tá em quiet/DND, e payload de push existe
    let pushed = false
    if (push && prefs.desktop && !muted) {
      sendPush(userId, push).catch(() => {})
      pushed = true
    }

    return { notificationId: created.id, pushed, skipped: muted ? 'dnd' : null }
  } catch (e) {
    logger.error('Notify', 'falhou', e)
    return { notificationId: null, pushed: false, skipped: null }
  }
}
