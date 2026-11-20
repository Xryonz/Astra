import jwt from 'jsonwebtoken'
import crypto from 'crypto'
import { env } from './env'

interface TokenPayload {
  userId: string
  jti: string
  sid?: string
}

interface RefreshPayload {
  userId: string
}

export const VIDA_DO_ACESSO_S = 15 * 60

export function generateAccessToken(userId: string, sessaoId: string): { token: string; jti: string } {
  const jti = crypto.randomUUID()
  const token = jwt.sign({ userId, jti, sid: sessaoId }, env.JWT_ACCESS_SECRET, {
    expiresIn: VIDA_DO_ACESSO_S,
  })
  return { token, jti }
}

export function generateRefreshToken(userId: string): string {
  const jti = crypto.randomUUID()
  return jwt.sign({ userId, jti }, env.JWT_REFRESH_SECRET, {
    expiresIn: '7d',
  })
}

export function verifyAccessToken(token: string): TokenPayload {
  return jwt.verify(token, env.JWT_ACCESS_SECRET, { algorithms: ['HS256'] }) as TokenPayload
}

export function verifyRefreshToken(token: string): RefreshPayload {
  return jwt.verify(token, env.JWT_REFRESH_SECRET, { algorithms: ['HS256'] }) as RefreshPayload
}

export function hashToken(token: string): string {
  return crypto.createHash('sha256').update(token).digest('hex')
}
