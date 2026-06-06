/**
 * useVoiceCall — wrapper fino sobre voiceStore (Zustand).
 *
 * Antes era useState por instância → cada componente tinha seu próprio state
 * e join() em um não notificava os outros. Agora todo mundo lê o mesmo store.
 *
 * Mantém a mesma API pra não quebrar callsites.
 */
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { useVoiceStore, parseRoomName as parseRoomNameImpl, type CallState, type CallParticipantInfo } from '@/store/voiceStore'

export type { CallState, CallParticipantInfo }

export interface VoiceCallConfig {
  enabled: boolean
  url:     string | null
}

export function useVoiceConfig() {
  return useQuery<VoiceCallConfig>({
    queryKey: ['voice', 'config'],
    queryFn:  async () => (await api.get('/api/voice/config')).data.data,
    staleTime: 10 * 60_000,
  })
}

/**
 * useVoiceChannelPresence — polling de quem está em cada canal voice.
 *
 * Retorna `{ [channelId]: identities[] }`. Refresh a cada 10s; tab oculta pausa.
 * Server cacheia 5s — pico de RPS pequeno mesmo com muitos clients.
 */
export function useVoiceChannelPresence(channelIds: string[]) {
  const sortedKey = [...channelIds].sort().join(',')
  return useQuery<Record<string, string[]>>({
    queryKey:    ['voice', 'presence', sortedKey],
    queryFn:     async () => {
      if (!sortedKey) return {}
      const res = await api.get(`/api/voice/presence?channelIds=${encodeURIComponent(sortedKey)}`)
      return res.data.data as Record<string, string[]>
    },
    enabled:           channelIds.length > 0,
    refetchInterval:   10_000,
    refetchOnWindowFocus: true,
    staleTime:         3_000,
  })
}

export function useVoiceCall() {
  // Subscribe direto no store — qualquer mudança re-renderiza
  const state        = useVoiceStore((s) => s.state)
  const roomName     = useVoiceStore((s) => s.roomName)
  const participants = useVoiceStore((s) => s.participants)
  const error        = useVoiceStore((s) => s.error)
  const deafened     = useVoiceStore((s) => s.deafened)
  const volume       = useVoiceStore((s) => s.volume)
  const join         = useVoiceStore((s) => s.join)
  const leave        = useVoiceStore((s) => s.leave)
  const toggleMic    = useVoiceStore((s) => s.toggleMic)
  const toggleScreen = useVoiceStore((s) => s.toggleScreen)
  const toggleCamera = useVoiceStore((s) => s.toggleCamera)
  const toggleDeafen = useVoiceStore((s) => s.toggleDeafen)
  const setVolume    = useVoiceStore((s) => s.setVolume)

  return {
    state, roomName, participants, error, deafened, volume,
    join, leave, toggleMic, toggleScreen, toggleCamera, toggleDeafen, setVolume,
  }
}

export const parseRoomName = parseRoomNameImpl
