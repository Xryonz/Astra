import { useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'

export default function OAuthCallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { handleOAuthCallback } = useAuth()
  // Em React StrictMode (dev) este useEffect roda 2x. Sem este guard
  // a segunda chamada tenta usar o refresh token já rotacionado pela
  // primeira, levando o usuário de volta para /login com erro.
  const ranRef = useRef(false)

  useEffect(() => {
    if (ranRef.current) return
    ranRef.current = true

    const error = searchParams.get('error')
    if (error) {
      navigate('/login?error=oauth_failed', { replace: true })
      return
    }

    handleOAuthCallback()
      .then(() => navigate('/app', { replace: true }))
      .catch(() => navigate('/login?error=oauth_failed', { replace: true }))
  }, [])

  return (
    <div className="min-h-screen bg-zinc-950 flex items-center justify-center">
      <div className="text-center">
        <div className="w-8 h-8 border-2 border-white border-t-transparent rounded-full animate-spin mx-auto mb-4" />
        <p className="text-zinc-400 text-sm">Autenticando...</p>
      </div>
    </div>
  )
}
