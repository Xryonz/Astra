-- ─────────────────────────────────────────────────────────────
-- Profile personalization cleanup round 13.
--
-- Remove features que não tiveram aderência visual:
--   profileBg        (animado, escondido pelo overlay backdrop-blur)
--   avatarDecoration (animado, mas user reportou "estático")
--   spotifyRefreshToken + spotifyConnectedAt (feature adiada)
--
-- Adiciona:
--   bannerTextColor  (hex, nullable — null = auto contrast)
--
-- IF EXISTS em drops → idempotente, reroda seguro.
-- ─────────────────────────────────────────────────────────────

ALTER TABLE "User" DROP COLUMN IF EXISTS "profileBg";
ALTER TABLE "User" DROP COLUMN IF EXISTS "avatarDecoration";
ALTER TABLE "User" DROP COLUMN IF EXISTS "spotifyRefreshToken";
ALTER TABLE "User" DROP COLUMN IF EXISTS "spotifyConnectedAt";

ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "bannerTextColor" text;
