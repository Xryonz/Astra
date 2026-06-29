
import * as React from 'react'
import { cn } from '@/lib/utils'

const Empty = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, style, ...props }, ref) => (
    <div
      ref={ref}
      style={{
        animation: 'reveal-rise 0.42s cubic-bezier(0.16,1,0.3,1) both',
        ['--reveal-distance' as string]: '12px',
        ...style,
      }}
      className={cn(
        'flex flex-col items-center justify-center text-center px-6 py-12 gap-2',
        className,
      )}
      {...props}
    />
  ),
)
Empty.displayName = 'Empty'

const EmptyIcon = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
  <div className={cn('size-12 grid place-items-center text-(--text-3) mb-2', className)} {...props} />
)

const EmptyLabel = ({ className, ...props }: React.HTMLAttributes<HTMLSpanElement>) => (
  <span className={cn('ed-marg', className)} {...props} />
)

const EmptyTitle = ({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) => (
  <h3 className={cn('ed-h text-xl m-0', className)} {...props} />
)

const EmptyDescription = ({ className, ...props }: React.HTMLAttributes<HTMLParagraphElement>) => (
  <p className={cn('text-sm text-(--text-3) max-w-[36ch] leading-relaxed m-0', className)} {...props} />
)

const EmptyActions = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
  <div className={cn('flex items-center gap-2 mt-2', className)} {...props} />
)

export { Empty, EmptyIcon, EmptyLabel, EmptyTitle, EmptyDescription, EmptyActions }
