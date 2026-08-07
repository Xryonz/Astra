import { eq } from 'drizzle-orm'
import { db } from '../db'
import { channels, channelCategories } from '../db/schema'

// Onde a bot pode falar.
//
// A regra (decisao do dono): LIGADA em tudo por padrao; quem quiser silencio
// DESLIGA. O contrario — nascer desligada — parece mais discreto e na pratica faz
// a bot "sumir": ninguem acha, e todo mundo acha que quebrou.
//
// Heranca em tres niveis, do mais especifico pro mais geral:
//   orbita.botEnabled   (nulo = nao decidi)
//     -> categoria.botEnabled (nulo = nao decidi)
//       -> ligada
//
// Por isso as duas colunas sao NULAVEIS: sem o "nao decidi" nao daria pra
// desligar uma categoria inteira e ainda assim reativar UMA orbita dentro dela.

// `guarda` sai daqui junto e nao de uma consulta propria: e a MESMA linha do
// Channel que ja foi buscada. Duas idas ao banco pra ler duas colunas vizinhas
// seria pagar latencia (Neon) por nada.
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
