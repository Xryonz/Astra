import { api, setStoredRefreshToken } from '@/lib/api'
import { connectSocket } from '@/lib/socket'
import { useAuthStore } from '@/store/authStore'

export async function completeOAuthLogin(refreshToken: string): Promise<void> {
  setStoredRefreshToken(refreshToken)
  const refreshRes = await api.post('/api/auth/refresh', {}, {
    headers: { Authorization: `Bearer ${refreshToken}` },
  })
  const newAccess  = refreshRes.data.data.accessToken
  const newRefresh = refreshRes.data.data.refreshToken
  setStoredRefreshToken(newRefresh)
  useAuthStore.getState().setAccessToken(newAccess)

  const meRes = await api.get('/api/auth/me')
  useAuthStore.getState().setAuth(meRes.data.data.user, newAccess)
  connectSocket()
}

// Login com Google. Morava no native.ts porque o app Capacitor abria um Browser
// in-app com ?platform=mobile; sem ele sobrou o unico caminho que o web usava.
export async function openGoogleLogin(): Promise<void> {
  window.location.href = `${import.meta.env.VITE_API_URL}/api/auth/google`
}
