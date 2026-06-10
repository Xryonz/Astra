import { useEffect, useState } from 'react'
import { useAuthStore } from '@/store/authStore'
import { useAuth } from '@/hooks/useAuth'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { LogOut, Fingerprint } from 'lucide-react'
import { SectionHeader, Row } from './_shared'
import { isAppLockEnabled, setAppLockEnabled, isBiometricAvailable, verifyAppLock } from '@/lib/appLock'

export default function AccountSection() {
  const user = useAuthStore((s) => s.user)
  const { logout } = useAuth()

  // App lock (só aparece no app nativo com biometria configurada)
  const [bioAvailable, setBioAvailable] = useState(false)
  const [lockOn, setLockOn] = useState(isAppLockEnabled())
  useEffect(() => { void isBiometricAvailable().then(setBioAvailable) }, [])

  const toggleLock = async (on: boolean) => {
    if (on) {
      // Confirma a digital ANTES de ativar — evita se trancar sem querer
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
        description="Suas credenciais de acesso à Astra."
      />

      <Row label="E-mail" hint="Usado pra login. Não é exibido publicamente.">
        <p className="text-sm font-mono text-(--text-2) m-0">{user?.email ?? '—'}</p>
      </Row>

      <Row label="Username" hint="Identificador único. @mention usa isso.">
        <p className="text-sm font-mono text-(--text-2) m-0">@{user?.username ?? '—'}</p>
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
