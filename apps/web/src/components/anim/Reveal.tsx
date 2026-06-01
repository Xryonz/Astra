/**
 * Reveal — wrapper que faz fade + rise quando o elemento entra na tela.
 *
 * Usa motion-one (lightweight, ~5kb gzipped, melhor que framer-motion
 * pra animações simples). Inspirado em "Serif Renaissance" / "Editorial web":
 * elementos chegam de baixo com curva spring suave, opcionalmente em stagger.
 *
 * Default settings tunados pra negative-space layouts: delay pequeno,
 * curva orgânica (não Material).
 */
import { motion, type HTMLMotionProps } from 'motion/react'

interface RevealProps extends HTMLMotionProps<'div'> {
  delay?:    number
  distance?: number
  duration?: number
}

export function Reveal({
  delay = 0, distance = 12, duration = 0.6, children, ...rest
}: RevealProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: distance }}
      animate={{ opacity: 1, y: 0 }}
      transition={{
        duration,
        delay,
        // curva orgânica — variant de cubic-bezier(0.16, 1, 0.3, 1)
        ease: [0.16, 1, 0.3, 1],
      }}
      {...rest}
    >
      {children}
    </motion.div>
  )
}

/**
 * Stagger — pai que escalona filhos. Cada child renderiza como Reveal
 * automaticamente com delay incremental.
 */
interface StaggerProps {
  initialDelay?: number
  step?:         number
  children:      React.ReactNode
  className?:    string
}

export function Stagger({ initialDelay = 0, step = 0.08, children, className }: StaggerProps) {
  const arr = Array.isArray(children) ? children : [children]
  return (
    <div className={className}>
      {arr.map((c, i) => (
        <Reveal key={i} delay={initialDelay + i * step}>
          {c}
        </Reveal>
      ))}
    </div>
  )
}
