import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { connectSocket } from '@/lib/socket'

/**
 * Em cold load, tenta restaurar o accessToken via refresh cookie.
 *
 * Resultado:
 *  - Se há refresh cookie válido → seta accessToken, conecta socket, retorna true
 *  - Caso contrário → limpa o auth state, retorna false
 *
 * Usamos axios cru (não `lib/api.ts`) para evitar o interceptor de refresh
 * iniciar um ciclo durante o próprio bootstrap.
 *
 * Dedup em nível de módulo: refresh tokens são rotacionados a cada uso,
 * então chamar /api/auth/refresh 2x em paralelo (ex: React StrictMode
 * em dev disparando o useEffect duas vezes) quebraria a segunda chamada
 * e logaria o usuário para fora. Aqui retornamos a mesma Promise se já
 * tem uma em voo.
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

  try {
    const { data } = await axios.post(
      `${import.meta.env.VITE_API_URL}/api/auth/refresh`,
      {},
      { withCredentials: true }
    )
    setAccessToken(data.data.accessToken)
    try { connectSocket() } catch { /* ignore */ }
    return true
  } catch {
    logout()
    return false
  }
}
