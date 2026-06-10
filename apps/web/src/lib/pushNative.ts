/**
 * Push nativo (FCM) — registro do device + canais Android + navegação
 * ao tocar na notificação. Só roda no app (isNative); web usa o web push
 * (VAPID) de sempre via usePushNotifications.
 *
 * Channels casam com o backend (lib/fcm.ts decide o channel por payload):
 *   mentions — som + heads-up (prioridade alta)
 *   dms      — som padrão
 *   general  — atividade de canal, silenciosa na barra
 * O user controla cada canal nas configs de notificação do Android.
 */
import { api } from '@/lib/api'
import { isNative } from '@/lib/native'

let registered = false

export async function registerNativePush(): Promise<void> {
  if (!isNative || registered) return
  registered = true

  try {
    const { PushNotifications } = await import('@capacitor/push-notifications')

    let perm = await PushNotifications.checkPermissions()
    if (perm.receive === 'prompt') perm = await PushNotifications.requestPermissions()
    if (perm.receive !== 'granted') { registered = false; return }

    // Canais Android (no-op no iOS)
    await PushNotifications.createChannel({
      id: 'mentions', name: 'Menções', description: 'Quando te mencionam',
      importance: 5, visibility: 1, vibration: true,
    }).catch(() => {})
    await PushNotifications.createChannel({
      id: 'dms', name: 'Sussurros (DMs)', description: 'Mensagens diretas',
      importance: 4, visibility: 0, vibration: true,
    }).catch(() => {})
    await PushNotifications.createChannel({
      id: 'general', name: 'Atividade', description: 'Atividade nas constelações',
      importance: 3, visibility: 0, vibration: false,
    }).catch(() => {})

    // Token → backend. 'registration' também dispara em renovação de token.
    await PushNotifications.addListener('registration', ({ value }) => {
      void api.post('/api/push/fcm-token', { token: value, platform: 'android' }).catch(() => {})
    })

    // Toque na notificação → navega pra URL do payload
    await PushNotifications.addListener('pushNotificationActionPerformed', (action) => {
      const url = (action.notification.data as { url?: string })?.url
      if (url && url.startsWith('/')) window.location.href = url
    })

    await PushNotifications.register()
  } catch {
    registered = false
  }
}
