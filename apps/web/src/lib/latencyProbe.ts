
const STARTS = new Map<string, number>()
const STARTS_CAP = 100
const RING_SIZE = 200
const samples: number[] = []
let ringIdx = 0

export function probeStart(nonce: string): void {
  if (!nonce) return

  if (STARTS.size >= STARTS_CAP) {
    const oldest = STARTS.keys().next().value
    if (oldest !== undefined) STARTS.delete(oldest)
  }
  STARTS.set(nonce, performance.now())
}

export function probeEnd(nonce?: string | null): number | null {
  if (!nonce) return null
  const t = STARTS.get(nonce)
  if (t == null) return null
  STARTS.delete(nonce)
  const dt = performance.now() - t
  if (samples.length < RING_SIZE) samples.push(dt)
  else { samples[ringIdx] = dt; ringIdx = (ringIdx + 1) % RING_SIZE }
  return dt
}

function percentile(arr: number[], p: number): number {
  if (arr.length === 0) return 0
  const sorted = [...arr].sort((a, b) => a - b)
  const idx = Math.min(sorted.length - 1, Math.floor(p * sorted.length))
  return sorted[idx]
}

export function latencySummary(): { count: number; p50: number; p95: number; p99: number; mean: number } {
  if (samples.length === 0) return { count: 0, p50: 0, p95: 0, p99: 0, mean: 0 }
  const mean = samples.reduce((a, b) => a + b, 0) / samples.length
  return {
    count: samples.length,
    p50:   Math.round(percentile(samples, 0.5)),
    p95:   Math.round(percentile(samples, 0.95)),
    p99:   Math.round(percentile(samples, 0.99)),
    mean:  Math.round(mean),
  }
}

if (typeof window !== 'undefined') {
  ;(window as unknown as { __astraLatency: { summary: typeof latencySummary } }).__astraLatency = {
    summary: latencySummary,
  }
}
