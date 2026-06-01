/**
 * Botão "📞 Chamar" no header de DM.
 *
 * Click → emit socket 'dm_call_invite' + entra na call.
 * Outro lado vê IncomingCallModal e aceita/recusa.
 *
 * Estados:
 *   - cfg desabilitada → disabled + tooltip
 *   - já em call dessa mesma DM → mostra "Em chamada"
 *   - já em outra call → disabled
 */
import { useEffect, useState } from 'react'
import { PhoneCall, PhoneOff } from 'lucide-react'
import { getSocket } from '@/lib/socket'
import { useVoiceCall, useVoiceConfig, parseRoomName } from '@/hooks/useVoiceCall'

interface Props {
  conversationId: string
  otherUserId:    string
  otherDisplayName: string
}

export function DMCallButton({ conversationId, otherUserId, otherDisplayName }: Props) {
  const cfg   = useVoiceConfig()
  const voice = useVoiceCall()
  const [ringing, setRinging] = useState(false)

  const parsed = parseRoomName(voice.roomName)
  const inThis  = parsed?.kind === 'dm' && parsed.id === conversationId
  const inOther = voice.state !== 'idle' && !inThis

  // Quando outro user aceita, entra na call
  useEffect(() => {
    if (!ringing) return
    let sock: ReturnType<typeof getSocket>
    try { sock = getSocket() } catch { return }

    const onAccept = async (p: { conversationId: string }) => {
      if (p.conversationId !== conversationId) return
      setRinging(false)
      await voice.join('dm', conversationId)
    }
    const onReject = (p: { conversationId: string }) => {
      if (p.conversationId !== conversationId) return
      setRinging(false)
    }

    sock.on('dm_call_accept', onAccept)
    sock.on('dm_call_reject', onReject)
    return () => {
      sock.off('dm_call_accept', onAccept)
      sock.off('dm_call_reject', onReject)
    }
  }, [ringing, conversationId, voice])

  // Auto-cancela ring após 30s
  useEffect(() => {
    if (!ringing) return
    const t = setTimeout(() => setRinging(false), 30_000)
    return () => clearTimeout(t)
  }, [ringing])

  const startCall = () => {
    if (!cfg.data?.enabled || inOther) return
    try {
      getSocket().emit('dm_call_invite', { conversationId, toUserId: otherUserId })
    } catch {}
    setRinging(true)
  }

  const cancelRing = () => {
    setRinging(false)
    try {
      getSocket().emit('dm_call_reject', { conversationId, toUserId: otherUserId })
    } catch {}
  }

  if (inThis) {
    return (
      <button
        onClick={() => voice.leave()}
        className="flex items-center gap-2 px-3 h-9 border border-(--danger)/40 text-(--danger) hover:bg-(--danger)/10 transition-colors text-sm"
        title="Sair da chamada"
      >
        <PhoneOff className="size-3.5" />
        <span className="hidden sm:inline">Sair</span>
      </button>
    )
  }

  if (ringing) {
    return (
      <button
        onClick={cancelRing}
        className="flex items-center gap-2 px-3 h-9 border border-(--accent)/40 text-(--accent) animate-pulse transition-colors text-sm"
        title={`Chamando ${otherDisplayName}…`}
      >
        <PhoneCall className="size-3.5" />
        <span className="hidden sm:inline">Tocando…</span>
      </button>
    )
  }

  return (
    <button
      onClick={startCall}
      disabled={!cfg.data?.enabled || inOther}
      title={
        !cfg.data?.enabled ? 'Chamadas não configuradas'
        : inOther          ? 'Você já está em outra chamada'
        : `Ligar para ${otherDisplayName}`
      }
      className="flex items-center gap-2 px-3 h-9 border border-(--border) text-(--text-2) hover:border-(--accent) hover:text-(--accent) disabled:opacity-40 disabled:cursor-not-allowed transition-colors text-sm"
    >
      <PhoneCall className="size-3.5" />
      <span className="hidden sm:inline">Ligar</span>
    </button>
  )
}
