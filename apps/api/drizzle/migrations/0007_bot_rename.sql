-- ─────────────────────────────────────────────────────────────
-- Rebatiza bot oficial: umbra_bot → astra_bot (Astra rebrand).
--
-- Atualiza username, displayName, email e bio do user existente.
-- WHERE específico → idempotente (se já rodou, no-op).
--
-- Email muda porque era bot@umbra.internal — alinha com identidade nova.
-- ─────────────────────────────────────────────────────────────

UPDATE "User"
SET username      = 'astra_bot',
    "displayName" = 'Astra',
    email         = 'bot@astra.internal',
    bio           = 'Bot oficial da Astra. Memória de 24h. Use /astra <pergunta>'
WHERE username = 'umbra_bot' AND "isBot" = true;
