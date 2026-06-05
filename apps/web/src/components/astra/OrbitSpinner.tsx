import { motion } from 'motion/react'

interface OrbitSpinnerProps {
  size?: number
  className?: string
}

/**
 * Loader orbital — 3 dots brilhantes em órbita de um centro vazio.
 * Substitui o Spinner editorial padrão em React Query pending states.
 *
 * Performance: 1 div + 3 spans animados via transform/opacity.
 * Sem layout thrashing, totalmente em compositor layer.
 */
export default function OrbitSpinner({ size = 28, className }: OrbitSpinnerProps) {
  const radius = size / 2 - 3
  const dotSize = Math.max(2, Math.round(size / 14))

  return (
    <div
      className={className}
      style={{
        position: 'relative',
        width:    size,
        height:   size,
        display:  'inline-block',
      }}
      aria-label="Carregando"
      role="status"
    >
      {[0, 1, 2].map((i) => (
        <motion.span
          key={i}
          style={{
            position:        'absolute',
            top:             '50%',
            left:            '50%',
            width:           dotSize,
            height:          dotSize,
            marginTop:       -dotSize / 2,
            marginLeft:      -dotSize / 2,
            borderRadius:    '50%',
            background:      'var(--accent)',
            boxShadow:       '0 0 6px var(--accent)',
          }}
          animate={{
            x: [
              radius * Math.cos((0 + i * (2 * Math.PI / 3))),
              radius * Math.cos((Math.PI / 2 + i * (2 * Math.PI / 3))),
              radius * Math.cos((Math.PI + i * (2 * Math.PI / 3))),
              radius * Math.cos((3 * Math.PI / 2 + i * (2 * Math.PI / 3))),
              radius * Math.cos((2 * Math.PI + i * (2 * Math.PI / 3))),
            ],
            y: [
              radius * Math.sin((0 + i * (2 * Math.PI / 3))),
              radius * Math.sin((Math.PI / 2 + i * (2 * Math.PI / 3))),
              radius * Math.sin((Math.PI + i * (2 * Math.PI / 3))),
              radius * Math.sin((3 * Math.PI / 2 + i * (2 * Math.PI / 3))),
              radius * Math.sin((2 * Math.PI + i * (2 * Math.PI / 3))),
            ],
          }}
          transition={{
            duration: 1.6,
            repeat:   Infinity,
            ease:     'linear',
          }}
        />
      ))}
    </div>
  )
}
