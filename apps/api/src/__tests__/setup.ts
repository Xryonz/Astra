
import { vi } from 'vitest'

process.env.NODE_ENV               = process.env.NODE_ENV               || 'test'
process.env.PORT                   = process.env.PORT                   || '3001'
process.env.DATABASE_URL           = process.env.DATABASE_URL           || 'postgresql://test:test@localhost/test'
process.env.JWT_ACCESS_SECRET      = process.env.JWT_ACCESS_SECRET      || 'test-jwt-access-secret-32-chars-aaaa'
process.env.JWT_REFRESH_SECRET     = process.env.JWT_REFRESH_SECRET     || 'test-jwt-refresh-secret-32-chars-bbb'
process.env.CLIENT_URL             = process.env.CLIENT_URL             || 'http://localhost:5173'
process.env.REDIS_URL              = process.env.REDIS_URL              || 'redis://localhost:6379'
process.env.GOOGLE_CLIENT_ID       = process.env.GOOGLE_CLIENT_ID       || 'test-google-id'
process.env.GOOGLE_CLIENT_SECRET   = process.env.GOOGLE_CLIENT_SECRET   || 'test-google-secret'

vi.mock('../lib/redis', async () => {
  const { default: RedisMock } = await import('ioredis-mock')
  const mock = new RedisMock()
  return {
    redis: mock,
    isTokenBlacklisted:  async () => false,
    blacklistToken:      async () => {},
    setUserOnline:       async () => {},
    setUserOffline:      async () => {},
    refreshPresence:     async () => {},
  }
})
