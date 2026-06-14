import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { useAuth } from '@/hooks/useAuth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { LogOut, Fingerprint, KeyRound } from 'lucide-react'
import { SectionHeader, Row, SaveStatus } from './_shared'
import { api } from '@/lib/api'
import { toast } from '@/components/ui/sonner'
import { isAppLockEnabled, setAppLockEnabled, isBiometricAvailable, verifyAppLock } from '@/lib/appLock'
import { UpdateProfileSchema, ChangePasswordSchema } from '@astra/types'

export default function AccountSection() {
  const { t }       = useTranslation()
  const user        = useAuthStore((s) => s.user)
  const updateUser  = useAuthStore((s) => s.updateUser)
  const queryClient = useQueryClient()
  const { logout }  = useAuth()

  // ── Identidade (Discord: nome + username vivem em Conta) — auto-save 800ms ──
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [username,    setUsername]    = useState(user?.username ?? '')
  const [saveStatus,  setSaveStatus]  = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const [saveError,   setSaveError]   = useState('')

  const errors = useMemo(() => {
    const result = UpdateProfileSchema.safeParse({
      displayName: displayName || undefined,
      username:    username    || undefined,
    })
    if (result.success) return {} as Record<string, string>
    const map: Record<string, string> = {}
    for (const issue of result.error.issues) map[issue.path.join('.')] = issue.message
    return map
  }, [displayName, username])

  const updateIdentity = useMutation({
    mutationFn: async () => {
      const payload: Record<string, unknown> = {}
      if (displayName !== (user?.displayName ?? '')) payload.displayName = displayName || undefined
      if (username    !== (user?.username    ?? '')) payload.username    = username    || undefined
      if (Object.keys(payload).length === 0) return null
      return (await api.patch('/api/profile', payload)).data.data.user
    },
    onSuccess: (u) => {
      if (!u) { setSaveStatus('idle'); return }
      updateUser(u)
      queryClient.invalidateQueries({ queryKey: ['profile', u.id] })
      queryClient.setQueryData(['profile', u.id], u)
      setSaveStatus('saved'); setSaveError('')
      setTimeout(() => setSaveStatus('idle'), 2200)
    },
    onError: (e: any) => {
      setSaveError(e.response?.data?.error ?? e.message ?? t('settings.save.error'))
      setSaveStatus('error')
    },
  })

  useEffect(() => {
    if (Object.keys(errors).length > 0) return
    if (displayName === (user?.displayName ?? '') && username === (user?.username ?? '')) return
    const t = setTimeout(() => { setSaveStatus('saving'); updateIdentity.mutate() }, 800)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [displayName, username])

  // ── Troca de senha ──
  const hasPassword = user?.hasPassword !== false // undefined (sessão antiga) = assume que tem
  const [pwOpen, setPwOpen]   = useState(false)
  const [curPw,  setCurPw]    = useState('')
  const [newPw,  setNewPw]    = useState('')
  const pwValid = ChangePasswordSchema.safeParse({ currentPassword: curPw, newPassword: newPw }).success

  const changePw = useMutation({
    mutationFn: async () =>
      (await api.post('/api/auth/password', { currentPassword: curPw, newPassword: newPw })).data,
    onSuccess: () => {
      toast.success(t('settings.account.passwordChanged'))
      setPwOpen(false); setCurPw(''); setNewPw('')
    },
    onError: (e: any) => toast.error(e?.response?.data?.error ?? t('settings.account.passwordChangeError')),
  })

  // ── App lock (só no app nativo com biometria) ──
  const [bioAvailable, setBioAvailable] = useState(false)
  const [lockOn, setLockOn] = useState(isAppLockEnabled())
  useEffect(() => { void isBiometricAvailable().then(setBioAvailable) }, [])

  const toggleLock = async (on: boolean) => {
    if (on) {
      setAppLockEnabled(true)
      const ok = await verifyAppLock()
      if (!ok) { setAppLockEnabled(false); return }
      setLockOn(true)
    } else {
      setAppLockEnabled(false)
      setLockOn(false)
    }
  }

  return (
    <div>
      <SectionHeader
        title={t('settings.account.title')}
        description={t('settings.account.description')}
      />

      <Row label={t('settings.account.displayName')} hint={t('settings.account.displayNameHint')}>
        <Input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={50} placeholder={t('settings.account.displayNamePlaceholder')} />
        {errors.displayName && <p className="text-xs text-(--danger) mt-1 m-0">{errors.displayName}</p>}
      </Row>

      <Row label={t('settings.account.username')} hint={t('settings.account.usernameHint')}>
        <Input value={username} onChange={(e) => setUsername(e.target.value.toLowerCase())} maxLength={30} placeholder={t('settings.account.usernamePlaceholder')} />
        {errors.username && <p className="text-xs text-(--danger) mt-1 m-0">{errors.username}</p>}
      </Row>

      <div className="-mt-2 mb-2"><SaveStatus status={saveStatus} error={saveError} /></div>

      <Row label={t('settings.account.email')} hint={t('settings.account.emailHint')}>
        <p className="text-sm font-mono text-(--text-2) m-0">{user?.email ?? '—'}</p>
      </Row>

      <Row label={t('settings.account.password')} hint={hasPassword ? t('settings.account.passwordHintHas') : t('settings.account.passwordHintNone')}>
        {!hasPassword ? (
          <p className="text-sm text-(--text-3) m-0 self-start">{t('settings.account.loginViaGoogle')}</p>
        ) : !pwOpen ? (
          <Button variant="outline" onClick={() => setPwOpen(true)} className="gap-2 self-start">
            <KeyRound className="size-3.5" /> {t('settings.account.changePassword')}
          </Button>
        ) : (
          <div className="flex flex-col gap-2 w-full max-w-xs">
            <Input type="password" autoComplete="current-password" value={curPw} onChange={(e) => setCurPw(e.target.value)} placeholder={t('settings.account.currentPassword')} />
            <Input type="password" autoComplete="new-password" value={newPw} onChange={(e) => setNewPw(e.target.value)} placeholder={t('settings.account.newPassword')} />
            <div className="flex gap-2">
              <Button size="sm" disabled={!pwValid || changePw.isPending} onClick={() => changePw.mutate()}>
                {changePw.isPending ? t('settings.account.saving') : t('settings.account.confirm')}
              </Button>
              <Button size="sm" variant="ghost" onClick={() => { setPwOpen(false); setCurPw(''); setNewPw('') }}>
                {t('settings.account.cancel')}
              </Button>
            </div>
          </div>
        )}
      </Row>

      <Row label={t('settings.account.internalId')} hint={t('settings.account.internalIdHint')}>
        <p className="text-xs font-mono text-(--text-3) m-0 break-all">{user?.id ?? '—'}</p>
      </Row>

      {bioAvailable && (
        <Row label={t('settings.account.appLock')} hint={t('settings.account.appLockHint')}>
          <div className="flex items-center gap-2 self-start">
            <Fingerprint className="size-4 text-(--text-3)" />
            <Switch checked={lockOn} onCheckedChange={(v: boolean) => void toggleLock(v)} />
          </div>
        </Row>
      )}

      <Row label={t('settings.account.logoutSession')} hint={t('settings.account.logoutSessionHint')}>
        <Button variant="outline" onClick={() => logout()} className="gap-2 self-start">
          <LogOut className="size-3.5" /> {t('settings.account.logout')}
        </Button>
      </Row>
    </div>
  )
}
