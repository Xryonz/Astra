-- ─────────────────────────────────────────────────────────────
-- ServerEmoji: emojis customizados por servidor (Discord-style :name:).
--
-- Cada server tem limite (50 default) de emojis. Quem usa: qualquer membro
-- do server. Quem gerencia: roles com MANAGE_CHANNELS ou OWNER.
-- name único por server. URL aponta pra /uploads/* (transcodificado em WebP).
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS "ServerEmoji" (
  "id"         text PRIMARY KEY,
  "serverId"   text NOT NULL REFERENCES "Server"("id") ON DELETE CASCADE,
  "name"       text NOT NULL,
  "url"        text NOT NULL,
  "createdBy"  text NOT NULL REFERENCES "User"("id") ON DELETE SET NULL,
  "createdAt"  timestamp(3) NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS "ServerEmoji_serverId_name_key"
  ON "ServerEmoji" ("serverId", "name");

CREATE INDEX IF NOT EXISTS "ServerEmoji_serverId_idx"
  ON "ServerEmoji" ("serverId");
