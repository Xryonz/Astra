import { and, eq, or } from 'drizzle-orm'
import { db } from './../db'
import { userBlocks } from '../db/schema'

export async function haBloqueio(a: string, b: string): Promise<boolean> {
  if (a === b) return false
  const [linha] = await db.select({ id: userBlocks.id }).from(userBlocks)
    .where(or(
      and(eq(userBlocks.blockerId, a), eq(userBlocks.blockedId, b)),
      and(eq(userBlocks.blockerId, b), eq(userBlocks.blockedId, a)),
    ))
    .limit(1)
  return !!linha
}
