import { create } from 'zustand'
import type { UserStatus } from '@/components/StatusDot'

interface PresenceState {
  /** Status real do próprio user (inclui INVISIBLE pra ele mesmo) */
  myStatus: UserStatus
  /** Mapa de presença de outros users (computado pelo server, sem INVISIBLE) */
  others:   Record<string, UserStatus>

  setMyStatus: (s: UserStatus) => void
  setOther:    (userId: string, s: UserStatus) => void
  bulkSet:     (m: Record<string, UserStatus>) => void
  reset:       () => void
}

// Buffer pra coalescer N updates dentro do mesmo frame em UMA atualização do store.
// Quando 50 users vêm ONLINE de uma vez (ex.: server boot), em vez de 50 setStates
// (cada um spread um Object novo, cada um agendando re-renders), juntamos tudo e
// chamamos set 1x no próximo frame.
let pending: Record<string, UserStatus> | null = null
let flushScheduled = false

function scheduleFlush(set: (fn: (st: PresenceState) => Partial<PresenceState>) => void) {
  if (flushScheduled) return
  flushScheduled = true
  // requestAnimationFrame = alinha o flush ao paint cycle; React renderiza no
  // máximo 1x por frame (60fps cap). Fallback pro setTimeout em ambientes sem raf.
  const raf = typeof requestAnimationFrame !== 'undefined'
    ? requestAnimationFrame
    : (cb: FrameRequestCallback) => setTimeout(() => cb(performance.now()), 16)
  raf(() => {
    const buf = pending
    pending = null
    flushScheduled = false
    if (!buf) return
    set((st) => ({ others: { ...st.others, ...buf } }))
  })
}

export const usePresenceStore = create<PresenceState>((set) => ({
  myStatus: 'ONLINE',
  others:   {},

  setMyStatus: (s) => set({ myStatus: s }),
  setOther:    (userId, s) => {
    pending = pending ?? {}
    pending[userId] = s
    scheduleFlush(set)
  },
  bulkSet:     (m) => set((st) => ({ others: { ...st.others, ...m } })),
  reset:       () => set({ myStatus: 'ONLINE', others: {} }),
}))
