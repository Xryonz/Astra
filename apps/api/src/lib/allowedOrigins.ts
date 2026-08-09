import { env } from './env'

const LOCALHOST_DEV_RE = /^http:\/\/(localhost|127\.0\.0\.1):\d+$/

export function isAllowedOrigin(origin: string | undefined): boolean {
  if (!origin) return false
  if (origin === env.CLIENT_URL) return true
  if (env.NODE_ENV === 'development' && LOCALHOST_DEV_RE.test(origin)) return true
  return false
}
