import helmet from 'helmet'
import { env } from '../lib/env'

const isProd = env.NODE_ENV === 'production'


export const secureHeaders = helmet({

  contentSecurityPolicy: {
    directives: {
      defaultSrc:     ["'self'"],
      scriptSrc:      isProd ? ["'self'"] : ["'self'", "'unsafe-inline'", "'unsafe-eval'"],
      styleSrc:       ["'self'", "'unsafe-inline'", 'https://fonts.googleapis.com'],
      fontSrc:        ["'self'", 'https://fonts.gstatic.com'],
      imgSrc:         ["'self'", 'data:', 'https:'],

      mediaSrc:       ["'self'", 'blob:', 'https:'],
      connectSrc:     ["'self'", env.CLIENT_URL, ...(isProd ? [] : ['ws://localhost:*'])],
      frameSrc:       ["'none'"],
      objectSrc:      ["'none'"],
      upgradeInsecureRequests: isProd ? [] : null,
    },
  },


  crossOriginEmbedderPolicy: false,
  crossOriginOpenerPolicy:   { policy: 'same-origin-allow-popups' },

  crossOriginResourcePolicy: { policy: 'cross-origin' },
  referrerPolicy:            { policy: 'strict-origin-when-cross-origin' },
  hsts: isProd
    ? { maxAge: 31_536_000, includeSubDomains: true, preload: true }
    : false,
  noSniff:                   true,
  xssFilter:                 true,
  frameguard:                { action: 'deny' },
})


export function hidePoweredBy(_req: unknown, res: any, next: () => void) {
  res.removeHeader('X-Powered-By')
  next()
}