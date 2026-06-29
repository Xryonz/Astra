import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Bell, BellOff, BellRing } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { toast } from '@/components/ui/sonner'
import { usePushNotifications } from '@/hooks/usePushNotifications'
import { useNotificationPrefs, useUpdatePrefs, type NotificationPrefs } from '@/hooks/useNotifications'
import { cn } from '@/lib/utils'
import { SectionHeader, Row } from './_shared'

const HOURS = Array.from({ length: 24 }, (_, i) => i)

export default function NotificationsSection() {
  const { t } = useTranslation()
  const { state, subscribe, unsubscribe, sendTest, native } = usePushNotifications()
  const { data: prefsData } = useNotificationPrefs()
  const updatePrefs = useUpdatePrefs()

  const prefs = prefsData?.prefs

  const [localMute, setLocalMute] = useState<boolean>(() => localStorage.getItem('astra-sound') === '0')
  const toggleLocalMute = () => {
    const next = !localMute
    setLocalMute(next)
    localStorage.setItem('astra-sound', next ? '0' : '1')
  }

  const togglePref = (key: keyof NotificationPrefs) => () => {
    if (!prefs) return
    updatePrefs.mutate({ [key]: !prefs[key] } as Partial<NotificationPrefs>)
  }

  const updateQuiet = (key: 'quietStart' | 'quietEnd') => (e: React.ChangeEvent<HTMLSelectElement>) => {
    const v = e.target.value === '' ? null : parseInt(e.target.value, 10)
    updatePrefs.mutate({ [key]: v } as Partial<NotificationPrefs>)
  }

  const localTest = async () => {
    if (!('Notification' in window)) {
      toast.error(t('settings.notifications.toastNoSupport'))
      return
    }
    if (Notification.permission !== 'granted') {
      const p = await Notification.requestPermission()
      if (p !== 'granted') {
        toast.info(t('settings.notifications.toastPermDenied'))
        return
      }
    }
    new Notification(t('settings.notifications.testTitle'), {
      body: t('settings.notifications.testBody'),
      icon: '/astra-logo.png',
    })
  }

  return (
    <div>
      <SectionHeader
        title={t('settings.notifications.title')}
        description={t('settings.notifications.description')}
      />

      {}
      <Row label={t('settings.notifications.push')} hint={t('settings.notifications.pushHint')}>
        {state === 'unsupported' && (
          <div className="border border-(--border) bg-(--raised)/40 p-3 text-sm text-(--text-3)">
            {t('settings.notifications.unsupported')}
          </div>
        )}
        {state === 'server-disabled' && (
          <div className="border border-(--accent)/30 bg-(--raised)/40 p-3 text-sm">
            <p className="m-0 text-(--text-2)">
              {t('settings.notifications.serverDisabled')}
            </p>
          </div>
        )}
        {state === 'denied' && (
          <div className="border border-(--danger)/40 bg-(--danger)/5 p-3 text-sm">
            <p className="m-0 text-(--danger) flex items-center gap-2"><BellOff className="size-3.5" /> {t('settings.notifications.deniedTitle')}</p>
            <p className="m-0 mt-1 text-(--text-3) text-xs">
              {native
                ? t('settings.notifications.deniedNative')
                : t('settings.notifications.deniedWeb')}
            </p>
          </div>
        )}
        {state === 'unsubscribed' && (
          <div className="flex gap-2 flex-wrap">
            <Button onClick={subscribe} className="gap-2"><Bell className="size-4" /> {t('settings.notifications.enablePush')}</Button>
            {!native && <Button variant="outline" onClick={localTest} className="gap-2"><BellRing className="size-3.5" /> {t('settings.notifications.testLocal')}</Button>}
          </div>
        )}
        {state === 'subscribed' && (
          <div className="flex flex-col gap-2">
            <div className="border border-(--accent)/40 bg-(--accent-dim) px-3 py-2 text-sm flex items-center gap-2">
              <BellRing className="size-4 text-(--accent)" />
              <span className="text-(--accent) font-medium">{t('settings.notifications.enabled')}</span>
              <span className="text-(--text-3) text-xs">{t('settings.notifications.onThisDevice')}</span>
            </div>
            <div className="flex gap-2 flex-wrap">
              <Button variant="secondary" size="sm" onClick={sendTest}>{t('settings.notifications.testPush')}</Button>
              {!native && <Button variant="outline" size="sm" onClick={localTest}>{t('settings.notifications.testLocal')}</Button>}
              {!native && (
                <Button variant="outline" size="sm" onClick={unsubscribe} className="gap-2">
                  <BellOff className="size-3.5" /> {t('settings.notifications.disable')}
                </Button>
              )}
            </div>
            {native && (
              <p className="m-0 text-xs text-(--text-3)">
                {t('settings.notifications.nativeManage')}
              </p>
            )}
          </div>
        )}
        {state === 'loading' && (
          <div className="flex items-center gap-2 text-sm text-(--text-3)">
            <Spinner size={12} /> {t('settings.notifications.loading')}
          </div>
        )}
      </Row>

      {}
      <Row label={t('settings.notifications.types')} hint={t('settings.notifications.typesHint')}>
        {!prefs ? (
          <div className="flex items-center gap-2 text-xs text-(--text-3)">
            <Spinner size={12} /> {t('settings.notifications.loadingPrefs')}
          </div>
        ) : (
          <div className="grid sm:grid-cols-2 gap-2">
            <PrefToggle label={t('settings.notifications.mentions')} hint={t('settings.notifications.mentionsHint')} active={prefs.mentions}  onClick={togglePref('mentions')} />
            <PrefToggle label={t('settings.notifications.dms')}      hint={t('settings.notifications.dmsHint')}      active={prefs.dms}       onClick={togglePref('dms')} />
            <PrefToggle label={t('settings.notifications.replies')}  hint={t('settings.notifications.repliesHint')}  active={prefs.replies}   onClick={togglePref('replies')} />
            <PrefToggle label={t('settings.notifications.reactions')} hint={t('settings.notifications.reactionsHint')} active={prefs.reactions} onClick={togglePref('reactions')} />
          </div>
        )}
      </Row>

      {}
      <Row label={t('settings.notifications.sound')} hint={t('settings.notifications.soundHint')}>
        {prefs && (
          <div className="flex gap-2 flex-wrap">
            <button
              onClick={togglePref('sounds')}
              className={cn(
                'self-start px-3 h-9 border text-sm transition-colors cursor-pointer flex items-center gap-2',
                prefs.sounds
                  ? 'border-(--accent) bg-(--accent-dim) text-(--accent)'
                  : 'border-(--border) text-(--text-2) hover:border-(--accent) hover:text-(--accent)',
              )}
            >
              {prefs.sounds ? <BellRing className="size-3.5" /> : <BellOff className="size-3.5" />}
              {prefs.sounds ? t('settings.notifications.soundOn') : t('settings.notifications.soundOff')}
            </button>
            <button
              onClick={toggleLocalMute}
              className="self-start px-3 h-9 border border-(--border) text-(--text-2) hover:border-(--accent) hover:text-(--accent) text-sm transition-colors cursor-pointer flex items-center gap-2"
            >
              {localMute ? t('settings.notifications.mutedThisDevice') : t('settings.notifications.muteThisDevice')}
            </button>
          </div>
        )}
      </Row>

      {}
      <Row
        label={t('settings.notifications.quiet')}
        hint={t('settings.notifications.quietHint')}
      >
        {prefs && (
          <div className="flex items-end gap-3 flex-wrap">
            <HourPicker label={t('settings.notifications.start')} value={prefs.quietStart} onChange={updateQuiet('quietStart')} />
            <HourPicker label={t('settings.notifications.end')}   value={prefs.quietEnd}   onChange={updateQuiet('quietEnd')} />
            {prefs.quietStart != null && prefs.quietEnd != null && (
              <button
                onClick={() => updatePrefs.mutate({ quietStart: null, quietEnd: null })}
                className="h-9 px-3 text-xs text-(--text-3) hover:text-(--text-2) transition-colors"
              >
                {t('settings.notifications.clear')}
              </button>
            )}
          </div>
        )}
      </Row>
    </div>
  )
}

function PrefToggle({
  label, hint, active, onClick,
}: { label: string; hint: string; active: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'text-left p-3 border transition-colors cursor-pointer flex items-start gap-3',
        active
          ? 'border-(--accent)/40 bg-(--accent)/4'
          : 'border-(--border) hover:border-(--accent)/40',
      )}
    >
      <span className={cn(
        'mt-0.5 size-3.5 rounded-full border shrink-0 transition-colors',
        active ? 'bg-(--accent) border-(--accent)' : 'border-(--border-mid)',
      )} />
      <span className="flex-1 min-w-0">
        <span className={cn('block text-sm font-medium', active ? 'text-foreground' : 'text-(--text-2)')}>{label}</span>
        <span className="block text-xs text-(--text-3) mt-0.5">{hint}</span>
      </span>
    </button>
  )
}

function HourPicker({
  label, value, onChange,
}: { label: string; value: number | null; onChange: (e: React.ChangeEvent<HTMLSelectElement>) => void }) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-xs text-(--text-3)">{label}</span>
      <select
        value={value ?? ''}
        onChange={onChange}
        className="h-9 px-2 border border-(--border) bg-(--raised) text-sm text-foreground"
      >
        <option value="">—</option>
        {HOURS.map((h) => (
          <option key={h} value={h}>{String(h).padStart(2, '0')}:00</option>
        ))}
      </select>
    </label>
  )
}
