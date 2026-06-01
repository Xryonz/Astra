import { useState, useEffect } from 'react'
import { getSocket } from '@/lib/socket'

interface MentionPayload {
  messageId:   string
  channelId:   string
  serverId:    string
  serverName:  string
  channelName: string
  authorName:  string
  preview:     string
}

interface MentionBannerProps {
  onNavigate: (channelId: string, channelName: string, serverId: string) => void
}

export default function MentionBanner({ onNavigate }: MentionBannerProps) {
  const [queue, setQueue] = useState<(MentionPayload & { id: string })[]>([])

  useEffect(() => {
    let socket: ReturnType<typeof getSocket>
    try { socket = getSocket() } catch { return }

    const onMention = (payload: MentionPayload) => {
      const id = `mention-${Date.now()}-${Math.random()}`
      setQueue((prev) => [...prev, { ...payload, id }])

      // Auto-dismiss after 6 seconds
      setTimeout(() => {
        setQueue((prev) => prev.filter((m) => m.id !== id))
      }, 6000)
    }

    socket.on('mention', onMention)
    return () => { socket.off('mention', onMention) }
  }, [])

  if (queue.length === 0) return null

  return (
    <div style={{
      position: 'fixed', top: 16, right: 16, zIndex: 500,
      display: 'flex', flexDirection: 'column', gap: 8,
      maxWidth: 340,
    }}>
      {queue.map((m) => (
        <div
          key={m.id}
          style={{
            background: 'var(--overlay)',
            border: '1px solid var(--accent)',
            borderRadius: 14,
            padding: '12px 14px',
            boxShadow: '0 8px 32px rgba(0,0,0,0.6), 0 0 0 1px var(--accent-dim)',
            animation: 'mentionSlide 0.3s var(--ease-spring) both',
            cursor: 'pointer',
          }}
          onClick={() => {
            onNavigate(m.channelId, m.channelName, m.serverId)
            setQueue((prev) => prev.filter((x) => x.id !== m.id))
          }}
        >
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                <span style={{ fontSize: 14 }}>🔔</span>
                <span style={{ color: 'var(--accent)', fontSize: 12, fontWeight: 600 }}>
                  Você foi mencionado
                </span>
              </div>
              <p style={{ color: 'var(--text-2)', fontSize: 12, margin: '0 0 4px' }}>
                <strong style={{ color: 'var(--text-1)' }}>{m.authorName}</strong>
                {' '}em{' '}
                <strong style={{ color: 'var(--text-1)' }}>{m.serverName}</strong>
                {' '}#{m.channelName}
              </p>
              <p style={{
                color: 'var(--text-3)', fontSize: 12, margin: 0,
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
              }}>
                {m.preview}
              </p>
            </div>
            <button
              onClick={(e) => { e.stopPropagation(); setQueue((prev) => prev.filter((x) => x.id !== m.id)) }}
              style={{
                background: 'transparent', border: 'none', cursor: 'pointer',
                color: 'var(--text-3)', fontSize: 16, lineHeight: 1, padding: 2,
                flexShrink: 0, borderRadius: 4, transition: 'color 0.15s',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.color = 'var(--text-1)')}
              onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--text-3)')}
            >×</button>
          </div>
        </div>
      ))}

      <style>{`
        @keyframes mentionSlide {
          from { opacity: 0; transform: translateX(20px) scale(0.96); }
          to   { opacity: 1; transform: translateX(0)    scale(1);    }
        }
      `}</style>
    </div>
  )
}