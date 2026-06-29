

export interface Star  { x: number; y: number; r: number; alpha: boolean }
export interface Edge  { a: number; b: number }
export interface Dust  { x: number; y: number; r: number }
export interface Constellation { stars: Star[]; edges: Edge[]; dust: Dust[] }

function hashSeed(str: string): number {
  let h = 1779033703 ^ str.length
  for (let i = 0; i < str.length; i++) {
    h = Math.imul(h ^ str.charCodeAt(i), 3432918353)
    h = (h << 13) | (h >>> 19)
  }
  h = Math.imul(h ^ (h >>> 16), 2246822507)
  h = Math.imul(h ^ (h >>> 13), 3266489909)
  return (h ^= h >>> 16) >>> 0
}

function mulberry32(seed: number): () => number {
  let a = seed
  return () => {
    a |= 0; a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const MARGIN    = 14
const MIN_DIST  = 14
const MAX_STARS = 28

export function generateConstellation(name: string, starCount?: number): Constellation {
  const seed = hashSeed(name.trim().toLowerCase() || 'astra')
  const rnd  = mulberry32(seed)

  const count = starCount !== undefined
    ? Math.max(1, Math.min(MAX_STARS, Math.floor(starCount)))
    : 5 + Math.floor(rnd() * 5)
  const stars: Star[] = []
  let attempts = 0
  while (stars.length < count && attempts < 600) {
    attempts++
    const x = MARGIN + rnd() * (100 - MARGIN * 2)
    const y = MARGIN + rnd() * (100 - MARGIN * 2)
    if (stars.every((s) => Math.hypot(s.x - x, s.y - y) >= MIN_DIST)) {
      stars.push({ x, y, r: 1.1 + rnd() * 0.9, alpha: false })
    }
  }

  const alphaIdx = stars
    .map((s, i) => ({ i, d: Math.hypot(s.x - 50, s.y - 50) }))
    .sort((a, b) => a.d - b.d)[0].i
  stars[alphaIdx] = { ...stars[alphaIdx], r: 2.4, alpha: true }

  const edges: Edge[] = []
  const inTree = new Set<number>([0])
  while (inTree.size < stars.length) {
    let best: Edge | null = null
    let bestD = Infinity
    for (const a of inTree) {
      for (let b = 0; b < stars.length; b++) {
        if (inTree.has(b)) continue
        const d = Math.hypot(stars[a].x - stars[b].x, stars[a].y - stars[b].y)
        if (d < bestD) { bestD = d; best = { a, b } }
      }
    }
    edges.push(best!)
    inTree.add(best!.b)
  }

  const dustRnd = mulberry32(seed ^ 0x9e3779b9)
  const dust: Dust[] = []
  const dustCount = 6 + Math.floor(dustRnd() * 5)
  for (let i = 0; i < dustCount; i++) {
    dust.push({ x: dustRnd() * 100, y: dustRnd() * 100, r: 0.3 + dustRnd() * 0.3 })
  }

  return { stars, edges, dust }
}
