import { env } from './env'

const CAPACITOR_ORIGINS = new Set([
  'https://localhost',
  'capacitor://localhost',
])

const LOCALHOST_DEV_RE = /^http:\/\/(localhost|127\.0\.0\.1):\d+$/

export function isAllowedOrigin(origin: string | undefined): boolean {
  if (!origin) return false
  if (origin === env.CLIENT_URL) return true
  if (CAPACITOR_ORIGINS.has(origin)) return true
  if (env.NODE_ENV === 'development' && LOCALHOST_DEV_RE.test(origin)) return true
  return false
}
