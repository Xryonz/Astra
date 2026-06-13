import { useEffect, useMemo, useState } from 'react'
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
      setSaveError(e.response?.data?.error ?? e.message ?? 'Erro ao salvar')
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
      toast.success('Senha alterada com sucesso')
      setPwOpen(false); setCurPw(''); setNewPw('')
    },
    onError: (e: any) => toast.error(e?.response?.data?.error ?? 'Não foi possível trocar a senha'),
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
        title="Conta"
        description="Suas credenciais de acesso à Astra. Avatar, banner e bio ficam em Perfil."
      />

      <Row label="Nome de exibição" hint="Como os outros te veem no chat.">
        <Input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={50} placeholder="Como quer ser chamado" />
        {errors.displayName && <p className="text-xs text-(--danger) mt-1 m-0">{errors.displayName}</p>}
      </Row>

      <Row label="Username" hint="Único. @mention usa isso.">
        <Input value={username} onChange={(e) => setUsername(e.target.value.toLowerCase())} maxLength={30} placeholder="seu_username" />
        {errors.username && <p className="text-xs text-(--danger) mt-1 m-0">{errors.username}</p>}
      </Row>

      <div className="-mt-2 mb-2"><SaveStatus status={saveStatus} error={saveError} /></div>

      <Row label="E-mail" hint="Usado pra login. Não é exibido publicamente.">
        <p className="text-sm font-mono text-(--text-2) m-0">{user?.email ?? '—'}</p>
      </Row>

      <Row label="Senha" hint={hasPassword ? 'Troque periodicamente. Mín. 8 caracteres, 1 maiúscula e 1 número.' : 'Sua conta usa login Google — sem senha local.'}>
        {!hasPassword ? (
          <p className="text-sm text-(--text-3) m-0 self-start">Login via Google</p>
        ) : !pwOpen ? (
          <Button variant="outline" onClick={() => setPwOpen(true)} className="gap-2 self-start">
            <KeyRound className="size-3.5" /> Trocar senha
          </Button>
        ) : (
          <div className="flex flex-col gap-2 w-full max-w-xs">
            <Input type="password" autoComplete="current-password" value={curPw} onChange={(e) => setCurPw(e.target.value)} placeholder="Senha atual" />
            <Input type="password" autoComplete="new-password" value={newPw} onChange={(e) => setNewPw(e.target.value)} placeholder="Nova senha" />
            <div className="flex gap-2">
              <Button size="sm" disabled={!pwValid || changePw.isPending} onClick={() => changePw.mutate()}>
                {changePw.isPending ? 'Salvando…' : 'Confirmar'}
              </Button>
              <Button size="sm" variant="ghost" onClick={() => { setPwOpen(false); setCurPw(''); setNewPw('') }}>
                Cancelar
              </Button>
            </div>
          </div>
        )}
      </Row>

      <Row label="ID interno" hint="Pra suporte técnico.">
        <p className="text-xs font-mono text-(--text-3) m-0 break-all">{user?.id ?? '—'}</p>
      </Row>

      {bioAvailable && (
        <Row label="Bloquear com digital" hint="Pede sua biometria ao abrir o app. Só neste dispositivo.">
          <div className="flex items-center gap-2 self-start">
            <Fingerprint className="size-4 text-(--text-3)" />
            <Switch checked={lockOn} onCheckedChange={(v: boolean) => void toggleLock(v)} />
          </div>
        </Row>
      )}

      <Row label="Sair desta sessão" hint="Revoga este token. Outras sessões em outros dispositivos ficam ativas.">
        <Button variant="outline" onClick={() => logout()} className="gap-2 self-start">
          <LogOut className="size-3.5" /> Sair
        </Button>
      </Row>
    </div>
  )
}
