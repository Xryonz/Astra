import { useEffect, useState, lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import { bootstrapAuth } from '@/lib/bootstrap'
import { useVisibilityRefresh } from '@/hooks/useVisibilityRefresh'
import { registerNativePush } from '@/lib/pushNative'
import { isAppLockEnabled, verifyAppLock } from '@/lib/appLock'
import { AppShellSkeleton } from '@/components/skeletons/AppShellSkeleton'
import { Toaster } from '@/components/ui/sonner'
import { ConfirmProvider } from '@/hooks/useConfirm'
import StarField from '@/components/astra/StarField'
import SplashScreen from '@/components/astra/SplashScreen'

// Rotas grandes vão lazy — usuário não-logado nunca carrega AppPage, e vice-versa.
// Splitting por rota é a otimização de maior impacto pro bundle inicial.
const LoginPage         = lazy(() => import('@/pages/LoginPage'))
const RegisterPage      = lazy(() => import('@/pages/RegisterPage'))
const AppPage           = lazy(() => import('@/pages/AppPage'))
const OAuthCallbackPage = lazy(() => import('@/pages/OAuthCallbackPage'))
const InvitePage        = lazy(() => import('@/pages/InvitePage'))

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />
}

function PublicRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return !isAuthenticated ? <>{children}</> : <Navigate to="/app" replace />
}

export default function App() {
  const [booted, setBooted] = useState(false)
  // App lock (biometria): começa trancado se o user ativou. Web: sempre livre.
  const [locked, setLocked] = useState(isAppLockEnabled)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  useEffect(() => {
    bootstrapAuth().finally(() => setBooted(true))
  }, [])

  // Push nativo (FCM): registra o device quando autenticado. No-op no web.
  useEffect(() => {
    if (isAuthenticated) void registerNativePush()
  }, [isAuthenticated])

  // Pede a digital assim que o app abre trancado
  useEffect(() => {
    if (locked) void verifyAppLock().then((ok) => { if (ok) setLocked(false) })
  }, [locked])

  // Refresh proativo quando a aba volta após >5min — evita 401 na 1ª request
  useVisibilityRefresh()

  if (!booted) return <SplashScreen />

  if (locked) {
    return (
      <div className="h-screen-safe flex flex-col items-center justify-center gap-6 bg-(--void)">
        <img src="/favicon.svg" alt="" width={72} height={72} draggable={false} />
        <p className="text-(--text-2) text-sm m-0">Astra bloqueado</p>
        <button
          onClick={() => void verifyAppLock().then((ok) => { if (ok) setLocked(false) })}
          className="px-5 py-2.5 rounded-xl border border-(--accent)/50 text-(--accent) text-sm cursor-pointer hover:bg-(--accent-dim) transition-colors"
        >
          Desbloquear com digital
        </button>
      </div>
    )
  }

  return (
    <BrowserRouter>
      <StarField />
      <ConfirmProvider>
        <Suspense fallback={<AppShellSkeleton />}>
          <Routes>
            <Route path="/" element={<Navigate to="/app" replace />} />
            <Route path="/login"    element={<PublicRoute><LoginPage /></PublicRoute>} />
            <Route path="/register" element={<PublicRoute><RegisterPage /></PublicRoute>} />
            <Route path="/auth/callback" element={<OAuthCallbackPage />} />
            <Route path="/invite/:code"  element={<InvitePage />} />
            <Route path="/app/*" element={<PrivateRoute><AppPage /></PrivateRoute>} />
          </Routes>
        </Suspense>
        <Toaster />
      </ConfirmProvider>
    </BrowserRouter>
  )
}
