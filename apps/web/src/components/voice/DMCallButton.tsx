
import { useTranslation } from 'react-i18next'
import { PhoneCall } from 'lucide-react'
import { useDownloadApp } from '@/components/voice/DownloadAppDialog'

interface Props {
  otherDisplayName: string
}

export function DMCallButton({ otherDisplayName }: Props) {
  const { t }     = useTranslation()
  const baixarApp = useDownloadApp((s) => s.abrir)

  return (
    <button
      onClick={baixarApp}
      title={t('voice.callUser', { name: otherDisplayName })}
      className="flex items-center gap-2 px-3 h-9 border border-(--border) text-(--text-2) hover:border-(--accent) hover:text-(--accent) transition-colors text-sm"
    >
      <PhoneCall className="size-3.5" />
      <span className="hidden sm:inline">{t('voice.call')}</span>
    </button>
  )
}
