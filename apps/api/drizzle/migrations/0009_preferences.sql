-- ─────────────────────────────────────────────────────────────
-- Preferences: tema/aparência (accent, bg, etc) sincronizado por conta.
-- JSON em coluna text — schema livre no client, server só passa por.
-- Idempotente — IF NOT EXISTS na coluna.
-- ─────────────────────────────────────────────────────────────

ALTER TABLE "User"
  ADD COLUMN IF NOT EXISTS "preferences" text;
