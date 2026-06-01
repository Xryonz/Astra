import { Button } from '@/components/ui/button'
import { Download, Trash2 } from 'lucide-react'
import { SectionHeader, Row } from './_shared'

/**
 * Dados pessoais — export/import/delete.
 * Placeholder enquanto endpoints não existem (LGPD/GDPR — fase M).
 */
export default function DataSection() {
  return (
    <div>
      <SectionHeader
        title="Dados"
        description="Controle dos seus dados pessoais — exportar, importar, excluir."
      />

      <Row label="Exportar meus dados" hint="Baixa um arquivo .json com mensagens, perfil e configurações.">
        <Button variant="outline" disabled className="gap-2 self-start" title="Em breve">
          <Download className="size-3.5" /> Exportar (em breve)
        </Button>
      </Row>

      <Row label="Excluir conta" hint="Apaga permanentemente sua conta e todas mensagens enviadas. Esta ação não pode ser desfeita.">
        <Button variant="outline" disabled className="gap-2 self-start text-(--danger) border-(--danger)/40 hover:bg-(--danger)/10" title="Em breve">
          <Trash2 className="size-3.5" /> Excluir conta (em breve)
        </Button>
      </Row>
    </div>
  )
}
