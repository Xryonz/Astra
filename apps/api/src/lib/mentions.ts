
import { getCachedMembers } from './membersCache'

export async function parseMentions(content: string, serverId: string): Promise<string[]> {
  const matches = content.match(/@([a-z0-9_]+)/gi)
  if (!matches || matches.length === 0) return []
  const wanted = new Set(matches.map((m) => m.slice(1).toLowerCase()))
  const members = await getCachedMembers(serverId)
  return members
    .filter((m) => wanted.has(m.username.toLowerCase()))
    .map((m) => m.userId)
}
