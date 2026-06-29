
import { useEffect, useState } from 'react'
import { isNative } from '@/lib/native'
import { reconnectSocketNow } from '@/lib/socket'
import { flushOutbox } from '@/lib/outbox'

export function useOnlineStatus(): boolean {
  const [online, setOnline] = useState(true)

  useEffect(() => {
    let cleanup = () => {}

    const apply = (isOnline: boolean) => {
      setOnline(isOnline)
      if (isOnline) {
        reconnectSocketNow()

        setTimeout(() => void flushOutbox(), 600)
      }
    }

    if (navigator.onLine) void flushOutbox()

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
