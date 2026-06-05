/**
 * Reveal — wrapper que faz fade + rise quando o elemento monta.
 *
 * CSS-only: usa animation keyframe `reveal-rise` (definido em index.css)
 * com --reveal-distance custom property. Sem custo de motion lib runtime —
 * o browser compositor cuida sozinho. GPU-direct, zero JS observers.
 *
 * Trade-off vs motion: não tem viewport-detection (anima no mount, não
 * quando entra no viewport). 99% dos usos na Astra são mount-time
 * (página abrindo), então CSS é estritamente melhor aqui.
 */
import * as React from 'react'
import { cn } from '@/lib/utils'

interface RevealProps extends React.HTMLAttributes<HTMLDivElement> {
  delay?:    number
  distance?: number
  duration?: number
}

export function Reveal({
  delay = 0, distance = 12, duration = 0.6,
  children, style, className, ...rest
}: RevealProps) {
  return (
    <div
      className={className}
      style={{
        animation: `reveal-rise ${duration}s cubic-bezier(0.16,1,0.3,1) ${delay}s both`,
        ['--reveal-distance' as string]: `${distance}px`,
        ...style,
      }}
      {...rest}
    >
      {children}
    </div>
  )
}

/**
 * Stagger — pai que escalona filhos. Cada child entra em Reveal
 * com delay incremental.
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
    <div className={cn(className)}>
      {arr.map((c, i) => (
        <Reveal key={i} delay={initialDelay + i * step}>
          {c}
        </Reveal>
      ))}
    </div>
  )
}
