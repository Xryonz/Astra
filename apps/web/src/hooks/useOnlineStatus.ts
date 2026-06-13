/**
 * Estado de conexão — nativo (@capacitor/network) + web (window online/offline).
 *
 * Por quê os dois: no app nativo o Network plugin reporta o estado real do
 * rádio (Wi-Fi/celular caiu), mais confiável que navigator.onLine no WebView.
 * No web, os eventos do window bastam. Quando a rede VOLTA, cutuca o socket
 * pra reconectar na hora em vez de esperar o backoff.
 */
import { useEffect, useState } from 'react'
import { isNative } from '@/lib/native'
import { reconnectSocketNow } from '@/lib/socket'

export function useOnlineStatus(): boolean {
  const [online, setOnline] = useState(true)

  useEffect(() => {
    let cleanup = () => {}

    const apply = (isOnline: boolean) => {
      setOnline(isOnline)
      if (isOnline) reconnectSocketNow()
    }

    if (isNative) {
      let remove: (() => void) | undefined
      void import('@capacitor/network').then(async ({ Network }) => {
        const status = await Network.getStatus()
        setOnline(status.connected)
        const handle = await Network.addListener('networkStatusChange', (s) => apply(s.connected))
        remove = () => handle.remove()
      }).catch(() => {})
      cleanup = () => remove?.()
    } else {
      setOnline(navigator.onLine)
      const on  = () => apply(true)
      const off = () => apply(false)
      window.addEventListener('online', on)
      window.addEventListener('offline', off)
      cleanup = () => {
        window.removeEventListener('online', on)
        window.removeEventListener('offline', off)
      }
    }

    return cleanup
  }, [])

  return online
}
