import * as React from 'react'
import { cn } from '@/lib/utils'

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
