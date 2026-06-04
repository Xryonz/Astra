-- ─────────────────────────────────────────────────────────────
-- Schema sync round 14 (deploy fix).
--
-- Várias tabelas + colunas foram adicionadas ao schema.ts via db:push
-- em dev e nunca tiveram migration gerado. Esse arquivo sincroniza
-- production com o schema canônico.
--
-- IF NOT EXISTS / IF EXISTS em tudo → idempotente.
-- ─────────────────────────────────────────────────────────────

-- ───── User: colunas faltantes ─────
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "customStatus"      text;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "notificationPrefs" text;

-- ───── Channel: isPrivate (acl por role) ─────
ALTER TABLE "Channel" ADD COLUMN IF NOT EXISTS "isPrivate" boolean NOT NULL DEFAULT false;

-- ───── Message: poll + expiresAt ─────
ALTER TABLE "Message" ADD COLUMN IF NOT EXISTS "poll"      text;
ALTER TABLE "Message" ADD COLUMN IF NOT EXISTS "expiresAt" timestamp (3);

-- ───── DMConversation: read receipts ─────
ALTER TABLE "DMConversation" ADD COLUMN IF NOT EXISTS "lastReadByA" timestamp (3);
ALTER TABLE "DMConversation" ADD COLUMN IF NOT EXISTS "lastReadByB" timestamp (3);

-- ───── DirectMessage: attachments + reply + expiresAt ─────
ALTER TABLE "DirectMessage" ADD COLUMN IF NOT EXISTS "attachments" text NOT NULL DEFAULT '[]';
ALTER TABLE "DirectMessage" ADD COLUMN IF NOT EXISTS "replyToId"   text;
ALTER TABLE "DirectMessage" ADD COLUMN IF NOT EXISTS "expiresAt"   timestamp (3);

-- ───── Tabelas: roles/perms ─────

CREATE TABLE IF NOT EXISTS "ServerRole" (
  "id"          text PRIMARY KEY NOT NULL,
  "serverId"    text NOT NULL REFERENCES "Server"("id") ON DELETE CASCADE,
  "name"        text NOT NULL,
  "color"       text,
  "position"    integer NOT NULL DEFAULT 0,
  "permissions" text NOT NULL DEFAULT '[]',
  "hoist"       boolean NOT NULL DEFAULT false,
  "createdAt"   timestamp (3) NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "ServerRole_serverId_idx" ON "ServerRole" ("serverId");

CREATE TABLE IF NOT EXISTS "ServerMemberRole" (
  "id"        text PRIMARY KEY NOT NULL,
  "memberId"  text NOT NULL REFERENCES "ServerMember"("id") ON DELETE CASCADE,
  "roleId"    text NOT NULL REFERENCES "ServerRole"("id")   ON DELETE CASCADE,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "ServerMemberRole_memberId_roleId_key" ON "ServerMemberRole" ("memberId", "roleId");
CREATE INDEX IF NOT EXISTS "ServerMemberRole_memberId_idx" ON "ServerMemberRole" ("memberId");
CREATE INDEX IF NOT EXISTS "ServerMemberRole_roleId_idx"   ON "ServerMemberRole" ("roleId");

CREATE TABLE IF NOT EXISTS "ChannelRolePerm" (
  "id"        text PRIMARY KEY NOT NULL,
  "channelId" text NOT NULL REFERENCES "Channel"("id")    ON DELETE CASCADE,
  "roleId"    text NOT NULL REFERENCES "ServerRole"("id") ON DELETE CASCADE,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "ChannelRolePerm_channelId_roleId_key" ON "ChannelRolePerm" ("channelId", "roleId");
CREATE INDEX IF NOT EXISTS "ChannelRolePerm_channelId_idx" ON "ChannelRolePerm" ("channelId");
CREATE INDEX IF NOT EXISTS "ChannelRolePerm_roleId_idx"    ON "ChannelRolePerm" ("roleId");

-- ───── Tabelas: moderação ─────

CREATE TABLE IF NOT EXISTS "ServerBan" (
  "id"         text PRIMARY KEY NOT NULL,
  "serverId"   text NOT NULL REFERENCES "Server"("id") ON DELETE CASCADE,
  "userId"     text NOT NULL REFERENCES "User"("id")   ON DELETE CASCADE,
  "bannedById" text NOT NULL REFERENCES "User"("id"),
  "reason"     text,
  "createdAt"  timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "ServerBan_serverId_userId_key" ON "ServerBan" ("serverId", "userId");
CREATE INDEX IF NOT EXISTS "ServerBan_serverId_idx" ON "ServerBan" ("serverId");

CREATE TABLE IF NOT EXISTS "ServerAuditLog" (
  "id"        text PRIMARY KEY NOT NULL,
  "serverId"  text NOT NULL REFERENCES "Server"("id") ON DELETE CASCADE,
  "actorId"   text NOT NULL REFERENCES "User"("id")   ON DELETE CASCADE,
  "action"    text NOT NULL,
  "targetId"  text,
  "metadata"  text NOT NULL DEFAULT '{}',
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "ServerAuditLog_serverId_createdAt_idx" ON "ServerAuditLog" ("serverId", "createdAt" DESC);

-- ───── Tabelas: leitura + edição ─────

CREATE TABLE IF NOT EXISTS "ChannelRead" (
  "id"         text PRIMARY KEY NOT NULL,
  "userId"     text NOT NULL REFERENCES "User"("id")    ON DELETE CASCADE,
  "channelId"  text NOT NULL REFERENCES "Channel"("id") ON DELETE CASCADE,
  "lastReadAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "ChannelRead_userId_channelId_key" ON "ChannelRead" ("userId", "channelId");
CREATE INDEX IF NOT EXISTS "ChannelRead_userId_idx" ON "ChannelRead" ("userId");

CREATE TABLE IF NOT EXISTS "MessageEdit" (
  "id"        text PRIMARY KEY NOT NULL,
  "messageId" text NOT NULL REFERENCES "Message"("id") ON DELETE CASCADE,
  "content"   text NOT NULL,
  "editedAt"  timestamp (3) NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "MessageEdit_messageId_idx" ON "MessageEdit" ("messageId", "editedAt" DESC);

-- ───── Tabelas: produtividade ─────

CREATE TABLE IF NOT EXISTS "Reminder" (
  "id"           text PRIMARY KEY NOT NULL,
  "creatorId"    text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "targetUserId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "content"      text NOT NULL,
  "channelId"    text,
  "dueAt"        timestamp (3) NOT NULL,
  "deliveredAt"  timestamp (3),
  "createdAt"    timestamp (3) NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "Reminder_dueAt_deliveredAt_idx" ON "Reminder" ("dueAt", "deliveredAt");
CREATE INDEX IF NOT EXISTS "Reminder_targetUserId_idx"      ON "Reminder" ("targetUserId");
CREATE INDEX IF NOT EXISTS "Reminder_creatorId_idx"         ON "Reminder" ("creatorId");

CREATE TABLE IF NOT EXISTS "Bookmark" (
  "id"        text PRIMARY KEY NOT NULL,
  "userId"    text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "targetId"  text NOT NULL,
  "kind"      text NOT NULL,
  "note"      text,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "Bookmark_userId_targetId_kind_key" ON "Bookmark" ("userId", "targetId", "kind");
CREATE INDEX IF NOT EXISTS "Bookmark_userId_createdAt_idx" ON "Bookmark" ("userId", "createdAt" DESC);

CREATE TABLE IF NOT EXISTS "Notification" (
  "id"        text PRIMARY KEY NOT NULL,
  "userId"    text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "type"      text NOT NULL,
  "payload"   text NOT NULL DEFAULT '{}',
  "readAt"    timestamp (3),
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "Notification_userId_createdAt_idx" ON "Notification" ("userId", "createdAt" DESC);
CREATE INDEX IF NOT EXISTS "Notification_userId_readAt_idx"    ON "Notification" ("userId", "readAt");

-- ───── Indexes adicionais (cols criadas acima) ─────
CREATE INDEX IF NOT EXISTS "Message_expiresAt_idx"       ON "Message" ("expiresAt");
CREATE INDEX IF NOT EXISTS "DirectMessage_replyToId_idx" ON "DirectMessage" ("replyToId");
CREATE INDEX IF NOT EXISTS "DirectMessage_expiresAt_idx" ON "DirectMessage" ("expiresAt");
