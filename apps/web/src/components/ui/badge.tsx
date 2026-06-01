import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center gap-1 px-1.5 py-0.5 text-[10px] font-mono uppercase tracking-wider transition-colors',
  {
    variants: {
      variant: {
        default:     'bg-(--accent)/15 text-(--accent) border border-(--accent)/40',
        secondary:   'bg-(--raised) text-(--text-2) border border-(--border-mid)',
        outline:     'border border-(--border) text-(--text-2)',
        destructive: 'bg-(--danger)/10 text-(--danger) border border-(--danger)/40',
        success:     'bg-(--success)/10 text-(--success) border border-(--success)/40',
      },
    },
    defaultVariants: { variant: 'default' },
  },
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />
}

export { Badge, badgeVariants }
