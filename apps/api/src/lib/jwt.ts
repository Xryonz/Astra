import jwt from 'jsonwebtoken'
import crypto from 'crypto'
import { env } from './env'

interface TokenPayload {
  userId: string
  jti: string
}

interface RefreshPayload {
  userId: string
}

export function generateAccessToken(userId: string): { token: string; jti: string } {
  const jti = crypto.randomUUID()
  const token = jwt.sign({ userId, jti }, env.JWT_ACCESS_SECRET, {
    expiresIn: '15m',
  })
  return { token, jti }
}

export function generateRefreshToken(userId: string): string {
  // jti garante que cada token é único mesmo se gerado no mesmo segundo
  const jti = crypto.randomUUID()
  return jwt.sign({ userId, jti }, env.JWT_REFRESH_SECRET, {
    expiresIn: '7d',
  })
}

export function verifyAccessToken(token: string): TokenPayload {
  return jwt.verify(token, env.JWT_ACCESS_SECRET) as TokenPayload
}

export function verifyRefreshToken(token: string): RefreshPayload {
  return jwt.verify(token, env.JWT_REFRESH_SECRET) as RefreshPayload
}

// Hash do refresh token antes de armazenar no banco
// Nunca armazene tokens raw em banco de dados
export function hashToken(token: string): string {
  return crypto.createHash('sha256').update(token).digest('hex')
}
