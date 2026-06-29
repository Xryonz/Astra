import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import './index.css'
import '@/i18n'
import { restoreTheme } from '@/lib/theme'
import { TooltipProvider } from '@/components/ui/tooltip'
import { initSentry } from '@/lib/sentry'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import { migrateLocalStorage } from '@/lib/migrateLocalStorage'
import { setupOfflineCache } from '@/lib/offlineCache'
import { setupMessageCache } from '@/lib/messageCache'
import { initNativeApp } from '@/lib/native'

migrateLocalStorage()
initSentry()
restoreTheme()
void initNativeApp()

const apiUrl = (import.meta as any).env?.VITE_API_URL as string | undefined
if (apiUrl && /^https?:/.test(apiUrl)) {
  try {
    const link = document.createElement('link')
    link.rel = 'preconnect'
    link.href = apiUrl
    link.crossOrigin = ''
    document.head.appendChild(link)
  } catch {}
}

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {})
  })
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {

      staleTime:          60 * 1000,
      gcTime:             10 * 60 * 1000,
      retry:              1,
      refetchOnWindowFocus: false,
      refetchOnReconnect:   'always',
    },
  },
})

setupOfflineCache(queryClient)
setupMessageCache(queryClient)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <TooltipProvider delayDuration={300}>
          <App />
        </TooltipProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  </StrictMode>
)
