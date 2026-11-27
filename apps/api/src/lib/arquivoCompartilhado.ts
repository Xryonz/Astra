import { and, eq, like, notInArray, or } from 'drizzle-orm'
import { db } from '../db'
import {
  botPersonas,
  directMessages,
  messages,
  roles,
  serverEmojis,
  serverSounds,
  serverStickers,
  servers,
  users,
} from '../db/schema'
import { removeAttachment } from './storage'

async function aindaUsado(url: string, excetoMensagens: string[]): Promise<boolean> {
  const padrao = `%${url}%`
  const naMensagem = or(like(messages.attachments, padrao), eq(messages.authorAvatarUrl, url))
  const doCanal = excetoMensagens.length > 0 ? and(naMensagem, notInArray(messages.id, excetoMensagens)) : naMensagem
  const achados = await Promise.all([
    db.select({ id: messages.id }).from(messages).where(doCanal).limit(1),
    db.select({ id: directMessages.id }).from(directMessages).where(like(directMessages.attachments, padrao)).limit(1),
    db.select({ id: serverStickers.id }).from(serverStickers).where(eq(serverStickers.url, url)).limit(1),
    db.select({ id: serverSounds.id }).from(serverSounds).where(eq(serverSounds.url, url)).limit(1),
    db.select({ id: serverEmojis.id }).from(serverEmojis).where(eq(serverEmojis.url, url)).limit(1),
    db.select({ id: roles.id }).from(roles).where(eq(roles.iconUrl, url)).limit(1),
    db
      .select({ id: users.id })
      .from(users)
      .where(
        or(
          eq(users.avatarUrl, url),
          eq(users.avatarFullUrl, url),
          eq(users.bannerUrl, url),
          eq(users.bannerFullUrl, url),
        ),
      )
      .limit(1),
    db
      .select({ id: servers.id })
      .from(servers)
      .where(
        or(
          eq(servers.iconUrl, url),
          eq(servers.iconFullUrl, url),
          eq(servers.bannerUrl, url),
          eq(servers.bannerFullUrl, url),
        ),
      )
      .limit(1),
    db
      .select({ chave: botPersonas.chave })
      .from(botPersonas)
      .where(or(eq(botPersonas.avatarUrl, url), eq(botPersonas.bannerUrl, url)))
      .limit(1),
  ])
  return achados.some((linhas) => linhas.length > 0)
}

export async function removerSeNinguemUsa(
  url: string | null | undefined,
  excetoMensagens: string[] = [],
): Promise<void> {
  if (!url) return
  if (await aindaUsado(url, excetoMensagens)) return
  await removeAttachment(url)
}

export async function removerOsSemUso(urls: Array<string | null | undefined>): Promise<void> {
  for (const url of new Set(urls)) await removerSeNinguemUsa(url)
}

export async function soltarFotosAntigas(
  autorId: string,
  fotoDasMensagens: string | null,
  arquivos: Array<string | null | undefined>,
): Promise<void> {
  if (fotoDasMensagens) {
    await db
      .update(messages)
      .set({ authorAvatarUrl: null })
      .where(and(eq(messages.authorId, autorId), eq(messages.authorAvatarUrl, fotoDasMensagens)))
  }
  await removerOsSemUso(arquivos)
}
