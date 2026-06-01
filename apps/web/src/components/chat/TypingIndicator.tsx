import { useEffect, useState } from 'react'
import { getSocket } from '@/lib/socket'
import { useAuthStore } from '@/store/authStore'

interface TypingUser { userId: string; username: string }

export default function TypingIndicator({ channelId }: { channelId: string }) {
  const currentUserId = useAuthStore((s) => s.user?.id)
  const [typingUsers, setTypingUsers] = useState<TypingUser[]>([])

  useEffect(() => {
    let socket: ReturnType<typeof getSocket>
    try { socket = getSocket() } catch { return }

    const handleStart = (p: { userId: string; username: string; channelId: string }) => {
      if (p.channelId !== channelId || p.userId === currentUserId) return
      setTypingUsers((prev) =>
        prev.some((u) => u.userId === p.userId) ? prev : [...prev, { userId: p.userId, username: p.username }]
      )
    }
    const handleStop = (p: { userId: string; channelId: string }) => {
      if (p.channelId !== channelId) return
      setTypingUsers((prev) => prev.filter((u) => u.userId !== p.userId))
    }

    socket.on('user_typing', handleStart)
    socket.on('user_stopped_typing', handleStop)
    return () => {
      socket.off('user_typing', handleStart)
      socket.off('user_stopped_typing', handleStop)
    }
  }, [channelId, currentUserId])

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