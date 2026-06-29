import crypto from 'crypto'

export function createId(): string {
  return 'c' + crypto.randomBytes(15).toString('base64url').replace(/[-_]/g, '').slice(0, 24)
}
