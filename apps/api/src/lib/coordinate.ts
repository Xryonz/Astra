import { createHash } from 'node:crypto'

export function generateCoordinate(userId: string): string {
  const hex = createHash('md5').update(userId).digest('hex').toUpperCase()
  return `${hex.slice(0, 4)}-${hex.slice(4, 6)}`
}

export function isValidCoordinate(coord: string): boolean {
  return /^[A-F0-9]{4}-[A-F0-9]{2}$/i.test(coord)
}

export function normalizeCoordinate(coord: string): string {
  return coord.toUpperCase().trim()
}
