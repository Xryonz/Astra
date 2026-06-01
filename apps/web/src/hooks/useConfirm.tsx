/**
 * useConfirm — substitui window.confirm() por AlertDialog editorial.
 *
 * Setup:
 *   <ConfirmProvider>
 *     <App />
 *   </ConfirmProvider>
 *
 * Uso:
 *   const confirm = useConfirm()
 *   const ok = await confirm({
 *     title: 'Excluir mensagem?',
 *     description: 'Esta ação é permanente.',
 *     confirmLabel: 'Excluir',
 *     destructive: true,
 *   })
 *   if (ok) { ... }
 */
import * as React from 'react'
import {
  AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogFooter,
  AlertDialogTitle, AlertDialogDescription, AlertDialogAction, AlertDialogCancel,
} from '@/components/ui/alert-dialog'
import {
  Dialog, DialogContent, DialogHeader, DialogFooter, DialogTitle, DialogDescription,
} from '@/components/ui/dialog'
import { Button, buttonVariants } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

interface ConfirmOpts {
  title:         string
  description?:  string
  confirmLabel?: string
  cancelLabel?:  string
  destructive?:  boolean
}

interface PromptOpts {
  title:         string
  description?:  string
  label?:        string
  placeholder?:  string
  defaultValue?: string
  confirmLabel?: string
  cancelLabel?:  string
  maxLength?:    number
}

type ConfirmFn = (opts: ConfirmOpts) => Promise<boolean>
type PromptFn  = (opts: PromptOpts) => Promise<string | null>

const ConfirmCtx = React.createContext<ConfirmFn | null>(null)
const PromptCtx  = React.createContext<PromptFn | null>(null)

export function ConfirmProvider({ children }: { children: React.ReactNode }) {
  // Confirm state
  const [opts, setOpts] = React.useState<ConfirmOpts | null>(null)
  const resolverRef = React.useRef<((value: boolean) => void) | null>(null)

  // Prompt state
  const [pOpts, setPOpts] = React.useState<PromptOpts | null>(null)
  const [pValue, setPValue] = React.useState('')
  const pResolverRef = React.useRef<((value: string | null) => void) | null>(null)

  const confirm: ConfirmFn = React.useCallback((o) => {
    return new Promise<boolean>((resolve) => {
      resolverRef.current = resolve
      setOpts(o)
    })
  }, [])

  const prompt: PromptFn = React.useCallback((o) => {
    return new Promise<string | null>((resolve) => {
      pResolverRef.current = resolve
      setPValue(o.defaultValue ?? '')
      setPOpts(o)
    })
  }, [])

  const close = (result: boolean) => {
    resolverRef.current?.(result)
    resolverRef.current = null
    setOpts(null)
  }
  const closeP = (result: string | null) => {
    pResolverRef.current?.(result)
    pResolverRef.current = null
    setPOpts(null)
    setPValue('')
  }

  return (
    <ConfirmCtx.Provider value={confirm}>
      <PromptCtx.Provider value={prompt}>
        {children}

        {/* AlertDialog (confirm) */}
        <AlertDialog open={!!opts} onOpenChange={(o) => { if (!o) close(false) }}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>{opts?.title ?? 'Confirmar?'}</AlertDialogTitle>
              {opts?.description && <AlertDialogDescription>{opts.description}</AlertDialogDescription>}
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel onClick={() => close(false)}>
                {opts?.cancelLabel ?? 'Cancelar'}
              </AlertDialogCancel>
              <AlertDialogAction
                onClick={() => close(true)}
                className={cn(opts?.destructive && buttonVariants({ variant: 'destructive' }))}
              >
                {opts?.confirmLabel ?? 'Confirmar'}
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>

        {/* Dialog com input (prompt) */}
        <Dialog open={!!pOpts} onOpenChange={(o) => { if (!o) closeP(null) }}>
          <DialogContent className="max-w-95!">
            <DialogHeader>
              <DialogTitle>{pOpts?.title ?? 'Editar'}</DialogTitle>
              {pOpts?.description && <DialogDescription>{pOpts.description}</DialogDescription>}
            </DialogHeader>
            <form
              onSubmit={(e) => {
                e.preventDefault()
                const v = pValue.trim()
                if (v) closeP(v)
              }}
              className="flex flex-col gap-1.5"
            >
              {pOpts?.label && <Label htmlFor="prompt-input">{pOpts.label}</Label>}
              <Input
                id="prompt-input"
                autoFocus
                value={pValue}
                onChange={(e) => setPValue(e.target.value)}
                placeholder={pOpts?.placeholder}
                maxLength={pOpts?.maxLength}
              />
            </form>
            <DialogFooter>
              <Button variant="secondary" onClick={() => closeP(null)}>
                {pOpts?.cancelLabel ?? 'Cancelar'}
              </Button>
              <Button
                onClick={() => { const v = pValue.trim(); if (v) closeP(v) }}
                disabled={!pValue.trim()}
              >
                {pOpts?.confirmLabel ?? 'Salvar'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </PromptCtx.Provider>
    </ConfirmCtx.Provider>
  )
}

export function useConfirm(): ConfirmFn {
  const fn = React.useContext(ConfirmCtx)
  if (!fn) throw new Error('useConfirm precisa de <ConfirmProvider>')
  return fn
}

export function usePrompt(): PromptFn {
  const fn = React.useContext(PromptCtx)
  if (!fn) throw new Error('usePrompt precisa de <ConfirmProvider>')
  return fn
}
