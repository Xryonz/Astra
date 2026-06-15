/**
 * Rename server/grupo.
 */
import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import {
  Dialog, DialogContent, DialogHeader, DialogFooter, DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface Target { id: string; name: string }

interface Props {
  open:    boolean
  onClose: () => void
  target:  Target | null
}

export function EditServerDialog({ open, onClose, target }: Props) {
  const { t } = useTranslation()
  const [name, setName]   = useState('')
  const [error, setError] = useState('')
  const queryClient = useQueryClient()

  // Sincroniza com target ao abrir
  useEffect(() => {
    if (open && target) { setName(target.name); setError('') }
  }, [open, target])

  const renameServer = useMutation({
    mutationFn: async ({ id, n }: { id: string; n: string }) =>
      (await api.patch(`/api/servers/${id}`, { name: n })).data.data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['servers'] })
      onClose()
    },
    onError: (e: any) => setError(e.response?.data?.error ?? t('common.error')),
  })

  const submit = () => {
    const trimmed = name.trim()
    if (!trimmed || !target) return
    renameServer.mutate({ id: target.id, n: trimmed })
  }

  return (
    <Dialog open={open && !!target} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-95!">
        <DialogHeader>
          <DialogTitle>{t('srvDialog.rename')}</DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="editName">{t('srvDialog.newName')}</Label>
          <Input
            id="editName"
            autoFocus
            value={name}
            onChange={(e) => { setName(e.target.value); setError('') }}
            onKeyDown={(e) => e.key === 'Enter' && submit()}
            placeholder={target?.name}
            maxLength={100}
          />
          {error && <p className="text-xs text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={submit} disabled={renameServer.isPending || !name.trim()}>
            {renameServer.isPending ? t('srvDialog.saving') : t('srvDialog.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
