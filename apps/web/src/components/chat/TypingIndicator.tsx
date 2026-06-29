import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getSocket } from '@/lib/socket'
import { useAuthStore } from '@/store/authStore'

interface TypingUser { userId: string; username: string }

interface Props {
  channelId?:      string
  conversationId?: string
}

export default function TypingIndicator({ channelId, conversationId }: Props) {
  const { t } = useTranslation()
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
      ? t('typing.one', { user: typingUsers[0].username })
      : typingUsers.length === 2
        ? t('typing.two', { a: typingUsers[0].username, b: typingUsers[1].username })
        : t('typing.many', { count: typingUsers.length })

  return (
    <div style={{
      height: 20, padding: '0 20px',
      display: 'flex', alignItems: 'center', gap: 7,
      animation: 'fadeUp 0.2s var(--ease-spring) both',
    }}>
      {}
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