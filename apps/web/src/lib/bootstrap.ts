import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { connectSocket } from '@/lib/socket'
import { getStoredRefreshToken, setStoredRefreshToken, clearStoredRefreshToken } from '@/lib/api'

/**
 * Em cold load, tenta restaurar accessToken usando refreshToken de localStorage.
 *
 *  - Se há refresh em localStorage válido → seta accessToken, conecta socket, retorna true
 *  - Caso contrário → limpa auth state, retorna false
 *
 * Dedup em nível de módulo: refresh é rotacionado a cada uso, então
 * chamar 2x em paralelo (StrictMode) quebraria a segunda chamada.
 */
let inFlight: Promise<boolean> | null = null

export function bootstrapAuth(): Promise<boolean> {
  if (inFlight) return inFlight
  inFlight = doBootstrap().finally(() => { inFlight = null })
  return inFlight
}

async function doBootstrap(): Promise<boolean> {
  const { isAuthenticated, accessToken, logout, setAccessToken } = useAuthStore.getState()

  if (accessToken) return true
  if (!isAuthenticated) return false

  const storedRefresh = getStoredRefreshToken()
  if (!storedRefresh) {
    logout()
    return false
  }

  try {
    const { data } = await axios.post(
      `${import.meta.env.VITE_API_URL}/api/auth/refresh`,
      {},
      { headers: { Authorization: `Bearer ${storedRefresh}` } }
    )
    setAccessToken(data.data.accessToken)
    setStoredRefreshToken(data.data.refreshToken)
    try { connectSocket() } catch { /* ignore */ }
    return true
  } catch {
    clearStoredRefreshToken()
    logout()
    return false
  }
}
