/**
 * HoverCard — primitive sobre @radix-ui/react-hover-card.
 * Editorial: hairline border, sutil shadow, animação spring na entrada.
 */
import * as React from 'react'
import * as HoverCardPrimitive from '@radix-ui/react-hover-card'
import { cn } from '@/lib/utils'

const HoverCard        = HoverCardPrimitive.Root
const HoverCardTrigger = HoverCardPrimitive.Trigger

const HoverCardContent = React.forwardRef<
  React.ElementRef<typeof HoverCardPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof HoverCardPrimitive.Content>
>(({ className, align = 'center', sideOffset = 4, style, ...props }, ref) => (
  <HoverCardPrimitive.Portal>
    <HoverCardPrimitive.Content
      ref={ref}
      align={align}
      sideOffset={sideOffset}
      // translateZ(0) força compositor layer — necessário pra cobrir GIFs (<video> hardware-accel)
      style={{ transform: 'translateZ(0)', ...style }}
      className={cn(
        'z-50 w-64 border border-(--border-mid) bg-(--overlay) p-3 text-foreground shadow-2xl outline-none',
        // Animação suave: duração maior + curva spring orgânica (--ease-spring)
        'data-[state=open]:animate-in data-[state=closed]:animate-out',
        'data-[state=open]:[animation-duration:380ms] data-[state=closed]:[animation-duration:180ms]',
        'data-[state=open]:[animation-timing-function:cubic-bezier(0.16,1,0.3,1)]',
        'data-[state=closed]:[animation-timing-function:cubic-bezier(0.4,0,0.2,1)]',
        'data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0',
        'data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95',
        'data-[side=bottom]:slide-in-from-top-1 data-[side=left]:slide-in-from-right-1',
        'data-[side=right]:slide-in-from-left-1 data-[side=top]:slide-in-from-bottom-1',
        // Origem do scale acompanha o side — crescimento natural
        'data-[side=top]:origin-bottom data-[side=bottom]:origin-top',
        'data-[side=left]:origin-right data-[side=right]:origin-left',
        className,
      )}
      {...props}
    />
  </HoverCardPrimitive.Portal>
))
HoverCardContent.displayName = 'HoverCardContent'

export { HoverCard, HoverCardTrigger, HoverCardContent }
