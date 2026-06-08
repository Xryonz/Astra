/**
 * LatencyOverlay — HUD canto inferior-direito com p50/p95/p99 do round-trip
 * de envio de mensagens (fonte: lib/latencyProbe).
 *
 * Toggle: Ctrl+Shift+L (não conflita com atalhos do app).
 * Persistência: localStorage 'astra:dev:latency' = '1' → começa visível
 * (útil pra deixar ligado durante sessão de tuning).
 *
 * Não bloqueia produção: pode ficar montado sempre, é só cosmético e
 * lê do ring buffer já existente.
 */
import { useEffect, useState } from 'react'
import { latencySummary } from '@/lib/latencyProbe'

const STORAGE_KEY = 'astra:dev:latency'

export function LatencyOverlay() {
  const [open, setOpen] = useState(() => {
    if (typeof window === 'undefined') return false
    return window.localStorage.getItem(STORAGE_KEY) === '1'
  })
  const [s, setS] = useState(() => latencySummary())

  // Toggle Ctrl+Shift+L (ou Cmd+Shift+L)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key.toLowerCase() === 'l') {
        e.preventDefault()
        setOpen((v) => {
          const next = !v
          try { window.localStorage.setItem(STORAGE_KEY, next ? '1' : '0') } catch {}
          return next
        })
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  // Tick a cada 1s enquanto aberto — sem requestAnimationFrame
  // (HUD muda devagar; 1Hz é suficiente e barato).
  useEffect(() => {
    if (!open) return
    const id = window.setInterval(() => setS(latencySummary()), 1000)
    return () => window.clearInterval(id)
  }, [open])

  if (!open) return null

  return (
    <div
      style={{
        position:     'fixed',
        right:        12,
        bottom:       60,
        zIndex:       9000,
        padding:      '8px 12px',
        background:   'color-mix(in srgb, var(--void) 86%, transparent)',
        border:       '1px solid var(--border-mid)',
        borderRadius: 8,
        backdropFilter: 'blur(8px)',
        color:        'var(--text-2)',
        fontFamily:   'var(--font-mono)',
        fontSize:     11,
        lineHeight:   1.5,
        userSelect:   'none',
        pointerEvents: 'none',
        minWidth:     150,
      }}
      aria-hidden
    >
      <div style={{ color: 'var(--accent)', marginBottom: 4, fontWeight: 600, letterSpacing: '0.05em' }}>
        LATENCY · {s.count} msg
      </div>
      <Row label="p50"  value={s.p50}  />
      <Row label="p95"  value={s.p95}  />
      <Row label="p99"  value={s.p99}  />
      <Row label="mean" value={s.mean} />
      <div style={{ marginTop: 6, fontSize: 9, color: 'var(--text-3)' }}>Ctrl+Shift+L pra esconder</div>
    </div>
  )
}

function Row({ label, value }: { label: string; value: number }) {
  // Tint pelo p50 alvo: <80ms verde, 80-150 normal, >150 alerta
  const color = value < 80 ? 'var(--success)' : value > 150 ? 'var(--warning)' : 'var(--text-1)'
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
      <span style={{ color: 'var(--text-3)' }}>{label}</span>
      <span style={{ color, fontVariantNumeric: 'tabular-nums' }}>
        {value > 0 ? `${value} ms` : '—'}
      </span>
    </div>
  )
}
