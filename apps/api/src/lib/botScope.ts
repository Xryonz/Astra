import { eq } from 'drizzle-orm'
import { db } from '../db'
import { channels, channelCategories } from '../db/schema'

export type RegraDaBot = { fala: boolean; guarda: boolean }

export async function botNaOrbita(channelId: string): Promise<RegraDaBot> {
  const [ch] = await db.select({
    botEnabled:     channels.botEnabled,
    botKeepReplies: channels.botKeepReplies,
    categoryId:     channels.categoryId,
  }).from(channels).where(eq(channels.id, channelId)).limit(1)

  if (!ch) return { fala: false, guarda: false }
  const guarda = ch.botKeepReplies

  if (ch.botEnabled !== null) return { fala: ch.botEnabled, guarda }
  if (!ch.categoryId) return { fala: true, guarda }

  const [cat] = await db.select({ botEnabled: channelCategories.botEnabled })
    .from(channelCategories).where(eq(channelCategories.id, ch.categoryId)).limit(1)
  return { fala: cat?.botEnabled ?? true, guarda }
}
