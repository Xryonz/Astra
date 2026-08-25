import { eq } from 'drizzle-orm'
import { db } from '../db'
import { users } from '../db/schema'
import { env } from './env'

const DONOS: ReadonlySet<string> = new Set(
  (env.ASTRA_OWNER_USERNAMES ?? '')
    .split(',')
    .map((s) => s.trim().toLowerCase())
    .filter(Boolean),
)

export async function ehDonoDoAstra(userId: string | undefined): Promise<boolean> {
  if (!userId || DONOS.size === 0) return false
  const [u] = await db.select({ username: users.username })
    .from(users).where(eq(users.id, userId)).limit(1)
  return !!u && DONOS.has(u.username.toLowerCase())
}
