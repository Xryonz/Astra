import { useEffect, useState } from 'react'
import { getSocket } from '@/lib/socket'
import { useAuthStore } from '@/store/authStore'

interface TypingUser { userId: string; username: string }

interface Props {
  channelId?:      string
  conversationId?: string  // DM scope: ouve dm_user_typing/dm_user_stopped_typing
}

export default function TypingIndicator({ channelId, conversationId }: Props) {
  const currentUserId = useAuthStore((s) => s.user?.id)
  const [typingUsers, setTypingUsers] = useState<TypingUser[]>([])
  const isDM = !!conversationId
  const scopeId = (channelId ?? conversationId) as string

  useEffect(() => {
    let socket: ReturnType<typeof getSocket>
    try { socket = getSocket() } catch { return }

    const startEv = isDM ? 'dm_user_typing'         : 'user_typing'
    const stopEv  = isDM ? 'dm_user_stopped_typing' : 'user_stopped_typing'
    const idKey   = isDM ? 'conversationId'         : 'channelId'

    const handleStart = (p: any) => {
      if (p[idKey] !== scopeId || p.userId === currentUserId) return
      setTypingUsers((prev) =>
        prev.some((u) => u.userId === p.userId) ? prev : [...prev, { userId: p.userId, username: p.username }]
      )
    }
    const handleStop = (p: any) => {
      if (p[idKey] !== scopeId) return
      setTypingUsers((prev) => prev.filter((u) => u.userId !== p.userId))
    }

    socket.on(startEv, handleStart)
    socket.on(stopEv,  handleStop)
    return () => {
      socket.off(startEv, handleStart)
      socket.off(stopEv,  handleStop)
    }
  }, [scopeId, isDM, currentUserId])

  if (typingUsers.length === 0) return <div style={{ height: 20 }} />

  const label =
    typingUsers.length === 1
      ? `${typingUsers[0].username} está digitando`
      : typingUsers.length === 2
        ? `${typingUsers[0].username} e ${typingUsers[1].username} estão digitando`
        : `${typingUsers.length} pessoas estão digitando`

  return (
    <div style={{
      height: 20, padding: '0 20px',
      display: 'flex', alignItems: 'center', gap: 7,
      animation: 'fadeUp 0.2s var(--ease-spring) both',
    }}>
      {/* Animated dots */}
      <div style={{ display: 'flex', gap: 3, alignItems: 'center' }}>
        <span className="t-dot" />
        <span className="t-dot" />
        <span className="t-dot" />
      </div>
      <span style={{
        color: 'var(--text-2)', fontSize: 12,
        fontStyle: 'italic',
      }}>
        {label}
        <span style={{ fontStyle: 'normal', color: 'var(--text-3)' }}>…</span>
      </span>
    </div>
  )
}