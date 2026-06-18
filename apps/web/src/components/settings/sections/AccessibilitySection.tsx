import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Vibrate } from 'lucide-react'
import { Switch } from '@/components/ui/switch'
import { SectionHeader, Row } from './_shared'
import { isHapticsEnabled, setHapticsEnabled, hapticMedium } from '@/lib/haptics'
import { isNative } from '@/lib/native'

export default function AccessibilitySection() {
  const { t } = useTranslation()
  const [haptics, setHaptics] = useState(isHapticsEnabled())

  const toggleHaptics = (on: boolean) => {
    setHapticsEnabled(on)
    setHaptics(on)
    if (on) hapticMedium() // confirma com um toque ao ligar
  }

  return (
    <div>
      <SectionHeader
        title={t('settings.accessibility.title')}
        description={t('settings.accessibility.description')}
      />

      <Row
        label={t('settings.accessibility.haptics')}
        hint={isNative ? t('settings.accessibility.hapticsHint') : t('settings.accessibility.hapticsWebHint')}
      >
        <div className="flex items-center gap-2 self-start">
          <Vibrate className="size-4 text-(--text-3)" />
          <Switch
            checked={haptics}
            disabled={!isNative}
            onCheckedChange={(v: boolean) => toggleHaptics(v)}
          />
        </div>
      </Row>
    </div>
  )
}
