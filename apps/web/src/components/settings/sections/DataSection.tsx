import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { Download, Trash2 } from 'lucide-react'
import { SectionHeader, Row } from './_shared'

/**
 * Dados pessoais — export/import/delete.
 * Placeholder enquanto endpoints não existem (LGPD/GDPR — fase M).
 */
export default function DataSection() {
  const { t } = useTranslation()
  return (
    <div>
      <SectionHeader
        title={t('settings.data.title')}
        description={t('settings.data.description')}
      />

      <Row label={t('settings.data.export')} hint={t('settings.data.exportHint')}>
        <Button variant="outline" disabled className="gap-2 self-start" title={t('settings.data.soon')}>
          <Download className="size-3.5" /> {t('settings.data.exportBtn')}
        </Button>
      </Row>

      <Row label={t('settings.data.deleteAccount')} hint={t('settings.data.deleteAccountHint')}>
        <Button variant="outline" disabled className="gap-2 self-start text-(--danger) border-(--danger)/40 hover:bg-(--danger)/10" title={t('settings.data.soon')}>
          <Trash2 className="size-3.5" /> {t('settings.data.deleteAccountBtn')}
        </Button>
      </Row>
    </div>
  )
}
