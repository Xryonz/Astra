import crypto from 'crypto'

/**
 * Gerador de IDs estilo cuid (case-insensitive, URL-safe, ordenável).
 * Prisma usa `@default(cuid())` — aqui replicamos com primitivos do Node:
 *   - prefixo 'c' (mantém compat com IDs existentes)
 *   - 24 chars de entropy base32 (crypto.randomBytes)
 *
 * Não é o cuid2 puro, mas é compatível com IDs Prisma já no banco e
 * suficiente para chaves primárias sem colisão prática.
 */
export function createId(): string {
  return 'c' + crypto.randomBytes(15).toString('base64url').replace(/[-_]/g, '').slice(0, 24)
}
