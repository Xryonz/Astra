import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { sentry } from '@/lib/sentry'

const API_URL = (import.meta as any).env?.VITE_API_URL ?? ''
export const apiBaseUrl = API_URL
/** Resolve uma URL relativa (ex. "/uploads/abc.png") pro endpoint do backend. */
export const resolveApiUrl = (url: string) =>
  url.startsWith('http') || url.startsWith('data:') ? url : `${API_URL}${url}`

export const api = axios.create({
  baseURL: API_URL,
  withCredentials: true, // Envia cookies httpOnly (refresh token)
})

// ── Interceptor de request: injeta access token ──────────────
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// ── Interceptor de response: renova token automaticamente ────
//
// Refresh-loop guard (anti-hang):
//   - Refresh tem timeout de 8s. Se backend trava, o promise rejeita
//     e libera todas as requests pendentes em vez de pendurar para sempre.
//   - Pending requests guardam {resolve, reject}. Falha do refresh propaga
//     o erro pra TODAS — caso contrário, queries do React Query ficariam
//     em isLoading: true eterno (cenário do bug do ProfileCard).
//   - _retried flag impede uma request retentar refresh mais de 1x.
let isRefreshing = false
let pendingRequests: Array<{ resolve: (token: string) => void; reject: (err: unknown) => void }> = []

const REFRESH_TIMEOUT_MS = 8000

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retried) {
      originalRequest._retried = true

      if (isRefreshing) {
        // Aguarda o refresh em andamento. Reject propaga se refresh falhar.
        return new Promise((resolve, reject) => {
          pendingRequests.push({
            resolve: (token) => {
              originalRequest.headers.Authorization = `Bearer ${token}`
              resolve(api(originalRequest))
            },
            reject,
          })
        })
      }

      isRefreshing = true

      try {
        const { data } = await axios.post(
          `${import.meta.env.VITE_API_URL}/api/auth/refresh`,
          {},
          { withCredentials: true, timeout: REFRESH_TIMEOUT_MS }
        )
        const newToken = data.data.accessToken
        useAuthStore.getState().setAccessToken(newToken)

        pendingRequests.forEach((p) => p.resolve(newToken))
        pendingRequests = []

        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return api(originalRequest)
      } catch (refreshError) {
        // Refresh hang/falhou: libera todas pendentes COM erro pra evitar
        // que React Query / consumidores fiquem presos em loading infinito.
        pendingRequests.forEach((p) => p.reject(refreshError))
        pendingRequests = []

        useAuthStore.getState().logout()
        window.location.href = '/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    // Reporta 5xx pro Sentry (mantém 4xx fora pra não poluir com erros de user)
    const status = error.response?.status
    if (status && status >= 500) {
      sentry.captureException(error)
    }
    return Promise.reject(error)
  }
)
