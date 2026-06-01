/**
 * Slider — shadcn-style sobre @radix-ui/react-slider.
 * Trilho hairline, thumb accent quadrado (editorial, sem rounded).
 */
import * as React from 'react'
import * as SliderPrimitive from '@radix-ui/react-slider'
import { cn } from '@/lib/utils'

const Slider = React.forwardRef<
  React.ElementRef<typeof SliderPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof SliderPrimitive.Root>
>(({ className, ...props }, ref) => (
  <SliderPrimitive.Root
    ref={ref}
    className={cn(
      'relative flex w-full touch-none select-none items-center',
      'data-[orientation=vertical]:h-full data-[orientation=vertical]:w-1.5',
      className,
    )}
    {...props}
  >
    <SliderPrimitive.Track className="relative h-1 w-full grow overflow-hidden bg-(--raised) data-[orientation=vertical]:h-full data-[orientation=vertical]:w-1">
      <SliderPrimitive.Range className="absolute h-full bg-(--accent) data-[orientation=vertical]:w-full" />
    </SliderPrimitive.Track>
    <SliderPrimitive.Thumb
      className={cn(
        'block size-3 border border-(--accent) bg-(--accent)',
        'transition-transform hover:scale-125 focus-visible:scale-125',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-(--accent)/40',
        'disabled:pointer-events-none disabled:opacity-50',
        'shadow-sm shadow-(--accent-glow)',
      )}
    />
  </SliderPrimitive.Root>
))
Slider.displayName = 'Slider'

export { Slider }
