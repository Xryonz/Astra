import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * Input — shadcn editorial.
 * Tokens: --raised/40 background, --border-mid border, --accent on focus.
 * rounded-lg pra suavizar (menos "quadrado") sem virar slate genérico.
 */
const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, type, ...props }, ref) => (
    <input
      type={type}
      ref={ref}
      className={cn(
        'flex h-10 w-full rounded-lg border border-(--border-mid) bg-(--raised)/40 px-3 py-2 text-sm',
        'text-foreground font-(family-name:--font-body)',
        'placeholder:text-(--text-3) placeholder:font-normal',
        'focus-visible:outline-none focus-visible:border-(--accent) focus-visible:bg-(--raised)/60 focus-visible:ring-2 focus-visible:ring-(--accent)/20',
        'disabled:cursor-not-allowed disabled:opacity-50',
        'transition-[border-color,background-color,box-shadow] duration-200',
        className,
      )}
      {...props}
    />
  ),
)
Input.displayName = 'Input'

export { Input }
