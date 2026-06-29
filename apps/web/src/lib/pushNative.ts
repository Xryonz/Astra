
import i18n from '@/i18n'
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

    await PushNotifications.createChannel({
      id: 'mentions', name: i18n.t('push.mentions'), description: i18n.t('push.mentionsDesc'),
      importance: 5, visibility: 1, vibration: true,
    }).catch(() => {})
    await PushNotifications.createChannel({
      id: 'dms', name: i18n.t('push.dms'), description: i18n.t('push.dmsDesc'),
      importance: 4, visibility: 0, vibration: true,
    }).catch(() => {})
    await PushNotifications.createChannel({
      id: 'general', name: i18n.t('push.general'), description: i18n.t('push.generalDesc'),
      importance: 3, visibility: 0, vibration: false,
    }).catch(() => {})

    await PushNotifications.addListener('registration', ({ value }) => {
      void api.post('/api/push/fcm-token', { token: value, platform: 'android' }).catch(() => {})
    })

    await PushNotifications.addListener('pushNotificationActionPerformed', (action) => {
      const url = (action.notification.data as { url?: string })?.url
      if (url && url.startsWith('/')) window.location.href = url
    })

    await PushNotifications.register()
  } catch {
    registered = false
  }
}
