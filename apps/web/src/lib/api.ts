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
let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    // Se 401 e ainda não tentou renovar
    if (error.response?.status === 401 && !originalRequest._retried) {
      originalRequest._retried = true

      if (isRefreshing) {
        // Aguarda o refresh em andamento e re-executa a request
        return new Promise((resolve) => {
          pendingRequests.push((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(api(originalRequest))
          })
        })
      }

      isRefreshing = true

      try {
        const { data } = await axios.post(
          `${import.meta.env.VITE_API_URL}/api/auth/refresh`,
          {},
          { withCredentials: true }
        )
        const newToken = data.data.accessToken
        useAuthStore.getState().setAccessToken(newToken)

        // Executa todas as requests que ficaram aguardando
        pendingRequests.forEach((cb) => cb(newToken))
        pendingRequests = []

        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return api(originalRequest)
      } catch {
        // Refresh falhou: faz logout
        useAuthStore.getState().logout()
        window.location.href = '/login'
        return Promise.reject(error)
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
