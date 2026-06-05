/**
 * Campo de estrelas — fundo atmosférico da Astra.
 *
 * - Fixed inset-0, z-index -1, pointer-events none
 * - 70 dots estáticos via radial-gradient (zero overhead — 1 elemento, 1 bg)
 * - 12 estrelas com twinkle individual (DOM span + CSS animation, delays
 *   determinísticos pra não sincronizar visualmente)
 * - mix-blend-mode: 'screen' → estrelas adaptam: em fundo escuro ficam
 *   claras, em --raised/--base mais visíveis sem precisar de query JS
 * - Opacidade 14% pra ler com o conteúdo sem dominar
 * - Determinístico (seed fixa) → mesma constelação em todos os clients
 */

const TWO_TONES = ['1px', '2px']

function seededRand(seed: number) {
  let s = seed
  return () => {
    s = (s * 1103515245 + 12345) & 0x7fffffff
    return s / 0x7fffffff
  }
}

interface TwinkleStar {
  x:     number
  y:     number
  size:  number
  delay: number
  dur:   number
}

function buildBackground(): string {
  const rand = seededRand(424242)
  const parts: string[] = []
  for (let i = 0; i < 70; i++) {
    const x = (rand() * 100).toFixed(2)
    const y = (rand() * 100).toFixed(2)
    const size = TWO_TONES[Math.floor(rand() * 2)]
    parts.push(`radial-gradient(${size} ${size} at ${x}% ${y}%, currentColor 0, transparent 100%)`)
  }
  return parts.join(', ')
}

function buildTwinkleStars(): TwinkleStar[] {
  const rand = seededRand(99883)
  return Array.from({ length: 12 }, () => ({
    x:     rand() * 100,
    y:     rand() * 100,
    size:  1 + Math.floor(rand() * 2),                // 1 ou 2px
    delay: rand() * 6,                                // 0–6s spread
    dur:   2.6 + rand() * 2.4,                        // 2.6–5s
  }))
}

// Computed once on module load — não recalcula a cada render
const STARS_BG       = buildBackground()
const TWINKLE_STARS  = buildTwinkleStars()

export default function StarField() {
  return (
    <div
      aria-hidden
      className="astra-stars"
      style={{
        position:       'fixed',
        inset:          0,
        zIndex:         -1,
        pointerEvents:  'none',
        color:          'var(--text-1)',
        backgroundImage: STARS_BG,
        opacity:        0.14,
        mixBlendMode:   'screen',
      }}
    >
      {TWINKLE_STARS.map((s, i) => (
        <span
          key={i}
          aria-hidden
          className="astra-twinkle"
          style={{
            position:        'absolute',
            left:            `${s.x}%`,
            top:             `${s.y}%`,
            width:           `${s.size}px`,
            height:          `${s.size}px`,
            borderRadius:    '50%',
            background:      'currentColor',
            animationDelay:  `${s.delay}s`,
            animationDuration: `${s.dur}s`,
          }}
        />
      ))}
    </div>
  )
}
