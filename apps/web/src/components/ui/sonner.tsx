/**
 * Sonner toaster wrapper — estilo editorial.
 *
 * Custom icons via lucide pra match do resto do site.
 * Fonte display no title, body no description, hairline border, sem rounded.
 * Left border accent colorido por variant — vibe de "marca de margem" editorial.
 */
import { Toaster as SonnerToaster, toast } from 'sonner'
import { CheckCircle2, AlertCircle, Info, Loader2, AlertTriangle } from 'lucide-react'

export function Toaster() {
  return (
    <SonnerToaster
      position="bottom-right"
      theme="dark"
      expand={false}
      visibleToasts={4}
      offset={20}
      gap={10}
      icons={{
        success: <CheckCircle2  className="size-4 text-(--success)" />,
        error:   <AlertCircle   className="size-4 text-(--danger)" />,
        warning: <AlertTriangle className="size-4 text-yellow-500" />,
        info:    <Info          className="size-4 text-(--accent)" />,
        loading: <Loader2       className="size-4 text-(--text-3) animate-spin" />,
      }}
      toastOptions={{
        unstyled: false,
        classNames: {
          toast: [
            'group',
            'bg-(--overlay) text-foreground',
            'border border-(--border-mid) border-l-2',
            'shadow-[0_18px_48px_-12px_rgba(0,0,0,0.7)]',
            'font-(family-name:--font-body)',
            'px-4 py-3 gap-3',
            'backdrop-blur-md',
          ].join(' '),
          title:       'text-sm leading-tight font-(family-name:--font-display)',
          description: 'text-xs text-(--text-3) mt-1 leading-relaxed',
          actionButton: 'bg-(--accent) text-(--text-inv) hover:brightness-110 px-3 py-1 text-xs uppercase tracking-wider',
          cancelButton: 'bg-(--raised) text-(--text-2) px-3 py-1 text-xs',
          closeButton:  'bg-(--raised) border-(--border) text-(--text-3) hover:text-(--accent)',
          icon:         'shrink-0',
          // Border-left accent — "marca de margem" editorial
          error:   'border-l-(--danger)',
          success: 'border-l-(--success)',
          info:    'border-l-(--accent)',
          warning: 'border-l-yellow-500',
        },
      }}
    />
  )
}

export { toast }
