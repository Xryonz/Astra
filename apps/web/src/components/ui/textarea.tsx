import * as React from 'react'
import { cn } from '@/lib/utils'

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {}

/**
 * Textarea — shadcn editorial.
 * rounded-lg pra suavizar. Focus ring com --accent. resize-none por default.
 */
const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, ...props }, ref) => (
    <textarea
      ref={ref}
      className={cn(
        'flex min-h-[60px] w-full rounded-lg border border-(--border-mid) bg-(--raised)/40 px-3 py-2 text-sm',
        'text-foreground font-(family-name:--font-body)',
        'placeholder:text-(--text-3) placeholder:font-normal',
        'focus-visible:outline-none focus-visible:border-(--accent) focus-visible:bg-(--raised)/60 focus-visible:ring-2 focus-visible:ring-(--accent)/20',
        'disabled:cursor-not-allowed disabled:opacity-50',
        'transition-[border-color,background-color,box-shadow] duration-200 resize-none',
        className,
      )}
      {...props}
    />
  ),
)
Textarea.displayName = 'Textarea'

export { Textarea }
