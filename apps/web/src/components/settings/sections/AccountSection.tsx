import { useAuthStore } from '@/store/authStore'
import { useAuth } from '@/hooks/useAuth'
import { Button } from '@/components/ui/button'
import { LogOut } from 'lucide-react'
import { SectionHeader, Row } from './_shared'

export default function AccountSection() {
  const user = useAuthStore((s) => s.user)
  const { logout } = useAuth()

  return (
    <div>
      <SectionHeader
        title="Conta"
        description="Suas credenciais de acesso ao Umbra."
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

      <Row label="Sair desta sessão" hint="Revoga este token. Outras sessões em outros dispositivos ficam ativas.">
        <Button variant="outline" onClick={() => logout()} className="gap-2 self-start">
          <LogOut className="size-3.5" /> Sair
        </Button>
      </Row>
    </div>
  )
}
