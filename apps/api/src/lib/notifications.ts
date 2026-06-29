
import { eq } from 'drizzle-orm'
import { db } from '../db'
import { users, notifications } from '../db/schema'
import { sendPush, type PushPayload } from './push'
import { logger } from './logger'
import type { Server as SocketServer } from 'socket.io'

export type NotificationType = 'mention' | 'dm' | 'reaction' | 'reply'

export interface NotificationPrefs {

  mentions: boolean

  dms: boolean

  reactions: boolean

  replies: boolean

  sounds: boolean

  desktop: boolean

  quietStart: number | null

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

function isInQuietHours(prefs: NotificationPrefs, now = new Date()): boolean {
  if (prefs.quietStart == null || prefs.quietEnd == null) return false
  const h = now.getHours()
  const s = prefs.quietStart
  const e = prefs.quietEnd

  if (s < e) return h >= s && h < e

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
  userId:     string
  actorId?:   string
  type:       NotificationType
  payload:    Record<string, unknown>
  push?:      PushPayload
}

export interface NotifyResult {
  notificationId: string | null
  pushed:         boolean
  skipped:        'self' | 'pref' | 'dnd' | null
}

export async function notify(opts: NotifyOpts): Promise<NotifyResult> {
  const { io, userId, actorId, type, payload, push } = opts

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

    if (!typeEnabled(type, prefs)) {
      return { notificationId: null, pushed: false, skipped: 'pref' }
    }

    const isDnd  = user.status === 'DND'
    const quiet  = isInQuietHours(prefs)
    const muted  = isDnd || quiet

    const [created] = await db.insert(notifications).values({
      userId, type, payload: JSON.stringify(payload),
    }).returning({ id: notifications.id, createdAt: notifications.createdAt })

    io.to(`user:${userId}`).emit('notification', {
      id:        created.id,
      type,
      payload,
      createdAt: created.createdAt.toISOString(),
      silent:    muted,
    })

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
