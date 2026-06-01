/**
 * DM core helpers — compartilhado entre routes/dm.ts e routes/friends.ts
 * pra evitar circular import.
 *
 * Regra: par sempre normalizado (id menor primeiro) pra evitar
 * duplicatas do tipo (A,B) vs (B,A).
 */
import { and, eq } from 'drizzle-orm'
import { db } from '../db'
import { dmConversations } from '../db/schema'

export async function getOrCreateConversation(userIdA: string, userIdB: string) {
  const [userAId, userBId] = [userIdA, userIdB].sort()

  const [existing] = await db.select().from(dmConversations)
    .where(and(eq(dmConversations.userAId, userAId), eq(dmConversations.userBId, userBId)))
    .limit(1)
  if (existing) return existing

  // Race-safe: 2 chamadas paralelas → insert do segundo bate no unique.
  // Trata com onConflictDoNothing + select de fallback.
  const inserted = await db.insert(dmConversations)
    .values({ userAId, userBId })
    .onConflictDoNothing({ target: [dmConversations.userAId, dmConversations.userBId] })
    .returning()
  if (inserted[0]) return inserted[0]

  const [winner] = await db.select().from(dmConversations)
    .where(and(eq(dmConversations.userAId, userAId), eq(dmConversations.userBId, userBId)))
    .limit(1)
  return winner
}
