/**
 * Sentry frontend — opt-in via VITE_SENTRY_DSN, full-lazy import.
 *
 * Antes: `import * as Sentry from '@sentry/react'` (eager, 153KB gzip no main bundle).
 * Agora: TODO o módulo `@sentry/react` só é baixado se VITE_SENTRY_DSN existe.
 *
 * Side-effect: APIs viraram async (captureException, setUser). Quem usa
 * sentry.captureException(err) em try/catch já não esperava resposta — ainda funciona.
 *
 * Ativa browser tracing (auto) + replay desligado por padrão.
 */
type SentryNs = typeof import('@sentry/react')

let sentryPromise: Promise<SentryNs | null> | null = null
let initialized = false

/**
 * Inicia (ou tenta) — chamado uma vez em main.tsx.
 * Resolve com módulo ou null se DSN ausente. Idempotente.
 */
export function initSentry(): Promise<SentryNs | null> {
  if (sentryPromise) return sentryPromise
  const dsn = import.meta.env.VITE_SENTRY_DSN as string | undefined
  if (!dsn) {
    sentryPromise = Promise.resolve(null)
    return sentryPromise
  }

  sentryPromise = (async () => {
    const Sentry = await import('@sentry/react')
    Sentry.init({
      dsn,
      environment: (import.meta.env.VITE_SENTRY_ENVIRONMENT as string | undefined) ?? import.meta.env.MODE,
      release:     import.meta.env.VITE_RELEASE as string | undefined,
      tracesSampleRate: Number(import.meta.env.VITE_SENTRY_TRACES_SAMPLE ?? 0.1),
      sendDefaultPii: false,
      integrations: [
        Sentry.browserTracingIntegration(),
      ],
      ignoreErrors: [
        /ResizeObserver loop/,
        /Non-Error promise rejection/,
        /Network Error/,
      ],
    })
    initialized = true
    return Sentry
  })()
  return sentryPromise
}

/**
 * Helper fire-and-forget — nunca propaga erro do Sentry pro caller.
 * Se Sentry ainda está carregando, queue-a e libera na resolução.
 */
export const sentry = {
  captureException(err: unknown) {
    if (!sentryPromise) return
    sentryPromise.then((s) => { try { s?.captureException(err) } catch {} })
  },
  setUser(id: string | null) {
    if (!sentryPromise) return
    sentryPromise.then((s) => { try { s?.setUser(id ? { id } : null) } catch {} })
  },
  isEnabled() { return initialized },
}
