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
