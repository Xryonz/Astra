import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { sentry } from '@/lib/sentry'

const API_URL = (import.meta as any).env?.VITE_API_URL ?? ''
export const apiBaseUrl = API_URL
/** Resolve uma URL relativa (ex. "/uploads/abc.png") pro endpoint do backend. */
export const resolveApiUrl = (url: string) =>
  url.startsWith('http') || url.startsWith('data:') ? url : `${API_URL}${url}`

const REFRESH_KEY = 'astra-refresh'
export const getStoredRefreshToken = () => localStorage.getItem(REFRESH_KEY) || null
export const setStoredRefreshToken = (token: string) => localStorage.setItem(REFRESH_KEY, token)
export const clearStoredRefreshToken = () => localStorage.removeItem(REFRESH_KEY)

export const api = axios.create({ baseURL: API_URL })

// ── Interceptor de request: injeta access token ──────────────
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// ── Singleton refresh ────────────────────────────────────────
// Single-flight promise compartilhada entre o interceptor de axios E
// useVisibilityRefresh. Antes cada um tinha seu próprio lock — quando
// visibilitychange e refetchOnWindowFocus disparavam ao mesmo tempo (voltando
// do jogo), os dois caminhos POSTavam /refresh com o MESMO refresh-token,
// o servidor (atomic claim) só aceita o primeiro, o segundo recebia 401 →
// logout cascata. Centralizando aqui, o segundo caller espera o primeiro.
const REFRESH_TIMEOUT_MS = 8000

interface RefreshResult { accessToken: string; refreshToken: string }
let refreshInFlight: Promise<RefreshResult> | null = null

export function refreshSession(): Promise<RefreshResult> {
  if (refreshInFlight) return refreshInFlight

  refreshInFlight = (async () => {
    const stored = getStoredRefreshToken()
    if (!stored) throw Object.assign(new Error('NO_REFRESH'), { response: { status: 401 } })

    const { data } = await axios.post(
      `${API_URL}/api/auth/refresh`,
      {},
      { headers: { Authorization: `Bearer ${stored}` }, timeout: REFRESH_TIMEOUT_MS },
    )
    const newAccess: string  = data.data.accessToken
    const newRefresh: string = data.data.refreshToken
    useAuthStore.getState().setAccessToken(newAccess)
    setStoredRefreshToken(newRefresh)
    return { accessToken: newAccess, refreshToken: newRefresh }
  })().finally(() => { refreshInFlight = null })

  return refreshInFlight
}

// ── Interceptor de response: renova token automaticamente ────
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retried) {
      originalRequest._retried = true

      try {
        const { accessToken } = await refreshSession()
        originalRequest.headers.Authorization = `Bearer ${accessToken}`
        return api(originalRequest)
      } catch (refreshError) {
        // Só desloga se servidor respondeu 401 (refresh inválido/expirado).
        // Network error, timeout, 5xx → mantém sessão; user tenta de novo.
        const refreshStatus = (refreshError as { response?: { status?: number } })?.response?.status
        if (refreshStatus === 401) {
          clearStoredRefreshToken()
          useAuthStore.getState().logout()
          window.location.href = '/login'
        }
        return Promise.reject(refreshError)
      }
    }

    const status = error.response?.status
    if (status && status >= 500) {
      sentry.captureException(error)
    }
    return Promise.reject(error)
  }
)
