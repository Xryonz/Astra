import { and, eq, isNull, like, or, sql } from 'drizzle-orm'
import { db } from './../db'
import { sessaoDaLinha } from './sessoes'
import {
  channelNotifPrefs,
  channelReads,
  fcmTokens,
  friendships,
  messages,
  mutedMembers,
  notifications,
  refreshTokens,
  reminders,
  serverMembers,
  serverNotifPrefs,
  servers,
  userBlocks,
  users,
} from '../db/schema'

export async function constelacoesQueImpedem(userId: string) {
  return db.select({ id: servers.id, name: servers.name }).from(servers).where(eq(servers.ownerId, userId))
}

export const NOME_DA_LAPIDE = 'conta apagada'

const LAPIDE_NO_AVISO = JSON.stringify({ authorName: NOME_DA_LAPIDE, authorUsername: null, authorAvatar: null })

function aindaComASenha(hashConferido: string | null) {
  return hashConferido === null ? isNull(users.passwordHash) : eq(users.passwordHash, hashConferido)
}

export async function virarLapide(userId: string, hashConferido: string | null): Promise<string[] | null> {
  const marca = userId.slice(-12).toLowerCase()

  const [lapide] = await db
    .update(users)
    .set({
      email: `apagada+${marca}@astra.invalid`,
      username: `apagada_${marca}`,
      displayName: NOME_DA_LAPIDE,
      passwordHash: null,
      googleId: null,
      avatarUrl: null,
      avatarFullUrl: null,
      bannerUrl: null,
      bannerFullUrl: null,
      bannerColor: null,
      profileTheme: null,
      bannerTextColor: null,
      bio: null,
      pronouns: null,
      statusEmoji: null,
      customStatus: null,
      notificationPrefs: null,
      preferences: null,
      emailCode: null,
      emailCodeExpiresAt: null,
      pendingEmail: null,
      pendingEmailCode: null,
      pendingEmailExpiresAt: null,
      status: 'INVISIBLE',
      deletedAt: new Date(),
    })
    .where(and(eq(users.id, userId), aindaComASenha(hashConferido)))
    .returning({ id: users.id })
  if (!lapide) return null

  await db.update(messages).set({ authorName: null, authorAvatarUrl: null }).where(eq(messages.authorId, userId))
  await db
    .update(notifications)
    .set({ payload: sql`(${notifications.payload}::jsonb || ${LAPIDE_NO_AVISO}::jsonb)::text` })
    .where(like(notifications.payload, `%"authorId":"${userId}"%`))

  const apagadas = await db
    .delete(refreshTokens)
    .where(eq(refreshTokens.userId, userId))
    .returning({ id: refreshTokens.id, sessionId: refreshTokens.sessionId, revokedAt: refreshTokens.revokedAt })
  await db.delete(fcmTokens).where(eq(fcmTokens.userId, userId))
  await db.delete(serverMembers).where(eq(serverMembers.userId, userId))
  await db.delete(channelNotifPrefs).where(eq(channelNotifPrefs.userId, userId))
  await db.delete(serverNotifPrefs).where(eq(serverNotifPrefs.userId, userId))
  await db.delete(channelReads).where(eq(channelReads.userId, userId))
  await db.delete(reminders).where(or(eq(reminders.creatorId, userId), eq(reminders.targetUserId, userId)))
  await db.delete(notifications).where(eq(notifications.userId, userId))
  await db.delete(mutedMembers).where(eq(mutedMembers.userId, userId))
  await db.delete(friendships).where(or(eq(friendships.userAId, userId), eq(friendships.userBId, userId)))
  await db.delete(userBlocks).where(or(eq(userBlocks.blockerId, userId), eq(userBlocks.blockedId, userId)))
  return [...new Set(apagadas.filter((linha) => linha.revokedAt === null).map(sessaoDaLinha))]
}
