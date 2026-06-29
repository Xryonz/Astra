
import * as Sentry from '@sentry/node'
import { env } from './env'
import { logger } from './logger'

let initialized = false

export function initSentry(): void {
  if (initialized) return
  if (!env.SENTRY_DSN) {
    logger.info('Sentry', 'DSN ausente — error tracking desligado')
    return
  }
  Sentry.init({
    dsn:              env.SENTRY_DSN,
    environment:      env.SENTRY_ENVIRONMENT ?? env.NODE_ENV,
    release:          env.RELEASE,
    tracesSampleRate: env.SENTRY_TRACES_SAMPLE,

    sendDefaultPii:   false,

    ignoreErrors: [
      /ECONNRESET/,
      /EPIPE/,
      /socket hang up/,
    ],
  })
  initialized = true
  logger.info('Sentry', `inicializado (env=${env.SENTRY_ENVIRONMENT ?? env.NODE_ENV})`)
}

export const sentry = {
  captureException(err: unknown, ctx?: { tags?: Record<string, string>; user?: { id?: string } }) {
    if (!initialized) return
    Sentry.withScope((scope) => {
      if (ctx?.tags) for (const [k, v] of Object.entries(ctx.tags)) scope.setTag(k, v)
      if (ctx?.user?.id) scope.setUser({ id: ctx.user.id })
      Sentry.captureException(err)
    })
  },
  captureMessage(msg: string, level: Sentry.SeverityLevel = 'info') {
    if (!initialized) return
    Sentry.captureMessage(msg, level)
  },
  setUser(id: string | null) {
    if (!initialized) return
    Sentry.setUser(id ? { id } : null)
  },
  isEnabled() { return initialized },
  raw() { return Sentry },
}
