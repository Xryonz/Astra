// FONTE ÚNICA de DDL idempotente do Astra. Rodada AUTOMATICAMENTE no boot
// (ensureSchema) E disponível pro script manual (manualMigrate). Existe porque o
// Render NÃO roda migration no deploy: colunas/tabelas que entram no schema.ts
// mas nunca viram migration aplicada deixavam o Neon defasado e a operação
// correspondente dava 500 "Erro interno" (foi assim com ChannelCategory, e de
// novo com o sistema de cargos: getMemberPerms faz join em ServerRole/
// ServerMemberRole — sem as tabelas, expulsar/checar permissão estoura).
//
// Regras: TUDO IF NOT EXISTS / IF EXISTS / duplicate_object -> no-op. Rodar isto
// N vezes é seguro e barato (Postgres pula o que já existe). Ordem: tabelas-base
// (User/Server/ServerMember/Channel/Message/DirectMessage/DMConversation) já vêm
// do migration 0000; aqui só garantimos o que veio depois. DDL espelha o estilo
// do Drizzle (nomes de constraint/index) pra um futuro db:push não ver drift.
export const GUARD_DDL = `
-- ===== Sistema de cargos (ServerRole / ServerMemberRole) =====
CREATE TABLE IF NOT EXISTS "ServerRole" (
  "id" text PRIMARY KEY NOT NULL,
  "serverId" text NOT NULL REFERENCES "Server"("id") ON DELETE CASCADE,
  "name" text NOT NULL,
  "color" text,
  "iconUrl" text,
  "position" integer NOT NULL DEFAULT 0,
  "permissions" text NOT NULL DEFAULT '[]',
  "hoist" boolean NOT NULL DEFAULT false,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "ServerRole_serverId_idx" ON "ServerRole" USING btree ("serverId");
ALTER TABLE "ServerRole" ADD COLUMN IF NOT EXISTS "iconUrl" text;

CREATE TABLE IF NOT EXISTS "ServerMemberRole" (
  "id" text PRIMARY KEY NOT NULL,
  "memberId" text NOT NULL REFERENCES "ServerMember"("id") ON DELETE CASCADE,
  "roleId" text NOT NULL REFERENCES "ServerRole"("id") ON DELETE CASCADE,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "ServerMemberRole_memberId_roleId_key" ON "ServerMemberRole" USING btree ("memberId", "roleId");
CREATE INDEX IF NOT EXISTS "ServerMemberRole_memberId_idx" ON "ServerMemberRole" USING btree ("memberId");
CREATE INDEX IF NOT EXISTS "ServerMemberRole_roleId_idx" ON "ServerMemberRole" USING btree ("roleId");

-- ===== Banimentos + audit log =====
CREATE TABLE IF NOT EXISTS "ServerBan" (
  "id" text PRIMARY KEY NOT NULL,
  "serverId" text NOT NULL REFERENCES "Server"("id") ON DELETE CASCADE,
  "userId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "bannedById" text NOT NULL REFERENCES "User"("id"),
  "reason" text,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "ServerBan_serverId_userId_key" ON "ServerBan" USING btree ("serverId", "userId");
CREATE INDEX IF NOT EXISTS "ServerBan_serverId_idx" ON "ServerBan" USING btree ("serverId");

CREATE TABLE IF NOT EXISTS "ServerAuditLog" (
  "id" text PRIMARY KEY NOT NULL,
  "serverId" text NOT NULL REFERENCES "Server"("id") ON DELETE CASCADE,
  "actorId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "action" text NOT NULL,
  "targetId" text,
  "metadata" text NOT NULL DEFAULT '{}',
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "ServerAuditLog_serverId_createdAt_idx" ON "ServerAuditLog" USING btree ("serverId", "createdAt" DESC);

-- ===== Cor de nome por servidor + cor no autor da mensagem =====
-- (existem no migration 0000, mas se o Neon foi construído por db:push antigo
--  podem faltar — o envio lê nameColor e insere authorColor, logo isto blinda.)
ALTER TABLE "ServerMember" ADD COLUMN IF NOT EXISTS "nameColor" text;
ALTER TABLE "Message" ADD COLUMN IF NOT EXISTS "authorColor" text;

-- ===== Poll + MessageEdit =====
ALTER TABLE "Message" ADD COLUMN IF NOT EXISTS "poll" text;

CREATE TABLE IF NOT EXISTS "MessageEdit" (
  "id" text PRIMARY KEY NOT NULL,
  "messageId" text NOT NULL REFERENCES "Message"("id") ON DELETE CASCADE,
  "content" text NOT NULL,
  "editedAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "MessageEdit_messageId_idx" ON "MessageEdit" USING btree ("messageId", "editedAt" DESC);

-- ===== Read receipts (ChannelRead + DM lastRead) =====
CREATE TABLE IF NOT EXISTS "ChannelRead" (
  "id" text PRIMARY KEY NOT NULL,
  "userId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "channelId" text NOT NULL REFERENCES "Channel"("id") ON DELETE CASCADE,
  "lastReadAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "ChannelRead_userId_channelId_key" ON "ChannelRead" USING btree ("userId", "channelId");
CREATE INDEX IF NOT EXISTS "ChannelRead_userId_idx" ON "ChannelRead" USING btree ("userId");

ALTER TABLE "DMConversation" ADD COLUMN IF NOT EXISTS "lastReadByA" timestamp (3);
ALTER TABLE "DMConversation" ADD COLUMN IF NOT EXISTS "lastReadByB" timestamp (3);
-- "fechar conversa": esconde a DM so pro lado que fechou (ver schema.ts).
ALTER TABLE "DMConversation" ADD COLUMN IF NOT EXISTS "hiddenByA" timestamp (3);
ALTER TABLE "DMConversation" ADD COLUMN IF NOT EXISTS "hiddenByB" timestamp (3);

-- ===== Notificações =====
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "notificationPrefs" text;

CREATE TABLE IF NOT EXISTS "Notification" (
  "id" text PRIMARY KEY NOT NULL,
  "userId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "type" text NOT NULL,
  "payload" text NOT NULL DEFAULT '{}',
  "readAt" timestamp (3),
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "Notification_userId_createdAt_idx" ON "Notification" USING btree ("userId", "createdAt" DESC);
CREATE INDEX IF NOT EXISTS "Notification_userId_readAt_idx" ON "Notification" USING btree ("userId", "readAt");

-- ===== Bot por orbita / categoria =====
-- NULO de proposito: "nao decidi". Orbita nula herda a categoria; categoria nula
-- fica ligada. Com boolean NOT NULL nao haveria como representar a heranca, e
-- desligar a categoria nao alcancaria as orbitas dela.
ALTER TABLE "Channel"         ADD COLUMN IF NOT EXISTS "botEnabled" boolean;
ALTER TABLE "ChannelCategory" ADD COLUMN IF NOT EXISTS "botEnabled" boolean;

-- ===== Bloqueio de pessoa =====
CREATE TABLE IF NOT EXISTS "UserBlock" (
  "id" text PRIMARY KEY NOT NULL,
  "blockerId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "blockedId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "UserBlock_blockerId_blockedId_key" ON "UserBlock" USING btree ("blockerId", "blockedId");
CREATE INDEX IF NOT EXISTS "UserBlock_blockerId_idx" ON "UserBlock" USING btree ("blockerId");
CREATE INDEX IF NOT EXISTS "UserBlock_blockedId_idx" ON "UserBlock" USING btree ("blockedId");

-- ===== Bookmarks =====
CREATE TABLE IF NOT EXISTS "Bookmark" (
  "id" text PRIMARY KEY NOT NULL,
  "userId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "targetId" text NOT NULL,
  "kind" text NOT NULL,
  "note" text,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "Bookmark_userId_targetId_kind_key" ON "Bookmark" USING btree ("userId", "targetId", "kind");
CREATE INDEX IF NOT EXISTS "Bookmark_userId_createdAt_idx" ON "Bookmark" USING btree ("userId", "createdAt" DESC);

-- ===== Canais privados por role =====
ALTER TABLE "Channel" ADD COLUMN IF NOT EXISTS "isPrivate" boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS "ChannelRolePerm" (
  "id" text PRIMARY KEY NOT NULL,
  "channelId" text NOT NULL REFERENCES "Channel"("id") ON DELETE CASCADE,
  "roleId" text NOT NULL REFERENCES "ServerRole"("id") ON DELETE CASCADE,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "ChannelRolePerm_channelId_roleId_key" ON "ChannelRolePerm" USING btree ("channelId", "roleId");
CREATE INDEX IF NOT EXISTS "ChannelRolePerm_channelId_idx" ON "ChannelRolePerm" USING btree ("channelId");
CREATE INDEX IF NOT EXISTS "ChannelRolePerm_roleId_idx" ON "ChannelRolePerm" USING btree ("roleId");

-- ===== Reminders =====
CREATE TABLE IF NOT EXISTS "Reminder" (
  "id" text PRIMARY KEY NOT NULL,
  "creatorId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "targetUserId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "content" text NOT NULL,
  "channelId" text,
  "dueAt" timestamp (3) NOT NULL,
  "deliveredAt" timestamp (3),
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "Reminder_dueAt_deliveredAt_idx" ON "Reminder" USING btree ("dueAt", "deliveredAt");
CREATE INDEX IF NOT EXISTS "Reminder_targetUserId_idx" ON "Reminder" USING btree ("targetUserId");
CREATE INDEX IF NOT EXISTS "Reminder_creatorId_idx" ON "Reminder" USING btree ("creatorId");

-- ===== Mensagens efêmeras =====
ALTER TABLE "Message" ADD COLUMN IF NOT EXISTS "expiresAt" timestamp (3);
CREATE INDEX IF NOT EXISTS "Message_expiresAt_idx" ON "Message" USING btree ("expiresAt");

-- ===== Custom status + Friendships =====
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "customStatus" text;

CREATE TABLE IF NOT EXISTS "Friendship" (
  "id" text PRIMARY KEY NOT NULL,
  "userAId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "userBId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "requesterId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "status" text NOT NULL DEFAULT 'pending',
  "acceptedAt" timestamp (3),
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "Friendship_userAId_userBId_key" ON "Friendship" USING btree ("userAId", "userBId");
CREATE INDEX IF NOT EXISTS "Friendship_userAId_idx" ON "Friendship" USING btree ("userAId");
CREATE INDEX IF NOT EXISTS "Friendship_userBId_idx" ON "Friendship" USING btree ("userBId");

-- ===== DM rich features =====
ALTER TABLE "DirectMessage" ADD COLUMN IF NOT EXISTS "attachments" text NOT NULL DEFAULT '[]';
ALTER TABLE "DirectMessage" ADD COLUMN IF NOT EXISTS "replyToId" text;
ALTER TABLE "DirectMessage" ADD COLUMN IF NOT EXISTS "expiresAt" timestamp (3);
CREATE INDEX IF NOT EXISTS "DirectMessage_replyToId_idx" ON "DirectMessage" USING btree ("replyToId");
CREATE INDEX IF NOT EXISTS "DirectMessage_expiresAt_idx" ON "DirectMessage" USING btree ("expiresAt");

-- ===== Banner personalization =====
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "bannerPositionY" integer NOT NULL DEFAULT 50;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "bannerScale"     integer NOT NULL DEFAULT 100;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "bannerBorder"    text    NOT NULL DEFAULT 'none';

-- ===== Constelacao: enquadramento do banner (posicao/zoom) + zoom do icone =====
ALTER TABLE "Server" ADD COLUMN IF NOT EXISTS "bannerPositionY" integer NOT NULL DEFAULT 50;
ALTER TABLE "Server" ADD COLUMN IF NOT EXISTS "bannerScale"     integer NOT NULL DEFAULT 100;
ALTER TABLE "Server" ADD COLUMN IF NOT EXISTS "iconScale"       integer NOT NULL DEFAULT 100;

-- ===== Sessao por dispositivo (#4: dedup do mesmo PC via X-Device-Id) =====
ALTER TABLE "RefreshToken" ADD COLUMN IF NOT EXISTS "deviceId" text;

-- ===== Profile personalization (pronouns / statusEmoji / displayFont) =====
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "pronouns"     text;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "statusEmoji"  text;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "displayFont"  text NOT NULL DEFAULT 'serif';

-- ===== Profile cleanup (derruba features sem aderência) + bannerTextColor =====
ALTER TABLE "User" DROP COLUMN IF EXISTS "avatarDecoration";
ALTER TABLE "User" DROP COLUMN IF EXISTS "profileBg";
ALTER TABLE "User" DROP COLUMN IF EXISTS "spotifyRefreshToken";
ALTER TABLE "User" DROP COLUMN IF EXISTS "spotifyConnectedAt";
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "bannerTextColor" text;

CREATE TABLE IF NOT EXISTS "ProfileNote" (
  "id"            text PRIMARY KEY NOT NULL,
  "profileUserId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "authorId"      text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "content"       text NOT NULL,
  "pinned"        boolean NOT NULL DEFAULT false,
  "createdAt"     timestamp (3) NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "ProfileNote_profileUserId_createdAt_idx"
  ON "ProfileNote"("profileUserId", "createdAt" DESC);
CREATE UNIQUE INDEX IF NOT EXISTS "ProfileNote_profileUserId_authorId_key"
  ON "ProfileNote"("profileUserId", "authorId");

-- ===== Categorias de canal (ChannelCategory + Channel.categoryId/position) =====
CREATE TABLE IF NOT EXISTS "ChannelCategory" (
  "id" text PRIMARY KEY NOT NULL,
  "name" text NOT NULL,
  "serverId" text NOT NULL,
  "position" integer DEFAULT 0 NOT NULL,
  "createdAt" timestamp (3) DEFAULT now() NOT NULL
);
CREATE INDEX IF NOT EXISTS "ChannelCategory_serverId_idx" ON "ChannelCategory" ("serverId");
ALTER TABLE "Channel" ADD COLUMN IF NOT EXISTS "categoryId" text;
ALTER TABLE "Channel" ADD COLUMN IF NOT EXISTS "position" integer NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS "Channel_categoryId_idx" ON "Channel" ("categoryId");
DO $$ BEGIN
  ALTER TABLE "ChannelCategory" ADD CONSTRAINT "ChannelCategory_serverId_Server_id_fk"
    FOREIGN KEY ("serverId") REFERENCES "Server"("id") ON DELETE cascade ON UPDATE no action;
EXCEPTION WHEN duplicate_object THEN null; END $$;
DO $$ BEGIN
  ALTER TABLE "Channel" ADD CONSTRAINT "Channel_categoryId_ChannelCategory_id_fk"
    FOREIGN KEY ("categoryId") REFERENCES "ChannelCategory"("id") ON DELETE set null ON UPDATE no action;
EXCEPTION WHEN duplicate_object THEN null; END $$;

-- ===== Remoção da feature de threads (mensagens de thread viram normais) =====
DROP INDEX IF EXISTS "Message_threadId_idx";
ALTER TABLE "Message" DROP COLUMN IF EXISTS "threadId";
DROP TABLE IF EXISTS "Thread";
`
