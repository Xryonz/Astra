export interface CursorPayload {
  createdAt: Date
  id: string
}

export function encodeCursor(createdAt: Date, id: string): string {
  return Buffer.from(JSON.stringify({ createdAt: createdAt.toISOString(), id })).toString('base64url')
}

export function parseCursor(cursor?: string): CursorPayload | null {
  if (!cursor) return null
  try {
    const raw = JSON.parse(Buffer.from(cursor, 'base64url').toString('utf8')) as {
      createdAt?: string
      id?: string
    }
    if (!raw.createdAt || !raw.id) return null
    const createdAt = new Date(raw.createdAt)
    if (Number.isNaN(createdAt.getTime())) return null
    return { createdAt, id: raw.id }
  } catch {
    return null
  }
}
