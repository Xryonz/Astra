/**
 * Pull-to-refresh — puxar a lista pra baixo (a partir do topo) pra atualizar.
 * Norma de app de mensagem. Só em touch; no desktop é inerte.
 *
 * Anexa listeners NÃO-passivos no container de scroll (precisa de
 * preventDefault pra segurar o overscroll nativo enquanto puxa). Resistência
 * elástica + threshold; ao soltar passando do limite, roda onRefresh e segura
 * o indicador até resolver.
 *
 * Uso:
 *   const { ref, pull, refreshing } = usePullToRefresh(() => refetch())
 *   <div ref={ref} className="overflow-y-auto">…indicador usa pull/refreshing…</div>
 */
import { useEffect, useRef, useState } from 'react'

const THRESHOLD = 64   // px puxados (já com resistência) pra disparar
const MAX_PULL  = 90   // teto visual do arraste
const RESIST    = 0.5  // fator elástico (puxa metade do dedo)

export function usePullToRefresh<T extends HTMLElement>(onRefresh: () => Promise<unknown> | void) {
  const ref = useRef<T>(null)
  const [pull, setPull] = useState(0)
  const [refreshing, setRefreshing] = useState(false)
  // refs pra não recriar listeners a cada render
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
      // Só sequestra o gesto se ainda está no topo (senão é scroll normal)
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
          return THRESHOLD  // segura o indicador no limite enquanto carrega
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
