import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { sentry } from '@/lib/sentry'
import { saveRefreshNative, loadRefreshNative, clearRefreshNative } from '@/lib/secureStore'

const API_URL = (import.meta as any).env?.VITE_API_URL ?? ''
export const apiBaseUrl = API_URL

export const resolveApiUrl = (url: string) =>
  url.startsWith('http') || url.startsWith('data:') ? url : `${API_URL}${url}`

const REFRESH_KEY = 'astra-refresh'
export const getStoredRefreshToken = () => localStorage.getItem(REFRESH_KEY) || null
export const setStoredRefreshToken = (token: string) => {
  localStorage.setItem(REFRESH_KEY, token)
  void saveRefreshNative(token)
}
export const clearStoredRefreshToken = () => {
  localStorage.removeItem(REFRESH_KEY)
  void clearRefreshNative()
}

export async function hydrateRefreshFromNative(): Promise<void> {
  if (getStoredRefreshToken()) return
  const native = await loadRefreshNative()
  if (native) localStorage.setItem(REFRESH_KEY, native)
}

export const api = axios.create({ baseURL: API_URL })

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

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
