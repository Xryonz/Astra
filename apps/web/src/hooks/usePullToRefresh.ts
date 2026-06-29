
import { useEffect, useRef, useState } from 'react'

const THRESHOLD = 64
const MAX_PULL  = 90
const RESIST    = 0.5

export function usePullToRefresh<T extends HTMLElement>(onRefresh: () => Promise<unknown> | void) {
  const ref = useRef<T>(null)
  const [pull, setPull] = useState(0)
  const [refreshing, setRefreshing] = useState(false)

  const refreshingRef = useRef(false)
  const startY = useRef(-1)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    if (!window.matchMedia('(pointer: coarse)').matches) return

    const onStart = (e: TouchEvent) => {
      if (refreshingRef.current) return
      startY.current = el.scrollTop <= 0 ? e.touches[0].clientY : -1
    }

    const onMove = (e: TouchEvent) => {
      if (startY.current < 0 || refreshingRef.current) return
      const dy = e.touches[0].clientY - startY.current
      if (dy <= 0) { setPull(0); return }

      if (el.scrollTop > 0) { startY.current = -1; setPull(0); return }
      e.preventDefault()
      setPull(Math.min(MAX_PULL, dy * RESIST))
    }

    const onEnd = () => {
      if (startY.current < 0) return
      startY.current = -1
      setPull((p) => {
        if (p >= THRESHOLD && !refreshingRef.current) {
          refreshingRef.current = true
          setRefreshing(true)
          Promise.resolve(onRefresh()).finally(() => {
            refreshingRef.current = false
            setRefreshing(false)
            setPull(0)
          })
          return THRESHOLD
        }
        return 0
      })
    }

    el.addEventListener('touchstart', onStart, { passive: true })
    el.addEventListener('touchmove', onMove, { passive: false })
    el.addEventListener('touchend', onEnd, { passive: true })
    el.addEventListener('touchcancel', onEnd, { passive: true })
    return () => {
      el.removeEventListener('touchstart', onStart)
      el.removeEventListener('touchmove', onMove)
      el.removeEventListener('touchend', onEnd)
      el.removeEventListener('touchcancel', onEnd)
    }
  }, [onRefresh])

  return { ref, pull, refreshing }
}
