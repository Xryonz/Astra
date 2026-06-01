import * as React from 'react'
import * as DialogPrimitive from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

const Sheet        = DialogPrimitive.Root
const SheetTrigger = DialogPrimitive.Trigger
const SheetPortal  = DialogPrimitive.Portal
const SheetClose   = DialogPrimitive.Close

const SheetOverlay = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Overlay>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Overlay>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Overlay
    ref={ref}
    className={cn(
      'fixed inset-0 z-50 bg-black/60 backdrop-blur-sm',
      'data-[state=open]:animate-in data-[state=closed]:animate-out',
      'data-[state=open]:fade-in-0 data-[state=closed]:fade-out-0',
      className
    )}
    {...props}
  />
))
SheetOverlay.displayName = DialogPrimitive.Overlay.displayName

interface SheetContentProps extends React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content> {
  side?: 'right' | 'left' | 'top' | 'bottom'
}

const SheetContent = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Content>,
  SheetContentProps
>(({ className, side = 'right', children, ...props }, ref) => (
  <SheetPortal>
    <SheetOverlay />
    <DialogPrimitive.Content
      ref={ref}
      className={cn(
        'fixed z-50 bg-(--overlay) border-(--border-mid) shadow-2xl',
        'data-[state=open]:animate-in data-[state=closed]:animate-out',
        'transition ease-(--ease-spring) duration-300',
        side === 'right' && [
          'top-0 right-0 h-full w-full sm:max-w-md border-l',
          'data-[state=open]:slide-in-from-right data-[state=closed]:slide-out-to-right',
        ],
        side === 'left' && [
          'top-0 left-0 h-full w-full sm:max-w-md border-r',
          'data-[state=open]:slide-in-from-left data-[state=closed]:slide-out-to-left',
        ],
        side === 'top' && [
          'top-0 left-0 right-0 border-b',
          'data-[state=open]:slide-in-from-top data-[state=closed]:slide-out-to-top',
        ],
        side === 'bottom' && [
          'bottom-0 left-0 right-0 border-t',
          'data-[state=open]:slide-in-from-bottom data-[state=closed]:slide-out-to-bottom',
        ],
        className
      )}
      {...props}
    >
      {children}
      <DialogPrimitive.Close
        className="absolute right-4 top-4 size-8 flex items-center justify-center border border-(--border) hover:border-(--accent) hover:text-(--accent) transition-all duration-300 ease-(--ease-spring) text-(--text-2) cursor-pointer"
        aria-label="Fechar"
      >
        <X className="size-4" />
      </DialogPrimitive.Close>
    </DialogPrimitive.Content>
  </SheetPortal>
))
SheetContent.displayName = DialogPrimitive.Content.displayName

const SheetTitle = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Title>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Title>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Title
    ref={ref}
    className={cn('text-lg leading-none tracking-tight', className)}
    style={{ fontFamily: 'var(--font-display)', fontWeight: 400 }}
    {...props}
  />
))
SheetTitle.displayName = DialogPrimitive.Title.displayName

const SheetDescription = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Description>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Description>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Description
    ref={ref}
    className={cn('text-sm text-muted-foreground', className)}
    {...props}
  />
))
SheetDescription.displayName = DialogPrimitive.Description.displayName

export {
  Sheet, SheetTrigger, SheetClose, SheetPortal, SheetOverlay, SheetContent,
  SheetTitle, SheetDescription,
}
