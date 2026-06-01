/**
 * ProfileCard — Nitro-style premium.
 *
 * Estrutura:
 *  - Banner 240px com rounded-bl/br-2xl (suavização)
 *  - Body sobe -mt-6 pra criar overlap com banner, rounded-tl/tr-2xl
 *  - Avatar grande flutuando -mt-20 sobre banner, ring accent
 *  - Hero: displayName XL serif + status pill compacto
 *  - Custom status: quote italic destacado
 *  - Selos: chip row flutuante (sem header "Selos")
 *  - Bio: editorial quote serif
 *  - Mutual servers: grid compacto com hover scale
 *
 * Animação: banner settle (scale 1.05→1), avatar pop-in spring,
 * cascata stagger das seções, hover micro nos mutuais.
 */
import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { MessageCircle, Pencil, Calendar, Users as UsersIcon, Crown, Shield, Sparkles, Quote } from 'lucide-react'
import { format } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { motion, type Variants } from 'motion/react'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import {
  Sheet, SheetContent, SheetTitle, SheetDescription,
} from '@/components/ui/sheet'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { Tooltip, TooltipTrigger, TooltipContent } from '@/components/ui/tooltip'
import StatusDot, { STATUS_META, type UserStatus } from '@/components/StatusDot'

// Cascata interna — chega depois do slide do Sheet.
const bodyVariants: Variants = {
  hidden:  { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.07, delayChildren: 0.22 } },
}
const sectionVariants: Variants = {
  hidden:  { opacity: 0, y: 14 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.42, ease: [0.16, 1, 0.3, 1] } },
}

interface PublicUser {
  id:          string
  username:    string
  displayName: string
  avatarUrl:   string | null
  bio:         string | null
  bannerUrl:   string | null
  bannerColor: string | null
  profileTheme?: string | null
  customStatus?: string | null
  isBot?:      boolean
  createdAt?:  string
  status?:     UserStatus
  effectiveStatus?: UserStatus
}
interface MutualServer { id: string; name: string; iconUrl: string|null; isGroup: boolean; role: string }

interface ProfileCardProps {
  userId:    string
  anchorEl?: HTMLElement | null
  onClose:   () => void
}

const PALETTE = ['#c9a96e','#7c6fc4','#6fa8c9','#c97c6e','#6ec98a']
function userColor(id: string) {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0
  return PALETTE[h % PALETTE.length]
}

function isGif(url: string) {
  return url.toLowerCase().endsWith('.gif') || url.includes('giphy') || url.includes('tenor')
}

/**
 * Fallback gradient (cada user deterministico via hash do id).
 * Usado se profileTheme não tiver sido configurado.
 * Mesma família dos presets Nitro disponíveis em ProfileSection.
 */
function nitroGradient(id: string) {
  const presets = [
    'linear-gradient(135deg,#ff6b9d,#ff9874,#ffd6a5)',  // Amanhecer
    'linear-gradient(135deg,#6e57e0,#4fc3f7,#00d4ff)',  // Cyber
    'linear-gradient(135deg,#3a1c71,#d76d77,#ffaf7b)',  // Aurora
    'linear-gradient(135deg,#8e2de2,#4a00e0,#f12711)',  // Plasma
    'linear-gradient(135deg,#2193b0,#6dd5ed)',          // Oceano
    'linear-gradient(135deg,#0f0c29,#302b63,#24243e)',  // Galáxia
    'linear-gradient(135deg,#41295a,#2f0743)',          // Veludo
    'linear-gradient(135deg,#11998e,#38ef7d)',          // Menta
  ]
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0
  return presets[h % presets.length]
}

export default function ProfileCard({ userId, onClose }: ProfileCardProps) {
  const currentUser = useAuthStore((s) => s.user)
  const navigate    = useNavigate()
  const [bannerError, setBannerError] = useState(false)
  const [avatarError, setAvatarError] = useState(false)

  const isSelf = userId === currentUser?.id

  const { data, isLoading } = useQuery<{ user: PublicUser; mutualServers: MutualServer[] }>({
    queryKey: ['profile', userId],
    queryFn:  async () => (await api.get(`/api/profile/${userId}`)).data.data,
    staleTime: 10_000,
    refetchOnMount: 'always',
  })
  const profile  = data?.user
  const mutuals  = data?.mutualServers ?? []

  useEffect(() => {
    setBannerError(false)
    setAvatarError(false)
  }, [profile?.bannerUrl, profile?.avatarUrl])

  const handleSendDM = async () => {
    if (!profile) return
    try {
      const res = await api.post(`/api/dm/open/${profile.username}`)
      const { conversationId, otherUser } = res.data.data
      navigate('/app/dm', { state: { conversationId, otherUser } })
    } catch {}
    onClose()
  }

  const accentColor   = profile?.id ? userColor(profile.id) : '#c9a96e'
  const fallbackTheme = profile?.id ? nitroGradient(profile.id) : 'linear-gradient(135deg,#1a1a2e,#16213e)'
  const themeBg       = profile?.profileTheme || fallbackTheme
  const bannerBg      = profile?.bannerUrl && !bannerError
    ? undefined
    : (profile?.bannerColor ?? fallbackTheme)

  return (
    <Sheet open onOpenChange={(o: boolean) => !o && onClose()}>
      <SheetContent
        side="right"
        className="p-0 overflow-y-auto gap-0 flex flex-col w-full sm:max-w-md bg-(--popover) data-[state=open]:[animation-duration:480ms] data-[state=closed]:[animation-duration:260ms] data-[state=open]:[animation-timing-function:cubic-bezier(0.16,1,0.3,1)] data-[state=closed]:[animation-timing-function:cubic-bezier(0.4,0,0.2,1)]"
      >
        {isLoading ? (
          <div className="flex-1 flex items-center justify-center gap-2 text-sm text-(--text-3)">
            <Spinner size={16} /> Carregando perfil…
            <SheetTitle className="sr-only">Carregando perfil</SheetTitle>
            <SheetDescription className="sr-only">Aguarde</SheetDescription>
          </div>
        ) : profile ? (
          <>
            <SheetTitle className="sr-only">Perfil de {profile.displayName}</SheetTitle>
            <SheetDescription className="sr-only">@{profile.username}</SheetDescription>

            {/* ── Banner ─────────────────────────────────── */}
            <motion.div
              initial={{ opacity: 0, scale: 1.06 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.7, delay: 0.08, ease: [0.16, 1, 0.3, 1] }}
              className="relative h-60 overflow-hidden shrink-0 rounded-bl-2xl rounded-br-2xl"
              style={{ background: bannerBg }}
            >
              {profile.bannerUrl && !bannerError && (
                <img
                  src={profile.bannerUrl}
                  alt=""
                  referrerPolicy="no-referrer"
                  onError={() => setBannerError(true)}
                  className="absolute inset-0 w-full h-full object-cover block"
                  style={{ willChange: isGif(profile.bannerUrl) ? 'contents' : 'auto' }}
                />
              )}
              {/* Gradient overlay pra contraste do texto sobre imagem */}
              <div className="absolute bottom-0 left-0 right-0 h-24 bg-linear-to-t from-black/55 to-transparent pointer-events-none" />
              <span className="ed-marg absolute top-4 left-6 text-white/80 z-10 drop-shadow-sm">
                Profil · No. {(profile.id ?? '').slice(-3).toUpperCase()}
              </span>
            </motion.div>

            {/* ── Body (sobe sobre banner, com gradient + overlay) ── */}
            <div
              className="flex-1 px-6 sm:px-7 pb-8 pt-0 relative -mt-6 rounded-tl-2xl rounded-tr-2xl"
              style={{ background: themeBg }}
            >
              {/* Overlay pra legibilidade — mantém vibe do gradient mas legível */}
              <div className="absolute inset-0 bg-(--popover)/88 backdrop-blur-md rounded-tl-2xl rounded-tr-2xl pointer-events-none" />

              <motion.div className="relative" variants={bodyVariants} initial="hidden" animate="visible">
                {/* ── Avatar row ───────────────────────────── */}
                <div className="flex items-end justify-between -mt-14 mb-4">
                  <motion.div
                    initial={{ opacity: 0, scale: 0.5, y: 20 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    transition={{ duration: 0.55, delay: 0.28, ease: [0.16, 1, 0.3, 1] }}
                    className="relative shrink-0"
                  >
                    <Avatar
                      className="size-28 rounded-full border-[5px] shadow-[0_8px_32px_-8px_rgba(0,0,0,0.6)]"
                      style={{ borderColor: 'var(--popover)', background: profile.isBot ? 'var(--accent-dim)' : accentColor + '22' }}
                    >
                      {profile.avatarUrl && !avatarError && (
                        <AvatarImage
                          src={profile.avatarUrl}
                          onError={() => setAvatarError(true)}
                          referrerPolicy="no-referrer"
                          style={{ willChange: isGif(profile.avatarUrl) ? 'contents' : 'auto' }}
                        />
                      )}
                      <AvatarFallback
                        style={{ color: profile.isBot ? 'var(--accent)' : accentColor, background: 'transparent' }}
                        className="text-3xl font-(family-name:--font-display) rounded-full"
                      >
                        {profile.isBot ? '🤖' : profile.displayName.slice(0,1).toUpperCase()}
                      </AvatarFallback>
                    </Avatar>
                    {!profile.isBot && profile.effectiveStatus && (
                      <span className="absolute bottom-1.5 right-1.5">
                        <StatusDot status={profile.effectiveStatus} size={22} bordered borderColor="var(--popover)" />
                      </span>
                    )}
                  </motion.div>

                  <motion.div variants={sectionVariants} className="mb-2">
                    {!isSelf && !profile.isBot && (
                      <Button
                        onClick={handleSendDM}
                        className="rounded-full h-10 px-5 gap-2 bg-(--accent) text-(--text-inv) font-medium tracking-wider uppercase text-[11px] hover:scale-105 hover:shadow-[0_8px_24px_var(--accent-glow)] transition-all duration-300 ease-(--ease-spring)"
                      >
                        <MessageCircle className="size-3.5" />
                        Mensagem
                      </Button>
                    )}
                    {isSelf && (
                      <span className="ed-marg flex items-center gap-1.5">
                        <Pencil className="size-3" /> Seu perfil
                      </span>
                    )}
                  </motion.div>
                </div>

                {/* ── Hero block: name + handle + status ──── */}
                <motion.header variants={sectionVariants} className="mb-5">
                  <div className="flex items-baseline flex-wrap gap-2 mb-1">
                    <h2
                      className="text-[2rem] font-normal tracking-tight m-0 leading-tight wrap-break-word"
                      style={{ fontFamily: 'var(--font-display)', color: accentColor }}
                    >
                      {profile.displayName}
                    </h2>
                    {profile.isBot && (
                      <span
                        className="text-[9px] font-bold uppercase tracking-widest px-2 py-0.5 rounded-full self-center"
                        style={{ background: 'var(--accent)', color: 'var(--text-inv)' }}
                      >
                        BOT
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <p className="font-mono text-xs text-(--text-3) m-0 tracking-wide">@{profile.username}</p>
                    {!profile.isBot && profile.effectiveStatus && (
                      <>
                        <span className="text-(--text-3) text-[10px]">·</span>
                        <span className="inline-flex items-center gap-1.5 text-[11px] text-(--text-2)">
                          <StatusDot status={profile.effectiveStatus} size={8} />
                          {STATUS_META[profile.effectiveStatus].label}
                        </span>
                      </>
                    )}
                  </div>
                </motion.header>

                {/* ── Custom status quote (se tiver) ──────── */}
                {profile.customStatus && (
                  <motion.div variants={sectionVariants} className="mb-5">
                    <div
                      className="relative rounded-xl px-4 py-3 border-l-2"
                      style={{
                        borderLeftColor: accentColor,
                        background: 'color-mix(in srgb, var(--raised) 60%, transparent)',
                      }}
                    >
                      <Quote className="absolute top-2 right-3 size-3 text-(--text-3) opacity-40" />
                      <p
                        className="m-0 text-sm italic leading-relaxed text-(--text-1)"
                        style={{ fontFamily: 'var(--font-display)' }}
                      >
                        {profile.customStatus}
                      </p>
                    </div>
                  </motion.div>
                )}

                {/* ── Selos (chip row flutuante, sem header) ── */}
                {!profile.isBot && (
                  <motion.div variants={sectionVariants} className="flex flex-wrap gap-1.5 mb-5">
                    <Chip icon={<Sparkles className="size-3" />} label="Early Reader" tone="accent" />
                    {profile.createdAt && new Date(profile.createdAt) < new Date(Date.now() - 30*24*60*60*1000) && (
                      <Chip icon={<Calendar className="size-3" />} label="30+ dias" />
                    )}
                  </motion.div>
                )}

                {/* ── Bio ─────────────────────────────────── */}
                <motion.section variants={sectionVariants} className="mb-5">
                  <span className="ed-label block mb-2">— Sobre</span>
                  {profile.bio ? (
                    <p
                      className="text-(--text-2) text-[14px] leading-[1.7] m-0 wrap-break-word"
                      style={{ fontFamily: 'var(--font-body)' }}
                    >
                      {profile.bio}
                    </p>
                  ) : (
                    <p className="text-(--text-3) text-sm italic m-0">
                      {isSelf ? 'Você ainda não escreveu uma bio.' : 'Sem bio ainda.'}
                    </p>
                  )}
                </motion.section>

                {/* ── Membro desde ─────────────────────────── */}
                {profile.createdAt && (
                  <motion.div variants={sectionVariants} className="mb-5">
                    <span className="ed-label block mb-1.5">— Membro desde</span>
                    <p className="text-(--text-2) text-sm m-0 flex items-center gap-2">
                      <Calendar className="size-3.5 text-(--text-3)" />
                      {format(new Date(profile.createdAt), "d 'de' MMMM 'de' yyyy", { locale: ptBR })}
                    </p>
                  </motion.div>
                )}

                {/* ── Servidores em comum (grid compacto) ─── */}
                {!isSelf && mutuals.length > 0 && (
                  <motion.div variants={sectionVariants} className="mb-2">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="ed-label">— Em comum</span>
                      <span className="text-[10px] font-mono text-(--text-3)">{mutuals.length}</span>
                    </div>
                    <ul className="flex flex-wrap gap-2">
                      {mutuals.slice(0, 12).map((s) => (
                        <li key={s.id}>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <button
                                type="button"
                                className="relative size-10 rounded-xl border border-(--border) bg-(--raised) overflow-hidden flex items-center justify-center text-[10px] font-bold transition-all duration-300 ease-(--ease-spring) hover:scale-110 hover:-translate-y-0.5 hover:border-(--accent) hover:shadow-[0_4px_16px_rgba(0,0,0,0.4)] cursor-pointer"
                              >
                                {s.iconUrl
                                  ? <img src={s.iconUrl} alt="" referrerPolicy="no-referrer" className="w-full h-full object-cover" />
                                  : <span style={{ fontFamily: 'var(--font-display)' }}>{s.name.slice(0,2).toUpperCase()}</span>}
                                {(s.role === 'OWNER' || s.role === 'ADMIN' || s.isGroup) && (
                                  <span className="absolute -bottom-0.5 -right-0.5 size-4 rounded-full bg-(--popover) border border-(--border) grid place-items-center">
                                    {s.role === 'OWNER' && <Crown className="size-2.5 text-(--accent)" />}
                                    {s.role === 'ADMIN' && <Shield className="size-2.5 text-(--text-2)" />}
                                    {s.isGroup && s.role !== 'OWNER' && s.role !== 'ADMIN' && <UsersIcon className="size-2.5 text-(--text-3)" />}
                                  </span>
                                )}
                              </button>
                            </TooltipTrigger>
                            <TooltipContent side="top">{s.name}</TooltipContent>
                          </Tooltip>
                        </li>
                      ))}
                    </ul>
                  </motion.div>
                )}

                {isSelf && (
                  <motion.p variants={sectionVariants} className="ed-marg text-center mt-2">
                    Use o botão <Pencil className="size-3 inline align-middle" /> no rodapé pra editar perfil
                  </motion.p>
                )}
              </motion.div>
            </div>
          </>
        ) : (
          <div className="p-10 text-center">
            <SheetTitle className="sr-only">Perfil não encontrado</SheetTitle>
            <SheetDescription>Esse usuário pode ter sido removido.</SheetDescription>
          </div>
        )}
      </SheetContent>
    </Sheet>
  )
}

/** Chip flutuante pra selos/badges no hero. */
function Chip({ icon, label, tone }: { icon: React.ReactNode; label: string; tone?: 'accent' }) {
  return (
    <span
      className={`inline-flex items-center gap-1 text-[10px] uppercase font-bold tracking-wider px-2 py-1 rounded-full border ${
        tone === 'accent'
          ? 'border-(--accent)/40 text-(--accent) bg-(--accent)/10'
          : 'border-(--border) text-(--text-2) bg-(--raised)/60'
      }`}
    >
      {icon} {label}
    </span>
  )
}
