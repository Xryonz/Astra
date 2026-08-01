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

export async function botPodeFalar(channelId: string): Promise<boolean> {
  const [ch] = await db.select({
    botEnabled: channels.botEnabled,
    categoryId: channels.categoryId,
  }).from(channels).where(eq(channels.id, channelId)).limit(1)

  if (!ch) return false
  if (ch.botEnabled !== null) return ch.botEnabled
  if (!ch.categoryId) return true

  const [cat] = await db.select({ botEnabled: channelCategories.botEnabled })
    .from(channelCategories).where(eq(channelCategories.id, ch.categoryId)).limit(1)
  return cat?.botEnabled ?? true
}
