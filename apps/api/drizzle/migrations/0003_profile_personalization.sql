-- ─────────────────────────────────────────────────────────────
-- Profile personalization round 12.
--
-- Adiciona ao User:
--   pronouns text                    (nullable, max 32 app-side)
--   statusEmoji text                 (nullable, 1 codepoint)
--   displayFont text default 'serif' (enum: serif|sans|mono|...)
--   avatarDecoration text default 'none' (enum: none|halo|ring|...)
--   profileBg text default 'none'    (enum: none|aurora|nebula|...)
--   spotifyRefreshToken text         (nullable, encrypted)
--   spotifyConnectedAt timestamp(3)  (nullable)
--
-- Cria tabela ProfileNote (guestbook):
--   unique(profileUserId, authorId) → 1 nota por autor por perfil
--   index(profileUserId, createdAt desc) → listing ordenado
--
-- IF NOT EXISTS em tudo → idempotente.
-- ─────────────────────────────────────────────────────────────

ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "pronouns"            text;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "statusEmoji"         text;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "displayFont"         text NOT NULL DEFAULT 'serif';
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "avatarDecoration"    text NOT NULL DEFAULT 'none';
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "profileBg"           text NOT NULL DEFAULT 'none';
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "spotifyRefreshToken" text;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "spotifyConnectedAt"  timestamp (3);

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
