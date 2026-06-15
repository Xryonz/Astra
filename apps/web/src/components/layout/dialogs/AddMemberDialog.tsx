/**
 * Convida user via username pra um server/grupo.
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

interface Props {
  open:     boolean
  onClose:  () => void
  serverId: string | null
}

export function AddMemberDialog({ open, onClose, serverId }: Props) {
  const { t } = useTranslation()
  const [username, setUsername] = useState('')
  const [error,    setError]    = useState('')
  const [success,  setSuccess]  = useState('')
  const queryClient = useQueryClient()

  useEffect(() => { if (!open) { setUsername(''); setError(''); setSuccess('') } }, [open])

  const inviteMember = useMutation({
    mutationFn: async ({ id, u }: { id: string; u: string }) =>
      (await api.post(`/api/servers/${id}/invite/${u}`)).data,
    onSuccess: (data) => {
      setSuccess(data.message ?? t('srvDialog.memberAdded'))
      setUsername(''); setError('')
      queryClient.invalidateQueries({ queryKey: ['servers'] })
      setTimeout(() => setSuccess(''), 3000)
    },
    onError: (e: any) => setError(e.response?.data?.error ?? t('common.error')),
  })

  const submit = () => {
    const trimmed = username.trim()
    if (!trimmed || !serverId) return
    inviteMember.mutate({ id: serverId, u: trimmed })
  }

  return (
    <Dialog open={open && !!serverId} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-95!">
        <DialogHeader>
          <DialogTitle>{t('srvDialog.addMember')}</DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="inviteUsername">{t('auth.username')}</Label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm pointer-events-none">@</span>
            <Input
              id="inviteUsername"
              autoFocus
              value={username}
              onChange={(e) => { setUsername(e.target.value.toLowerCase()); setError('') }}
              onKeyDown={(e) => e.key === 'Enter' && submit()}
              placeholder={t('srvDialog.usernamePlaceholder')}
              className="pl-7"
            />
          </div>
          {error && <p className="text-xs text-destructive">{error}</p>}
          {success && <p className="text-xs" style={{ color: 'var(--success)' }}>✓ {success}</p>}
        </div>

        <DialogFooter>
          <Button variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={submit} disabled={inviteMember.isPending || !username.trim()}>
            {inviteMember.isPending ? t('srvDialog.adding') : t('srvDialog.add')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
