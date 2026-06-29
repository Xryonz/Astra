
import { useEffect, useRef } from 'react'
import { useAuthStore } from '@/store/authStore'
import { getStoredRefreshToken, clearStoredRefreshToken, refreshSession } from '@/lib/api'

const STALE_THRESHOLD_MS = 5 * 60_000

export function useVisibilityRefresh() {
  const lastActiveAtRef = useRef<number>(Date.now())

  useEffect(() => {
    const onVisible = async () => {
      if (document.visibilityState !== 'visible') {
        lastActiveAtRef.current = Date.now()
        return
      }
      const elapsed = Date.now() - lastActiveAtRef.current
      lastActiveAtRef.current = Date.now()

      if (elapsed < STALE_THRESHOLD_MS) return

      const { isAuthenticated } = useAuthStore.getState()
      if (!isAuthenticated) return
      if (!getStoredRefreshToken()) return

      try {

        await refreshSession()
      } catch (e) {

        const status = (e as { response?: { status?: number } })?.response?.status
        if (status === 401) {
          clearStoredRefreshToken()
          useAuthStore.getState().logout()
        }
      }
    }

    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [])
}
