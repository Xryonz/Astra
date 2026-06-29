
import { useRef, useCallback } from 'react'
import { hapticLight } from '@/lib/haptics'

const THRESHOLD = 56
const MAX_PULL  = 72

export function useSwipeReply(onTrigger: (() => void) | undefined) {
  const start      = useRef<{ x: number; y: number } | null>(null)
  const el         = useRef<HTMLElement | null>(null)
  const armed      = useRef(false)
  const horizontal = useRef<boolean | null>(null)

  const reset = useCallback(() => {
    const node = el.current
    if (node && node.style.transform) {
      node.style.transition = 'transform 0.18s cubic-bezier(0.16,1,0.3,1)'
      node.style.transform  = ''
      setTimeout(() => { if (el.current === node) node.style.transition = '' }, 200)
    }
    start.current      = null
    horizontal.current = null
    armed.current      = false
  }, [])

  const onTouchStart = useCallback((e: React.TouchEvent) => {
    if (!onTrigger) return

    if ((e.target as Element).closest('pre')) return
    const t = e.touches[0]
    start.current      = { x: t.clientX, y: t.clientY }
    el.current         = e.currentTarget as HTMLElement
    horizontal.current = null
    armed.current      = false
  }, [onTrigger])

  const onTouchMove = useCallback((e: React.TouchEvent) => {
    if (!start.current || !onTrigger) return
    const t  = e.touches[0]
    const dx = t.clientX - start.current.x
    const dy = t.clientY - start.current.y

    if (horizontal.current === null) {
      if (Math.abs(dx) < 8 && Math.abs(dy) < 8) return
      horizontal.current = Math.abs(dx) > Math.abs(dy) * 1.4 && dx > 0
      if (!horizontal.current) { start.current = null; return }
    }
    const pull = Math.min(dx, MAX_PULL)
    if (el.current) el.current.style.transform = `translateX(${pull}px)`
    const isArmed = dx >= THRESHOLD
    if (isArmed && !armed.current) void hapticLight()
    armed.current = isArmed
  }, [onTrigger])

  const onTouchEnd = useCallback(() => {
    if (armed.current) onTrigger?.()
    reset()
  }, [onTrigger, reset])

  return { onTouchStart, onTouchMove, onTouchEnd, onTouchCancel: reset }
}
