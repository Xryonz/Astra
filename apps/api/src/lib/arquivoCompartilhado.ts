import { and, eq, like, notInArray } from 'drizzle-orm'
import { db } from '../db'
import { directMessages, messages, serverSounds, serverStickers } from '../db/schema'
import { removeAttachment } from './storage'

async function aindaUsado(url: string, excetoMensagens: string[]): Promise<boolean> {
  const padrao = `%${url}%`
  const doCanal = excetoMensagens.length > 0
    ? and(like(messages.attachments, padrao), notInArray(messages.id, excetoMensagens))
    : like(messages.attachments, padrao)
  const [noCanal, noSussurro, figurinha, som] = await Promise.all([
    db.select({ id: messages.id }).from(messages).where(doCanal).limit(1),
    db.select({ id: directMessages.id }).from(directMessages).where(like(directMessages.attachments, padrao)).limit(1),
    db.select({ id: serverStickers.id }).from(serverStickers).where(eq(serverStickers.url, url)).limit(1),
    db.select({ id: serverSounds.id }).from(serverSounds).where(eq(serverSounds.url, url)).limit(1),
  ])
  return noCanal.length + noSussurro.length + figurinha.length + som.length > 0
}

export async function removerSeNinguemUsa(
  url: string | null | undefined,
  excetoMensagens: string[] = [],
): Promise<void> {
  if (!url) return
  if (await aindaUsado(url, excetoMensagens)) return
  await removeAttachment(url)
}
