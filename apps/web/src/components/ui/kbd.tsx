import * as React from 'react'
import { cn } from '@/lib/utils'

const Kbd = React.forwardRef<HTMLElement, React.HTMLAttributes<HTMLElement>>(
  ({ className, ...props }, ref) => (
    <kbd
      ref={ref}
      className={cn(
        'inline-flex items-center justify-center min-w-5 px-1.5 h-5 text-[10px] font-mono',
        'border border-(--border-mid) bg-(--raised) text-(--text-2) shadow-[inset_0_-1px_0_0_var(--border-mid)]',
        className,
      )}
      {...props}
    />
  ),
)
Kbd.displayName = 'Kbd'

export { Kbd }
