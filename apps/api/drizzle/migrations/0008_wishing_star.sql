-- ─────────────────────────────────────────────────────────────
-- WishingStar: lista global de sugestões do que melhorar no site.
--
-- Idempotente — IF NOT EXISTS em tabela + indexes.
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS "WishingStar" (
  "id"        text PRIMARY KEY,
  "userId"    text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "content"   text NOT NULL,
  "createdAt" timestamp(3) NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS "WishingStar_createdAt_idx"
  ON "WishingStar" ("createdAt" DESC);

CREATE INDEX IF NOT EXISTS "WishingStar_userId_idx"
  ON "WishingStar" ("userId");
