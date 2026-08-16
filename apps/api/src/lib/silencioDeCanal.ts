import { and, eq } from 'drizzle-orm'
import { db } from './../db'
import { channelNotifPrefs, channels, serverNotifPrefs } from '../db/schema'

// SILENCIAR UMA ÓRBITA (ou uma constelação inteira).
//
// As tabelas `ChannelNotifPref` e `ServerNotifPref` existiam, tinham rota e até
// cliente no `shared` — e **ninguém lia**. Dava pra silenciar um canal, o valor
// ia pro banco, e a notificação chegava do mesmo jeito. Era um interruptor
// desligado de fábrica: parecia funcionar e não fazia nada.
//
// Este arquivo é o lado que faltava. A resolução é em CASCATA, e a ordem importa:
//
//   pref do CANAL  >  pref da CONSTELAÇÃO  >  'all'
//
// O canal vence o servidor porque é a escolha mais específica: quem silenciou a
// constelação inteira mas reativou uma órbita disse exatamente isso, e devolver o
// silêncio ali seria ignorar a segunda frase por causa da primeira.
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

// `mentions` deixa passar SÓ menção. Resposta à sua mensagem não passa, e isso é
// escolha: quem pediu "só quando me chamarem" está dizendo que responder no meio
// da conversa não é ser chamado. Quem quiser tudo tem o modo 'all' logo acima.
export function avisoPassa(m: ModoDeAviso, tipo: string): boolean {
  if (m === 'mute') return false
  if (m === 'mentions') return tipo === 'mention'
  return true
}
