import { useEffect, useState } from 'react'
import { reconnectSocketNow } from '@/lib/socket'
import { flushOutbox } from '@/lib/outbox'

export function useOnlineStatus(): boolean {
  const [online, setOnline] = useState(true)

  useEffect(() => {
    const apply = (isOnline: boolean) => {
      setOnline(isOnline)
      if (isOnline) {
        reconnectSocketNow()

        setTimeout(() => void flushOutbox(), 600)
      }
    }

    if (navigator.onLine) void flushOutbox()

    setOnline(navigator.onLine)
    const on  = () => apply(true)
    const off = () => apply(false)
    window.addEventListener('online', on)
    window.addEventListener('offline', off)
    return () => {
      window.removeEventListener('online', on)
      window.removeEventListener('offline', off)
    }
  }, [])

  return online
}
