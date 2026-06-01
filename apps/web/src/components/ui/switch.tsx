import * as React from 'react'
import * as SwitchPrimitive from '@radix-ui/react-switch'
import { cn } from '@/lib/utils'

const Switch = React.forwardRef<
  React.ElementRef<typeof SwitchPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof SwitchPrimitive.Root>
>(({ className, ...props }, ref) => (
  <SwitchPrimitive.Root
    ref={ref}
    className={cn(
      'peer inline-flex h-5 w-9 shrink-0 cursor-pointer items-center border border-(--border-mid) transition-colors',
      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-(--accent) focus-visible:ring-offset-2 focus-visible:ring-offset-(--base)',
      'disabled:cursor-not-allowed disabled:opacity-50',
      'data-[state=checked]:bg-(--accent) data-[state=unchecked]:bg-(--raised)',
      className,
    )}
    {...props}
  >
    <SwitchPrimitive.Thumb
      className={cn(
        'pointer-events-none block size-3.5 bg-(--text-1) shadow transition-transform',
        'data-[state=checked]:translate-x-[16px] data-[state=unchecked]:translate-x-0',
        'data-[state=checked]:bg-(--text-inv)',
      )}
    />
  </SwitchPrimitive.Root>
))
Switch.displayName = 'Switch'

export { Switch }
