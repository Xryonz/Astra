import { useEffect, useState, lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import { bootstrapAuth } from '@/lib/bootstrap'
import { useVisibilityRefresh } from '@/hooks/useVisibilityRefresh'
import { AppShellSkeleton } from '@/components/skeletons/AppShellSkeleton'
import { Toaster } from '@/components/ui/sonner'
import { ConfirmProvider } from '@/hooks/useConfirm'
import StarField from '@/components/astra/StarField'
import SplashScreen from '@/components/astra/SplashScreen'
import { OfflineBanner } from '@/components/OfflineBanner'

const LoginPage         = lazy(() => import('@/pages/LoginPage'))
const RegisterPage      = lazy(() => import('@/pages/RegisterPage'))
const AppPage           = lazy(() => import('@/pages/AppPage'))
const OAuthCallbackPage = lazy(() => import('@/pages/OAuthCallbackPage'))
const InvitePage        = lazy(() => import('@/pages/InvitePage'))
const OnboardingPage    = lazy(() => import('@/pages/OnboardingPage'))

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />
}

function PublicRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return !isAuthenticated ? <>{children}</> : <Navigate to="/app" replace />
}

function RequireOnboarded({ children }: { children: React.ReactNode }) {
  const user = useAuthStore((s) => s.user)
  if (user && user.onboardedAt === null) return <Navigate to="/onboarding" replace />
  return <>{children}</>
}

export default function App() {
  const [booted, setBooted] = useState(false)

  useEffect(() => {

    void (async () => {
      try { await bootstrapAuth() } catch { }
      setBooted(true)
    })()
  }, [])

  useVisibilityRefresh()

  if (!booted) return <SplashScreen />

  return (
    <BrowserRouter>
      <StarField />
      <OfflineBanner />
      <ConfirmProvider>
        <Suspense fallback={<AppShellSkeleton />}>
          <Routes>
            <Route path="/" element={<Navigate to="/app" replace />} />
            <Route path="/login"    element={<PublicRoute><LoginPage /></PublicRoute>} />
            <Route path="/register" element={<PublicRoute><RegisterPage /></PublicRoute>} />
            <Route path="/auth/callback" element={<OAuthCallbackPage />} />
            <Route path="/invite/:code"  element={<InvitePage />} />
            <Route path="/onboarding" element={<PrivateRoute><OnboardingPage /></PrivateRoute>} />
            <Route path="/app/*" element={<PrivateRoute><RequireOnboarded><AppPage /></RequireOnboarded></PrivateRoute>} />
          </Routes>
        </Suspense>
        <Toaster />
      </ConfirmProvider>
    </BrowserRouter>
  )
}
