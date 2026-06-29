import { useRef, useCallback } from 'react'

export function useLongPress(onLongPress: (e: React.TouchEvent | React.MouseEvent) => void, {
  ms = 500,
  moveThreshold = 8,
}: { ms?: number; moveThreshold?: number } = {}) {
  const timer    = useRef<ReturnType<typeof setTimeout> | null>(null)
  const startPos = useRef<{ x: number; y: number } | null>(null)
  const fired    = useRef(false)

  const clear = useCallback(() => {
    if (timer.current) { clearTimeout(timer.current); timer.current = null }
    startPos.current = null
  }, [])

  const start = useCallback((e: React.TouchEvent | React.MouseEvent) => {
    fired.current = false
    const p = 'touches' in e
      ? { x: e.touches[0].clientX, y: e.touches[0].clientY }
      : { x: (e as React.MouseEvent).clientX, y: (e as React.MouseEvent).clientY }
    startPos.current = p
    timer.current = setTimeout(() => {
      fired.current = true
      onLongPress(e)

      if ('vibrate' in navigator) try { (navigator as any).vibrate?.(8) } catch {}
    }, ms)
  }, [onLongPress, ms])

  const move = useCallback((e: React.TouchEvent | React.MouseEvent) => {
    if (!startPos.current) return
    const p = 'touches' in e
      ? { x: e.touches[0].clientX, y: e.touches[0].clientY }
      : { x: (e as React.MouseEvent).clientX, y: (e as React.MouseEvent).clientY }
    const dx = Math.abs(p.x - startPos.current.x)
    const dy = Math.abs(p.y - startPos.current.y)
    if (dx > moveThreshold || dy > moveThreshold) clear()
  }, [clear, moveThreshold])

  return {
    onTouchStart:  start,
    onTouchMove:   move,
    onTouchEnd:    clear,
    onTouchCancel: clear,
    onMouseDown:   start,
    onMouseMove:   move,
    onMouseUp:     clear,
    onMouseLeave:  clear,

    didFire: () => fired.current,
  }
}
