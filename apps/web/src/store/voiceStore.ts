
import { create } from 'zustand'
import i18n from '@/i18n'
import type {
  Room, RoomEvent as RoomEventT, Track as TrackT, ConnectionState as ConnectionStateT,
  LocalParticipant, Participant,
} from 'livekit-client'
import { api } from '@/lib/api'
import { setPipEnabled, setCallActive } from '@/lib/native'
import { playCallJoin, playCallLeave } from '@/lib/callSounds'

export type CallState = 'idle' | 'connecting' | 'connected' | 'disconnecting' | 'error'

export interface CallParticipantInfo {
  identity:        string
  isLocal:         boolean
  isSpeaking:      boolean
  isMicEnabled:    boolean
  isScreenSharing: boolean
  isCameraEnabled: boolean

  connectionQuality: string
  participant:     Participant
}

interface VoiceState {
  state:        CallState
  roomName:     string | null
  participants: CallParticipantInfo[]
  error:        string | null
  deafened:     boolean

  volume:       number

  join:         (kind: 'channel' | 'dm', id: string) => Promise<void>
  leave:        () => Promise<void>
  toggleMic:    () => Promise<void>
  toggleScreen: () => Promise<void>
  toggleCamera: () => Promise<void>
  toggleDeafen: () => void
  setVolume:    (v: number) => void

  participantVolumes: Record<string, number>
  setParticipantVolume: (identity: string, v: number) => void

  noiseFilter: boolean
  toggleNoiseFilter: () => void

  showStats: boolean
  toggleStats: () => void

  audioInputId:  string | null
  audioOutputId: string | null
  setAudioInput:  (id: string) => Promise<void>
  setAudioOutput: (id: string) => Promise<void>

  screenQuality: 'motion' | 'detail'
  setScreenQuality: (q: 'motion' | 'detail') => void
}

const VOLUME_STORAGE_KEY = 'astra-voice-volume'
function loadInitialVolume(): number {
  try {
    const v = localStorage.getItem(VOLUME_STORAGE_KEY)
    if (v === null) return 1
    const n = Number(v)
    return Number.isFinite(n) ? Math.max(0, Math.min(1, n)) : 1
  } catch { return 1 }
}

const PVOL_STORAGE_KEY = 'astra-voice-pvol'
function loadParticipantVolumes(): Record<string, number> {
  try {
    const raw = localStorage.getItem(PVOL_STORAGE_KEY)
    const obj = raw ? JSON.parse(raw) : null
    return obj && typeof obj === 'object' ? (obj as Record<string, number>) : {}
  } catch { return {} }
}

const NOISE_FILTER_KEY = 'astra-noise-filter'
function loadNoiseFilter(): boolean {
  try { return localStorage.getItem(NOISE_FILTER_KEY) !== '0' } catch { return true }
}

const DEV_IN_KEY = 'astra-voice-in'
const DEV_OUT_KEY = 'astra-voice-out'
const SCREEN_Q_KEY = 'astra-screen-quality'
function loadDev(key: string): string | null {
  try { return localStorage.getItem(key) || null } catch { return null }
}
function loadScreenQuality(): 'motion' | 'detail' {
  try { return localStorage.getItem(SCREEN_Q_KEY) === 'detail' ? 'detail' : 'motion' } catch { return 'motion' }
}

function humanizeMediaError(err: any): string | null {
  const name = err?.name ?? err?.error?.name
  const msg  = String(err?.message ?? '').toLowerCase()
  if (name === 'NotAllowedError' || msg.includes('permission denied')) {
    return i18n.t('voice.errPermission')
  }
  if (name === 'NotFoundError' || msg.includes('not found') || msg.includes('no device')) {
    return i18n.t('voice.errNotFound')
  }
  if (name === 'NotReadableError' || msg.includes('in use')) {
    return i18n.t('voice.errInUse')
  }
  if (name === 'NotSupportedError' || msg.includes('not supported')) {
    return i18n.t('voice.errNotSupported')
  }
  if (msg.includes('secure context') || msg.includes('https')) {
    return i18n.t('voice.errHttps')
  }
  return null
}

let activeRoom: Room | null = null

type LKNs = typeof import('livekit-client')
let lkNs: LKNs | null = null
async function loadLK(): Promise<LKNs> {
  if (!lkNs) lkNs = await import('livekit-client')
  return lkNs
}

async function applyMicNoiseFilter(enabled: boolean): Promise<void> {
  if (!activeRoom || !lkNs) return
  const pub = activeRoom.localParticipant.getTrackPublication(lkNs.Track.Source.Microphone)
  const track: any = (pub as any)?.audioTrack ?? pub?.track
  if (!track || typeof track.setProcessor !== 'function') return
  try {
    if (enabled) {
      const krisp = await import('@livekit/krisp-noise-filter')
      if (!krisp.isKrispNoiseFilterSupported()) return
      await track.setProcessor(krisp.KrispNoiseFilter())
    } else if (typeof track.stopProcessor === 'function') {
      await track.stopProcessor()
    }
  } catch (e: any) {
    console.warn('[voice] filtro de ruído:', e?.message)
  }
}

function snapshot(room: Room, TrackC: typeof TrackT): CallParticipantInfo[] {
  const all: Participant[] = [room.localParticipant, ...Array.from(room.remoteParticipants.values())]
  return all.map((p) => ({
    identity:        p.identity,
    isLocal:         p === room.localParticipant,
    isSpeaking:      p.isSpeaking,
    isMicEnabled:    p.isMicrophoneEnabled,
    isScreenSharing: p.getTrackPublications().some(
      (t) => t.source === TrackC.Source.ScreenShare && !!t.track && !t.isMuted,
    ),
    isCameraEnabled: p.getTrackPublications().some(
      (t) => t.source === TrackC.Source.Camera && !!t.track && !t.isMuted,
    ),
    connectionQuality: String(p.connectionQuality ?? 'unknown'),
    participant:     p,
  }))
}

function bindRoomEvents(
  RoomEventC: typeof RoomEventT,
  room: Room,
  refresh: () => void,
  onDisc: () => void,
) {
  const onUpdate = () => refresh()
  room.on(RoomEventC.ParticipantConnected,    onUpdate)
  room.on(RoomEventC.ParticipantDisconnected, onUpdate)
  room.on(RoomEventC.TrackMuted,              onUpdate)
  room.on(RoomEventC.TrackUnmuted,            onUpdate)
  room.on(RoomEventC.TrackSubscribed,         onUpdate)
  room.on(RoomEventC.TrackUnsubscribed,       onUpdate)
  room.on(RoomEventC.LocalTrackPublished,     onUpdate)
  room.on(RoomEventC.LocalTrackUnpublished,   onUpdate)
  room.on(RoomEventC.ActiveSpeakersChanged,   onUpdate)
  room.on(RoomEventC.ConnectionQualityChanged, onUpdate)
  room.on(RoomEventC.Disconnected,            onDisc)
}

export const useVoiceStore = create<VoiceState>((set, get) => {
  const refresh = () => {
    if (activeRoom && lkNs) {
      const participants = snapshot(activeRoom, lkNs.Track)
      set({ participants })

      setPipEnabled(participants.some((p) => p.isCameraEnabled || p.isScreenSharing))
    }
  }
  const handleDisc = () => {
    activeRoom = null
    setPipEnabled(false)
    setCallActive(false)
    set({ state: 'idle', roomName: null, participants: [], error: null })
  }

  const applyAudioVolumes = () => {
    const { volume, deafened, participantVolumes } = get()
    document.querySelectorAll<HTMLAudioElement>('audio[data-astra-voice]').forEach((a) => {
      const id = a.getAttribute('data-voice-identity') ?? ''
      const pv = participantVolumes[id] ?? 1
      a.volume = Math.max(0, Math.min(1, volume * pv))
      a.muted  = deafened
    })
  }

  return {
    state:        'idle',
    roomName:     null,
    participants: [],
    error:        null,
    deafened:     false,
    volume:       loadInitialVolume(),
    participantVolumes: loadParticipantVolumes(),
    noiseFilter:        loadNoiseFilter(),
    showStats:          false,
    audioInputId:       loadDev(DEV_IN_KEY),
    audioOutputId:      loadDev(DEV_OUT_KEY),
    screenQuality:      loadScreenQuality(),

    join: async (kind, id) => {
      const targetName = `${kind}:${id}`
      const lk = await loadLK()
      const { Room, ConnectionState, RoomEvent, Track } = lk

      if (activeRoom?.state === ConnectionState.Connected && activeRoom.name === targetName) return

      if (activeRoom) {
        try { await activeRoom.disconnect() } catch {}
        activeRoom = null
      }
      set({ state: 'connecting', error: null })
      try {
        const tokenRes = await api.post('/api/voice/token', { roomKind: kind, roomId: id })
        const { token, url } = tokenRes.data.data

        const room = new Room({
          adaptiveStream: false,
          // dynacast pausa camadas que nenhum subscriber "pede" via sinalizacao de
          // qualidade — coisa que o SDK oficial manda mas o cliente hand-rolled do
          // desktop (VoiceEngine.kt) nao. Com dynacast ligado, a track de tela chegava
          // no desktop mas sem frame (tela preta). Off = todas as camadas fluem sempre.
          dynacast:       false,

          audioCaptureDefaults: {
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl:  true,
          },

          publishDefaults: {
            audioPreset: lk.AudioPresets.music,
            dtx: true,
            red: true,
            // H264 (nao VP9) pra compat com o desktop nativo: o backup-codec do LiveKit
            // so roteia o H264 reserva quando o subscriber SINALIZA que nao decodifica
            // VP9 — de novo, algo que o hand-rolled do desktop nao faz. H264 primario
            // = todo cliente (web/mobile/desktop) decodifica sem depender disso.
            videoCodec: 'h264',
          },
        })
        bindRoomEvents(RoomEvent, room, refresh, handleDisc)
        await room.connect(url, token)

        try {
          await room.localParticipant.setMicrophoneEnabled(true)
        } catch (micErr: any) {
          console.warn('[voice] mic permission denied/error — entrando mudo:', micErr?.message)
        }

        const savedIn  = get().audioInputId
        const savedOut = get().audioOutputId
        if (savedIn)  { try { await room.switchActiveDevice('audioinput',  savedIn)  } catch {} }
        if (savedOut) { try { await room.switchActiveDevice('audiooutput', savedOut) } catch {} }

        activeRoom = room
        void applyMicNoiseFilter(get().noiseFilter)

        setCallActive(true)
        playCallJoin()
        set({
          state:        'connected',
          roomName:     room.name,
          participants: snapshot(room, Track),
        })
      } catch (e: any) {
        const msg = humanizeMediaError(e) ?? e?.response?.data?.error ?? e?.message ?? 'Falha ao conectar'
        if (activeRoom) { try { await activeRoom.disconnect() } catch {} activeRoom = null }
        set({ state: 'error', error: msg })
      }
    },

    leave: async () => {
      if (!activeRoom) { set({ state: 'idle' }); return }
      playCallLeave()
      set({ state: 'disconnecting' })
      try { await activeRoom.disconnect() } catch {}
      activeRoom = null
      setPipEnabled(false)
      setCallActive(false)
      set({ state: 'idle', roomName: null, participants: [] })
    },

    toggleMic: async () => {
      if (!activeRoom) return
      const lp = activeRoom.localParticipant as LocalParticipant
      try {
        await lp.setMicrophoneEnabled(!lp.isMicrophoneEnabled)
        if (lp.isMicrophoneEnabled) void applyMicNoiseFilter(get().noiseFilter)
        set({ error: null })
      } catch (e: any) {
        set({ error: humanizeMediaError(e) ?? 'Falha ao acessar microfone' })
      }
      refresh()
    },

    toggleScreen: async () => {
      if (!activeRoom || !lkNs) return
      const lp = activeRoom.localParticipant as LocalParticipant
      const sharing = lp.getTrackPublications().some(
        (t) => t.source === lkNs!.Track.Source.ScreenShare && !!t.track && !t.isMuted,
      )
      if (sharing) {
        try { await lp.setScreenShareEnabled(false) } catch {}
      } else {

        if (typeof navigator === 'undefined' ||
            !navigator.mediaDevices ||
            !('getDisplayMedia' in navigator.mediaDevices)) {
          set({ error: 'Compartilhamento de tela não é suportado neste navegador (mobile, em geral).' })
          return
        }
        try {

          const detail = get().screenQuality === 'detail'
          const fps    = detail ? 30 : 60
          const pub = await lp.setScreenShareEnabled(
            true,
            { resolution: { width: 1920, height: 1080, frameRate: fps }, audio: true },
            { videoEncoding: { maxBitrate: detail ? 5_000_000 : 8_000_000, maxFramerate: fps }, simulcast: false },
          )
          const track = pub?.track?.mediaStreamTrack
          if (track && 'contentHint' in track) {
            try { (track as any).contentHint = detail ? 'detail' : 'motion' } catch {}
          }
          set({ error: null })
        } catch (e: any) {
          set({ error: humanizeMediaError(e) ?? 'Falha ao compartilhar tela' })
        }
      }
      refresh()
    },

    toggleCamera: async () => {
      if (!activeRoom || !lkNs) return
      const lp = activeRoom.localParticipant as LocalParticipant
      const on = lp.getTrackPublications().some(
        (t) => t.source === lkNs!.Track.Source.Camera && !!t.track && !t.isMuted,
      )
      try {
        if (on) {
          await lp.setCameraEnabled(false)
        } else {

          await lp.setCameraEnabled(true)
        }
        set({ error: null })
      } catch (e: any) {
        set({ error: humanizeMediaError(e) ?? 'Falha ao acessar câmera' })
      }
      refresh()
    },

    toggleDeafen: () => {
      set({ deafened: !get().deafened })
      applyAudioVolumes()
    },

    setVolume: (v) => {
      const clamped = Math.max(0, Math.min(1, v))
      set({ volume: clamped })
      applyAudioVolumes()
      try { localStorage.setItem(VOLUME_STORAGE_KEY, String(clamped)) } catch {}
    },

    setParticipantVolume: (identity, v) => {
      const clamped = Math.max(0, Math.min(1, v))
      set((s) => ({ participantVolumes: { ...s.participantVolumes, [identity]: clamped } }))
      applyAudioVolumes()
      try { localStorage.setItem(PVOL_STORAGE_KEY, JSON.stringify(get().participantVolumes)) } catch {}
    },

    toggleNoiseFilter: () => {
      const next = !get().noiseFilter
      set({ noiseFilter: next })
      try { localStorage.setItem(NOISE_FILTER_KEY, next ? '1' : '0') } catch {}
      void applyMicNoiseFilter(next)
    },

    toggleStats: () => set((s) => ({ showStats: !s.showStats })),

    setAudioInput: async (id) => {
      set({ audioInputId: id })
      try { localStorage.setItem(DEV_IN_KEY, id) } catch {}
      if (activeRoom) {
        try { await activeRoom.switchActiveDevice('audioinput', id) } catch (e: any) { console.warn('[voice] troca de mic:', e?.message) }
        void applyMicNoiseFilter(get().noiseFilter)
      }
    },

    setAudioOutput: async (id) => {
      set({ audioOutputId: id })
      try { localStorage.setItem(DEV_OUT_KEY, id) } catch {}
      if (activeRoom) { try { await activeRoom.switchActiveDevice('audiooutput', id) } catch {} }
      document.querySelectorAll<HTMLAudioElement>('audio[data-astra-voice]').forEach((a) => {
        if ('setSinkId' in a) { try { void (a as any).setSinkId(id) } catch {} }
      })
    },

    setScreenQuality: (q) => {
      set({ screenQuality: q })
      try { localStorage.setItem(SCREEN_Q_KEY, q) } catch {}
    },
  }
})

export function parseRoomName(name: string | null): { kind: 'channel' | 'dm'; id: string } | null {
  if (!name) return null
  const [kind, id] = name.split(':')
  if ((kind === 'channel' || kind === 'dm') && id) return { kind, id }
  return null
}

export type _UnusedKeepTypes = ConnectionStateT
