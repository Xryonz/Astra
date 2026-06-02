import { useRef, useState, useMemo, useEffect, useCallback } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Upload, Check, Sparkles, Zap, Droplet, Minus, RotateCcw } from 'lucide-react'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { GradientBuilder } from '@/components/settings/GradientBuilder'
import { cn } from '@/lib/utils'
import {
  UpdateProfileSchema, type BannerBorderStyle,
  type DisplayFont, type AvatarDecoration, type ProfileBackground,
} from '@umbra/types'
import { SectionHeader, Row, SaveStatus } from './_shared'

// 30 presets editoriais agrupados — varia de quentes solares até escuras moody.
// Cada gradient já vem com ângulo 135° = ponto de partida do GradientBuilder.
type GradientPreset = { id: string; label: string; value: string }
type GradientGroup  = { id: string; label: string; presets: GradientPreset[] }

const BANNER_GRADIENT_GROUPS: GradientGroup[] = [
  {
    id: 'warm', label: 'Quentes',
    presets: [
      { id: 'sunrise',  label: 'Amanhecer',  value: 'linear-gradient(135deg,#ff6b9d,#ff9874,#ffd6a5)' },
      { id: 'sunset',   label: 'Pôr-do-sol', value: 'linear-gradient(135deg,#fc4a1a,#f7b733)' },
      { id: 'ember',    label: 'Brasa',      value: 'linear-gradient(135deg,#ff5722,#ff9800,#ffc107)' },
      { id: 'magma',    label: 'Magma',      value: 'linear-gradient(135deg,#f12711,#f5af19)' },
      { id: 'coral',    label: 'Coral',      value: 'linear-gradient(135deg,#ff7e5f,#feb47b)' },
      { id: 'amber',    label: 'Âmbar',      value: 'linear-gradient(135deg,#d97706,#facc15)' },
      { id: 'saffron',  label: 'Açafrão',    value: 'linear-gradient(135deg,#ee9b00,#ca6702)' },
    ],
  },
  {
    id: 'cool', label: 'Frias',
    presets: [
      { id: 'ocean',    label: 'Oceano',     value: 'linear-gradient(135deg,#2193b0,#6dd5ed)' },
      { id: 'mist',     label: 'Bruma',      value: 'linear-gradient(135deg,#bdc3c7,#2c3e50)' },
      { id: 'lagoon',   label: 'Lagoa',      value: 'linear-gradient(135deg,#43cea2,#185a9d)' },
      { id: 'twilight', label: 'Crepúsculo', value: 'linear-gradient(135deg,#3a1c71,#4a00e0)' },
      { id: 'aurora',   label: 'Aurora',     value: 'linear-gradient(135deg,#3a1c71,#d76d77,#ffaf7b)' },
      { id: 'arctic',   label: 'Ártico',     value: 'linear-gradient(135deg,#a1c4fd,#c2e9fb)' },
    ],
  },
  {
    id: 'dark', label: 'Escuras',
    presets: [
      { id: 'galaxy',   label: 'Galáxia',    value: 'linear-gradient(135deg,#0f0c29,#302b63,#24243e)' },
      { id: 'velvet',   label: 'Veludo',     value: 'linear-gradient(135deg,#41295a,#2f0743)' },
      { id: 'ink',      label: 'Tinta',      value: 'linear-gradient(135deg,#000000,#0f3460)' },
      { id: 'charcoal', label: 'Carvão',     value: 'linear-gradient(135deg,#232526,#414345)' },
      { id: 'obsidian', label: 'Obsidiana',  value: 'linear-gradient(135deg,#000000,#1a4d2e)' },
      { id: 'onyx',     label: 'Ônix',       value: 'linear-gradient(135deg,#0c0c0c,#3c3c3c)' },
    ],
  },
  {
    id: 'nature', label: 'Natureza',
    presets: [
      { id: 'forest',   label: 'Floresta',   value: 'linear-gradient(135deg,#134e5e,#71b280)' },
      { id: 'moss',     label: 'Musgo',      value: 'linear-gradient(135deg,#5a7140,#a1aa6d)' },
      { id: 'mint',     label: 'Menta',      value: 'linear-gradient(135deg,#11998e,#38ef7d)' },
      { id: 'willow',   label: 'Salgueiro',  value: 'linear-gradient(135deg,#7a9e7e,#c8d5b9)' },
    ],
  },
  {
    id: 'vivid', label: 'Vívidas',
    presets: [
      { id: 'cyber',    label: 'Cyber',      value: 'linear-gradient(135deg,#6e57e0,#4fc3f7,#00d4ff)' },
      { id: 'plasma',   label: 'Plasma',     value: 'linear-gradient(135deg,#8e2de2,#4a00e0,#f12711)' },
      { id: 'neon',     label: 'Néon',       value: 'linear-gradient(135deg,#ff00cc,#333399)' },
      { id: 'wine',     label: 'Vinho',      value: 'linear-gradient(135deg,#6e0d25,#bd5734)' },
      { id: 'burgundy', label: 'Borgonha',   value: 'linear-gradient(135deg,#600000,#9c1f1f)' },
    ],
  },
  {
    id: 'soft', label: 'Suaves',
    presets: [
      { id: 'lavender', label: 'Lavanda',    value: 'linear-gradient(135deg,#8e44ad,#c39bd3)' },
      { id: 'petal',    label: 'Pétala',     value: 'linear-gradient(135deg,#ffafbd,#ffc3a0)' },
    ],
  },
]

const BANNER_GRADIENTS = BANNER_GRADIENT_GROUPS.flatMap((g) => g.presets)
const PROFILE_THEMES   = BANNER_GRADIENTS

const BANNER_BORDER_OPTIONS: { id: BannerBorderStyle; label: string; description: string; icon: React.ReactNode }[] = [
  { id: 'none',   label: 'Sem borda', description: 'Limpo, sem efeitos.',     icon: <Minus className="size-3.5" /> },
  { id: 'aurora', label: 'Aurora',    description: 'Anel rotativo policromo.', icon: <Sparkles className="size-3.5" /> },
  { id: 'pulse',  label: 'Pulso',     description: 'Borda pulsando accent.',   icon: <Zap className="size-3.5" /> },
  { id: 'ink',    label: 'Tinta',     description: 'Vinheta que respira.',     icon: <Droplet className="size-3.5" /> },
]

// 8 fontes editoriais curadas. CSS family vem direto do --font-display alternativo.
// Não importa fontes externas — usa apenas system fonts ou aliases já em CSS vars.
const DISPLAY_FONT_OPTIONS: { id: DisplayFont; label: string; family: string; preview: string }[] = [
  { id: 'serif',       label: 'Serif Display',  family: 'var(--font-display)',                    preview: 'Editorial clássico' },
  { id: 'sans',        label: 'Sans Limpa',      family: '-apple-system, ui-sans-serif, system-ui', preview: 'Limpo & moderno' },
  { id: 'mono',        label: 'Mono Técnica',    family: 'var(--font-mono)',                       preview: 'Identidade hacker' },
  { id: 'rounded',     label: 'Sans Arredondada', family: 'ui-rounded, "SF Pro Rounded", system-ui', preview: 'Amigável & soft' },
  { id: 'condensed',   label: 'Condensada',      family: '"Helvetica Neue Condensed", Impact, Arial Narrow, sans-serif', preview: 'Apertada & forte' },
  { id: 'handwriting', label: 'Manuscrita',      family: '"Brush Script MT", cursive',             preview: 'Pessoal & solto' },
  { id: 'gothic',      label: 'Gótica',          family: 'UnifrakturCook, "Times New Roman", serif', preview: 'Antiga & ritual' },
  { id: 'modern',      label: 'Geométrica',      family: 'Futura, "Avenir Next", "Trebuchet MS", sans-serif', preview: 'Geométrica & limpa' },
]

const AVATAR_DECORATION_OPTIONS: { id: AvatarDecoration; label: string; description: string }[] = [
  { id: 'none',    label: 'Nenhuma',   description: 'Avatar limpo, sem moldura.' },
  { id: 'halo',    label: 'Halo',      description: 'Coroa de luz estática suave.' },
  { id: 'ring',    label: 'Anel',      description: 'Conic gradient girando 10s.' },
  { id: 'thorns',  label: 'Espinhos',  description: '8 raios pontilhados em rotação.' },
  { id: 'orbit',   label: 'Órbita',    description: '2 pontos de luz orbitando.' },
  { id: 'pulse',   label: 'Pulso',     description: 'Anel sólido pulsando accent.' },
  { id: 'mosaic',  label: 'Mosaico',   description: 'Borda pixelada estática.' },
  { id: 'sigil',   label: 'Sigilo',    description: 'Marcas cardinais com glow.' },
]

const PROFILE_BG_OPTIONS: { id: ProfileBackground; label: string; description: string }[] = [
  { id: 'none',   label: 'Nenhum',   description: 'Apenas o tema do card.' },
  { id: 'aurora', label: 'Aurora',   description: 'Manchas que respiram 14s.' },
  { id: 'nebula', label: 'Nebulosa', description: 'Gradients se cruzando 30s.' },
  { id: 'mesh',   label: 'Malha',    description: 'Grade tracejada na diagonal.' },
  { id: 'rain',   label: 'Chuva',    description: 'Listras finas caindo 4.5s.' },
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
  const [bannerPositionY, setBannerPositionY] = useState<number>((user as any)?.bannerPositionY ?? 50)
  const [bannerScale,     setBannerScale]     = useState<number>((user as any)?.bannerScale     ?? 100)
  const [bannerBorder,    setBannerBorder]    = useState<BannerBorderStyle>(((user as any)?.bannerBorder ?? 'none') as BannerBorderStyle)
  const [pronouns,        setPronouns]        = useState<string>((user as any)?.pronouns ?? '')
  const [statusEmoji,     setStatusEmoji]     = useState<string>((user as any)?.statusEmoji ?? '')
  const [displayFont,     setDisplayFont]     = useState<DisplayFont>(((user as any)?.displayFont ?? 'serif') as DisplayFont)
  const [avatarDecoration, setAvatarDecoration] = useState<AvatarDecoration>(((user as any)?.avatarDecoration ?? 'none') as AvatarDecoration)
  const [profileBg,        setProfileBg]        = useState<ProfileBackground>(((user as any)?.profileBg ?? 'none') as ProfileBackground)
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
      bannerPositionY,
      bannerScale,
      bannerBorder,
      pronouns:        pronouns || null,
      statusEmoji:     statusEmoji || null,
      displayFont,
      avatarDecoration,
      profileBg,
    }
    const result = UpdateProfileSchema.safeParse(candidate)
    if (result.success) return {} as Record<string, string>
    const map: Record<string, string> = {}
    for (const issue of result.error.issues) map[issue.path.join('.')] = issue.message
    return map
  }, [displayName, username, bio, avatarUrl, bannerUrl, bannerColor, profileTheme, bannerPositionY, bannerScale, bannerBorder, pronouns, statusEmoji, displayFont, avatarDecoration, profileBg])

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
        bannerPositionY: (user as any)?.bannerPositionY ?? 50,
        bannerScale:     (user as any)?.bannerScale     ?? 100,
        bannerBorder:    (user as any)?.bannerBorder    ?? 'none',
        pronouns:         (user as any)?.pronouns ?? '',
        statusEmoji:      (user as any)?.statusEmoji ?? '',
        displayFont:      (user as any)?.displayFont ?? 'serif',
        avatarDecoration: (user as any)?.avatarDecoration ?? 'none',
        profileBg:        (user as any)?.profileBg ?? 'none',
      }
      const payload: Record<string, unknown> = {}
      if (displayName  !== initial.displayName)  payload.displayName  = displayName || undefined
      if (username     !== initial.username)     payload.username     = username    || undefined
      if (bio          !== initial.bio)          payload.bio          = bio !== '' ? bio : null
      if (avatarUrl    !== initial.avatarUrl)    payload.avatarUrl    = avatarUrl   || null
      if (bannerUrl    !== initial.bannerUrl)    payload.bannerUrl    = bannerUrl   || null
      if (bannerColor  !== initial.bannerColor)  payload.bannerColor  = bannerColor || null
      if (profileTheme !== initial.profileTheme) payload.profileTheme = profileTheme || null
      if (bannerPositionY !== initial.bannerPositionY) payload.bannerPositionY = bannerPositionY
      if (bannerScale     !== initial.bannerScale)     payload.bannerScale     = bannerScale
      if (bannerBorder    !== initial.bannerBorder)    payload.bannerBorder    = bannerBorder
      if (pronouns         !== initial.pronouns)         payload.pronouns         = pronouns || null
      if (statusEmoji      !== initial.statusEmoji)      payload.statusEmoji      = statusEmoji || null
      if (displayFont      !== initial.displayFont)      payload.displayFont      = displayFont
      if (avatarDecoration !== initial.avatarDecoration) payload.avatarDecoration = avatarDecoration
      if (profileBg        !== initial.profileBg)        payload.profileBg        = profileBg
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
  }, [displayName, username, bio, avatarUrl, bannerUrl, bannerColor, profileTheme, bannerPositionY, bannerScale, bannerBorder, pronouns, statusEmoji, displayFont, avatarDecoration, profileBg])

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

      <Row label="Pronomes" hint="Ex: ela/dela, ele/dele, elu/delu, they/them. Aparece como chip no card.">
        <Input
          value={pronouns}
          onChange={(e) => setPronouns(e.target.value.slice(0, 32))}
          maxLength={32}
          placeholder="ela/dela"
        />
        {errors.pronouns && <p className="text-xs text-(--danger) mt-1 m-0">{errors.pronouns}</p>}
      </Row>

      <Row label="Status atual" hint="Emoji + frase curta que aparece pros amigos. Some quando você troca ou apaga.">
        <div className="flex items-stretch gap-2">
          <Input
            value={statusEmoji}
            onChange={(e) => setStatusEmoji(e.target.value.slice(0, 8))}
            maxLength={8}
            placeholder="🎮"
            className="w-16 text-center text-lg shrink-0"
            aria-label="Emoji do status"
          />
          <div className="flex-1">
            <CustomStatusEditor />
          </div>
        </div>
        {errors.statusEmoji && <p className="text-xs text-(--danger) mt-1 m-0">{errors.statusEmoji}</p>}
      </Row>

      <Row label="Bio" hint="Até 300 caracteres. Markdown: **negrito** · *itálico* · `código` · [texto](url) · quebra de linha.">
        <Textarea
          value={bio}
          onChange={(e) => setBio(e.target.value)}
          maxLength={300}
          rows={4}
          placeholder="Algo sobre você… use **negrito**, *itálico*, ou [link](https://...)"
        />
        <p className="text-[11px] text-(--text-3) mt-1 m-0 text-right">{bio.length}/300</p>
        {errors.bio && <p className="text-xs text-(--danger) m-0">{errors.bio}</p>}
      </Row>

      <Row label="Fonte do nome" hint="Tipografia do displayName e bio no card. 8 famílias curadas.">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          {DISPLAY_FONT_OPTIONS.map((f) => (
            <button
              key={f.id}
              type="button"
              onClick={() => setDisplayFont(f.id)}
              className={cn(
                'group relative flex flex-col items-start gap-1 p-3 rounded-lg border text-left transition-all hover:scale-[1.02] cursor-pointer',
                displayFont === f.id
                  ? 'border-(--accent) bg-(--accent)/8 ring-1 ring-(--accent)/40'
                  : 'border-(--border-mid) hover:border-(--accent)/60',
              )}
            >
              <span className="text-[10px] uppercase tracking-wider text-(--text-3) font-mono">{f.label}</span>
              <span className="text-base leading-tight text-(--text-1)" style={{ fontFamily: f.family }}>
                {f.preview}
              </span>
              {displayFont === f.id && <Check className="absolute top-2 right-2 size-3.5 text-(--accent)" />}
            </button>
          ))}
        </div>
      </Row>

      <Row label="Decoração do avatar" hint="Moldura animada em volta da foto. 8 opções, todas GPU-leves.">
        <div className="grid grid-cols-4 sm:grid-cols-8 gap-2.5">
          {AVATAR_DECORATION_OPTIONS.map((d) => (
            <button
              key={d.id}
              type="button"
              onClick={() => setAvatarDecoration(d.id)}
              title={d.description}
              className={cn(
                'flex flex-col items-center gap-1.5 p-2 rounded-lg border transition-all hover:scale-105 cursor-pointer',
                avatarDecoration === d.id
                  ? 'border-(--accent) bg-(--accent)/8 ring-1 ring-(--accent)/40'
                  : 'border-(--border-mid) hover:border-(--accent)/60',
              )}
            >
              <div className={cn('avatar-deco-wrap', d.id !== 'none' && `avatar-deco-${d.id}`)}>
                <div className="size-9 rounded-full bg-(--raised) border border-(--border-mid) grid place-items-center text-[10px] font-mono text-(--text-3)">
                  AV
                </div>
              </div>
              <span className="text-[10px] text-(--text-3) text-center leading-tight">{d.label}</span>
            </button>
          ))}
        </div>
      </Row>

      <Row label="Fundo animado do card" hint="Camada CSS-only sobre o tema. Animação leve, GPU-direct.">
        <div className="grid grid-cols-3 sm:grid-cols-5 gap-2.5">
          {PROFILE_BG_OPTIONS.map((bg) => (
            <button
              key={bg.id}
              type="button"
              onClick={() => setProfileBg(bg.id)}
              title={bg.description}
              className={cn(
                'flex flex-col gap-2 p-2 rounded-lg border transition-all hover:scale-[1.03] cursor-pointer',
                profileBg === bg.id
                  ? 'border-(--accent) bg-(--accent)/8 ring-1 ring-(--accent)/40'
                  : 'border-(--border-mid) hover:border-(--accent)/60',
              )}
            >
              <div className="relative h-12 rounded-md overflow-hidden bg-(--popover)">
                {bg.id !== 'none' && <div className={cn('profile-bg', `profile-bg-${bg.id}`)} />}
              </div>
              <span className="text-[10px] text-(--text-3) text-center leading-tight">{bg.label}</span>
            </button>
          ))}
        </div>
      </Row>

      <Row label="Spotify" hint="Mostra a música que você tá ouvindo no seu perfil. Requer OAuth.">
        <SpotifyConnectStub />
      </Row>

      {/* ═══ Banner — bloco unificado com tabs pra dar respiro ═══ */}
      <Row label="Banner" hint="Imagem grande no topo do perfil. Customize fundo, posição e borda em abas separadas.">
        <div className="flex flex-col gap-5">
          {/* Preview SEMPRE visível — vai refletindo qualquer mudança nas tabs */}
          <BannerPreview
            bannerUrl={bannerUrl && !bannerImgErr ? bannerUrl : undefined}
            fallbackBg={bannerColor}
            positionY={bannerPositionY}
            scale={bannerScale}
            border={bannerBorder}
            onImgError={() => setBannerImgErr(true)}
          />

          <Tabs defaultValue="fundo" className="w-full">
            <TabsList className="grid grid-cols-3 w-full sm:w-auto sm:inline-flex">
              <TabsTrigger value="fundo">Fundo</TabsTrigger>
              <TabsTrigger value="ajuste" disabled={!bannerUrl || bannerImgErr}>Ajuste</TabsTrigger>
              <TabsTrigger value="borda">Borda</TabsTrigger>
            </TabsList>

            {/* ── FUNDO: upload + 30 presets + custom builder ───────── */}
            <TabsContent value="fundo" className="mt-6 flex flex-col gap-6">
              <div className="flex items-center gap-2 flex-wrap">
                <Button type="button" variant="outline" onClick={() => bannerFileRef.current?.click()} className="gap-2">
                  <Upload className="size-4" /> Enviar imagem
                </Button>
                {bannerUrl && (
                  <Button type="button" variant="ghost" size="sm" onClick={() => setBannerUrl('')}>
                    Remover imagem
                  </Button>
                )}
                <input
                  ref={bannerFileRef} type="file" accept="image/jpeg,image/png,image/webp,image/gif"
                  className="hidden"
                  onChange={(e) => readImageAsDataUri(e, setBannerUrl, () => setBannerImgErr(false))}
                />
              </div>
              {fileError && <p className="text-xs text-(--danger) m-0">{fileError}</p>}

              <div>
                <span className="ed-label block mb-3">— Gradient de fundo</span>
                <div className="flex flex-col gap-5">
                  {BANNER_GRADIENT_GROUPS.map((group) => (
                    <div key={group.id}>
                      <span className="ed-marg block mb-2.5">— {group.label}</span>
                      <div className="grid grid-cols-7 sm:grid-cols-10 gap-1.5">
                        {group.presets.map((g) => (
                          <button
                            key={g.id}
                            type="button"
                            onClick={() => { setBannerColor(g.value); setShowBannerBuilder(false) }}
                            className={cn(
                              'h-8 rounded-md border cursor-pointer transition-all hover:scale-110 relative grid place-items-center',
                              bannerColor === g.value ? 'border-(--accent) ring-1 ring-(--accent)/40' : 'border-(--border-mid) hover:border-(--accent)',
                            )}
                            style={{ background: g.value }}
                            title={g.label}
                          >
                            {bannerColor === g.value && <Check className="size-2.5 text-white drop-shadow-md" />}
                          </button>
                        ))}
                      </div>
                    </div>
                  ))}
                  <button
                    type="button"
                    onClick={() => setShowBannerBuilder((v) => !v)}
                    className={cn(
                      'h-11 rounded-lg border text-[11px] font-mono uppercase tracking-wider cursor-pointer transition-all hover:scale-[1.01]',
                      showBannerBuilder
                        ? 'border-(--accent) text-(--accent) bg-(--accent)/10'
                        : 'border-dashed border-(--border-mid) text-(--text-3) hover:border-(--accent) hover:text-(--accent)',
                    )}
                  >
                    {showBannerBuilder ? 'Fechar custom' : 'Custom gradient'}
                  </button>
                </div>
                {showBannerBuilder && (
                  <div className="mt-4 p-5 rounded-2xl border border-(--border-mid) bg-(--raised)/30">
                    <GradientBuilder value={bannerColor} onChange={setBannerColor} previewH={72} />
                  </div>
                )}
              </div>
            </TabsContent>

            {/* ── AJUSTE: position + zoom (só com imagem) ───────────── */}
            <TabsContent value="ajuste" className="mt-6">
              {bannerUrl && !bannerImgErr ? (
                <div className="flex flex-col gap-3">
                  <p className="text-xs text-(--text-3) m-0 leading-relaxed max-w-prose">
                    Arraste a imagem verticalmente pra escolher qual parte aparece. Use o slider de zoom pra dar close.
                  </p>
                  <BannerPositioner
                    bannerUrl={bannerUrl}
                    positionY={bannerPositionY}
                    scale={bannerScale}
                    onChange={(y, s) => { setBannerPositionY(y); setBannerScale(s) }}
                    onReset={() => { setBannerPositionY(50); setBannerScale(100) }}
                  />
                </div>
              ) : (
                <p className="text-sm text-(--text-3) italic m-0 py-8 text-center border border-dashed border-(--border-mid) rounded-xl">
                  Envie uma imagem na aba Fundo pra desbloquear ajuste de posição e zoom.
                </p>
              )}
            </TabsContent>

            {/* ── BORDA: 4 estilos animados ─────────────────────────── */}
            <TabsContent value="borda" className="mt-6">
              <p className="text-xs text-(--text-3) m-0 mb-4 leading-relaxed max-w-prose">
                Efeito visual ao redor do banner que aparece pra quem visita seu perfil. Aplicado em tempo real no preview acima.
              </p>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                {BANNER_BORDER_OPTIONS.map((opt) => (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => setBannerBorder(opt.id)}
                    className={cn(
                      'group relative flex flex-col items-start gap-1.5 p-4 rounded-xl border text-left transition-all hover:scale-[1.02] cursor-pointer min-h-20',
                      bannerBorder === opt.id
                        ? 'border-(--accent) bg-(--accent)/8 ring-1 ring-(--accent)/40'
                        : 'border-(--border-mid) hover:border-(--accent)/60',
                    )}
                  >
                    <span className="flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wider text-(--text-2)">
                      {opt.icon} {opt.label}
                    </span>
                    <span className="text-[10px] text-(--text-3) leading-tight">{opt.description}</span>
                    {bannerBorder === opt.id && (
                      <Check className="absolute top-2.5 right-2.5 size-3.5 text-(--accent)" />
                    )}
                  </button>
                ))}
              </div>
            </TabsContent>
          </Tabs>
        </div>
      </Row>

      {/* Profile theme */}
      <Row label="Tema do card de perfil" hint="Fundo do card que aparece quando alguém abre seu perfil.">
        <div className="flex flex-col gap-4">
          {BANNER_GRADIENT_GROUPS.map((group) => (
            <div key={group.id}>
              <span className="ed-marg block mb-2">— {group.label}</span>
              <div className="grid grid-cols-7 sm:grid-cols-10 gap-1.5">
                {group.presets.map((t) => (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => { setProfileTheme(t.value); setShowThemeBuilder(false) }}
                    className={cn(
                      'h-8 rounded-md border cursor-pointer transition-all hover:scale-110 relative grid place-items-center',
                      profileTheme === t.value ? 'border-(--accent) ring-1 ring-(--accent)/40' : 'border-(--border-mid) hover:border-(--accent)',
                    )}
                    style={{ background: t.value }}
                    title={t.label}
                  >
                    {profileTheme === t.value && <Check className="size-2.5 text-white drop-shadow-md" />}
                  </button>
                ))}
              </div>
            </div>
          ))}
          <button
            type="button"
            onClick={() => setShowThemeBuilder((v) => !v)}
            className={cn(
              'h-10 rounded-lg border text-[11px] font-mono uppercase tracking-wider cursor-pointer transition-all hover:scale-[1.01]',
              showThemeBuilder
                ? 'border-(--accent) text-(--accent) bg-(--accent)/10'
                : 'border-dashed border-(--border-mid) text-(--text-3) hover:border-(--accent) hover:text-(--accent)',
            )}
          >
            {showThemeBuilder ? 'Fechar custom' : 'Custom gradient'}
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

// ─── SpotifyConnectStub ─────────────────────────────────────
// Conectar Spotify via OAuth. Backend hoje retorna enabled=false sem
// SPOTIFY_CLIENT_ID setado → UI mostra estado "indisponível".
//
// Quando você ligar (env var + OAuth flow no backend), o botão abre
// /api/auth/spotify (redirect Spotify → callback salva refresh token).
function SpotifyConnectStub() {
  const [statusEnabled, setStatusEnabled] = useState<boolean | null>(null)
  const user      = useAuthStore((s) => s.user)
  const connected = !!(user as any)?.spotifyConnectedAt

  useEffect(() => {
    api.get('/api/profile/spotify/status')
      .then((r) => setStatusEnabled(!!r.data?.data?.enabled))
      .catch(() => setStatusEnabled(false))
  }, [])

  if (statusEnabled === null) {
    return <p className="text-xs text-(--text-3) italic m-0">Verificando…</p>
  }
  if (!statusEnabled) {
    return (
      <div className="rounded-lg border border-dashed border-(--border-mid) p-3 bg-(--raised)/30">
        <p className="text-xs text-(--text-3) m-0 leading-relaxed">
          Integração indisponível. Para ativar, o servidor precisa de <code className="font-mono text-(--text-2)">SPOTIFY_CLIENT_ID</code> e callback OAuth configurados.
        </p>
      </div>
    )
  }
  return (
    <div className="flex items-center gap-2">
      <Button
        type="button"
        variant={connected ? 'outline' : 'default'}
        onClick={() => { window.location.href = '/api/auth/spotify' }}
        className="gap-2"
      >
        {connected ? 'Reconectar Spotify' : 'Conectar Spotify'}
      </Button>
      {connected && (
        <span className="text-xs text-(--text-3) font-mono">✓ conectado</span>
      )}
    </div>
  )
}

// ─── BannerPreview ──────────────────────────────────────────
// Estática. Mostra o resultado final aplicando todas as 3 propriedades.
// Útil pra user ver o "como vai aparecer no perfil" enquanto edita.
function BannerPreview({
  bannerUrl, fallbackBg, positionY, scale, border, onImgError,
}: {
  bannerUrl?:  string
  fallbackBg:  string
  positionY:   number
  scale:       number
  border:      BannerBorderStyle
  onImgError:  () => void
}) {
  return (
    <div
      className={cn(
        'w-full h-44 sm:h-48 rounded-xl border border-(--border-mid) overflow-hidden relative',
        border !== 'none' && `banner-border-${border}`,
      )}
      style={!bannerUrl ? { background: fallbackBg } : undefined}
    >
      {bannerUrl && (
        <img
          src={bannerUrl}
          alt=""
          onError={onImgError}
          className="w-full h-full object-cover block"
          style={{
            objectPosition: `center ${positionY}%`,
            transform: `scale(${scale / 100})`,
            transformOrigin: 'center center',
          }}
        />
      )}
    </div>
  )
}

// ─── BannerPositioner ───────────────────────────────────────
// Interativo. Drag vertical no banner = ajusta objectPositionY.
// Slider abaixo controla zoom (100-200%). Botão reseta.
//
// Sensibilidade: 1px drag = (1 / wrapHeight * 100)% de positionY.
// Como wrap tem ~144px, 1px ≈ 0.7%. 100px de drag → cobre o range todo.
function BannerPositioner({
  bannerUrl, positionY, scale, onChange, onReset,
}: {
  bannerUrl: string
  positionY: number
  scale:     number
  onChange:  (positionY: number, scale: number) => void
  onReset:   () => void
}) {
  const wrapRef = useRef<HTMLDivElement>(null)
  const dragRef = useRef<{ startY: number; startPosY: number } | null>(null)

  const onPointerDown = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    e.preventDefault()
    e.currentTarget.setPointerCapture(e.pointerId)
    dragRef.current = { startY: e.clientY, startPosY: positionY }
  }, [positionY])

  const onPointerMove = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    if (!dragRef.current) return
    const wrap = wrapRef.current
    if (!wrap) return
    const dy = e.clientY - dragRef.current.startY
    // Drag pra baixo deve revelar a parte de CIMA da imagem → positionY ↓.
    const deltaPct = -dy / wrap.clientHeight * 100
    const newY = Math.max(0, Math.min(100, dragRef.current.startPosY + deltaPct))
    onChange(Math.round(newY), scale)
  }, [onChange, scale])

  const onPointerUp = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    if (e.currentTarget.hasPointerCapture(e.pointerId)) {
      e.currentTarget.releasePointerCapture(e.pointerId)
    }
    dragRef.current = null
  }, [])

  return (
    <div className="flex flex-col gap-3">
      <div
        ref={wrapRef}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        className="w-full h-44 rounded-xl border border-(--border-mid) overflow-hidden relative cursor-grab active:cursor-grabbing touch-none select-none"
      >
        <img
          src={bannerUrl}
          alt=""
          draggable={false}
          className="w-full h-full object-cover pointer-events-none"
          style={{
            objectPosition: `center ${positionY}%`,
            transform: `scale(${scale / 100})`,
            transformOrigin: 'center center',
          }}
        />
        <span className="ed-marg absolute top-2 left-2 text-white bg-black/45 px-2 py-1 rounded backdrop-blur-sm pointer-events-none">
          Arraste verticalmente
        </span>
        <span className="ed-marg absolute top-2 right-2 text-white bg-black/45 px-2 py-1 rounded backdrop-blur-sm pointer-events-none font-mono">
          Y {positionY}%
        </span>
      </div>
      <div className="flex items-center gap-3 flex-wrap">
        <div className="flex items-center gap-2 flex-1 min-w-50">
          <span className="ed-marg shrink-0">Zoom</span>
          <input
            type="range"
            min={100}
            max={200}
            step={5}
            value={scale}
            onChange={(e) => onChange(positionY, Number(e.target.value))}
            className="flex-1 accent-(--accent)"
          />
          <span className="text-[11px] font-mono text-(--text-3) w-10 text-right">{scale}%</span>
        </div>
        <Button type="button" variant="ghost" size="sm" onClick={onReset} className="gap-1.5">
          <RotateCcw className="size-3.5" /> Resetar
        </Button>
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
