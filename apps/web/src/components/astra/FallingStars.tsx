/**
 * FallingStars — chuva sutil de estrelas caindo na diagonal esquerda-baixo.
 *
 * - 6 estrelas <Star> (lucide), tamanhos 10-16px, cor var(--accent)
 * - Atravessam a tela em loop (10-14s), rotacionam ±360° por ciclo
 * - Fade nos extremos pra não aparecerem/sumirem cortando a tela
 *
 * Performance:
 *  - Só transform + opacity (compositor-only)
 *  - SEM will-change (cada star fica parada longos períodos com delay; the
 *    layer cost passou a não compensar em mobile fraco)
 *  - 2 @keyframes totais (CW e CCW), parametrizado por inline animation
 *  - pointer-events: none
 *  - prefers-reduced-motion → container retorna null (decorativo)
 *
 * Determinístico: seed fixa garante mesma "chuva" entre clients.
 */
import { Star } from 'lucide-react'

interface Falling {
  startX:   number   // % do topo (lateral horizontal de origem)
  delay:    number   // s — escalonado
  duration: number   // s — 10-14
  size:     number   // px — 10-16
  cw:       boolean  // sentido da rotação
}

function seededRand(seed: number) {
  let s = seed
  return () => {
    s = (s * 1103515245 + 12345) & 0x7fffffff
    return s / 0x7fffffff
  }
}

function buildFallingStars(): Falling[] {
  const rand = seededRand(73219)
  const N    = 6
  // Stagger: divide ciclo médio (12s) em N janelas — sempre 1-3 visíveis
  const span = 12.5
  return Array.from({ length: N }, (_, i) => ({
    startX:   10 + rand() * 85,                  // 10-95% da largura
    delay:    -(i * (span / N)) + rand() * 1.5,  // negativos pra começar mid-flight
    duration: 10 + rand() * 4,
    size:     10 + Math.floor(rand() * 7),       // 10-16
    cw:       rand() > 0.5,
  }))
}

const STARS = buildFallingStars()

export default function FallingStars() {
  return (
    <div
      aria-hidden
      className="astra-falling"
      style={{
        position:      'fixed',
        inset:         0,
        zIndex:        -1,
        pointerEvents: 'none',
        overflow:      'hidden',
        color:         'var(--accent)',
        mixBlendMode:  'screen',
      }}
    >
      {STARS.map((s, i) => (
        <span
          key={i}
          style={{
            position:  'absolute',
            top:       0,
            left:      `${s.startX}%`,
            // 1 anim só, shorthand inline — vence cascata + reduced-motion
            // sai pela inferior-esquerda; rotação no mesmo timing
            animation: `${s.cw ? 'astraFallCW' : 'astraFallCCW'} ${s.duration}s linear infinite ${s.delay.toFixed(2)}s`,
            opacity:   0,                        // keyframe controla o fade
          }}
        >
          <Star
            size={s.size}
            strokeWidth={1.5}
            fill="currentColor"
            style={{ display: 'block' }}
          />
        </span>
      ))}
    </div>
  )
}
