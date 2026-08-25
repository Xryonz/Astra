import { eq, or } from 'drizzle-orm'
import { db } from './../db'
import {
  bookmarks, channelNotifPrefs, channelReads, fcmTokens, friendships,
  mutedMembers, notifications, profileNotes, pushSubscriptions, refreshTokens, reminders,
  serverMembers, serverNotifPrefs, servers, userBlocks, users,
} from '../db/schema'

export async function constelacoesQueImpedem(userId: string) {
  return db.select({ id: servers.id, name: servers.name }).from(servers)
    .where(eq(servers.ownerId, userId))
}

export const NOME_DA_LAPIDE = 'conta apagada'

export async function virarLapide(userId: string): Promise<void> {
  const marca = userId.slice(-12).toLowerCase()

  await db.update(users).set({
    email:        `apagada+${marca}@astra.invalid`,
    username:     `apagada_${marca}`,
    displayName:  NOME_DA_LAPIDE,
    passwordHash: null,
    googleId:     null,
    avatarUrl:    null,
    bannerUrl:    null,
    bannerColor:  null,
    profileTheme: null,
    bannerTextColor: null,
    bio:          null,
    pronouns:     null,
    statusEmoji:  null,
    customStatus: null,
    status:       'INVISIBLE',
    deletedAt:    new Date(),
  }).where(eq(users.id, userId))

  await db.delete(refreshTokens).where(eq(refreshTokens.userId, userId))
  await db.delete(pushSubscriptions).where(eq(pushSubscriptions.userId, userId))
  await db.delete(fcmTokens).where(eq(fcmTokens.userId, userId))
  await db.delete(serverMembers).where(eq(serverMembers.userId, userId))
  await db.delete(channelNotifPrefs).where(eq(channelNotifPrefs.userId, userId))
  await db.delete(serverNotifPrefs).where(eq(serverNotifPrefs.userId, userId))
  await db.delete(channelReads).where(eq(channelReads.userId, userId))
  await db.delete(bookmarks).where(eq(bookmarks.userId, userId))
  await db.delete(reminders).where(
    or(eq(reminders.creatorId, userId), eq(reminders.targetUserId, userId)),
  )
  await db.delete(notifications).where(eq(notifications.userId, userId))
  await db.delete(profileNotes).where(eq(profileNotes.authorId, userId))
  await db.delete(mutedMembers).where(eq(mutedMembers.userId, userId))
  await db.delete(friendships).where(
    or(eq(friendships.userAId, userId), eq(friendships.userBId, userId)),
  )
  await db.delete(userBlocks).where(
    or(eq(userBlocks.blockerId, userId), eq(userBlocks.blockedId, userId)),
  )
}
