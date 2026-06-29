
import type { QueryClient } from '@tanstack/react-query'

const KEY = 'astra-offline-cache-v1'
const PERSIST_KEYS = ['servers', 'dm-list'] as const

export function setupOfflineCache(qc: QueryClient): void {

  try {
    const raw = localStorage.getItem(KEY)
    if (raw) {
      const saved = JSON.parse(raw) as Record<string, unknown>
      for (const k of PERSIST_KEYS) {
        if (saved[k] !== undefined) qc.setQueryData([k], saved[k], { updatedAt: 0 })
      }
    }
  } catch { }

  let timer: number | null = null
  qc.getQueryCache().subscribe(() => {
    if (timer !== null) return
    timer = window.setTimeout(() => {
      timer = null
      try {
        const out: Record<string, unknown> = {}
        for (const k of PERSIST_KEYS) {
          const data = qc.getQueryData([k])
          if (data !== undefined) out[k] = data
        }
        localStorage.setItem(KEY, JSON.stringify(out))
      } catch { }
    }, 1000)
  })
}

export function clearOfflineCache(): void {
  try { localStorage.removeItem(KEY) } catch {}
}
