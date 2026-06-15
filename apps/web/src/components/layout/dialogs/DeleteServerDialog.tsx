/**
 * Confirma exclusão de server/grupo. Destrutivo — sem volta.
 */
import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import { api } from '@/lib/api'
import {
  Dialog, DialogContent, DialogHeader, DialogFooter, DialogTitle, DialogDescription,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

interface Target { id: string; name: string }

interface Props {
  open:       boolean
  onClose:    () => void
  target:     Target | null
  onDeleted:  (id: string) => void  // parent limpa activeServerId se bater
}

export function DeleteServerDialog({ open, onClose, target, onDeleted }: Props) {
  const { t } = useTranslation()
  const [error, setError] = useState('')
  const queryClient = useQueryClient()

  useEffect(() => { if (!open) setError('') }, [open])

  const deleteServer = useMutation({
    mutationFn: async (id: string) => api.delete(`/api/servers/${id}`),
    onSuccess: (_d, id) => {
      queryClient.invalidateQueries({ queryKey: ['servers'] })
      onDeleted(id)
      onClose()
    },
    onError: (e: any) => setError(e.response?.data?.error ?? t('common.error')),
  })

  return (
    <Dialog open={open && !!target} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-95! text-center">
        <div className="flex justify-center mb-1">
          <div className="size-12 rounded-full bg-destructive/10 flex items-center justify-center">
            <Trash2 className="size-6 text-destructive" />
          </div>
        </div>
        <DialogHeader className="text-center! items-center!">
          <DialogTitle>{t('dialogs.deleteTitle', { name: target?.name })}</DialogTitle>
          <DialogDescription>{t('dialogs.deleteDesc')}</DialogDescription>
        </DialogHeader>
        {error && <p className="text-xs text-destructive">{error}</p>}
        <DialogFooter className="sm:justify-center">
          <Button variant="secondary" onClick={onClose} className="flex-1">{t('common.cancel')}</Button>
          <Button
            variant="destructive"
            onClick={() => target && deleteServer.mutate(target.id)}
            disabled={deleteServer.isPending}
            className="flex-1"
          >
            {deleteServer.isPending ? t('dialogs.deleting') : t('dialogs.confirmDelete')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
