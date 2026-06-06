import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { connectSocket } from '@/lib/socket'
import { getStoredRefreshToken, setStoredRefreshToken, clearStoredRefreshToken, api } from '@/lib/api'
import { applyTheme } from '@/lib/theme'

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
    // Sync de preferências (tema) com server. Não bloqueia o boot — roda em paralelo.
    void syncPreferencesFromServer()
    return true
  } catch {
    clearStoredRefreshToken()
    logout()
    return false
  }
}

// ── Preferências cross-device ─────────────────────────────────
// Fluxo: GET /preferences. Se server tem accent/bg → aplica (vence localStorage).
// Se server vazio → push localStorage como 1ª sync. Idempotente.
async function syncPreferencesFromServer() {
  try {
    const { data } = await api.get('/api/profile/preferences')
    const prefs = data?.data?.preferences ?? {}
    const accent = typeof prefs.accent === 'string' ? prefs.accent : null
    const bg     = typeof prefs.bg     === 'string' ? prefs.bg     : null

    if (accent || bg) {
      applyTheme(
        accent ?? localStorage.getItem('astra-accent') ?? localStorage.getItem('umbra-accent') ?? 'gold',
        bg     ?? localStorage.getItem('astra-bg')     ?? localStorage.getItem('umbra-bg')     ?? 'void',
      )
    } else {
      // 1º login: server vazio, empurra o que está local.
      const localAccent = localStorage.getItem('astra-accent') ?? localStorage.getItem('umbra-accent')
      const localBg     = localStorage.getItem('astra-bg')     ?? localStorage.getItem('umbra-bg')
      if (localAccent || localBg) {
        await api.patch('/api/profile/preferences', {
          preferences: { accent: localAccent ?? 'gold', bg: localBg ?? 'void' },
        })
      }
    }
  } catch { /* sem internet / endpoint 404 — fica com local mesmo */ }
}
