/**
 * VoiceCallStage — vista expandida da chamada (Discord-style).
 *
 * Estrutura:
 *   ┌─────────────────────────────────────────┐
 *   │  Header: status + room name             │
 *   ├─────────────────────────────────────────┤
 *   │  [Screen share — full width]            │
 *   │  ┌────┐ ┌────┐ ┌────┐ ┌────┐            │
 *   │  │tile│ │tile│ │tile│ │tile│            │
 *   │  └────┘ └────┘ └────┘ └────┘            │
 *   ├─────────────────────────────────────────┤
 *   │  Volume slider                          │
 *   │  [Mic][Deafen][Screen][Minimizar][Sair] │
 *   └─────────────────────────────────────────┘
 *
 * Vibe Umbra: hairline borders, --accent subtle pulse no speaking,
 * mono pra tech info, serif display pros nomes, tokens consistentes.
 */
import { useEffect, useMemo, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'motion/react'
import {
  Mic, MicOff, Volume2, VolumeX, Volume1,
  ScreenShare, ScreenShareOff, PhoneOff, Minimize2,
} from 'lucide-react'
import { Track } from 'livekit-client'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { Tooltip, TooltipTrigger, TooltipContent } from '@/components/ui/tooltip'
import { Slider } from '@/components/ui/slider'
import { api, resolveApiUrl } from '@/lib/api'
import { cn } from '@/lib/utils'
import { useVoiceCall, parseRoomName, type CallParticipantInfo } from '@/hooks/useVoiceCall'

interface UserMini {
  id: string
  username: string
  displayName: string
  avatarUrl: string | null
  bannerColor: string | null
}

interface Props {
  onMinimize: () => void
}

export function VoiceCallStage({ onMinimize }: Props) {
  const { state, roomName, participants, error, deafened, volume, leave, toggleMic, toggleScreen, toggleDeafen, setVolume } = useVoiceCall()

  const identities = participants.map((p) => p.identity)
  const { data: users = [] } = useQuery<UserMini[]>({
    queryKey: ['voice', 'users', identities.sort().join(',')],
    queryFn: async () => {
      try {
        const res = await api.get(`/api/profile/lookup?ids=${encodeURIComponent(identities.join(','))}`)
        return res.data.data as UserMini[]
      } catch {
        return identities.map((id) => ({ id, username: id.slice(0, 6), displayName: id.slice(0, 6), avatarUrl: null, bannerColor: null }))
      }
    },
    enabled: identities.length > 0,
    staleTime: 60_000,
  })

  const userMap = useMemo(() => new Map(users.map((u) => [u.id, u])), [users])
  const parsed  = parseRoomName(roomName)
  const screenSharer = participants.find((p) => p.isScreenSharing)
  const localMic = participants.find((p) => p.isLocal)?.isMicEnabled ?? true
  const localShare = participants.find((p) => p.isLocal)?.isScreenSharing ?? false

  // Grid responsivo baseado em quantos participantes
  // (excluindo o screen sharer da grid principal quando há screen ativo)
  const tiles = screenSharer
    ? participants.filter((p) => p.identity !== screenSharer.identity)
    : participants

  // Tiles menores: mais colunas por padrão pra cada um ocupar menos área
  const gridCols =
    tiles.length === 1 ? 'grid-cols-1 max-w-md mx-auto'
    : tiles.length === 2 ? 'grid-cols-2 max-w-2xl mx-auto'
    : tiles.length <= 4 ? 'grid-cols-2 sm:grid-cols-3 max-w-4xl mx-auto'
    : tiles.length <= 6 ? 'grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 max-w-5xl mx-auto'
    : 'grid-cols-2 sm:grid-cols-4 lg:grid-cols-5'

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{    opacity: 0 }}
      transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
      className="fixed inset-0 z-70 flex flex-col bg-(--void) overflow-hidden"
    >
      {/* ─── Header ─── */}
      <motion.header
        initial={{ y: -16, opacity: 0 }}
        animate={{ y: 0,   opacity: 1 }}
        transition={{ duration: 0.4, delay: 0.05, ease: [0.16, 1, 0.3, 1] }}
        className="h-14 max-[640px]:landscape:h-11 px-5 max-[640px]:landscape:px-3 flex items-center gap-3 border-b border-(--border) bg-(--base)/80 backdrop-blur-md shrink-0"
      >
        <span className="size-2 rounded-full bg-(--success) animate-pulse shrink-0" aria-hidden />
        <div className="flex-1 min-w-0">
          <p className="text-sm m-0 font-(family-name:--font-display) text-foreground truncate leading-tight">
            {state === 'connecting' ? 'Conectando…'
              : state === 'connected' ? (parsed?.kind === 'channel' ? 'Canal de voz' : 'Chamada DM')
              : state === 'disconnecting' ? 'Desconectando…'
              : 'Erro'}
          </p>
          <p className="text-[11px] font-mono text-(--text-3) m-0 truncate">
            {participants.length} {participants.length === 1 ? 'participante' : 'participantes'}
            {screenSharer && ' · 1 compartilhando tela'}
          </p>
        </div>
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              onClick={onMinimize}
              className="size-9 rounded-lg border border-(--border-mid) text-(--text-2) hover:border-(--accent) hover:text-(--accent) transition-colors cursor-pointer grid place-items-center"
              aria-label="Minimizar"
            >
              <Minimize2 className="size-4" />
            </button>
          </TooltipTrigger>
          <TooltipContent side="bottom">Minimizar</TooltipContent>
        </Tooltip>
      </motion.header>

      {error && (
        <div className="px-5 py-2 text-xs text-(--danger) border-b border-(--danger)/30 bg-(--danger)/5 shrink-0">
          {error}
        </div>
      )}

      {/* ─── Main area ─── */}
      <div className="flex-1 overflow-y-auto p-4 sm:p-6 max-[640px]:landscape:p-2 flex flex-col gap-4 max-[640px]:landscape:gap-2">
        {/* Screen share — full width row */}
        {screenSharer && (
          <ScreenShareTile
            participant={screenSharer}
            user={userMap.get(screenSharer.identity)}
          />
        )}

        {/* Participant tiles grid */}
        <div className={cn('grid gap-3 sm:gap-4 flex-1 min-h-0', gridCols)}>
          <AnimatePresence initial={false}>
            {tiles.map((p, i) => (
              <ParticipantTile
                key={p.identity}
                index={i}
                participant={p}
                user={userMap.get(p.identity)}
              />
            ))}
          </AnimatePresence>
        </div>
      </div>

      {/* ─── Volume slider strip ─── */}
      <motion.div
        initial={{ y: 16, opacity: 0 }}
        animate={{ y: 0,  opacity: 1 }}
        transition={{ duration: 0.4, delay: 0.18, ease: [0.16, 1, 0.3, 1] }}
        className="px-5 py-2.5 max-[640px]:landscape:hidden border-t border-(--border) bg-(--base)/60 backdrop-blur-md flex items-center gap-3 shrink-0"
      >
        <button
          onClick={() => setVolume(volume > 0 ? 0 : 1)}
          disabled={deafened}
          title={volume === 0 ? 'Mudo' : `Volume ${Math.round(volume * 100)}%`}
          className={cn(
            'shrink-0 size-8 grid place-items-center text-(--text-3) hover:text-(--accent) transition-colors rounded-lg',
            deafened && 'opacity-40 cursor-not-allowed',
          )}
        >
          {volume === 0
            ? <VolumeX className="size-4" />
            : volume < 0.5
              ? <Volume1 className="size-4" />
              : <Volume2 className="size-4" />}
        </button>
        <span className="text-[10px] font-mono text-(--text-3) uppercase tracking-wider shrink-0 hidden sm:inline">
          Volume
        </span>
        <Slider
          value={[Math.round(volume * 100)]}
          onValueChange={(v) => setVolume((v[0] ?? 0) / 100)}
          min={0}
          max={100}
          step={1}
          disabled={deafened}
          aria-label="Volume da chamada"
          className="flex-1 max-w-xs"
        />
        <span className="text-[10px] font-mono text-(--text-2) tabular-nums w-9 text-right shrink-0">
          {Math.round(volume * 100)}%
        </span>
      </motion.div>

      {/* ─── Controls bar ─── */}
      <motion.footer
        initial={{ y: 20, opacity: 0 }}
        animate={{ y: 0,  opacity: 1 }}
        transition={{ duration: 0.45, delay: 0.22, ease: [0.16, 1, 0.3, 1] }}
        className="border-t border-(--border) bg-(--base) px-3 sm:px-4 py-3 sm:py-4 max-[640px]:landscape:py-1.5 pb-safe flex items-center justify-center gap-1.5 sm:gap-2 shrink-0"
      >
        <ControlButton
          label={localMic ? 'Mutar microfone' : 'Desmutar microfone'}
          onClick={toggleMic}
          active={!localMic}
          danger={!localMic}
        >
          {localMic ? <Mic className="size-5" /> : <MicOff className="size-5" />}
        </ControlButton>

        <ControlButton
          label={deafened ? 'Reabilitar áudio' : 'Mutar todos'}
          onClick={toggleDeafen}
          active={deafened}
          danger={deafened}
        >
          {deafened ? <VolumeX className="size-5" /> : <Volume2 className="size-5" />}
        </ControlButton>

        <ControlButton
          label={localShare ? 'Parar compartilhamento' : 'Compartilhar tela'}
          onClick={toggleScreen}
          active={localShare}
        >
          {localShare ? <ScreenShareOff className="size-5" /> : <ScreenShare className="size-5" />}
        </ControlButton>

        <div className="w-px h-8 bg-(--border) mx-1" aria-hidden />

        <ControlButton label="Sair da chamada" onClick={leave} danger primary>
          <PhoneOff className="size-5" />
        </ControlButton>
      </motion.footer>
    </motion.div>
  )
}

// ─── Participant tile ────────────────────────────────────────

// Extrai cor sólida do bannerColor (hex puro OU primeiro stop de um gradient).
// Fallback: --accent token.
function ringColorFrom(bannerColor: string | null | undefined): string {
  if (!bannerColor) return 'var(--accent)'
  // Hex puro
  if (/^#[0-9a-fA-F]{6}$/.test(bannerColor)) return bannerColor
  // Primeira cor de um linear-gradient
  const m = bannerColor.match(/#[0-9a-fA-F]{6}/)
  if (m) return m[0]
  return 'var(--accent)'
}

function ParticipantTile({ participant, user, index }: {
  participant: CallParticipantInfo
  user?: UserMini
  index: number
}) {
  const displayName = user?.displayName ?? participant.identity.slice(0, 8)
  const initials    = displayName.slice(0, 2).toUpperCase()
  const speaking    = participant.isSpeaking
  const muted       = !participant.isMicEnabled
  const ringColor   = ringColorFrom(user?.bannerColor)

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.92, y: 12 }}
      animate={{ opacity: 1, scale: 1,     y: 0 }}
      exit={{    opacity: 0, scale: 0.92, y: 12 }}
      transition={{ duration: 0.35, delay: index * 0.04, ease: [0.16, 1, 0.3, 1] }}
      className={cn(
        'relative aspect-video max-[640px]:landscape:aspect-square rounded-xl overflow-hidden border-2 transition-[border-color,box-shadow] duration-300',
        'bg-linear-to-br from-(--raised) to-(--base)',
        speaking
          ? 'border-(--accent) shadow-[0_0_0_2px_var(--accent-glow),0_6px_24px_-6px_var(--accent-glow)]'
          : 'border-(--border-mid)',
      )}
    >
      {/* Gradient backdrop (deterministic per user) */}
      <div
        aria-hidden
        className="absolute inset-0 opacity-25 mix-blend-overlay"
        style={{ background: userGradient(participant.identity) }}
      />

      {/* Speaking pulse ring (subtle, accent) */}
      {speaking && (
        <motion.div
          aria-hidden
          initial={{ opacity: 0.0 }}
          animate={{ opacity: [0.2, 0.5, 0.2] }}
          transition={{ duration: 1.6, repeat: Infinity, ease: 'easeInOut' }}
          className="absolute inset-0 ring-2 ring-(--accent) ring-inset rounded-xl pointer-events-none"
        />
      )}

      {/* Avatar centered — ring usa bannerColor do user */}
      <div className="absolute inset-0 flex items-center justify-center">
        <Avatar
          className={cn(
            'transition-transform duration-300',
            speaking ? 'scale-105' : 'scale-100',
            'size-14 sm:size-16 lg:size-20 rounded-full border-[3px] shadow-xl',
          )}
          style={{ borderColor: ringColor }}
        >
          {user?.avatarUrl
            ? <AvatarImage src={resolveApiUrl(user.avatarUrl)} alt={displayName} />
            : <AvatarFallback
                className="text-lg font-(family-name:--font-display)"
                style={{ background: ringColor + '22', color: ringColor }}
              >
                {initials}
              </AvatarFallback>}
        </Avatar>
      </div>

      {/* Top-right: mic indicator */}
      {muted && (
        <span className="absolute top-1.5 right-1.5 size-6 rounded-full bg-(--danger)/15 border border-(--danger)/40 grid place-items-center backdrop-blur-sm">
          <MicOff className="size-3 text-(--danger)" />
        </span>
      )}

      {/* Bottom overlay: name */}
      <div className="absolute bottom-0 left-0 right-0 px-2.5 py-1.5 bg-linear-to-t from-black/75 via-black/40 to-transparent flex items-center gap-2">
        <span
          className="text-xs font-(family-name:--font-display) text-white truncate flex-1 drop-shadow-md"
        >
          {displayName}
        </span>
        {participant.isLocal && (
          <span className="text-[9px] font-mono text-white/70 uppercase tracking-wider shrink-0">
            você
          </span>
        )}
      </div>
    </motion.div>
  )
}

// ─── Screen share tile ────────────────────────────────────────

function ScreenShareTile({ participant, user }: { participant: CallParticipantInfo; user?: UserMini }) {
  const videoRef = useRef<HTMLVideoElement>(null)

  useEffect(() => {
    const pub = participant.participant.getTrackPublications().find((t: any) => t.source === Track.Source.ScreenShare)
    const track = pub?.track
    if (!track || !videoRef.current) return
    try { track.attach(videoRef.current) } catch {}
    return () => { try { track.detach(videoRef.current!) } catch {} }
  }, [participant])

  const displayName = user?.displayName ?? participant.identity.slice(0, 8)

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.96 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
      className="relative w-full aspect-video rounded-2xl overflow-hidden border-2 border-(--accent) shadow-[0_8px_32px_-8px_var(--accent-glow)] bg-black"
    >
      <video ref={videoRef} autoPlay playsInline className="w-full h-full object-contain" />
      <div className="absolute top-2 left-2 px-2 py-1 rounded-md bg-black/60 backdrop-blur-md border border-(--accent)/40 flex items-center gap-1.5">
        <ScreenShare className="size-3 text-(--accent)" />
        <span className="text-[10px] font-mono text-white uppercase tracking-wider">
          {displayName} · compartilhando
        </span>
      </div>
    </motion.div>
  )
}

// ─── Control button (bottom toolbar) ──────────────────────────

function ControlButton({ label, onClick, children, danger, active, primary }: {
  label: string
  onClick: () => void
  children: React.ReactNode
  danger?: boolean
  active?: boolean
  primary?: boolean
}) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <motion.button
          onClick={onClick}
          whileTap={{ scale: 0.9 }}
          whileHover={{ scale: 1.06 }}
          transition={{ type: 'spring', stiffness: 600, damping: 22 }}
          className={cn(
            'size-11 sm:size-12 max-[640px]:landscape:size-9 rounded-full grid place-items-center border-2 transition-[background-color,border-color,box-shadow,color] duration-200 cursor-pointer',
            primary && danger
              ? 'border-(--danger) bg-(--danger) text-white hover:shadow-[0_8px_24px_-4px_rgba(239,68,68,0.5)]'
              : danger && active
                ? 'border-(--danger)/60 bg-(--danger)/15 text-(--danger)'
                : active
                  ? 'border-(--accent) bg-(--accent)/15 text-(--accent)'
                  : 'border-(--border-mid) bg-(--raised)/60 text-(--text-1) hover:border-(--accent) hover:text-(--accent)',
          )}
        >
          {children}
        </motion.button>
      </TooltipTrigger>
      <TooltipContent side="top">{label}</TooltipContent>
    </Tooltip>
  )
}

// ─── Helper: gradient backdrop deterministic per identity ─────

function userGradient(id: string): string {
  const presets = [
    'linear-gradient(135deg,#ff6b9d,#ff9874)',
    'linear-gradient(135deg,#6e57e0,#4fc3f7)',
    'linear-gradient(135deg,#ff5722,#ffc107)',
    'linear-gradient(135deg,#3a1c71,#d76d77)',
    'linear-gradient(135deg,#11998e,#38ef7d)',
    'linear-gradient(135deg,#8e44ad,#c39bd3)',
    'linear-gradient(135deg,#0f0c29,#302b63)',
    'linear-gradient(135deg,#41295a,#2f0743)',
  ]
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0
  return presets[h % presets.length]
}
