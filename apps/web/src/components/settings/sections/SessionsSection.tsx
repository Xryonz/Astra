import { Button } from '@/components/ui/button'
import { useAuth } from '@/hooks/useAuth'
import { LogOut, Shield } from 'lucide-react'
import { SectionHeader, Row } from './_shared'

/**
 * Sessões ativas. Por enquanto só permite "Sair desta sessão".
 * TODO: listar todas as sessões (refresh tokens não revogados) com IP/device,
 *       permitir revogar individualmente. Requer endpoint GET /api/auth/sessions.
 */
export default function SessionsSection() {
  const { logout } = useAuth()

  return (
    <div>
      <SectionHeader
        title="Sessões"
        description="Dispositivos onde você está logado no Umbra."
      />

      <Row label="Sessão atual" hint="Este navegador / dispositivo.">
        <div className="flex items-center gap-4 px-4 py-3.5 rounded-xl border border-(--accent)/40 bg-(--accent-dim)/40 backdrop-blur-sm">
          <div className="size-10 rounded-lg bg-(--accent)/15 border border-(--accent)/30 grid place-items-center shrink-0">
            <Shield className="size-4 text-(--accent)" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm m-0 font-medium text-foreground">Sessão ativa</p>
            <p className="text-marg text-(--text-3) m-0 mt-0.5 leading-relaxed">Token válido por até 7 dias (rotacionado a cada uso).</p>
          </div>
        </div>
      </Row>

      <Row label="Sair desta sessão" hint="Revoga só este token. Sessões em outros dispositivos continuam ativas.">
        <Button variant="outline" onClick={() => logout()} className="gap-2 self-start">
          <LogOut className="size-3.5" /> Sair
        </Button>
      </Row>

      <Row label="Outras sessões" hint="Em breve: lista de todos os dispositivos logados, com opção de revogar individualmente.">
        <p className="text-sm text-(--text-3) italic m-0">Funcionalidade em desenvolvimento.</p>
      </Row>
    </div>
  )
}
