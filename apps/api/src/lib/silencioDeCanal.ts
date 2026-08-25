import { and, eq } from 'drizzle-orm'
import { db } from './../db'
import { channelNotifPrefs, channels, serverNotifPrefs } from '../db/schema'

export type ModoDeAviso = 'all' | 'mentions' | 'mute'

function modo(cru: string | null | undefined): ModoDeAviso {
  return cru === 'mentions' || cru === 'mute' ? cru : 'all'
}

export async function modoDoCanal(userId: string, channelId: string): Promise<ModoDeAviso> {
  const [doCanal] = await db.select({ mode: channelNotifPrefs.mode }).from(channelNotifPrefs)
    .where(and(eq(channelNotifPrefs.userId, userId), eq(channelNotifPrefs.channelId, channelId)))
    .limit(1)
  if (doCanal) return modo(doCanal.mode)

  const [canal] = await db.select({ serverId: channels.serverId }).from(channels)
    .where(eq(channels.id, channelId))
    .limit(1)
  if (!canal?.serverId) return 'all'

  const [daConstelacao] = await db.select({ mode: serverNotifPrefs.mode }).from(serverNotifPrefs)
    .where(and(eq(serverNotifPrefs.userId, userId), eq(serverNotifPrefs.serverId, canal.serverId)))
    .limit(1)
  return modo(daConstelacao?.mode)
}

export function avisoPassa(m: ModoDeAviso, tipo: string): boolean {
  if (m === 'mute') return false
  if (m === 'mentions') return tipo === 'mention'
  return true
}
