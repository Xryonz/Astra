
import { eq } from 'drizzle-orm'
import { db } from '../db'
import { fcmTokens } from '../db/schema'
import { env } from './env'
import type { PushPayload } from './push'

type Messaging = import('firebase-admin/messaging').Messaging

let messaging: Messaging | null = null

export async function initFcm(): Promise<void> {
  const raw = env.FIREBASE_SERVICE_ACCOUNT
  if (!raw) {
    console.warn('[FCM] FIREBASE_SERVICE_ACCOUNT ausente — push nativo desabilitado')
    return
  }
  try {
    const creds = JSON.parse(raw)
    const { initializeApp, cert } = await import('firebase-admin/app')
    const { getMessaging } = await import('firebase-admin/messaging')
    const app = initializeApp({ credential: cert(creds) })
    messaging = getMessaging(app)
    console.log('[FCM] configurado')
  } catch (e) {
    console.error('[FCM] init falhou:', (e as Error).message)
  }
}

export function isFcmEnabled() { return messaging !== null }

export async function sendFcmToUser(userId: string, payload: PushPayload): Promise<void> {
  if (!messaging) return

  const rows = await db.select().from(fcmTokens).where(eq(fcmTokens.userId, userId))
  if (rows.length === 0) return

  const channelId = payload.tag?.includes('mention') ? 'mentions'
    : payload.dmConvId ? 'dms'
    : 'general'

  await Promise.allSettled(rows.map(async (row) => {
    try {
      await messaging!.send({
        token: row.token,
        notification: { title: payload.title, body: payload.body },
        data: {
          url:       payload.url ?? '/app',
          channelId: payload.channelId ?? '',
          dmConvId:  payload.dmConvId ?? '',
        },
        android: {
          priority: 'high',
          notification: {
            channelId,
            icon:  'ic_stat_astra',
            color: '#c9a96e',
            tag:   payload.tag,
          },
        },
      })
    } catch (err: unknown) {
      const code = (err as { code?: string })?.code ?? ''
      if (code === 'messaging/registration-token-not-registered' ||
          code === 'messaging/invalid-registration-token') {
        await db.delete(fcmTokens).where(eq(fcmTokens.id, row.id)).catch(() => {})
      } else {
        console.error('[FCM] erro:', code || (err as Error)?.message)
      }
    }
  }))
}
