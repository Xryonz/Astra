-- ─────────────────────────────────────────────────────────────
-- Banner personalization (round 11).
--
-- Adiciona 3 campos ao User:
--   bannerPositionY: int 0-100 (vertical %, default 50)
--   bannerScale:     int 100-200 (zoom %, default 100)
--   bannerBorder:    text enum 'none' | 'aurora' | 'pulse' | 'ink' (default 'none')
--
-- IF NOT EXISTS em tudo → idempotente, reroda seguro.
-- ─────────────────────────────────────────────────────────────

ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "bannerPositionY" integer NOT NULL DEFAULT 50;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "bannerScale"     integer NOT NULL DEFAULT 100;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "bannerBorder"    text    NOT NULL DEFAULT 'none';
