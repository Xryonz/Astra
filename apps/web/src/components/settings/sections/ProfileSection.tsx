
import { useRef, useState, useMemo, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Upload } from 'lucide-react'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { UpdateProfileSchema } from '@astra/types'
import { SectionHeader, Row, SaveStatus } from './_shared'

const ALLOWED_MIMES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']

export default function ProfileSection() {
  const { t }       = useTranslation()
  const user        = useAuthStore((s) => s.user)
  const updateUser  = useAuthStore((s) => s.updateUser)
  const queryClient = useQueryClient()

  const [bio,          setBio]          = useState(user?.bio ?? '')
  const [avatarUrl,    setAvatarUrl]    = useState(user?.avatarUrl ?? '')
  const [pronouns,     setPronouns]     = useState<string>((user as any)?.pronouns ?? '')
  const [statusEmoji,  setStatusEmoji]  = useState<string>((user as any)?.statusEmoji ?? '')

  const [fileError,    setFileError]    = useState('')
  const [avatarImgErr, setAvatarImgErr] = useState(false)
  const [saveStatus,   setSaveStatus]   = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const [saveError,    setSaveError]    = useState('')

  const avatarFileRef = useRef<HTMLInputElement>(null)

  const readImageAsDataUri = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (!ALLOWED_MIMES.includes(file.type)) {
      setFileError(t('settings.profile.formatError'))
      return
    }
    if (file.size > 5 * 1024 * 1024) {
      setFileError(t('settings.profile.sizeError'))
      return
    }
    setFileError('')
    const reader = new FileReader()
    reader.onload = (ev) => { setAvatarUrl(ev.target?.result as string); setAvatarImgErr(false) }
    reader.readAsDataURL(file)
    e.target.value = ''
  }

  const errors = useMemo(() => {
    const candidate = {
      bio:         bio !== '' ? bio : null,
      avatarUrl:   avatarUrl || null,
      pronouns:    pronouns || null,
      statusEmoji: statusEmoji || null,
    }
    const result = UpdateProfileSchema.safeParse(candidate)
    if (result.success) return {} as Record<string, string>
    const map: Record<string, string> = {}
    for (const issue of result.error.issues) map[issue.path.join('.')] = issue.message
    return map
  }, [bio, avatarUrl, pronouns, statusEmoji])

  const updateProfile = useMutation({
    mutationFn: async () => {
      const initial = {
        bio:         user?.bio         ?? '',
        avatarUrl:   user?.avatarUrl   ?? '',
        pronouns:    (user as any)?.pronouns ?? '',
        statusEmoji: (user as any)?.statusEmoji ?? '',
      }
      const payload: Record<string, unknown> = {}
      if (bio         !== initial.bio)         payload.bio         = bio !== '' ? bio : null
      if (avatarUrl   !== initial.avatarUrl)   payload.avatarUrl   = avatarUrl   || null
      if (pronouns    !== initial.pronouns)    payload.pronouns    = pronouns    || null
      if (statusEmoji !== initial.statusEmoji) payload.statusEmoji = statusEmoji || null
      if (Object.keys(payload).length === 0) return null

      const res = await api.patch('/api/profile', payload)
      return res.data.data.user
    },
    onSuccess: (u) => {
      if (!u) { setSaveStatus('idle'); return }
      updateUser(u)
      queryClient.invalidateQueries({ queryKey: ['profile', u.id] })
      queryClient.setQueryData(['profile', u.id], u)
      setSaveStatus('saved')
      setSaveError('')
      setTimeout(() => setSaveStatus('idle'), 2200)
    },
    onError: (e: any) => {
      setSaveError(e.response?.data?.error ?? e.message ?? t('settings.save.error'))
      setSaveStatus('error')
    },
  })

  useEffect(() => {
    if (Object.keys(errors).length > 0) return
    const t = setTimeout(() => {
      setSaveStatus('saving')
      updateProfile.mutate()
    }, 800)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bio, avatarUrl, pronouns, statusEmoji])

  return (
    <div>
      <SectionHeader
        title={t('settings.profile.title')}
        description={t('settings.profile.description')}
      />

      <Row label={t('settings.profile.avatar')} hint={t('settings.profile.avatarHint')}>
        <div className="flex items-center gap-5 flex-wrap">
          <Avatar className="size-24 rounded-full border-2 border-(--border-mid)">
            {avatarUrl && !avatarImgErr && <AvatarImage src={avatarUrl} onError={() => setAvatarImgErr(true)} />}
            <AvatarFallback className="text-2xl font-(family-name:--font-display)">
              {(user?.displayName || user?.username || '?').slice(0, 1).toUpperCase()}
            </AvatarFallback>
          </Avatar>
          <div className="flex flex-col gap-2">
            <Button type="button" variant="outline" onClick={() => avatarFileRef.current?.click()} className="gap-2">
              <Upload className="size-4" /> {t('settings.profile.uploadPhoto')}
            </Button>
            {avatarUrl && (
              <Button type="button" variant="ghost" size="sm" onClick={() => setAvatarUrl('')}>
                {t('settings.profile.remove')}
              </Button>
            )}
          </div>
          <input
            ref={avatarFileRef} type="file" accept="image/jpeg,image/png,image/webp,image/gif"
            className="hidden"
            onChange={readImageAsDataUri}
          />
        </div>
        {fileError && <p className="text-xs text-(--danger) mt-2 m-0">{fileError}</p>}
        {errors.avatarUrl && <p className="text-xs text-(--danger) mt-2 m-0">{errors.avatarUrl}</p>}
      </Row>

      <Row label={t('settings.profile.pronouns')} hint={t('settings.profile.pronounsHint')}>
        <Input
          value={pronouns}
          onChange={(e) => setPronouns(e.target.value.slice(0, 32))}
          maxLength={32}
          placeholder={t('settings.profile.pronounsPlaceholder')}
        />
        {errors.pronouns && <p className="text-xs text-(--danger) mt-1 m-0">{errors.pronouns}</p>}
      </Row>

      <Row label={t('settings.profile.status')} hint={t('settings.profile.statusHint')}>
        <div className="flex items-stretch gap-2">
          <Input
            value={statusEmoji}
            onChange={(e) => setStatusEmoji(e.target.value.slice(0, 8))}
            maxLength={8}
            placeholder="🎮"
            className="w-16 text-center text-lg shrink-0"
            aria-label={t('settings.profile.statusEmojiLabel')}
          />
          <div className="flex-1">
            <CustomStatusEditor />
          </div>
        </div>
        {errors.statusEmoji && <p className="text-xs text-(--danger) mt-1 m-0">{errors.statusEmoji}</p>}
      </Row>

      <Row label={t('settings.profile.bio')} hint={t('settings.profile.bioHint')}>
        <Textarea
          value={bio}
          onChange={(e) => setBio(e.target.value)}
          maxLength={300}
          rows={4}
          placeholder={t('settings.profile.bioPlaceholder')}
        />
        <p className="text-marg text-(--text-3) mt-1 m-0 text-right">{bio.length}/300</p>
        {errors.bio && <p className="text-xs text-(--danger) m-0">{errors.bio}</p>}
      </Row>

      <div className="pt-4">
        <SaveStatus status={saveStatus} error={saveError} />
      </div>
    </div>
  )
}

function CustomStatusEditor() {
  const { t } = useTranslation()
  const user = useAuthStore((s) => s.user)
  const updateUser = useAuthStore((s) => s.updateUser)
  const [text, setText]    = useState((user as any)?.customStatus ?? '')
  const [status, setStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (text === ((user as any)?.customStatus ?? '')) return
    if (timerRef.current) clearTimeout(timerRef.current)
    setStatus('saving')
    timerRef.current = setTimeout(async () => {
      try {
        await api.patch('/api/friends/custom-status', { customStatus: text.trim() || null })
        updateUser({ ...(user ?? {}), customStatus: text.trim() || null } as any)
        setStatus('saved')
        setTimeout(() => setStatus('idle'), 1200)
      } catch {
        setStatus('error')
      }
    }, 600)
    return () => { if (timerRef.current) clearTimeout(timerRef.current) }
  }, [text])

  return (
    <div>
      <Input
        value={text}
        onChange={(e) => setText(e.target.value.slice(0, 100))}
        maxLength={100}
        placeholder={t('settings.profile.customStatusPlaceholder')}
      />
      <p className="text-marg text-(--text-3) mt-1 m-0 text-right">{text.length}/100</p>
      <SaveStatus status={status} />
    </div>
  )
}
