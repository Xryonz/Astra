import * as React from 'react'
import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

interface SpinnerProps extends React.HTMLAttributes<SVGElement> {
  size?: number
}

/**
 * Spinner — loader minimal. Usa Loader2 do Lucide com animação rotate.
 */
export function Spinner({ size = 16, className, ...props }: SpinnerProps) {
  return (
    <Loader2
      className={cn('animate-spin text-(--accent)', className)}
      style={{ width: size, height: size }}
      {...props as any}
    />
  )
}
