/**
 * Sentry — error tracking + tracing (opt-in via SENTRY_DSN).
 *
 * Sem DSN, vira no-op completo: `captureException` aceita e descarta.
 * Init é idempotente; chame uma vez no boot, antes de criar o Express.
 *
 * Como usar:
 *   import { initSentry, sentry } from './lib/sentry'
 *   initSentry()
 *   ...
 *   sentry.captureException(err, { tags: { route: '/api/foo' } })
 */
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
    // Não envia PII automaticamente; envia só o que a gente marca via setUser/setTag.
    sendDefaultPii:   false,
    // Filtra ruído conhecido (timeouts de socket, etc) — pode crescer com tempo.
    ignoreErrors: [
      /ECONNRESET/,
      /EPIPE/,
      /socket hang up/,
    ],
  })
  initialized = true
  logger.info('Sentry', `inicializado (env=${env.SENTRY_ENVIRONMENT ?? env.NODE_ENV})`)
}

/** Wrapper que vira no-op se Sentry não foi inicializado. */
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
