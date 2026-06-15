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
 * Vibe Astra: hairline borders, --accent subtle pulse no speaking,
 * mono pra tech info, serif display pros nomes, tokens consistentes.
 */
import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { motion, AnimatePresence } from 'motion/react'
import {
  Mic, MicOff, Volume2, VolumeX, Volume1,
  ScreenShare, ScreenShareOff, Video, VideoOff, PhoneOff, Minimize2, Wand2, Activity, Settings2,
} from 'lucide-react'
import { Track } from 'livekit-client'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { Tooltip, TooltipTrigger, TooltipContent } from '@/components/ui/tooltip'
import { Slider } from '@/components/ui/slider'
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover'
import { resolveApiUrl } from '@/lib/api'
import { cn } from '@/lib/utils'
import { useVoiceCall, parseRoomName, type CallParticipantInfo } from '@/hooks/useVoiceCall'
import { useUsersMini, type UserMini } from '@/hooks/useUsersMini'

interface Props {
  onMinimize: () => void
}

export function VoiceCallStage({ onMinimize }: Props) {
  const { t } = useTranslation()
  const { state, roomName, participants, error, deafened, volume, leave, toggleMic, toggleScreen, toggleCamera, toggleDeafen, setVolume, participantVolumes, setParticipantVolume, noiseFilter, toggleNoiseFilter, showStats, toggleStats } = useVoiceCall()

  const identities = participants.map((p) => p.identity)
  const { data: users = [] } = useUsersMini(identities)

  const userMap = useMemo(() => new Map(users.map((u) => [u.id, u])), [users])
  const parsed  = parseRoomName(roomName)
  const screenSharer = participants.find((p) => p.isScreenSharing)
  const localMic   = participants.find((p) => p.isLocal)?.isMicEnabled    ?? true
  const localShare = participants.find((p) => p.isLocal)?.isScreenSharing ?? false
  const localCam   = participants.find((p) => p.isLocal)?.isCameraEnabled ?? false

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
      {/* Ambiente: aura âmbar suave no topo — profundidade sem custo de animação. */}
      <div
        aria-hidden
        className="absolute inset-x-0 top-0 h-1/2 pointer-events-none opacity-60"
        style={{ background: 'radial-gradient(55% 100% at 50% 0%, var(--accent-glow), transparent 72%)' }}
      />

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
            {state === 'connecting' ? t('voice.connecting')
              : state === 'connected' ? (parsed?.kind === 'channel' ? t('voice.voiceChannel') : t('voice.dmCall'))
              : state === 'disconnecting' ? t('voice.disconnecting')
              : t('voice.error')}
          </p>
          <p className="text-[11px] font-mono text-(--text-3) m-0 truncate">
            {t('voice.participant', { count: participants.length })}
            {screenSharer && ` ${t('voice.sharingScreen')}`}
          </p>
        </div>
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              onClick={onMinimize}
              className="size-9 rounded-lg border border-(--border-mid) text-(--text-2) hover:border-(--accent) hover:text-(--accent) transition-colors cursor-pointer grid place-items-center"
              aria-label={t('voice.minimize')}
            >
              <Minimize2 className="size-4" />
            </button>
          </TooltipTrigger>
          <TooltipContent side="bottom" className="z-80">{t('voice.minimize')}</TooltipContent>
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

        {/* Participant tiles grid — auto-rows-fr faz as linhas dividirem a
            altura igualmente e os tiles PREENCHEREM (estilo Discord); antes
            os tiles aspect-video boiavam com vão morto entre eles. */}
        <div className={cn('grid gap-3 sm:gap-4 flex-1 min-h-0 auto-rows-fr', gridCols)}>
          <AnimatePresence initial={false}>
            {tiles.map((p, i) => (
              <ParticipantTile
                key={p.identity}
                index={i}
                participant={p}
                user={userMap.get(p.identity)}
                volume={participantVolumes[p.identity] ?? 1}
                onVolume={(v) => setParticipantVolume(p.identity, v)}
                showStats={showStats}
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
          title={volume === 0 ? t('voice.muteTitle') : t('voice.volumeTitle', { pct: Math.round(volume * 100) })}
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
          {t('voice.volume')}
        </span>
        <Slider
          value={[Math.round(volume * 100)]}
          onValueChange={(v) => setVolume((v[0] ?? 0) / 100)}
          min={0}
          max={100}
          step={1}
          disabled={deafened}
          aria-label={t('voice.volume')}
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
        className="border-t border-(--border) bg-(--base) px-3 sm:px-4 py-3 sm:py-4 max-[640px]:landscape:py-1.5 pb-safe flex flex-wrap items-center justify-center gap-1.5 sm:gap-2 shrink-0"
      >
        <ControlButton
          label={localMic ? t('voice.muteMic') : t('voice.unmuteMic')}
          onClick={toggleMic}
          active={!localMic}
          danger={!localMic}
        >
          {localMic ? <Mic className="size-5" /> : <MicOff className="size-5" />}
        </ControlButton>

        <ControlButton
          label={deafened ? t('voice.reenableAudio') : t('voice.muteAll')}
          onClick={toggleDeafen}
          active={deafened}
          danger={deafened}
        >
          {deafened ? <VolumeX className="size-5" /> : <Volume2 className="size-5" />}
        </ControlButton>

        <ControlButton
          label={noiseFilter ? t('voice.noiseOn') : t('voice.noiseOff')}
          onClick={toggleNoiseFilter}
          active={noiseFilter}
        >
          <Wand2 className="size-5" />
        </ControlButton>

        <ControlButton
          label={localCam ? t('voice.cameraOff') : t('voice.cameraOn')}
          onClick={toggleCamera}
          active={localCam}
        >
          {localCam ? <VideoOff className="size-5" /> : <Video className="size-5" />}
        </ControlButton>

        {/* Screen share só faz sentido em desktop (mobile não suporta
            getDisplayMedia na maioria dos browsers) — esconde no md:flex. */}
        <div className="hidden md:contents">
          <ControlButton
            label={localShare ? t('voice.stopShare') : t('voice.share')}
            onClick={toggleScreen}
            active={localShare}
          >
            {localShare ? <ScreenShareOff className="size-5" /> : <ScreenShare className="size-5" />}
          </ControlButton>
        </div>

        <CallSettings />

        <ControlButton
          label={showStats ? t('voice.hideStats') : t('voice.showStats')}
          onClick={toggleStats}
          active={showStats}
        >
          <Activity className="size-5" />
        </ControlButton>

        <div className="w-px h-8 bg-(--border) mx-1" aria-hidden />

        <ControlButton label={t('voice.leave')} onClick={leave} danger primary>
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

function ParticipantTile({ participant, user, index, volume, onVolume, showStats }: {
  participant: CallParticipantInfo
  user?: UserMini
  index: number
  volume?: number
  onVolume?: (v: number) => void
  showStats?: boolean
}) {
  const { t } = useTranslation()
  const displayName = user?.displayName ?? participant.identity.slice(0, 8)
  const initials    = displayName.slice(0, 2).toUpperCase()
  const speaking    = participant.isSpeaking
  const muted       = !participant.isMicEnabled
  const ringColor   = ringColorFrom(user?.bannerColor)
  const cameraOn    = participant.isCameraEnabled
  const videoRef    = useRef<HTMLVideoElement>(null)
  const lkParticipant = participant.participant

  // Video element fica SEMPRE montado (display:hidden quando câmera off).
  // useEffect só depende de lkParticipant — sync interno detecta on/off
  // via track events e attach/detach incrementalmente. Antes a dep
  // [cameraOn] re-rodava o effect a cada toggle → rebinda listeners.
  useEffect(() => {
    const video = videoRef.current
    if (!video) return
    let currentTrack: any = null

    const sync = () => {
      const pub = lkParticipant.getTrackPublications().find(
        (t: any) => t.source === Track.Source.Camera,
      )
      const next = pub?.track ?? null
      if (next === currentTrack) return
      if (currentTrack) { try { currentTrack.detach(video) } catch {} }
      currentTrack = next
      if (next) { try { next.attach(video) } catch {} }
    }
    sync()
    lkParticipant.on('trackSubscribed' as any,   sync)
    lkParticipant.on('trackUnsubscribed' as any, sync)
    lkParticipant.on('trackMuted' as any,        sync)
    lkParticipant.on('trackUnmuted' as any,      sync)
    lkParticipant.on('localTrackPublished' as any,   sync)
    lkParticipant.on('localTrackUnpublished' as any, sync)

    return () => {
      lkParticipant.off('trackSubscribed' as any,   sync)
      lkParticipant.off('trackUnsubscribed' as any, sync)
      lkParticipant.off('trackMuted' as any,        sync)
      lkParticipant.off('trackUnmuted' as any,      sync)
      lkParticipant.off('localTrackPublished' as any,   sync)
      lkParticipant.off('localTrackUnpublished' as any, sync)
      if (currentTrack) { try { currentTrack.detach(video) } catch {} }
    }
  }, [lkParticipant])

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.92, y: 12 }}
      animate={{ opacity: 1, scale: 1,     y: 0 }}
      exit={{    opacity: 0, scale: 0.92, y: 12 }}
      transition={{ duration: 0.35, delay: index * 0.04, ease: [0.16, 1, 0.3, 1] }}
      className={cn(
        'relative h-full w-full min-h-27.5 rounded-2xl overflow-hidden border-2 transition-[border-color,box-shadow] duration-300',
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
          className="absolute inset-0 ring-2 ring-(--accent) ring-inset rounded-2xl pointer-events-none"
        />
      )}

      {/* Câmera ligada: vídeo cobre o tile. Senão: avatar centralizado.
          videoRef SEMPRE montado (com display none quando off) — evita
          attach race condition entre mount/unmount do <video>. */}
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted={participant.isLocal}
        disablePictureInPicture
        className={cn(
          'absolute inset-0 w-full h-full object-cover',
          cameraOn ? 'block' : 'hidden',
        )}
      />
      {!cameraOn && (
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
      )}

      {/* Top-left: qualidade de conexão (sempre) + stats quando ligado */}
      <div className="absolute top-1.5 left-1.5 z-10">
        {showStats ? (
          <span className="px-1.5 py-0.5 rounded-md bg-black/60 backdrop-blur-sm flex items-center gap-1.5 text-[9px] font-mono text-white">
            <span className="size-2 rounded-full" style={{ background: qualityColor(participant.connectionQuality) }} />
            {participant.connectionQuality}
            {cameraOn && videoRef.current?.videoWidth ? ` · ${videoRef.current.videoWidth}×${videoRef.current.videoHeight}` : ''}
          </span>
        ) : (
          <span
            className="block size-2.5 rounded-full ring-2 ring-black/40"
            style={{ background: qualityColor(participant.connectionQuality) }}
            aria-label={t('voice.connection', { quality: participant.connectionQuality })}
          />
        )}
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
        {participant.isLocal ? (
          <span className="text-[9px] font-mono text-white/70 uppercase tracking-wider shrink-0">
            {t('voice.you')}
          </span>
        ) : onVolume ? (
          <TileVolume value={volume ?? 1} onChange={onVolume} />
        ) : null}
      </div>
    </motion.div>
  )
}

// ─── Per-person volume (popover slider no tile remoto) ────────

function TileVolume({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  const { t } = useTranslation()
  const pct = Math.round(value * 100)
  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          onClick={(e) => e.stopPropagation()}
          aria-label={t('voice.personVolume', { pct })}
          className={cn(
            'shrink-0 size-6 grid place-items-center rounded-md text-white/80 hover:text-white hover:bg-white/15 transition-colors',
            pct === 0 && 'text-(--danger)',
          )}
        >
          {pct === 0 ? <VolumeX className="size-3.5" /> : <Volume2 className="size-3.5" />}
        </button>
      </PopoverTrigger>
      <PopoverContent side="top" align="end" className="z-80 w-48 p-3">
        <p className="text-[10px] font-mono text-(--text-3) uppercase tracking-wider m-0 mb-2">{t('voice.personVolumeLabel')}</p>
        <div className="flex items-center gap-2">
          <VolumeX className="size-3.5 text-(--text-3) shrink-0" />
          <Slider
            value={[pct]}
            onValueChange={(v) => onChange((v[0] ?? 0) / 100)}
            min={0} max={100} step={1}
            aria-label={t('voice.personVolumeLabel')}
            className="flex-1"
          />
          <span className="text-[10px] font-mono text-(--text-2) tabular-nums w-8 text-right shrink-0">{pct}%</span>
        </div>
      </PopoverContent>
    </Popover>
  )
}

// ─── Screen share tile ────────────────────────────────────────

function ScreenShareTile({ participant, user }: { participant: CallParticipantInfo; user?: UserMini }) {
  const { t } = useTranslation()
  const videoRef = useRef<HTMLVideoElement>(null)
  // Ref do LK Participant é estável entre snapshots (snapshot reusa o mesmo
  // objeto p). Antes a dep [participant] (CallParticipantInfo recriado a cada
  // ActiveSpeakersChanged) disparava detach+attach a cada fala → flicker.
  const lkParticipant = participant.participant

  useEffect(() => {
    const video = videoRef.current
    if (!video) return
    let currentTrack: any = null

    const sync = () => {
      const pub = lkParticipant.getTrackPublications().find(
        (t: any) => t.source === Track.Source.ScreenShare,
      )
      const next = pub?.track ?? null
      if (next === currentTrack) return
      if (currentTrack) { try { currentTrack.detach(video) } catch {} }
      currentTrack = next
      if (next) { try { next.attach(video) } catch {} }
    }

    sync()
    lkParticipant.on('trackSubscribed' as any,   sync)
    lkParticipant.on('trackUnsubscribed' as any, sync)
    lkParticipant.on('trackMuted' as any,        sync)
    lkParticipant.on('trackUnmuted' as any,      sync)
    lkParticipant.on('localTrackPublished' as any,   sync)
    lkParticipant.on('localTrackUnpublished' as any, sync)

    return () => {
      lkParticipant.off('trackSubscribed' as any,   sync)
      lkParticipant.off('trackUnsubscribed' as any, sync)
      lkParticipant.off('trackMuted' as any,        sync)
      lkParticipant.off('trackUnmuted' as any,      sync)
      lkParticipant.off('localTrackPublished' as any,   sync)
      lkParticipant.off('localTrackUnpublished' as any, sync)
      if (currentTrack) { try { currentTrack.detach(video) } catch {} }
    }
  }, [lkParticipant])

  const displayName = user?.displayName ?? participant.identity.slice(0, 8)

  return (
    // Sem motion.layout aqui — quando outros tiles entravam/saíam, o
    // layout animation re-medida disparava reflow no video element →
    // flicker visível. Fade-in só na 1ª render é suficiente.
    <div
      className="relative w-full aspect-video rounded-2xl overflow-hidden border-2 border-(--accent) shadow-[0_8px_32px_-8px_var(--accent-glow)] bg-black anim-fade-in"
    >
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted
        disablePictureInPicture
        className="w-full h-full object-contain"
      />
      <div className="absolute top-2 left-2 flex items-center gap-1.5">
        {/* Pill AO VIVO (estilo Discord LIVE) — vermelha, dot pulsando. */}
        <span className="px-2 py-0.5 rounded-md bg-(--danger) text-white text-[10px] font-bold uppercase tracking-wider flex items-center gap-1 shadow-sm">
          <span className="size-1.5 rounded-full bg-white animate-pulse" />
          {t('voice.liveBadge')}
        </span>
        <span className="px-2 py-0.5 rounded-md bg-black/60 backdrop-blur-md border border-white/10 text-[10px] font-mono text-white flex items-center gap-1">
          <ScreenShare className="size-3 text-(--accent)" />
          {displayName}
        </span>
      </div>
    </div>
  )
}

// ─── Configurações da call (dispositivos + modo qualidade) ────

function CallSettings() {
  const { t } = useTranslation()
  const { audioInputId, audioOutputId, setAudioInput, setAudioOutput, screenQuality, setScreenQuality } = useVoiceCall()
  const [mics, setMics]         = useState<MediaDeviceInfo[]>([])
  const [speakers, setSpeakers] = useState<MediaDeviceInfo[]>([])
  const canSetSink = typeof HTMLMediaElement !== 'undefined' && 'setSinkId' in HTMLMediaElement.prototype

  const loadDevices = async () => {
    try {
      const devs = await navigator.mediaDevices.enumerateDevices()
      setMics(devs.filter((d) => d.kind === 'audioinput'))
      setSpeakers(devs.filter((d) => d.kind === 'audiooutput'))
    } catch { /* sem permissão / não suportado */ }
  }

  const selectCls = 'w-full mt-1 px-2 py-1.5 text-xs bg-(--raised) border border-(--border-mid) rounded-lg text-(--text-1) outline-none focus:border-(--accent) cursor-pointer'

  return (
    <Popover onOpenChange={(o) => { if (o) void loadDevices() }}>
      <PopoverTrigger asChild>
        <motion.button
          whileTap={{ scale: 0.9 }}
          whileHover={{ scale: 1.06 }}
          transition={{ type: 'spring', stiffness: 600, damping: 22 }}
          aria-label={t('voice.callSettings')}
          className="size-11 sm:size-12 max-[640px]:landscape:size-9 rounded-full grid place-items-center border-2 border-(--border-mid) bg-(--raised)/60 text-(--text-1) hover:border-(--accent) hover:text-(--accent) transition-[background-color,border-color,color] duration-200 cursor-pointer"
        >
          <Settings2 className="size-5" />
        </motion.button>
      </PopoverTrigger>
      <PopoverContent side="top" align="center" className="z-80 w-72 p-4 space-y-4">
        <div>
          <label className="text-[10px] font-mono uppercase tracking-wider text-(--text-3)">{t('voice.microphone')}</label>
          <select value={audioInputId ?? ''} onChange={(e) => void setAudioInput(e.target.value)} className={selectCls}>
            <option value="">{t('voice.systemDefault')}</option>
            {mics.map((d) => <option key={d.deviceId} value={d.deviceId}>{d.label || t('voice.microphone')}</option>)}
          </select>
        </div>

        {canSetSink && (
          <div>
            <label className="text-[10px] font-mono uppercase tracking-wider text-(--text-3)">{t('voice.speaker')}</label>
            <select value={audioOutputId ?? ''} onChange={(e) => void setAudioOutput(e.target.value)} className={selectCls}>
              <option value="">{t('voice.systemDefault')}</option>
              {speakers.map((d) => <option key={d.deviceId} value={d.deviceId}>{d.label || t('voice.speaker')}</option>)}
            </select>
          </div>
        )}

        <div>
          <span className="text-[10px] font-mono uppercase tracking-wider text-(--text-3)">{t('voice.streamQuality')}</span>
          <div className="flex gap-2 mt-1.5">
            <QualityChip active={screenQuality === 'motion'} onClick={() => setScreenQuality('motion')} title={t('voice.fluidity')} sub={t('voice.fluiditySub')} />
            <QualityChip active={screenQuality === 'detail'} onClick={() => setScreenQuality('detail')} title={t('voice.sharpness')} sub={t('voice.sharpnessSub')} />
          </div>
          <p className="text-[10px] text-(--text-3) m-0 mt-1.5">{t('voice.streamQualityHint')}</p>
        </div>
      </PopoverContent>
    </Popover>
  )
}

function QualityChip({ active, onClick, title, sub }: { active: boolean; onClick: () => void; title: string; sub: string }) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'flex-1 px-2 py-2 rounded-lg border text-left transition-colors cursor-pointer',
        active ? 'border-(--accent) bg-(--accent-dim)' : 'border-(--border-mid) hover:border-(--border-bright)',
      )}
    >
      <span className={cn('block text-xs font-medium', active ? 'text-(--accent)' : 'text-(--text-1)')}>{title}</span>
      <span className="block text-[10px] text-(--text-3)">{sub}</span>
    </button>
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
                ? 'border-(--danger) bg-(--danger) text-white shadow-[0_4px_16px_-4px_rgba(239,68,68,0.5)]'
                : active
                  ? 'border-(--accent) bg-(--accent) text-(--void) shadow-[0_4px_16px_-4px_var(--accent-glow)]'
                  : 'border-(--border-mid) bg-(--raised)/60 text-(--text-1) hover:border-(--accent) hover:text-(--accent)',
          )}
        >
          {children}
        </motion.button>
      </TooltipTrigger>
      <TooltipContent side="top" className="z-80">{label}</TooltipContent>
    </Tooltip>
  )
}

// ─── Helper: gradient backdrop deterministic per identity ─────

function qualityColor(q: string): string {
  return q === 'excellent' ? 'var(--success)'
    : q === 'good'         ? 'var(--accent)'
    : q === 'poor'         ? '#f59e0b'
    : q === 'lost'         ? 'var(--danger)'
    :                        'var(--text-3)'
}

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
