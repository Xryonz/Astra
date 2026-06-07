/**
 * Dialogs da mensagem — criar thread, confirmar delete, editar texto.
 *
 * Extraídos de MessageItem (overhaul Fase 4d). Cada um self-contained,
 * controlado por open + callbacks do parent.
 */
import { useState, useEffect } from 'react'
import {
  Dialog, DialogContent, DialogHeader, DialogFooter, DialogTitle, DialogDescription,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'

// ── Create Thread ────────────────────────────────────────────
export function CreateThreadDialog({ open, onClose, onCreate }: {
  open:    boolean
  onClose: () => void
  onCreate: (name: string) => Promise<void> | void
}) {
  const [name,   setName]   = useState('')
  const [saving, setSaving] = useState(false)
  useEffect(() => { if (!open) setName('') }, [open])

  const submit = async () => {
    if (!name.trim()) return
    setSaving(true)
    try { await onCreate(name.trim()); onClose() } finally { setSaving(false) }
  }

  return (
    <Dialog open={open} onOpenChange={(o: boolean) => !o && onClose()}>
      <DialogContent className="max-w-95!">
        <DialogHeader>
          <DialogTitle>Soltar cometa</DialogTitle>
          <DialogDescription>Conversa derivada desta mensagem (thread).</DialogDescription>
        </DialogHeader>
        <Input
          autoFocus
          value={name}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter')  { e.preventDefault(); submit() }
            if (e.key === 'Escape') onClose()
          }}
          placeholder="Nome do cometa"
          maxLength={80}
        />
        <DialogFooter>
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button onClick={submit} disabled={!name.trim() || saving}>
            {saving ? 'Criando…' : 'Criar'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ── Delete Confirm ───────────────────────────────────────────
export function DeleteConfirm({ open, onConfirm, onCancel }: {
  open:      boolean
  onConfirm: () => void
  onCancel:  () => void
}) {
  return (
    <Dialog open={open} onOpenChange={(o: boolean) => !o && onCancel()}>
      <DialogContent className="max-w-90 text-center">
        <div className="text-4xl mb-2">🗑️</div>
        <DialogHeader>
          <DialogTitle className="text-center">Apagar mensagem?</DialogTitle>
          <DialogDescription className="text-center">
            Esta ação não pode ser desfeita.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="sm:justify-center">
          <Button variant="secondary"   className="flex-1" onClick={onCancel}>Cancelar</Button>
          <Button variant="destructive" className="flex-1" onClick={onConfirm}>Sim, apagar</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ── Edit Modal ───────────────────────────────────────────────
export function EditModal({ open, content, onSave, onClose }: {
  open:    boolean
  content: string
  onSave:  (c: string) => Promise<void> | void
  onClose: () => void
}) {
  const [value,  setValue]  = useState(content)
  const [saving, setSaving] = useState(false)

  useEffect(() => { setValue(content) }, [content])

  const handleSave = async () => {
    if (!value.trim() || value === content) { onClose(); return }
    setSaving(true)
    await onSave(value.trim())
    setSaving(false)
  }

  const disabled = saving || !value.trim() || value === content

  return (
    <Dialog open={open} onOpenChange={(o: boolean) => !o && onClose()}>
      <DialogContent className="max-w-115">
        <DialogHeader>
          <DialogTitle>Editar mensagem</DialogTitle>
        </DialogHeader>
        <Textarea
          autoFocus
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSave() }
            if (e.key === 'Escape') onClose()
          }}
          rows={3}
          className="min-h-18 resize-y leading-relaxed"
        />
        <p className="text-[11px] text-muted-foreground">Enter para salvar · Esc para cancelar</p>
        <DialogFooter>
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button onClick={handleSave} disabled={disabled}>
            {saving ? 'Salvando…' : 'Salvar'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
