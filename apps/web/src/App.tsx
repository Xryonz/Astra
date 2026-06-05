import { useEffect, useState, lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import { bootstrapAuth } from '@/lib/bootstrap'
import { AppShellSkeleton } from '@/components/skeletons/AppShellSkeleton'
import { Toaster } from '@/components/ui/sonner'
import { ConfirmProvider } from '@/hooks/useConfirm'
import StarField from '@/components/astra/StarField'
import FallingStars from '@/components/astra/FallingStars'
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

  useEffect(() => {
    bootstrapAuth().finally(() => setBooted(true))
  }, [])

  if (!booted) return <SplashScreen />

  return (
    <BrowserRouter>
      <StarField />
      <FallingStars />
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
