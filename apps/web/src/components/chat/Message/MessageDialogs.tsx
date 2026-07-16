
import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogContent, DialogHeader, DialogFooter, DialogTitle, DialogDescription,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'

export function DeleteConfirm({ open, onConfirm, onCancel }: {
  open:      boolean
  onConfirm: () => void
  onCancel:  () => void
}) {
  const { t } = useTranslation()
  return (
    <Dialog open={open} onOpenChange={(o: boolean) => !o && onCancel()}>
      <DialogContent className="max-w-90 text-center">
        <div className="text-4xl mb-2">🗑️</div>
        <DialogHeader>
          <DialogTitle className="text-center">{t('msgDialog.deleteTitle')}</DialogTitle>
          <DialogDescription className="text-center">
            {t('msgDialog.deleteDesc')}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="sm:justify-center">
          <Button variant="secondary"   className="flex-1" onClick={onCancel}>{t('common.cancel')}</Button>
          <Button variant="destructive" className="flex-1" onClick={onConfirm}>{t('msgDialog.deleteConfirm')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function EditModal({ open, content, onSave, onClose }: {
  open:    boolean
  content: string
  onSave:  (c: string) => Promise<void> | void
  onClose: () => void
}) {
  const { t } = useTranslation()
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
          <DialogTitle>{t('msgDialog.editTitle')}</DialogTitle>
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
        <p className="text-[11px] text-muted-foreground">{t('msgDialog.editHint')}</p>
        <DialogFooter>
          <Button variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={handleSave} disabled={disabled}>
            {saving ? t('msgDialog.saving') : t('msgDialog.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
