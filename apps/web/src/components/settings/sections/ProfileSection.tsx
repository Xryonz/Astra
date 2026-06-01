import { useRef, useState, useMemo, useEffect } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Upload, Check } from 'lucide-react'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { GradientBuilder } from '@/components/settings/GradientBuilder'
import { cn } from '@/lib/utils'
import { UpdateProfileSchema } from '@umbra/types'
import { SectionHeader, Row, SaveStatus } from './_shared'

// Presets inspirados nos banners do Discord Nitro — vibrantes, com 2-3 stops.
// Cada um já vem com ângulo configurado pra servir de ponto-de-partida no GradientBuilder.
// Clica num preset → abre Custom → ajusta cor/ângulo a partir dali.
const BANNER_GRADIENTS = [
  { id: 'sunrise',   label: 'Amanhecer',  value: 'linear-gradient(135deg,#ff6b9d,#ff9874,#ffd6a5)' },
  { id: 'cyber',     label: 'Cyber',      value: 'linear-gradient(135deg,#6e57e0,#4fc3f7,#00d4ff)' },
  { id: 'ember',     label: 'Brasa',      value: 'linear-gradient(135deg,#ff5722,#ff9800,#ffc107)' },
  { id: 'aurora',    label: 'Aurora',     value: 'linear-gradient(135deg,#3a1c71,#d76d77,#ffaf7b)' },
  { id: 'plasma',    label: 'Plasma',     value: 'linear-gradient(135deg,#8e2de2,#4a00e0,#f12711)' },
  { id: 'sunset',    label: 'Pôr-do-sol', value: 'linear-gradient(135deg,#fc4a1a,#f7b733)' },
  { id: 'ocean',     label: 'Oceano',     value: 'linear-gradient(135deg,#2193b0,#6dd5ed)' },
  { id: 'galaxy',    label: 'Galáxia',    value: 'linear-gradient(135deg,#0f0c29,#302b63,#24243e)' },
  { id: 'mint',      label: 'Menta',      value: 'linear-gradient(135deg,#11998e,#38ef7d)' },
  { id: 'lavender',  label: 'Lavanda',    value: 'linear-gradient(135deg,#8e44ad,#c39bd3)' },
  { id: 'magma',     label: 'Magma',      value: 'linear-gradient(135deg,#f12711,#f5af19)' },
]

const PROFILE_THEMES = [
  { id: 'sunrise',   label: 'Amanhecer',  value: 'linear-gradient(135deg,#ff6b9d,#ff9874,#ffd6a5)' },
  { id: 'cyber',     label: 'Cyber',      value: 'linear-gradient(135deg,#6e57e0,#4fc3f7,#00d4ff)' },
  { id: 'ember',     label: 'Brasa',      value: 'linear-gradient(135deg,#ff5722,#ff9800,#ffc107)' },
  { id: 'aurora',    label: 'Aurora',     value: 'linear-gradient(135deg,#3a1c71,#d76d77,#ffaf7b)' },
  { id: 'plasma',    label: 'Plasma',     value: 'linear-gradient(135deg,#8e2de2,#4a00e0,#f12711)' },
  { id: 'velvet',    label: 'Veludo',     value: 'linear-gradient(135deg,#41295a,#2f0743)' },
  { id: 'ocean',     label: 'Oceano',     value: 'linear-gradient(135deg,#2193b0,#6dd5ed)' },
  { id: 'galaxy',    label: 'Galáxia',    value: 'linear-gradient(135deg,#0f0c29,#302b63,#24243e)' },
  { id: 'mint',      label: 'Menta',      value: 'linear-gradient(135deg,#11998e,#38ef7d)' },
  { id: 'lavender',  label: 'Lavanda',    value: 'linear-gradient(135deg,#8e44ad,#c39bd3)' },
  { id: 'magma',     label: 'Magma',      value: 'linear-gradient(135deg,#f12711,#f5af19)' },
]

const ALLOWED_MIMES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']

/**
 * Seção de perfil — displayName/username/bio + avatar + banner + theme.
 * Auto-save com debounce de 800ms quando o user para de digitar/altera.
 * Erros de validação do zod aparecem inline por campo.
 */
export default function ProfileSection() {
  const user        = useAuthStore((s) => s.user)
  const updateUser  = useAuthStore((s) => s.updateUser)
  const queryClient = useQueryClient()

  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [username,    setUsername]    = useState(user?.username ?? '')
  const [bio,         setBio]         = useState(user?.bio ?? '')
  const [avatarUrl,   setAvatarUrl]   = useState(user?.avatarUrl ?? '')
  const [bannerUrl,   setBannerUrl]   = useState((user as any)?.bannerUrl ?? '')
  const [bannerColor, setBannerColor] = useState((user as any)?.bannerColor ?? BANNER_GRADIENTS[0].value)
  const [profileTheme, setProfileTheme] = useState((user as any)?.profileTheme ?? PROFILE_THEMES[0].value)
  const [fileError,   setFileError]   = useState('')
  const [avatarImgErr, setAvatarImgErr] = useState(false)
  const [bannerImgErr, setBannerImgErr] = useState(false)
  const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const [saveError,  setSaveError]  = useState('')

  const [showBannerBuilder, setShowBannerBuilder] = useState(false)
  const [showThemeBuilder,  setShowThemeBuilder]  = useState(false)

  const bannerFileRef = useRef<HTMLInputElement>(null)
  const avatarFileRef = useRef<HTMLInputElement>(null)

  const readImageAsDataUri = (
    e: React.ChangeEvent<HTMLInputElement>,
    setUrl: (url: string) => void,
    clearImgError: () => void,
  ) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (!ALLOWED_MIMES.includes(file.type)) {
      setFileError('Formato não suportado. Use JPEG, PNG, WebP ou GIF.')
      return
    }
    if (file.size > 5 * 1024 * 1024) {
      setFileError('Arquivo muito grande. Máximo 5MB.')
      return
    }
    setFileError('')
    const reader = new FileReader()
    reader.onload = (ev) => {
      setUrl(ev.target?.result as string)
      clearImgError()
    }
    reader.readAsDataURL(file)
    e.target.value = ''
  }

  // Validação live via zod
  const errors = useMemo(() => {
    const candidate = {
      displayName:  displayName || undefined,
      username:     username    || undefined,
      bio:          bio !== '' ? bio : null,
      avatarUrl:    avatarUrl   || null,
      bannerUrl:    bannerUrl   || null,
      bannerColor:  bannerColor || null,
      profileTheme: profileTheme || null,
    }
    const result = UpdateProfileSchema.safeParse(candidate)
    if (result.success) return {} as Record<string, string>
    const map: Record<string, string> = {}
    for (const issue of result.error.issues) map[issue.path.join('.')] = issue.message
    return map
  }, [displayName, username, bio, avatarUrl, bannerUrl, bannerColor, profileTheme])

  const updateProfile = useMutation({
    mutationFn: async () => {
      const initial = {
        displayName:  user?.displayName ?? '',
        username:     user?.username    ?? '',
        bio:          user?.bio         ?? '',
        avatarUrl:    user?.avatarUrl   ?? '',
        bannerUrl:    (user as any)?.bannerUrl    ?? '',
        bannerColor:  (user as any)?.bannerColor  ?? '',
        profileTheme: (user as any)?.profileTheme ?? '',
      }
      const payload: Record<string, unknown> = {}
      if (displayName  !== initial.displayName)  payload.displayName  = displayName || undefined
      if (username     !== initial.username)     payload.username     = username    || undefined
      if (bio          !== initial.bio)          payload.bio          = bio !== '' ? bio : null
      if (avatarUrl    !== initial.avatarUrl)    payload.avatarUrl    = avatarUrl   || null
      if (bannerUrl    !== initial.bannerUrl)    payload.bannerUrl    = bannerUrl   || null
      if (bannerColor  !== initial.bannerColor)  payload.bannerColor  = bannerColor || null
      if (profileTheme !== initial.profileTheme) payload.profileTheme = profileTheme || null
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
      setSaveError(e.response?.data?.error ?? e.message ?? 'Erro ao salvar')
      setSaveStatus('error')
    },
  })

  // Debounced auto-save quando muda campo válido
  useEffect(() => {
    if (Object.keys(errors).length > 0) return
    const t = setTimeout(() => {
      setSaveStatus('saving')
      updateProfile.mutate()
    }, 800)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [displayName, username, bio, avatarUrl, bannerUrl, bannerColor, profileTheme])

  return (
    <div>
      <SectionHeader
        title="Perfil"
        description="Como você aparece pra outros membros — nome, bio, avatar, banner."
      />

      {/* Avatar + identidade */}
      <Row label="Avatar" hint="Recomendado 256×256 ou maior. Quadrado.">
        <div className="flex items-center gap-5 flex-wrap">
          <Avatar className="size-24 rounded-full border-2 border-(--border-mid)">
            {avatarUrl && !avatarImgErr && <AvatarImage src={avatarUrl} onError={() => setAvatarImgErr(true)} />}
            <AvatarFallback className="text-2xl font-(family-name:--font-display)">
              {(displayName || username || '?').slice(0, 1).toUpperCase()}
            </AvatarFallback>
          </Avatar>
          <div className="flex flex-col gap-2">
            <Button type="button" variant="outline" onClick={() => avatarFileRef.current?.click()} className="gap-2">
              <Upload className="size-4" /> Enviar foto
            </Button>
            {avatarUrl && (
              <Button type="button" variant="ghost" size="sm" onClick={() => setAvatarUrl('')}>
                Remover
              </Button>
            )}
          </div>
          <input
            ref={avatarFileRef} type="file" accept="image/jpeg,image/png,image/webp,image/gif"
            className="hidden"
            onChange={(e) => readImageAsDataUri(e, setAvatarUrl, () => setAvatarImgErr(false))}
          />
        </div>
        {errors.avatarUrl && <p className="text-xs text-(--danger) mt-2 m-0">{errors.avatarUrl}</p>}
      </Row>

      <Row label="Nome de exibição">
        <Input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={50} placeholder="Como quer ser chamado" />
        {errors.displayName && <p className="text-xs text-(--danger) mt-1 m-0">{errors.displayName}</p>}
      </Row>

      <Row label="Username" hint="Único. @mention usa isso.">
        <Input value={username} onChange={(e) => setUsername(e.target.value.toLowerCase())} maxLength={30} placeholder="seu_username" />
        {errors.username && <p className="text-xs text-(--danger) mt-1 m-0">{errors.username}</p>}
      </Row>

      <Row label="Status atual" hint="Frase curta que aparece pros amigos. Some quando você troca ou apaga.">
        <CustomStatusEditor />
      </Row>

      <Row label="Bio" hint="Linha curta sobre você. Até 200 caracteres.">
        <Textarea
          value={bio}
          onChange={(e) => setBio(e.target.value)}
          maxLength={200}
          rows={3}
          placeholder="Algo sobre você…"
        />
        <p className="text-[11px] text-(--text-3) mt-1 m-0 text-right">{bio.length}/200</p>
        {errors.bio && <p className="text-xs text-(--danger) m-0">{errors.bio}</p>}
      </Row>

      {/* Banner */}
      <Row label="Banner" hint="Imagem grande no topo do perfil. Opcional.">
        <div className="flex flex-col gap-4">
          <div
            className="w-full h-36 rounded-xl border border-(--border-mid) bg-cover bg-center overflow-hidden"
            style={{ background: bannerUrl && !bannerImgErr ? `url(${bannerUrl}) center/cover` : bannerColor }}
          >
            {bannerUrl && (
              <img src={bannerUrl} alt="" className="hidden" onError={() => setBannerImgErr(true)} />
            )}
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <Button type="button" variant="outline" onClick={() => bannerFileRef.current?.click()} className="gap-2">
              <Upload className="size-4" /> Enviar banner
            </Button>
            {bannerUrl && (
              <Button type="button" variant="ghost" size="sm" onClick={() => setBannerUrl('')}>
                Remover banner
              </Button>
            )}
          </div>
          <input
            ref={bannerFileRef} type="file" accept="image/jpeg,image/png,image/webp,image/gif"
            className="hidden"
            onChange={(e) => readImageAsDataUri(e, setBannerUrl, () => setBannerImgErr(false))}
          />
          {fileError && <p className="text-xs text-(--danger) m-0">{fileError}</p>}
        </div>
      </Row>

      <Row label="Cor de fundo do banner" hint="Gradient editorial — escolha presets ou customize totalmente.">
        <div className="grid grid-cols-3 sm:grid-cols-4 gap-3">
          {BANNER_GRADIENTS.map((g) => (
            <button
              key={g.id}
              type="button"
              onClick={() => { setBannerColor(g.value); setShowBannerBuilder(false) }}
              className={cn(
                'h-14 rounded-xl border cursor-pointer transition-all hover:scale-105',
                bannerColor === g.value ? 'border-(--accent) ring-2 ring-(--accent)/30' : 'border-(--border-mid) hover:border-(--accent)',
              )}
              style={{ background: g.value }}
              title={g.label}
            >
              {bannerColor === g.value && <Check className="size-4 text-white mx-auto drop-shadow-md" />}
            </button>
          ))}
          <button
            type="button"
            onClick={() => setShowBannerBuilder((v) => !v)}
            className={cn(
              'h-14 rounded-xl border text-[11px] font-mono uppercase tracking-wider cursor-pointer transition-all hover:scale-105',
              showBannerBuilder
                ? 'border-(--accent) text-(--accent) bg-(--accent)/10'
                : 'border-dashed border-(--border-mid) text-(--text-3) hover:border-(--accent) hover:text-(--accent)',
            )}
          >
            {showBannerBuilder ? 'Fechar' : 'Custom'}
          </button>
        </div>
        {showBannerBuilder && (
          <div className="mt-4 p-5 rounded-2xl border border-(--border-mid) bg-(--raised)/30">
            <GradientBuilder value={bannerColor} onChange={setBannerColor} previewH={72} />
          </div>
        )}
      </Row>

      {/* Profile theme */}
      <Row label="Tema do card de perfil" hint="Fundo do card que aparece quando alguém abre seu perfil.">
        <div className="grid grid-cols-3 sm:grid-cols-4 gap-3">
          {PROFILE_THEMES.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => { setProfileTheme(t.value); setShowThemeBuilder(false) }}
              className={cn(
                'h-14 rounded-xl border cursor-pointer transition-all hover:scale-105',
                profileTheme === t.value ? 'border-(--accent) ring-2 ring-(--accent)/30' : 'border-(--border-mid) hover:border-(--accent)',
              )}
              style={{ background: t.value }}
              title={t.label}
            >
              {profileTheme === t.value && <Check className="size-4 text-white mx-auto drop-shadow-md" />}
            </button>
          ))}
          <button
            type="button"
            onClick={() => setShowThemeBuilder((v) => !v)}
            className={cn(
              'h-14 rounded-xl border text-[11px] font-mono uppercase tracking-wider cursor-pointer transition-all hover:scale-105',
              showThemeBuilder
                ? 'border-(--accent) text-(--accent) bg-(--accent)/10'
                : 'border-dashed border-(--border-mid) text-(--text-3) hover:border-(--accent) hover:text-(--accent)',
            )}
          >
            {showThemeBuilder ? 'Fechar' : 'Custom'}
          </button>
        </div>
        {showThemeBuilder && (
          <div className="mt-4 p-5 rounded-2xl border border-(--border-mid) bg-(--raised)/30">
            <GradientBuilder value={profileTheme} onChange={setProfileTheme} previewH={88} />
          </div>
        )}
      </Row>

      <div className="pt-4">
        <SaveStatus status={saveStatus} error={saveError} />
      </div>
    </div>
  )
}

// ─── CustomStatusEditor (server-synced) ─────────────────────
function CustomStatusEditor() {
  const user = useAuthStore((s) => s.user)
  const updateUser = useAuthStore((s) => s.updateUser)
  const [text, setText]    = useState((user as any)?.customStatus ?? '')
  const [status, setStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Debounce 600ms — autoSave em onChange
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
        placeholder="Ex: Lendo um livro · Compilando · BRB"
      />
      <p className="text-[11px] text-(--text-3) mt-1 m-0 text-right">{text.length}/100</p>
      <SaveStatus status={status} />
    </div>
  )
}
