import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * Skeleton — shimmer editorial.
 *
 * Sweep gradient via keyframe `shimmer` (definido em index.css) entre
 * --raised → --hover → --raised. Mais cinematográfico que pulse opacity
 * e adapta a qualquer paleta de tema que o usuário escolha.
 *
 * Use Tailwind pra width/height/rounded — esse primitive só aplica
 * a textura. Ex: `<Skeleton className="size-9 rounded-full" />`.
 */
function Skeleton({ className, style, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('border border-(--border)', className)}
      style={{
        background: 'linear-gradient(90deg, var(--raised) 0%, var(--hover) 50%, var(--raised) 100%)',
        backgroundSize: '200% 100%',
        animation: 'shimmer 1.4s ease-in-out infinite',
        ...style,
      }}
      {...props}
    />
  )
}

export { Skeleton }
