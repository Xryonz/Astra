import { useEffect } from 'react'
import { getSocket } from '@/lib/socket'
import { usePresenceStore } from '@/store/presenceStore'
import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'
import type { UserStatus } from '@/components/StatusDot'

/**
 * Liga listener global de presence_update no socket + busca status inicial do user.
 * Monta uma única vez no App root.
 */
export function usePresenceListener() {
  const setMyStatusStore = usePresenceStore((s) => s.setMyStatus)
  const setOther         = usePresenceStore((s) => s.setOther)
  const myId             = useAuthStore((s) => s.user?.id)

  // Seed status do próprio user (pega do GET /profile/:id, inclui INVISIBLE pq é self)
  useEffect(() => {
    if (!myId) return
    let cancel = false
    api.get(`/api/profile/${myId}`).then((r) => {
      if (cancel) return
      const s = (r.data?.data?.user?.status ?? 'ONLINE') as UserStatus
      setMyStatusStore(s)
    }).catch(() => {})
    return () => { cancel = true }
  }, [myId, setMyStatusStore])

  // Socket listener pra presence_update
  useEffect(() => {
    if (!myId) return
    let sock: ReturnType<typeof getSocket>
    try { sock = getSocket() } catch { return }

    const handler = (p: { userId: string; status: UserStatus | 'OFFLINE' | 'online' | 'offline' }) => {
      const raw = p.status === 'online' ? 'ONLINE' : p.status === 'offline' ? 'OFFLINE' : p.status
      const status = raw as UserStatus
      if (p.userId === myId) {
        if (status !== 'OFFLINE') setMyStatusStore(status)
      } else {
        setOther(p.userId, status)
      }
    }
    sock.on('presence_update', handler)
    return () => { sock.off('presence_update', handler) }
  }, [myId, setMyStatusStore, setOther])
}

/** Emite mudança de status. Backend persiste + broadcasta. */
export function setMyStatus(status: UserStatus) {
  try {
    getSocket().emit('set_status', status)
    usePresenceStore.getState().setMyStatus(status)
  } catch {}
}
