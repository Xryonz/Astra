
import { AsyncLocalStorage } from 'async_hooks'

type Level = 'debug' | 'info' | 'warn' | 'error'

const LEVEL_WEIGHT: Record<Level, number> = { debug: 0, info: 1, warn: 2, error: 3 }
const MIN_LEVEL: Level = (process.env.LOG_LEVEL as Level) ?? (process.env.NODE_ENV === 'production' ? 'info' : 'debug')
const MIN_WEIGHT = LEVEL_WEIGHT[MIN_LEVEL] ?? LEVEL_WEIGHT.info
const IS_PROD = process.env.NODE_ENV === 'production'

interface LogContext { reqId?: string; userId?: string }
const als = new AsyncLocalStorage<LogContext>()

export function runWithLogContext<T>(ctx: LogContext, fn: () => T): T {
  return als.run(ctx, fn)
}

export function getLogContext(): LogContext | undefined {
  return als.getStore()
}

let sentryRef: typeof import('./sentry').sentry | null = null
function getSentry() {
  if (sentryRef) return sentryRef
  try {
    sentryRef = require('./sentry').sentry
    return sentryRef
  } catch { return null }
}

function serializeExtra(extra: unknown[]): unknown[] {
  return extra.map((e) => {
    if (e instanceof Error) {
      return {
        name:    e.name,
        message: e.message,
        stack:   e.stack,
        cause:   (e as any).cause ? {
          message:    (e as any).cause?.message,
          code:       (e as any).cause?.code,
          constraint: (e as any).cause?.constraint,
          detail:     (e as any).cause?.detail,
        } : undefined,
      }
    }
    return e
  })
}

function emit(level: Level, scope: string, msg: string, extra: unknown[]) {
  if (LEVEL_WEIGHT[level] < MIN_WEIGHT) return
  const ctx = als.getStore()
  const sink = level === 'error' ? console.error : level === 'warn' ? console.warn : console.log

  if (IS_PROD) {
    const payload = {
      ts:    new Date().toISOString(),
      level,
      scope,
      msg,
      ...(ctx?.reqId  ? { reqId:  ctx.reqId  } : {}),
      ...(ctx?.userId ? { userId: ctx.userId } : {}),
      ...(extra.length > 0 ? { extra: serializeExtra(extra) } : {}),
    }
    sink(JSON.stringify(payload))
  } else {
    const reqTag = ctx?.reqId ? ` [req:${ctx.reqId.slice(0, 8)}]` : ''
    const line = `${new Date().toISOString()} [${level.toUpperCase()}] [${scope}]${reqTag} ${msg}`
    if (extra.length === 0) sink(line)
    else sink(line, ...extra)
  }

  if (level === 'error') {
    const s = getSentry()
    if (s?.isEnabled()) {
      const errLike = extra.find((e) => e instanceof Error) as Error | undefined
      if (errLike) s.captureException(errLike, { tags: { scope } })
      else s.captureMessage(`[${scope}] ${msg}`, 'error')
    }
  }
}

export const logger = {
  debug: (scope: string, msg: string, ...extra: unknown[]) => emit('debug', scope, msg, extra),
  info:  (scope: string, msg: string, ...extra: unknown[]) => emit('info',  scope, msg, extra),
  warn:  (scope: string, msg: string, ...extra: unknown[]) => emit('warn',  scope, msg, extra),
  error: (scope: string, msg: string, ...extra: unknown[]) => emit('error', scope, msg, extra),
}
