import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { connectSocket } from '@/lib/socket'
import { getStoredRefreshToken, setStoredRefreshToken, clearStoredRefreshToken, api } from '@/lib/api'
import { applyTheme } from '@/lib/theme'

let inFlight: Promise<boolean> | null = null

export function bootstrapAuth(): Promise<boolean> {
  if (inFlight) return inFlight
  inFlight = doBootstrap().finally(() => { inFlight = null })
  return inFlight
}

async function doBootstrap(): Promise<boolean> {
  const { accessToken } = useAuthStore.getState()
  if (accessToken) return true

  const storedRefresh = getStoredRefreshToken()
  if (!storedRefresh) {
    useAuthStore.getState().logout()
    return false
  }

  try {
    const apiUrl = import.meta.env.VITE_API_URL
    const { data: refreshData } = await axios.post(
      `${apiUrl}/api/auth/refresh`,
      {},
      { headers: { Authorization: `Bearer ${storedRefresh}` } }
    )
    const newAccess  = refreshData.data.accessToken
    const newRefresh = refreshData.data.refreshToken
    setStoredRefreshToken(newRefresh)

    const cachedUser = useAuthStore.getState().user
    if (cachedUser) {
      useAuthStore.getState().setAuth(cachedUser, newAccess)
    } else {
      const { data: meData } = await axios.get(`${apiUrl}/api/auth/me`, {
        headers: { Authorization: `Bearer ${newAccess}` },
      })
      useAuthStore.getState().setAuth(meData.data.user, newAccess)
    }

    try { connectSocket() } catch { }
    void syncPreferencesFromServer()
    return true
  } catch (e) {

    const status = (e as { response?: { status?: number } })?.response?.status
    if (status === 401) {
      clearStoredRefreshToken()
      useAuthStore.getState().logout()
    }
    return false
  }
}

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

      const localAccent = localStorage.getItem('astra-accent') ?? localStorage.getItem('umbra-accent')
      const localBg     = localStorage.getItem('astra-bg')     ?? localStorage.getItem('umbra-bg')
      if (localAccent || localBg) {
        await api.patch('/api/profile/preferences', {
          preferences: { accent: localAccent ?? 'gold', bg: localBg ?? 'void' },
        })
      }
    }
  } catch { }
}
