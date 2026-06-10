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
import { useAuthStore } from '@/store/authStore'
import { getStoredRefreshToken, clearStoredRefreshToken, refreshSession } from '@/lib/api'

const STALE_THRESHOLD_MS = 5 * 60_000  // 5 minutos

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

      // Só refresha se estamos autenticados — login page não precisa
      const { isAuthenticated } = useAuthStore.getState()
      if (!isAuthenticated) return
      if (!getStoredRefreshToken()) return

      try {
        // refreshSession() é singleton (apps/web/src/lib/api.ts). Se o axios
        // interceptor disparar em paralelo, ambos esperam o MESMO request.
        // Antes eram dois POSTs concorrentes com o mesmo token → atomic claim
        // do server rejeitava o segundo → logout durante voltada de jogo/call.
        await refreshSession()
      } catch (e) {
        // Só desloga em 401 (refresh realmente inválido). Network/timeout/5xx
        // → silencia; interceptor de axios resolve quando o user agir.
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
