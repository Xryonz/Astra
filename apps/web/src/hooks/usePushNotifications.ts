import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '@/lib/api'
import { isNative } from '@/lib/native'
import { registerNativePush } from '@/lib/pushNative'

type PushState = 'unsupported' | 'denied' | 'unsubscribed' | 'subscribed' | 'loading' | 'server-disabled'

/** Lê a permissão FCM no app nativo e mapeia pro PushState. */
async function nativePermState(): Promise<PushState> {
  try {
    const { PushNotifications } = await import('@capacitor/push-notifications')
    const perm = await PushNotifications.checkPermissions()
    return perm.receive === 'granted' ? 'subscribed'
         : perm.receive === 'denied'  ? 'denied'
         : 'unsubscribed'
  } catch {
    return 'unsupported'
  }
}

function urlBase64ToUint8Array(base64: string) {
  const padding = '='.repeat((4 - (base64.length % 4)) % 4)
  const b64 = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/')
  const raw = atob(b64)
  const out = new Uint8Array(raw.length)
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i)
  return out
}

async function getRegistration(): Promise<ServiceWorkerRegistration | null> {
  if (!('serviceWorker' in navigator)) return null
  try {
    const existing = await navigator.serviceWorker.getRegistration('/sw.js')
    if (existing) return existing
    return await navigator.serviceWorker.register('/sw.js')
  } catch (err) {
    console.error('[SW] erro ao registrar:', err)
    return null
  }
}

export function usePushNotifications() {
  const [state, setState] = useState<PushState>('loading')
  const navigate = useNavigate()

  // Verifica suporte + state inicial. Checa também enabled server-side
  // (VAPID configurado) antes de mostrar "Ativar" — sem isso o user clica,
  // recebe erro de subscribe e fica frustrado.
  useEffect(() => {
    // App nativo: push é FCM (não Web Push). Lê a permissão do device.
    // O token já é registrado no login (registerNativePush em App.tsx).
    if (isNative) {
      void nativePermState().then(setState)
      return
    }
    if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
      setState('unsupported'); return
    }
    if (Notification.permission === 'denied') { setState('denied'); return }
    (async () => {
      try {
        // Server tem VAPID configurado?
        const r = await api.get('/api/push/vapid-public-key')
        if (!r.data?.data?.enabled || !r.data?.data?.publicKey) {
          setState('server-disabled')
          return
        }
      } catch {
        setState('server-disabled')
        return
      }
      const reg = await getRegistration()
      if (!reg) { setState('unsupported'); return }
      const sub = await reg.pushManager.getSubscription()
      setState(sub ? 'subscribed' : 'unsubscribed')
    })()
  }, [])

  // Recebe navigate + reply vindos do SW (push click + actionable)
  useEffect(() => {
    if (!('serviceWorker' in navigator)) return
    const onMsg = async (e: MessageEvent) => {
      const d = e.data
      if (!d) return
      if (d.type === 'push-navigate' && typeof d.url === 'string') {
        try { navigate(d.url) } catch {}
      }
      if (d.type === 'push-reply' && typeof d.content === 'string' && d.content.trim()) {
        try {
          if (d.channelId) {
            await api.post(`/api/channels/${d.channelId}/messages`, { content: d.content })
          } else if (d.dmConvId) {
            await api.post(`/api/dm/${d.dmConvId}/messages`, { content: d.content })
          }
        } catch (err) {
          console.error('[Push reply] falhou:', err)
        }
      }
    }
    navigator.serviceWorker.addEventListener('message', onMsg)
    return () => navigator.serviceWorker.removeEventListener('message', onMsg)
  }, [navigate])

  const subscribe = useCallback(async () => {
    setState('loading')
    // App nativo: pede permissão + registra o token FCM.
    if (isNative) {
      await registerNativePush()
      const s = await nativePermState()
      setState(s)
      return s === 'subscribed'
    }
    try {
      const perm = await Notification.requestPermission()
      if (perm !== 'granted') { setState(perm === 'denied' ? 'denied' : 'unsubscribed'); return false }

      const reg = await getRegistration()
      if (!reg) { setState('unsupported'); return false }

      // Pega a public key do backend
      const r = await api.get('/api/push/vapid-public-key')
      const pub = r.data?.data?.publicKey as string | null
      if (!pub) { console.warn('[Push] backend sem VAPID key'); setState('unsupported'); return false }

      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(pub),
      })

      const json = sub.toJSON()
      await api.post('/api/push/subscribe', {
        endpoint: sub.endpoint,
        keys:     json.keys,
      })

      setState('subscribed')
      return true
    } catch (err) {
      console.error('[Push] subscribe falhou:', err)
      setState('unsubscribed')
      return false
    }
  }, [])

  const unsubscribe = useCallback(async () => {
    // App nativo: a permissão é gerida pelo Android (não dá pra revogar por
    // código). O usuário desativa por canal nas configs de notificação do SO.
    if (isNative) return
    setState('loading')
    try {
      const reg = await getRegistration()
      const sub = await reg?.pushManager.getSubscription()
      if (sub) {
        const endpoint = sub.endpoint
        await sub.unsubscribe().catch(() => {})
        await api.delete(`/api/push/subscribe?endpoint=${encodeURIComponent(endpoint)}`).catch(() => {})
      }
      setState('unsubscribed')
    } catch (err) {
      console.error('[Push] unsubscribe falhou:', err)
      setState('subscribed')
    }
  }, [])

  const sendTest = useCallback(async () => {
    try { await api.post('/api/push/test') } catch {}
  }, [])

  return { state, subscribe, unsubscribe, sendTest, native: isNative }
}
