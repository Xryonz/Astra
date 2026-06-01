import { Request, Response, NextFunction } from 'express'
import { verifyAccessToken } from '../lib/jwt'
import { isTokenBlacklisted } from '../lib/redis'

declare global {
  namespace Express {
    interface Request {
      userId?: string
      jti?: string
    }
  }
}

/**
 * Extracts a Bearer token from the Authorization header.
 * Returns null without throwing so the caller decides the response.
 */
function extractBearer(authHeader: string | undefined): string | null {
  if (!authHeader) return null
  // Accepts "Bearer <token>" — case-insensitive prefix
  const match = authHeader.match(/^Bearer\s+(\S+)$/i)
  return match ? match[1] : null
}

export async function requireAuth(req: Request, res: Response, next: NextFunction) {
  const token = extractBearer(req.headers.authorization)

  if (!token) {
    return res.status(401).json({ error: 'Token não fornecido', code: 'NO_TOKEN' })
  }

  try {
    const payload = verifyAccessToken(token)

    const revoked = await isTokenBlacklisted(payload.jti)
    if (revoked) {
      return res.status(401).json({ error: 'Token revogado', code: 'TOKEN_REVOKED' })
    }

    req.userId = payload.userId
    req.jti    = payload.jti
    next()
  } catch (err: any) {
    // Distinguish expired from malformed — clients can use this to trigger refresh
    const isExpired = err?.name === 'TokenExpiredError'
    return res.status(401).json({
      error: isExpired ? 'Token expirado' : 'Token inválido',
      code:  isExpired ? 'TOKEN_EXPIRED'  : 'TOKEN_INVALID',
    })
  }
}

/**
 * Optional auth — attaches user if token is valid but does NOT block the request.
 * Useful for public endpoints that change behaviour for logged-in users.
 */
export async function optionalAuth(req: Request, _res: Response, next: NextFunction) {
  const token = extractBearer(req.headers.authorization)
  if (!token) return next()

  try {
    const payload = verifyAccessToken(token)
    const revoked = await isTokenBlacklisted(payload.jti)
    if (!revoked) {
      req.userId = payload.userId
      req.jti    = payload.jti
    }
  } catch {
    // Silently ignore — optional auth never blocks
  }

  next()
}