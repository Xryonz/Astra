
import { memo, useMemo } from 'react'
import { generateConstellation } from '@/lib/constellation'

interface Props {
  name:      string

  stars?:    number
  className?: string
  animated?: boolean
}

export const Constellation = memo(function Constellation({ name, stars: starCount, className, animated = false }: Props) {
  const { stars, edges, dust } = useMemo(
    () => generateConstellation(name, starCount),
    [name, starCount],
  )

  return (
    <svg viewBox="0 0 100 100" className={className} aria-hidden>
      {animated && (
        <style>{`
          @keyframes astraTwinkle { 0%,100% { opacity: 1 } 50% { opacity: 0.35 } }
        `}</style>
      )}

      {}
      {dust.map((d, i) => (
        <circle key={`d${i}`} cx={d.x} cy={d.y} r={d.r} fill="currentColor" opacity={0.25} />
      ))}

      {}
      {edges.map((e, i) => (
        <line
          key={`e${i}`}
          x1={stars[e.a].x} y1={stars[e.a].y}
          x2={stars[e.b].x} y2={stars[e.b].y}
          stroke="currentColor" strokeWidth={0.5} opacity={0.4}
        />
      ))}

      {}
      {stars.map((s, i) => (
        <g key={`s${i}`}>
          {s.alpha && <circle cx={s.x} cy={s.y} r={s.r * 2.2} fill="currentColor" opacity={0.15} />}
          <circle
            cx={s.x} cy={s.y} r={s.r} fill="currentColor"
            style={animated ? {
              animation: `astraTwinkle ${2.6 + (i % 3) * 0.9}s ease-in-out ${i * 0.35}s infinite`,
            } : undefined}
          />
        </g>
      ))}
    </svg>
  )
})

export function ConstellationBanner({ name, stars, className }: { name: string; stars?: number; className?: string }) {
  return (
    <div
      className={`relative overflow-hidden bg-linear-to-br from-(--void) via-(--base) to-(--raised) ${className ?? ''}`}
    >
      <Constellation
        name={name}
        stars={stars}
        animated
        className="absolute inset-0 w-full h-full text-(--accent)"
      />
    </div>
  )
}
