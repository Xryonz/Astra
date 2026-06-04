import { useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'

export default function OAuthCallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { handleOAuthCallback } = useAuth()
  // StrictMode roda useEffect 2x em dev. Guard previne double-call.
  const ranRef = useRef(false)

  useEffect(() => {
    if (ranRef.current) return
    ranRef.current = true

    const error = searchParams.get('error')
    if (error) {
      navigate('/login?error=oauth_failed', { replace: true })
      return
    }

    // Backend redireciona com #refresh=<token> (hash fragment).
    // Hash fragment não vai pro server e não fica em access logs.
    const hash = window.location.hash.replace(/^#/, '')
    const params = new URLSearchParams(hash)
    const refreshToken = params.get('refresh')

    if (!refreshToken) {
      navigate('/login?error=oauth_failed', { replace: true })
      return
    }

    // Limpa hash da URL imediatamente após extrair (não fica no histórico)
    window.history.replaceState(null, '', window.location.pathname)

    handleOAuthCallback(refreshToken)
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
