import {
  pgTable, text, boolean, timestamp, integer, pgEnum, uniqueIndex, index,
} from 'drizzle-orm/pg-core'
import { sql } from 'drizzle-orm'
import { createId } from './cuid'

export const roleEnum        = pgEnum('Role',        ['OWNER', 'ADMIN', 'MEMBER'])
export const channelTypeEnum = pgEnum('ChannelType', ['TEXT', 'VOICE'])
export const userStatusEnum  = pgEnum('UserStatus',  ['ONLINE', 'IDLE', 'DND', 'INVISIBLE'])

export const users = pgTable('User', {
  id:           text('id').primaryKey().$defaultFn(createId),
  email:        text('email').notNull().unique(),
  username:     text('username').notNull().unique(),

  coordinate:   text('coordinate').notNull().unique(),
  displayName:  text('displayName').notNull(),
  avatarUrl:    text('avatarUrl'),
  bio:          text('bio'),
  googleId:     text('googleId').unique(),
  passwordHash: text('passwordHash'),
  isBot:        boolean('isBot').notNull().default(false),
  bannerUrl:    text('bannerUrl'),
  bannerColor:  text('bannerColor'),
  profileTheme: text('profileTheme'),

  bannerPositionY: integer('bannerPositionY').notNull().default(50),

  bannerScale:     integer('bannerScale').notNull().default(100),

  bannerBorder:    text('bannerBorder').notNull().default('none'),

  pronouns:     text('pronouns'),

  statusEmoji:  text('statusEmoji'),

  displayFont:  text('displayFont').notNull().default('serif'),

  bannerTextColor: text('bannerTextColor'),
  status:       userStatusEnum('status').notNull().default('ONLINE'),

  customStatus: text('customStatus'),

  notificationPrefs: text('notificationPrefs'),

  preferences:  text('preferences'),

  onboardedAt:  timestamp('onboardedAt', { precision: 3 }),
  createdAt:    timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
  updatedAt:    timestamp('updatedAt', { precision: 3 }).notNull().defaultNow().$onUpdate(() => new Date()),
})

export const profileNotes = pgTable('ProfileNote', {
  id:            text('id').primaryKey().$defaultFn(createId),
  profileUserId: text('profileUserId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  authorId:      text('authorId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  content:       text('content').notNull(),
  pinned:        boolean('pinned').notNull().default(false),
  createdAt:     timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  byProfileTime: index('ProfileNote_profileUserId_createdAt_idx').on(t.profileUserId, t.createdAt),
  uniqAuthor:    uniqueIndex('ProfileNote_profileUserId_authorId_key').on(t.profileUserId, t.authorId),
}))

export const mutedMembers = pgTable('MutedMember', {
  id:        text('id').primaryKey().$defaultFn(createId),
  userId:    text('userId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  serverId:  text('serverId').notNull(),
  mutedById: text('mutedById').notNull(),
  reason:    text('reason').notNull().default('Spam automático'),
  expiresAt: timestamp('expiresAt', { precision: 3 }).notNull(),
  createdAt: timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  uniqUserServer: uniqueIndex('MutedMember_userId_serverId_key').on(t.userId, t.serverId),
  byServer:       index('MutedMember_serverId_idx').on(t.serverId),
  byExpires:      index('MutedMember_expiresAt_idx').on(t.expiresAt),
}))

export const refreshTokens = pgTable('RefreshToken', {
  id:         text('id').primaryKey().$defaultFn(createId),
  token:      text('token').notNull().unique(),
  userId:     text('userId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  expiresAt:  timestamp('expiresAt', { precision: 3 }).notNull(),
  createdAt:  timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
  revokedAt:  timestamp('revokedAt', { precision: 3 }),

  userAgent:  text('userAgent'),

  ip:         text('ip'),

  lastUsedAt: timestamp('lastUsedAt', { precision: 3 }),
}, (t) => ({
  byUser: index('RefreshToken_userId_idx').on(t.userId),
}))

export const servers = pgTable('Server', {
  id:         text('id').primaryKey().$defaultFn(createId),
  name:       text('name').notNull(),
  iconUrl:    text('iconUrl'),

  bannerUrl:  text('bannerUrl'),
  inviteCode: text('inviteCode').notNull().unique().$defaultFn(createId),
  ownerId:    text('ownerId').notNull().references(() => users.id),
  isGroup:    boolean('isGroup').notNull().default(false),

  isPublic:   boolean('isPublic').notNull().default(false),

  description: text('description'),

  messageRetentionDays: integer('messageRetentionDays'),
  createdAt:  timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
  updatedAt:  timestamp('updatedAt', { precision: 3 }).notNull().defaultNow().$onUpdate(() => new Date()),
})

export const serverMembers = pgTable('ServerMember', {
  id:        text('id').primaryKey().$defaultFn(createId),
  userId:    text('userId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  serverId:  text('serverId').notNull().references(() => servers.id, { onDelete: 'cascade' }),
  role:      roleEnum('role').notNull().default('MEMBER'),
  nameColor: text('nameColor'),
  joinedAt:  timestamp('joinedAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  uniqUserServer: uniqueIndex('ServerMember_userId_serverId_key').on(t.userId, t.serverId),
  byServer:       index('ServerMember_serverId_idx').on(t.serverId),
  byUser:         index('ServerMember_userId_idx').on(t.userId),
}))

export const serverEmojis = pgTable('ServerEmoji', {
  id:        text('id').primaryKey().$defaultFn(createId),
  serverId:  text('serverId').notNull().references(() => servers.id, { onDelete: 'cascade' }),
  name:      text('name').notNull(),
  url:       text('url').notNull(),
  createdBy: text('createdBy').notNull(),
  createdAt: timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  uniqName: uniqueIndex('ServerEmoji_serverId_name_key').on(t.serverId, t.name),
  byServer: index('ServerEmoji_serverId_idx').on(t.serverId),
}))

export const roles = pgTable('ServerRole', {
  id:          text('id').primaryKey().$defaultFn(createId),
  serverId:    text('serverId').notNull().references(() => servers.id, { onDelete: 'cascade' }),
  name:        text('name').notNull(),
  color:       text('color'),
  position:    integer('position').notNull().default(0),
  permissions: text('permissions').notNull().default('[]'),
  hoist:       boolean('hoist').notNull().default(false),
  createdAt:   timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  byServer: index('ServerRole_serverId_idx').on(t.serverId),
}))

export const memberRoles = pgTable('ServerMemberRole', {
  id:        text('id').primaryKey().$defaultFn(createId),
  memberId:  text('memberId').notNull().references(() => serverMembers.id, { onDelete: 'cascade' }),
  roleId:    text('roleId').notNull().references(() => roles.id, { onDelete: 'cascade' }),
  createdAt: timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  uniqMemberRole: uniqueIndex('ServerMemberRole_memberId_roleId_key').on(t.memberId, t.roleId),
  byMember:       index('ServerMemberRole_memberId_idx').on(t.memberId),
  byRole:         index('ServerMemberRole_roleId_idx').on(t.roleId),
}))

export const serverBans = pgTable('ServerBan', {
  id:          text('id').primaryKey().$defaultFn(createId),
  serverId:    text('serverId').notNull().references(() => servers.id, { onDelete: 'cascade' }),
  userId:      text('userId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  bannedById:  text('bannedById').notNull().references(() => users.id),
  reason:      text('reason'),
  createdAt:   timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  uniqServerUser: uniqueIndex('ServerBan_serverId_userId_key').on(t.serverId, t.userId),
  byServer:       index('ServerBan_serverId_idx').on(t.serverId),
}))

export const auditLogs = pgTable('ServerAuditLog', {
  id:        text('id').primaryKey().$defaultFn(createId),
  serverId:  text('serverId').notNull().references(() => servers.id, { onDelete: 'cascade' }),
  actorId:   text('actorId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  action:    text('action').notNull(),
  targetId:  text('targetId'),
  metadata:  text('metadata').notNull().default('{}'),
  createdAt: timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  byServerCreated: index('ServerAuditLog_serverId_createdAt_idx').on(t.serverId, t.createdAt.desc()),
}))

export const channelCategories = pgTable('ChannelCategory', {
  id:        text('id').primaryKey().$defaultFn(createId),
  name:      text('name').notNull(),
  serverId:  text('serverId').notNull().references(() => servers.id, { onDelete: 'cascade' }),
  position:  integer('position').notNull().default(0),
  createdAt: timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  byServer: index('ChannelCategory_serverId_idx').on(t.serverId),
}))

export const channels = pgTable('Channel', {
  id:         text('id').primaryKey().$defaultFn(createId),
  name:       text('name').notNull(),
  type:       channelTypeEnum('type').notNull().default('TEXT'),
  serverId:   text('serverId').notNull().references(() => servers.id, { onDelete: 'cascade' }),
  categoryId: text('categoryId').references(() => channelCategories.id, { onDelete: 'set null' }),
  position:   integer('position').notNull().default(0),
  isPrivate:  boolean('isPrivate').notNull().default(false),
  createdAt:  timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  byServer:   index('Channel_serverId_idx').on(t.serverId),
  byCategory: index('Channel_categoryId_idx').on(t.categoryId),
}))

export const channelRolePerms = pgTable('ChannelRolePerm', {
  id:        text('id').primaryKey().$defaultFn(createId),
  channelId: text('channelId').notNull().references(() => channels.id, { onDelete: 'cascade' }),
  roleId:    text('roleId').notNull().references(() => roles.id, { onDelete: 'cascade' }),
  createdAt: timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  uniqChannelRole: uniqueIndex('ChannelRolePerm_channelId_roleId_key').on(t.channelId, t.roleId),
  byChannel:       index('ChannelRolePerm_channelId_idx').on(t.channelId),
  byRole:          index('ChannelRolePerm_roleId_idx').on(t.roleId),
}))

export const threads = pgTable('Thread', {
  id:              text('id').primaryKey().$defaultFn(createId),
  channelId:       text('channelId').notNull().references(() => channels.id, { onDelete: 'cascade' }),
  parentMessageId: text('parentMessageId').notNull(),
  name:            text('name').notNull(),
  createdById:     text('createdById').notNull().references(() => users.id),
  createdAt:       timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
  updatedAt:       timestamp('updatedAt', { precision: 3 }).notNull().defaultNow().$onUpdate(() => new Date()),
}, (t) => ({
  byChannel: index('Thread_channelId_idx').on(t.channelId),
  byParent:  index('Thread_parentMessageId_idx').on(t.parentMessageId),
}))

export const messages = pgTable('Message', {
  id:          text('id').primaryKey().$defaultFn(createId),
  content:     text('content').notNull(),
  authorId:    text('authorId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  channelId:   text('channelId').notNull().references(() => channels.id, { onDelete: 'cascade' }),
  threadId:    text('threadId'),
  replyToId:   text('replyToId'),
  authorColor: text('authorColor'),

  attachments: text('attachments').notNull().default('[]'),
  mentions:    text('mentions').notNull().default(''),
  edited:      boolean('edited').notNull().default(false),
  pinned:      boolean('pinned').notNull().default(false),

  poll:        text('poll'),

  expiresAt:   timestamp('expiresAt', { precision: 3 }),
  deletedAt:   timestamp('deletedAt', { precision: 3 }),
  createdAt:   timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
  updatedAt:   timestamp('updatedAt', { precision: 3 }).notNull().defaultNow().$onUpdate(() => new Date()),
}, (t) => ({
  byChannelCreated: index('Message_channelId_createdAt_idx').on(t.channelId, t.createdAt.desc()),
  byAuthor:         index('Message_authorId_idx').on(t.authorId),
  byChannelPinned:  index('Message_channelId_pinned_idx').on(t.channelId, t.pinned),
  byReplyTo:        index('Message_replyToId_idx').on(t.replyToId),
  byThread:         index('Message_threadId_idx').on(t.threadId),
  byExpires:        index('Message_expiresAt_idx').on(t.expiresAt),
}))

export const channelReads = pgTable('ChannelRead', {
  id:         text('id').primaryKey().$defaultFn(createId),
  userId:     text('userId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  channelId:  text('channelId').notNull().references(() => channels.id, { onDelete: 'cascade' }),
  lastReadAt: timestamp('lastReadAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  uniqUserChannel: uniqueIndex('ChannelRead_userId_channelId_key').on(t.userId, t.channelId),
  byUser:          index('ChannelRead_userId_idx').on(t.userId),
}))

export const channelNotifPrefs = pgTable('ChannelNotifPref', {
  id:        text('id').primaryKey().$defaultFn(createId),
  userId:    text('userId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  channelId: text('channelId').notNull().references(() => channels.id, { onDelete: 'cascade' }),

  mode:      text('mode').notNull().default('all'),
  updatedAt: timestamp('updatedAt', { precision: 3 }).notNull().defaultNow().$onUpdate(() => new Date()),
}, (t) => ({
  uniqUserChannel: uniqueIndex('ChannelNotifPref_userId_channelId_key').on(t.userId, t.channelId),
}))

export const messageEdits = pgTable('MessageEdit', {
  id:        text('id').primaryKey().$defaultFn(createId),
  messageId: text('messageId').notNull().references(() => messages.id, { onDelete: 'cascade' }),
  content:   text('content').notNull(),
  editedAt:  timestamp('editedAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  byMessage: index('MessageEdit_messageId_idx').on(t.messageId, t.editedAt.desc()),
}))

export const messageReactions = pgTable('MessageReaction', {
  id:        text('id').primaryKey().$defaultFn(createId),
  messageId: text('messageId').notNull().references(() => messages.id, { onDelete: 'cascade' }),
  userId:    text('userId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  emoji:     text('emoji').notNull(),
  createdAt: timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  uniq:      uniqueIndex('MessageReaction_messageId_userId_emoji_key').on(t.messageId, t.userId, t.emoji),
  byMessage: index('MessageReaction_messageId_idx').on(t.messageId),
}))

export const dmConversations = pgTable('DMConversation', {
  id:           text('id').primaryKey().$defaultFn(createId),
  userAId:      text('userAId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  userBId:      text('userBId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  lastReadByA:  timestamp('lastReadByA', { precision: 3 }),
  lastReadByB:  timestamp('lastReadByB', { precision: 3 }),
  createdAt:    timestamp('createdAt',   { precision: 3 }).notNull().defaultNow(),
  updatedAt:    timestamp('updatedAt',   { precision: 3 }).notNull().defaultNow().$onUpdate(() => new Date()),
}, (t) => ({
  uniqPair: uniqueIndex('DMConversation_userAId_userBId_key').on(t.userAId, t.userBId),

  byAUpdated: index('DMConversation_userAId_updatedAt_idx').on(t.userAId, t.updatedAt.desc()),
  byBUpdated: index('DMConversation_userBId_updatedAt_idx').on(t.userBId, t.updatedAt.desc()),
}))

export const directMessages = pgTable('DirectMessage', {
  id:             text('id').primaryKey().$defaultFn(createId),
  content:        text('content').notNull(),
  senderId:       text('senderId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  receiverId:     text('receiverId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  conversationId: text('conversationId').notNull().references(() => dmConversations.id, { onDelete: 'cascade' }),

  attachments:    text('attachments').notNull().default('[]'),

  replyToId:      text('replyToId'),

  expiresAt:      timestamp('expiresAt', { precision: 3 }),
  edited:         boolean('edited').notNull().default(false),
  deletedAt:      timestamp('deletedAt', { precision: 3 }),
  createdAt:      timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  byConversation: index('DirectMessage_conversationId_createdAt_idx').on(t.conversationId, t.createdAt.desc()),
  bySender:       index('DirectMessage_senderId_idx').on(t.senderId),
  byReplyTo:      index('DirectMessage_replyToId_idx').on(t.replyToId),
  byExpires:      index('DirectMessage_expiresAt_idx').on(t.expiresAt),
}))

export const pushSubscriptions = pgTable('PushSubscription', {
  id:        text('id').primaryKey().$defaultFn(createId),
  userId:    text('userId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  endpoint:  text('endpoint').notNull(),
  p256dh:    text('p256dh').notNull(),
  auth:      text('auth').notNull(),
  userAgent: text('userAgent'),
  createdAt: timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  uniqEndpoint: uniqueIndex('PushSubscription_endpoint_key').on(t.endpoint),
  byUser:       index('PushSubscription_userId_idx').on(t.userId),
}))

export const fcmTokens = pgTable('FcmToken', {
  id:         text('id').primaryKey().$defaultFn(createId),
  userId:     text('userId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  token:      text('token').notNull(),
  platform:   text('platform').notNull().default('android'),
  createdAt:  timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
  lastSeenAt: timestamp('lastSeenAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  uniqToken: uniqueIndex('FcmToken_token_key').on(t.token),
  byUser:    index('FcmToken_userId_idx').on(t.userId),
}))

export const friendships = pgTable('Friendship', {
  id:          text('id').primaryKey().$defaultFn(createId),
  userAId:     text('userAId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  userBId:     text('userBId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  requesterId: text('requesterId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  status:      text('status').notNull().default('pending'),
  acceptedAt:  timestamp('acceptedAt', { precision: 3 }),
  createdAt:   timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  uniqPair: uniqueIndex('Friendship_userAId_userBId_key').on(t.userAId, t.userBId),

  byAStatus: index('Friendship_userAId_status_idx').on(t.userAId, t.status),
  byBStatus: index('Friendship_userBId_status_idx').on(t.userBId, t.status),
}))

export const reminders = pgTable('Reminder', {
  id:           text('id').primaryKey().$defaultFn(createId),
  creatorId:    text('creatorId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  targetUserId: text('targetUserId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  content:      text('content').notNull(),
  channelId:    text('channelId'),
  dueAt:        timestamp('dueAt', { precision: 3 }).notNull(),
  deliveredAt:  timestamp('deliveredAt', { precision: 3 }),
  createdAt:    timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  byDuePending: index('Reminder_dueAt_deliveredAt_idx').on(t.dueAt, t.deliveredAt),
  byTarget:     index('Reminder_targetUserId_idx').on(t.targetUserId),
  byCreator:    index('Reminder_creatorId_idx').on(t.creatorId),
}))

export const bookmarks = pgTable('Bookmark', {
  id:        text('id').primaryKey().$defaultFn(createId),
  userId:    text('userId').notNull().references(() => users.id, { onDelete: 'cascade' }),

  targetId:  text('targetId').notNull(),
  kind:      text('kind').notNull(),
  note:      text('note'),
  createdAt: timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  uniqUserTarget:  uniqueIndex('Bookmark_userId_targetId_kind_key').on(t.userId, t.targetId, t.kind),
  byUserCreated:   index('Bookmark_userId_createdAt_idx').on(t.userId, t.createdAt.desc()),
}))

export const notifications = pgTable('Notification', {
  id:        text('id').primaryKey().$defaultFn(createId),
  userId:    text('userId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  type:      text('type').notNull(),
  payload:   text('payload').notNull().default('{}'),
  readAt:    timestamp('readAt', { precision: 3 }),
  createdAt: timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  byUserCreated: index('Notification_userId_createdAt_idx').on(t.userId, t.createdAt.desc()),
  byUserUnread:  index('Notification_userId_readAt_idx').on(t.userId, t.readAt),
}))

export const wishingStars = pgTable('WishingStar', {
  id:        text('id').primaryKey().$defaultFn(createId),
  userId:    text('userId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  content:   text('content').notNull(),
  createdAt: timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  byCreated: index('WishingStar_createdAt_idx').on(t.createdAt.desc()),
  byUser:    index('WishingStar_userId_idx').on(t.userId),
}))

export const badges = pgTable('Badge', {
  id:          text('id').primaryKey().$defaultFn(createId),
  serverId:    text('serverId').notNull().references(() => servers.id, { onDelete: 'cascade' }),
  name:        text('name').notNull(),
  icon:        text('icon').notNull(),
  color:       text('color'),
  description: text('description'),
  createdAt:   timestamp('createdAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  byServer: index('Badge_serverId_idx').on(t.serverId),
}))

export const badgeGrants = pgTable('BadgeGrant', {
  id:        text('id').primaryKey().$defaultFn(createId),
  badgeId:   text('badgeId').notNull().references(() => badges.id, { onDelete: 'cascade' }),
  userId:    text('userId').notNull().references(() => users.id, { onDelete: 'cascade' }),
  grantedBy: text('grantedBy'),
  grantedAt: timestamp('grantedAt', { precision: 3 }).notNull().defaultNow(),
}, (t) => ({
  uniq:   uniqueIndex('BadgeGrant_badge_user_uq').on(t.badgeId, t.userId),
  byUser: index('BadgeGrant_userId_idx').on(t.userId),
}))

export const _sqlMarker = sql`1`
