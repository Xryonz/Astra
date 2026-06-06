/**
 * useVisibilityRefresh — refresh proativo do access token quando a aba volta
 * a ficar visível depois de um tempo.
 *
 * Por quê: access token tem TTL curto (~15min). Quando o user esquece a aba
 * aberta por 2-3 horas, a primeira request falha com 401. O interceptor de
 * axios já trata, mas:
 *   1) gera latência extra na primeira ação (refresh round-trip)
 *   2) socket.io reconecta usando o access expirado → loop até resync
 *
 * Solução: quando documento volta a ficar visível e passaram >5min desde
 * a última atividade, dispara refresh ANTES de qualquer ação do user.
 *
 * Custo: 1 request leve cada vez que volta da aba (após threshold).
 * Padrão usado por Slack/Discord web.
 */
import { useEffect, useRef } from 'react'
import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { getStoredRefreshToken, setStoredRefreshToken, clearStoredRefreshToken } from '@/lib/api'

const STALE_THRESHOLD_MS = 5 * 60_000  // 5 minutos

export function useVisibilityRefresh() {
  const lastActiveAtRef = useRef<number>(Date.now())
  const inFlightRef     = useRef<boolean>(false)

  useEffect(() => {
    const onVisible = async () => {
      if (document.visibilityState !== 'visible') {
        lastActiveAtRef.current = Date.now()
        return
      }
      const elapsed = Date.now() - lastActiveAtRef.current
      lastActiveAtRef.current = Date.now()

      if (elapsed < STALE_THRESHOLD_MS) return
      if (inFlightRef.current) return

      // Só refresha se estamos autenticados — login page não precisa
      const { isAuthenticated } = useAuthStore.getState()
      if (!isAuthenticated) return

      const stored = getStoredRefreshToken()
      if (!stored) return

      inFlightRef.current = true
      try {
        const { data } = await axios.post(
          `${import.meta.env.VITE_API_URL}/api/auth/refresh`,
          {},
          { headers: { Authorization: `Bearer ${stored}` }, timeout: 8000 }
        )
        useAuthStore.getState().setAccessToken(data.data.accessToken)
        setStoredRefreshToken(data.data.refreshToken)
      } catch {
        // Refresh inválido — derruba pra login. Interceptor seguiria mesmo
        // caminho mas seria após uma request falhar; aqui fazemos preventivo.
        clearStoredRefreshToken()
        useAuthStore.getState().logout()
      } finally {
        inFlightRef.current = false
      }
    }

    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [])
}
