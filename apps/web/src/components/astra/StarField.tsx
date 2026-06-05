/**
 * Campo de estrelas — fundo atmosférico da Astra.
 *
 * - Fixed inset-0, z-index -1, pointer-events none
 * - 80 dots posicionados via radial-gradient (zero overhead — 1 elemento DOM, 1 background)
 * - Determinístico: posições calculadas uma vez no module-load via seed
 * - Opacidade 8% pra não brigar com o conteúdo
 */

const TWO_TONES = ['1px', '2px']

function seededRand(seed: number) {
  let s = seed
  return () => {
    s = (s * 1103515245 + 12345) & 0x7fffffff
    return s / 0x7fffffff
  }
}

function buildBackground(): string {
  const rand = seededRand(424242)
  const parts: string[] = []
  for (let i = 0; i < 80; i++) {
    const x = (rand() * 100).toFixed(2)
    const y = (rand() * 100).toFixed(2)
    const size = TWO_TONES[Math.floor(rand() * 2)]
    parts.push(`radial-gradient(${size} ${size} at ${x}% ${y}%, var(--text-1) 0, transparent 100%)`)
  }
  return parts.join(', ')
}

// Computed once on module load — não recalcula a cada render
const STARS_BG = buildBackground()

export default function StarField() {
  return (
    <div
      aria-hidden
      style={{
        position:       'fixed',
        inset:          0,
        zIndex:         -1,
        pointerEvents:  'none',
        backgroundImage: STARS_BG,
        opacity:        0.08,
      }}
    />
  )
}
