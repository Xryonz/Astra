-- ─────────────────────────────────────────────────────────────
-- Coordenada do usuário (Astra rebrand round 1).
--
-- Identificador público + funcional pra adicionar amigos.
-- Format: AAAA-BB (6 chars hex + hífen). Ex: A7F2-9B.
-- Derivado deterministicamente de md5(id) — único enquanto id for único.
--
-- IF NOT EXISTS / IF EXISTS em tudo → idempotente.
-- ─────────────────────────────────────────────────────────────

ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "coordinate" text;

-- Backfill: gera coordenadas pros users existentes via md5(id).
-- IS NULL evita reescrever em re-run.
UPDATE "User"
SET "coordinate" = upper(substr(md5("id"), 1, 4) || '-' || substr(md5("id"), 5, 2))
WHERE "coordinate" IS NULL;

-- NOT NULL + UNIQUE depois do backfill (não pode aplicar antes em tabela populada).
ALTER TABLE "User" ALTER COLUMN "coordinate" SET NOT NULL;

DO $$ BEGIN
  CREATE UNIQUE INDEX "User_coordinate_key" ON "User" ("coordinate");
EXCEPTION
  WHEN duplicate_table THEN NULL;
  WHEN duplicate_object THEN NULL;
END $$;
