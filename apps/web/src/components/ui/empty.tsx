/**
 * Empty — primitive editorial para empty states.
 * Centraliza padrão: rótulo mono + título serif + descrição body.
 *
 * Mount com fade-up suave (motion) pra evitar "pop" duro quando o user navega
 * pra uma tela vazia. Easing spring-soft → vibe editorial.
 */
import * as React from 'react'
import { motion, type HTMLMotionProps } from 'motion/react'
import { cn } from '@/lib/utils'

// HTMLMotionProps<'div'> conflita com algumas props HTML (onAnimationStart etc).
// Omitimos as conflitantes do tipo HTMLAttributes pra evitar TS error.
type EmptyProps = Omit<HTMLMotionProps<'div'>, 'ref'>

const Empty = React.forwardRef<HTMLDivElement, EmptyProps>(
  ({ className, ...props }, ref) => (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y:  0 }}
      transition={{ duration: 0.42, ease: [0.16, 1, 0.3, 1] }}
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
