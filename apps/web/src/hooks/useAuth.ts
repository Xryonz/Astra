import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '@/lib/api'
import { connectSocket, disconnectSocket } from '@/lib/socket'
import { useAuthStore } from '@/store/authStore'
import type { LoginInput, RegisterInput } from '@umbra/types'

export function useAuth() {
  const navigate = useNavigate()
  const { setAuth, logout: clearAuth, user, isAuthenticated } = useAuthStore()

  const login = useCallback(async (data: LoginInput) => {
    const res = await api.post('/api/auth/login', data)
    const { user, accessToken } = res.data.data
    setAuth(user, accessToken)
    connectSocket()
    navigate('/app')
  }, [setAuth, navigate])

  const register = useCallback(async (data: RegisterInput) => {
    const res = await api.post('/api/auth/register', data)
    const { user, accessToken } = res.data.data
    setAuth(user, accessToken)
    connectSocket()
    navigate('/app')
  }, [setAuth, navigate])

  const logout = useCallback(async () => {
    try {
      await api.post('/api/auth/logout')
    } catch {
      // Mesmo com erro, limpa o estado local
    } finally {
      disconnectSocket()
      clearAuth()
      navigate('/login')
    }
  }, [clearAuth, navigate])

  // Após callback OAuth o backend já gravou o refresh cookie.
  // Trocamos por accessToken e buscamos o usuário.
  const handleOAuthCallback = useCallback(async () => {
    const refreshRes = await api.post('/api/auth/refresh')
    const accessToken = refreshRes.data.data.accessToken
    useAuthStore.getState().setAccessToken(accessToken)

    const meRes = await api.get('/api/auth/me')
    setAuth(meRes.data.data.user, accessToken)
    connectSocket()
  }, [setAuth])

  return { user, isAuthenticated, login, register, logout, handleOAuthCallback }
}
