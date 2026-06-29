import { motion } from 'motion/react'
import type { ReactNode } from 'react'

interface ConstellationEmptyProps {
  title:       string
  description: string
  cta?:        ReactNode
  className?:  string
}

export default function ConstellationEmpty({
  title, description, cta, className,
}: ConstellationEmptyProps) {
  return (
    <div className={`flex flex-col items-center text-center gap-4 py-12 px-6 ${className ?? ''}`}>
      <svg width="140" height="100" viewBox="0 0 140 100" fill="none" aria-hidden>
        {}
        <motion.polyline
          points="20,75 50,40 80,55 110,25 125,65 95,85 55,80 20,75"
          stroke="var(--accent)"
          strokeWidth="0.5"
          strokeOpacity="0.4"
          fill="none"
          strokeDasharray="320"
          initial={{ strokeDashoffset: 320 }}
          animate={{ strokeDashoffset: 0 }}
          transition={{ duration: 2, ease: 'easeOut' }}
        />
        {}
        {[
          { x: 20,  y: 75, r: 2.0 },
          { x: 50,  y: 40, r: 2.5 },
          { x: 80,  y: 55, r: 1.8 },
          { x: 110, y: 25, r: 2.2 },
          { x: 125, y: 65, r: 1.8 },
          { x: 95,  y: 85, r: 2.0 },
          { x: 55,  y: 80, r: 1.5 },
        ].map((s, i) => (
          <motion.circle
            key={i}
            cx={s.x}
            cy={s.y}
            r={s.r}
            fill="var(--accent)"
            initial={{ opacity: 0, scale: 0 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: 0.1 + i * 0.15, duration: 0.4, ease: 'backOut' }}
          />
        ))}
      </svg>
      <div className="flex flex-col gap-2 max-w-sm">
        <p
          className="m-0 text-(--text-1) text-xl"
          style={{ fontFamily: 'var(--font-display)' }}
        >
          {title}
        </p>
        <p className="m-0 text-sm text-(--text-3)">{description}</p>
      </div>
      {cta && <div className="mt-2">{cta}</div>}
    </div>
  )
}
