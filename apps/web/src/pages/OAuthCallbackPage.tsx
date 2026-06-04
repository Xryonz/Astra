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
    <div className="min-h-screen bg-(--void) flex items-center justify-center font-(family-name:--font-body)">
      <div className="text-center">
        <div className="size-8 border-2 border-(--border-mid) border-t-(--accent) rounded-full animate-spin mx-auto mb-4" />
        <p className="text-(--text-3) text-sm">Autenticando…</p>
      </div>
    </div>
  )
}
