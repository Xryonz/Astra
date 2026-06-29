import { describe, it, expect } from 'vitest'
import jwt from 'jsonwebtoken'
import {
  generateAccessToken, generateRefreshToken,
  verifyAccessToken, verifyRefreshToken, hashToken,
} from './jwt'

describe('access token', () => {
  it('sign + verify roundtrip', () => {
    const { token, jti } = generateAccessToken('user-123')
    const decoded = verifyAccessToken(token)
    expect(decoded.userId).toBe('user-123')
    expect(decoded.jti).toBe(jti)
  })

  it('jti é único entre chamadas (mesmo userId no mesmo ms)', () => {
    const a = generateAccessToken('u1').jti
    const b = generateAccessToken('u1').jti
    expect(a).not.toBe(b)
  })

  it('verify rejeita token assinado com outro secret', () => {
    const evil = jwt.sign({ userId: 'attacker', jti: 'x' }, 'wrong-secret-32-chars-aaaaaaaaaaaa')
    expect(() => verifyAccessToken(evil)).toThrow()
  })

  it('verify rejeita garbage', () => {
    expect(() => verifyAccessToken('not-a-jwt')).toThrow()
  })
})

describe('refresh token', () => {
  it('sign + verify roundtrip', () => {
    const token = generateRefreshToken('user-xyz')
    const decoded = verifyRefreshToken(token)
    expect(decoded.userId).toBe('user-xyz')
  })

  it('dois refresh no mesmo segundo geram tokens DIFERENTES (jti aleatório)', () => {

    const a = generateRefreshToken('same-user')
    const b = generateRefreshToken('same-user')
    expect(a).not.toBe(b)
    expect(hashToken(a)).not.toBe(hashToken(b))
  })

  it('refresh assinado com access secret é rejeitado', () => {
    const { token } = generateAccessToken('user-abc')
    expect(() => verifyRefreshToken(token)).toThrow()
  })
})

describe('hashToken', () => {
  it('determinístico — mesmo input → mesmo hash', () => {
    expect(hashToken('foo')).toBe(hashToken('foo'))
  })

  it('inputs diferentes → hashes diferentes', () => {
    expect(hashToken('foo')).not.toBe(hashToken('bar'))
  })

  it('comprimento sha256 = 64 hex chars', () => {
    expect(hashToken('x')).toMatch(/^[0-9a-f]{64}$/)
  })
})
