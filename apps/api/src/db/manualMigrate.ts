/**
 * Script temporário pra aplicar migrations das tabelas ServerRole/ServerMemberRole/ServerBan/ServerAuditLog
 * sem o prompt interativo do drizzle-kit. Rodar com:
 *   npx ts-node src/db/manualMigrate.ts
 */
import 'dotenv/config'
import { Pool } from 'pg'
import dns from 'dns'

// Força Node a resolver IPv4 primeiro. Sem isso o Node 18+ pode pegar
// AAAA (IPv6) que ISPs como Vivo bloqueiam → ETIMEDOUT silencioso.
dns.setDefaultResultOrder('ipv4first')

const pool = new Pool({
  connectionString:     process.env.DATABASE_URL!,
  ssl:                  { rejectUnauthorized: false },
  connectionTimeoutMillis: 15_000,  // falha rápido em vez de pendurar
})

const sql = `
CREATE TABLE IF NOT EXISTS "ServerRole" (
  "id" text PRIMARY KEY NOT NULL,
  "serverId" text NOT NULL REFERENCES "Server"("id") ON DELETE CASCADE,
  "name" text NOT NULL,
  "color" text,
  "position" integer NOT NULL DEFAULT 0,
  "permissions" text NOT NULL DEFAULT '[]',
  "hoist" boolean NOT NULL DEFAULT false,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS "ServerRole_serverId_idx" ON "ServerRole" USING btree ("serverId");

CREATE TABLE IF NOT EXISTS "ServerMemberRole" (
  "id" text PRIMARY KEY NOT NULL,
  "memberId" text NOT NULL REFERENCES "ServerMember"("id") ON DELETE CASCADE,
  "roleId" text NOT NULL REFERENCES "ServerRole"("id") ON DELETE CASCADE,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS "ServerMemberRole_memberId_roleId_key" ON "ServerMemberRole" USING btree ("memberId", "roleId");
CREATE INDEX IF NOT EXISTS "ServerMemberRole_memberId_idx" ON "ServerMemberRole" USING btree ("memberId");
CREATE INDEX IF NOT EXISTS "ServerMemberRole_roleId_idx" ON "ServerMemberRole" USING btree ("roleId");

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

-- F.2 polish: poll JSON em messages + MessageEdit table
ALTER TABLE "Message" ADD COLUMN IF NOT EXISTS "poll" text;

CREATE TABLE IF NOT EXISTS "MessageEdit" (
  "id" text PRIMARY KEY NOT NULL,
  "messageId" text NOT NULL REFERENCES "Message"("id") ON DELETE CASCADE,
  "content" text NOT NULL,
  "editedAt" timestamp (3) NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS "MessageEdit_messageId_idx" ON "MessageEdit" USING btree ("messageId", "editedAt" DESC);

-- Read receipts: ChannelRead + DM lastRead columns
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

-- Fase J: notification preferences + notification feed
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

-- Bookmarks (mensagens salvas)
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

-- Canais privados por role
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

-- Reminders
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

-- Mensagens efêmeras
ALTER TABLE "Message" ADD COLUMN IF NOT EXISTS "expiresAt" timestamp (3);
CREATE INDEX IF NOT EXISTS "Message_expiresAt_idx" ON "Message" USING btree ("expiresAt");

-- Custom status + Friendships
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

-- DM rich features: attachments, reply, ephemeral
ALTER TABLE "DirectMessage" ADD COLUMN IF NOT EXISTS "attachments" text NOT NULL DEFAULT '[]';
ALTER TABLE "DirectMessage" ADD COLUMN IF NOT EXISTS "replyToId" text;
ALTER TABLE "DirectMessage" ADD COLUMN IF NOT EXISTS "expiresAt" timestamp (3);
CREATE INDEX IF NOT EXISTS "DirectMessage_replyToId_idx" ON "DirectMessage" USING btree ("replyToId");
CREATE INDEX IF NOT EXISTS "DirectMessage_expiresAt_idx" ON "DirectMessage" USING btree ("expiresAt");

-- Banner personalization round 11
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "bannerPositionY" integer NOT NULL DEFAULT 50;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "bannerScale"     integer NOT NULL DEFAULT 100;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "bannerBorder"    text    NOT NULL DEFAULT 'none';

-- Profile personalization round 12
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "pronouns"            text;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "statusEmoji"         text;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "displayFont"         text NOT NULL DEFAULT 'serif';

-- Profile cleanup round 13: derruba features sem aderência + adiciona bannerTextColor
ALTER TABLE "User" DROP COLUMN IF EXISTS "avatarDecoration";
ALTER TABLE "User" DROP COLUMN IF EXISTS "profileBg";
ALTER TABLE "User" DROP COLUMN IF EXISTS "spotifyRefreshToken";
ALTER TABLE "User" DROP COLUMN IF EXISTS "spotifyConnectedAt";
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "bannerTextColor"     text;

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
`

async function main() {
  console.log('[Migrate] Aplicando ServerRole + ServerMemberRole + ServerBan + ServerAuditLog + Poll + MessageEdit…')
  if (!process.env.DATABASE_URL) {
    console.error('[Migrate] ❌ DATABASE_URL ausente — verifique apps/api/.env')
    process.exit(1)
  }
  console.log('[Migrate] DATABASE_URL host:',
    process.env.DATABASE_URL.replace(/:\/\/[^@]*@/, '://***:***@').slice(0, 80) + '…')

  try {
    // Testa conexão antes do query
    await pool.query('SELECT 1')
    console.log('[Migrate] ✓ Conexão DB ok')

    await pool.query(sql)
    console.log('[Migrate] ✓ Tabelas criadas')
  } catch (err: any) {
    // Logging robusto — pg errors raramente têm err.message útil
    console.error('[Migrate] ❌ erro ao migrar:')
    console.error('  message:    ', err?.message || '(vazio)')
    console.error('  code:       ', err?.code)
    console.error('  errno:      ', err?.errno)
    console.error('  syscall:    ', err?.syscall)
    console.error('  hostname:   ', err?.hostname)
    console.error('  address:    ', err?.address)
    console.error('  detail:     ', err?.detail)
    console.error('  hint:       ', err?.hint)
    console.error('  where:      ', err?.where)
    console.error('  routine:    ', err?.routine)
    console.error('  position:   ', err?.position)
    console.error('  severity:   ', err?.severity)
    if (err?.stack) console.error('  stack:\n', err.stack.split('\n').slice(0, 6).join('\n'))
    process.exit(1)
  } finally {
    await pool.end()
  }
}

main()
