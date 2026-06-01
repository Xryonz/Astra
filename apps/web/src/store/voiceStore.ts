/**
 * Store global de chamada de voz (Zustand).
 *
 * livekit-client é importado APENAS via `loadLK()` quando o user entra numa call.
 * No boot do app, esse store é só metadata (state, participants vazio).
 * Isso tira ~120KB gzip do main bundle pra um path que talvez nunca ocorra.
 */
import { create } from 'zustand'
import type {
  Room, RoomEvent as RoomEventT, Track as TrackT, ConnectionState as ConnectionStateT,
  LocalParticipant, Participant,
} from 'livekit-client'
import { api } from '@/lib/api'

export type CallState = 'idle' | 'connecting' | 'connected' | 'disconnecting' | 'error'

export interface CallParticipantInfo {
  identity:        string
  isLocal:         boolean
  isSpeaking:      boolean
  isMicEnabled:    boolean
  isScreenSharing: boolean
  participant:     Participant
}

interface VoiceState {
  state:        CallState
  roomName:     string | null
  participants: CallParticipantInfo[]
  error:        string | null
  deafened:     boolean
  /** Volume master 0–1 — aplicado em todos os <audio> remotos */
  volume:       number

  join:         (kind: 'channel' | 'dm', id: string) => Promise<void>
  leave:        () => Promise<void>
  toggleMic:    () => Promise<void>
  toggleScreen: () => Promise<void>
  toggleDeafen: () => void
  setVolume:    (v: number) => void
}

const VOLUME_STORAGE_KEY = 'umbra-voice-volume'
function loadInitialVolume(): number {
  try {
    const v = localStorage.getItem(VOLUME_STORAGE_KEY)
    if (v === null) return 1
    const n = Number(v)
    return Number.isFinite(n) ? Math.max(0, Math.min(1, n)) : 1
  } catch { return 1 }
}

// Singleton — única conexão por aba
let activeRoom: Room | null = null

// LiveKit namespace cache. null antes do primeiro join.
type LKNs = typeof import('livekit-client')
let lkNs: LKNs | null = null
async function loadLK(): Promise<LKNs> {
  if (!lkNs) lkNs = await import('livekit-client')
  return lkNs
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
  room.on(RoomEventC.Disconnected,            onDisc)
}

export const useVoiceStore = create<VoiceState>((set, get) => {
  const refresh = () => {
    if (activeRoom && lkNs) set({ participants: snapshot(activeRoom, lkNs.Track) })
  }
  const handleDisc = () => {
    activeRoom = null
    set({ state: 'idle', roomName: null, participants: [], error: null })
  }

  return {
    state:        'idle',
    roomName:     null,
    participants: [],
    error:        null,
    deafened:     false,
    volume:       loadInitialVolume(),

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

        const room = new Room({ adaptiveStream: true, dynacast: true })
        bindRoomEvents(RoomEvent, room, refresh, handleDisc)
        await room.connect(url, token)
        await room.localParticipant.setMicrophoneEnabled(true)

        activeRoom = room
        set({
          state:        'connected',
          roomName:     room.name,
          participants: snapshot(room, Track),
        })
      } catch (e: any) {
        const msg = e?.response?.data?.error ?? e?.message ?? 'Falha ao conectar'
        if (activeRoom) { try { await activeRoom.disconnect() } catch {} activeRoom = null }
        set({ state: 'error', error: msg })
      }
    },

    leave: async () => {
      if (!activeRoom) { set({ state: 'idle' }); return }
      set({ state: 'disconnecting' })
      try { await activeRoom.disconnect() } catch {}
      activeRoom = null
      set({ state: 'idle', roomName: null, participants: [] })
    },

    toggleMic: async () => {
      if (!activeRoom) return
      const lp = activeRoom.localParticipant as LocalParticipant
      await lp.setMicrophoneEnabled(!lp.isMicrophoneEnabled)
      refresh()
    },

    toggleScreen: async () => {
      if (!activeRoom || !lkNs) return
      const lp = activeRoom.localParticipant as LocalParticipant
      const sharing = lp.getTrackPublications().some(
        (t) => t.source === lkNs!.Track.Source.ScreenShare && !!t.track && !t.isMuted,
      )
      if (sharing) {
        await lp.setScreenShareEnabled(false)
      } else {
        // ── 60fps adaptativo ─────────────────────────────────
        // Preset custom: 1280×720 @ 60fps, 2.5Mbps. Por que essas escolhas?
        //  - 720p (não 1080p): ainda nítido pra leitura de código/UI; libera
        //    encoding budget pro framerate maior (animações fluidas, jogos, scroll).
        //  - 60fps: alvo pedido. Tela "fluida" como app nativo, vs 30fps "filme".
        //  - 2.5Mbps: 720p60 razoável; dynacast (já ligado no Room init) corta
        //    automático se nenhum subscriber precisa de full res, então pico é
        //    soft. Em rede ruim, LK degrada framerate antes de res.
        const { VideoPreset } = lkNs
        const screen60 = new VideoPreset(1280, 720, 2_500_000, 60)
        await lp.setScreenShareEnabled(
          true,
          { resolution: screen60.resolution },
          { screenShareEncoding: screen60.encoding },
        )
      }
      refresh()
    },

    toggleDeafen: () => {
      const next = !get().deafened
      set({ deafened: next })
      document.querySelectorAll<HTMLAudioElement>('audio').forEach((a) => { a.muted = next })
    },

    setVolume: (v) => {
      const clamped = Math.max(0, Math.min(1, v))
      set({ volume: clamped })
      document.querySelectorAll<HTMLAudioElement>('audio[data-umbra-voice]').forEach((a) => {
        a.volume = clamped
      })
      try { localStorage.setItem(VOLUME_STORAGE_KEY, String(clamped)) } catch {}
    },
  }
})

export function parseRoomName(name: string | null): { kind: 'channel' | 'dm'; id: string } | null {
  if (!name) return null
  const [kind, id] = name.split(':')
  if ((kind === 'channel' || kind === 'dm') && id) return { kind, id }
  return null
}

// Silenciar warning unused — tipos importados são usados via parâmetros tipados.
export type _UnusedKeepTypes = ConnectionStateT
