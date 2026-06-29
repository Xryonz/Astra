
type SentryNs = typeof import('@sentry/react')

let sentryPromise: Promise<SentryNs | null> | null = null
let initialized = false

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
