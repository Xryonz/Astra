import { createHash } from 'node:crypto'

/**
 * Gera coordenada Astra (AAAA-BB) determinística a partir do userId.
 *
 * - Determinismo via md5(userId): mesma input → mesmo output sempre,
 *   garantindo unicidade enquanto userId for único (cuid já garante).
 * - Format: 4 hex + hífen + 2 hex (uppercase). Ex: "A7F2-9B".
 * - Espaço efetivo: 16M combinações (16^6).
 */
export function generateCoordinate(userId: string): string {
  const hex = createHash('md5').update(userId).digest('hex').toUpperCase()
  return `${hex.slice(0, 4)}-${hex.slice(4, 6)}`
}

/** Valida formato AAAA-BB. Aceita upper e lower (normaliza). */
export function isValidCoordinate(coord: string): boolean {
  return /^[A-F0-9]{4}-[A-F0-9]{2}$/i.test(coord)
}

/** Normaliza pra UPPERCASE (formato canônico no DB). */
export function normalizeCoordinate(coord: string): string {
  return coord.toUpperCase().trim()
}
