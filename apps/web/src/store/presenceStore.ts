import { create } from 'zustand'
import type { UserStatus } from '@/components/StatusDot'

interface PresenceState {

  myStatus: UserStatus

  others:   Record<string, UserStatus>

  setMyStatus: (s: UserStatus) => void
  setOther:    (userId: string, s: UserStatus) => void
  bulkSet:     (m: Record<string, UserStatus>) => void
  reset:       () => void
}

let pending: Record<string, UserStatus> | null = null
let flushScheduled = false

function scheduleFlush(set: (fn: (st: PresenceState) => Partial<PresenceState>) => void) {
  if (flushScheduled) return
  flushScheduled = true

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
