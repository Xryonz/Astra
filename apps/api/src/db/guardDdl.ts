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

-- ===== Troca de e-mail com confirmação no endereço novo =====
-- O endereço só entra em "email" depois do código voltar certo, então erro de
-- digitação não deixa a conta presa num endereço que não existe.
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "pendingEmail" text;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "pendingEmailCode" text;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "pendingEmailExpiresAt" timestamp (3);

-- ===== Poll =====
ALTER TABLE "Message" ADD COLUMN IF NOT EXISTS "poll" text;

-- ===== MessageEdit: removida de propósito =====
-- O histórico de edições saiu do produto. Guardar o que a pessoa apagou trabalha
-- contra ela, então a tabela é derrubada com as linhas que já existiam. Esta
-- linha fica: banco antigo que ainda tenha a tabela se limpa ao subir.
DROP TABLE IF EXISTS "MessageEdit";

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

-- ===== Imagem de identidade em duas versões =====
-- "avatarUrl" e "iconUrl" passam a guardar a versão de EXIBIÇÃO (256px) e estas colunas
-- a original que a pessoa enviou (1024px). Ver persistImagemDeExibicao em lib/storage.ts
-- para o porquê da inversão. Nulas para quem não reenviou imagem desde a mudança — e isso
-- é inofensivo: a imagem antiga continua na coluna de sempre, só não encolhida.
--
-- A mini-imagem de cargo (ServerRole.iconUrl) encolhe igual, mas NÃO ganha coluna: um
-- emblema ao lado de um nome nunca vai ser desenhado grande, então não há reprocessamento
-- futuro para o qual guardar a original.
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "avatarFullUrl" text;
ALTER TABLE "Server" ADD COLUMN IF NOT EXISTS "iconFullUrl" text;

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

-- Guardar a conversa com a bot. NOT NULL com default: nao ha heranca aqui, e as
-- orbitas que ja existiam adotam o padrao (guardar) sem passo extra.
ALTER TABLE "Channel" ADD COLUMN IF NOT EXISTS "botKeepReplies" boolean NOT NULL DEFAULT true;

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
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "dmPrivacy"       text    NOT NULL DEFAULT 'all';
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "deletedAt" timestamp(3);

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

-- ===== Progressao (XP + Brilho) =====
CREATE TABLE IF NOT EXISTS "UserXp" (
  "userId"    text PRIMARY KEY NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "xp"        integer NOT NULL DEFAULT 0,
  "brilho"    integer NOT NULL DEFAULT 0,
  "updatedAt" timestamp (3) NOT NULL DEFAULT now()
);

-- ===== Missoes =====
CREATE TABLE IF NOT EXISTS "UserMission" (
  "userId"      text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "missionId"   text NOT NULL,
  "periodo"     text NOT NULL,
  "progresso"   integer NOT NULL DEFAULT 0,
  "concluidaEm" timestamp (3),
  PRIMARY KEY ("userId", "missionId", "periodo")
);

-- ===== Remoção da feature de threads (mensagens de thread viram normais) =====
DROP INDEX IF EXISTS "Message_threadId_idx";
ALTER TABLE "Message" DROP COLUMN IF EXISTS "threadId";
DROP TABLE IF EXISTS "Thread";

-- ===== Efeitos sonoros da constelação (soundboard) =====
CREATE TABLE IF NOT EXISTS "ServerSound" (
  "id" text PRIMARY KEY NOT NULL,
  "serverId" text NOT NULL REFERENCES "Server"("id") ON DELETE CASCADE,
  "name" text NOT NULL,
  "url" text NOT NULL,
  "durationMs" integer NOT NULL DEFAULT 0,
  "createdBy" text NOT NULL,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "ServerSound_serverId_name_key" ON "ServerSound" USING btree ("serverId", "name");
CREATE INDEX IF NOT EXISTS "ServerSound_serverId_idx" ON "ServerSound" USING btree ("serverId");

-- ===== Figurinhas da constelação =====
CREATE TABLE IF NOT EXISTS "ServerSticker" (
  "id" text PRIMARY KEY NOT NULL,
  "serverId" text NOT NULL REFERENCES "Server"("id") ON DELETE CASCADE,
  "name" text NOT NULL,
  "url" text NOT NULL,
  "width" integer NOT NULL DEFAULT 0,
  "height" integer NOT NULL DEFAULT 0,
  "createdBy" text NOT NULL,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "ServerSticker_serverId_name_key" ON "ServerSticker" USING btree ("serverId", "name");
CREATE INDEX IF NOT EXISTS "ServerSticker_serverId_idx" ON "ServerSticker" USING btree ("serverId");

-- ===== Emojis da constelação =====
-- Faltava aqui: a tabela existe desde a migration 0012, e migration NÃO roda no
-- deploy do Render — por isso as duas vizinhas acima foram parar neste arquivo. O
-- emoji personalizado nunca tinha sido pedido por um cliente rodando contra o Neon
-- (o desktop não o tinha), então o buraco não aparecia: aparecia como 500 na
-- primeira vez que alguém abrisse a aba.
--
-- "createdBy" SEM chave estrangeira, seguindo o schema.ts. A migration 0012 pedia
-- REFERENCES ... ON DELETE SET NULL numa coluna NOT NULL, que é uma contradição:
-- apagar o autor tentaria gravar nulo onde nulo é proibido e o banco recusaria.
CREATE TABLE IF NOT EXISTS "ServerEmoji" (
  "id" text PRIMARY KEY NOT NULL,
  "serverId" text NOT NULL REFERENCES "Server"("id") ON DELETE CASCADE,
  "name" text NOT NULL,
  "url" text NOT NULL,
  "createdBy" text NOT NULL,
  "createdAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "ServerEmoji_serverId_name_key" ON "ServerEmoji" USING btree ("serverId", "name");
CREATE INDEX IF NOT EXISTS "ServerEmoji_serverId_idx" ON "ServerEmoji" USING btree ("serverId");

-- ===== Chamada de voz/vídeo no sussurro =====
-- Nulo = mensagem normal. Preenchido = a linha e um registro de chamada.
ALTER TABLE "DirectMessage" ADD COLUMN IF NOT EXISTS "call" text;

-- ===== Órbita dos avisos da bot =====
-- Onde a bot fala sem ser chamada (troca de turno, boas-vindas). Nulo = escolhe
-- sozinha, a primeira órbita de texto em que ela tem voz (o comportamento antigo).
--
-- SEM chave estrangeira de propósito: se a órbita for apagada, a coluna aponta pra
-- um id que não existe mais e o código simplesmente não acha o canal -> cai no
-- automático. Com ON DELETE SET NULL o efeito final seria o mesmo, mas apagar um
-- canal passaria a escrever na tabela Server, e a operação de apagar canal não
-- precisa desse acoplamento.
ALTER TABLE "Server" ADD COLUMN IF NOT EXISTS "botNoticeChannelId" text;

-- ===== Aparência das personas da bot =====
-- Sobreposição do que está escrito no código (lib/bot.ts). Uma linha por irmã, e a
-- chave é a persona ('sparkle' / 'sparxie') e NÃO o id do usuário: as duas dividem
-- a MESMA conta, trocando de rosto na virada do turno, então guardar por usuário
-- daria uma configuração só pras duas.
--
-- Coluna nula = "usa o que está no código". Isso mantém a arte de fábrica como
-- verdade e faz a personalização ser só o que foi realmente mexido — apagar a
-- linha devolve tudo ao original, sem precisar saber quais eram os valores.
CREATE TABLE IF NOT EXISTS "BotPersona" (
  "chave"           text PRIMARY KEY,
  "displayName"     text,
  "avatarUrl"       text,
  "bannerUrl"       text,
  "bannerColor"     text,
  "bannerScale"     integer,
  "bannerPositionY" integer,
  "updatedAt"       timestamp(3) NOT NULL DEFAULT now()
);

-- ===== Comandos da bot por constelação =====
-- Lista separada por vírgula dos comandos DESLIGADOS ali. Guarda o que está
-- desligado e não o que está ligado de propósito: comando novo entra ligado pra
-- todo mundo sem precisar de migração, que é o comportamento certo pra uma lista
-- que cresce.
ALTER TABLE "Server" ADD COLUMN IF NOT EXISTS "botDisabledCommands" text;
`
