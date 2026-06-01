-- ─────────────────────────────────────────────────────────────
-- Perf indexes round 8.
--
-- Aplicar com `psql $DATABASE_URL -f 0001_perf_indexes.sql` em prod,
-- ou via npx drizzle-kit migrate (já registrado no journal).
--
-- IF NOT EXISTS em tudo → idempotente, reroda seguro.
-- ─────────────────────────────────────────────────────────────

-- DM list ordenado por updatedAt: composite cobre WHERE userAId/userBId + ORDER BY.
-- Antes: 2 index scans + sort. Depois: index-only scan, sem sort.
CREATE INDEX IF NOT EXISTS "DMConversation_userAId_updatedAt_idx"
  ON "DMConversation" ("userAId", "updatedAt" DESC);
CREATE INDEX IF NOT EXISTS "DMConversation_userBId_updatedAt_idx"
  ON "DMConversation" ("userBId", "updatedAt" DESC);

-- Drop indexes simples que viraram redundantes (composites cobrem o prefixo)
DROP INDEX IF EXISTS "DMConversation_userAId_idx";
DROP INDEX IF EXISTS "DMConversation_userBId_idx";

-- Friendship lookups por status (pedidos pendentes recebidos, amigos aceitos)
CREATE INDEX IF NOT EXISTS "Friendship_userAId_status_idx"
  ON "Friendship" ("userAId", "status");
CREATE INDEX IF NOT EXISTS "Friendship_userBId_status_idx"
  ON "Friendship" ("userBId", "status");

DROP INDEX IF EXISTS "Friendship_userAId_idx";
DROP INDEX IF EXISTS "Friendship_userBId_idx";
