import { useEffect, useRef, useState, useCallback } from 'react'
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { CornerDownRight, File as FileIcon, Reply } from 'lucide-react'
import { api, resolveApiUrl } from '@/lib/api'
import { getSocket, trackJoinDM, trackLeaveDM } from '@/lib/socket'
import { useAuthStore } from '@/store/authStore'
import { useDMReads } from '@/hooks/useUnread'
import { format, isToday, isYesterday } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { FONT_FAMILY } from '@/components/profile/profileFonts'
import { getOutboxFor } from '@/lib/outbox'
import { cn } from '@/lib/utils'
import type { MessageWithAuthor, PaginatedResponse, Attachment } from '@astra/types'

function isImage(a: { type?: string; name?: string; url?: string }) {
  if (a.type?.startsWith('image/')) return true
  const target = a.url || a.name || ''
  return /\.(png|jpe?g|gif|webp|avif|svg|bmp|heic|heif)(\?|#|$)/i.test(target)
}
function isAudio(a: { type?: string; name?: string; url?: string }) {
  if (a.type?.startsWith('audio/')) return true
  const t = a.url || a.name || ''
  return /\.(mp3|ogg|wav|m4a|aac|webm)(\?|#|$)/i.test(t)
}

type OptimisticMessage = MessageWithAuthor & { optimisticId?: string; isPending?: boolean }

interface OtherUser {
  id: string; username: string; displayName: string; avatarUrl: string | null
}

interface DMChatProps {
  conversationId: string
  otherUser: OtherUser
  onRegisterOptimistic: (
    add:     (msg: OptimisticMessage) => void,
    remove:  (id: string) => void,
    confirm: (optimisticId: string, msg: MessageWithAuthor) => void,
  ) => void
  onReply?: (msg: MessageWithAuthor) => void
}

const PALETTE = ['#c9a96e','#8b7fc4','#6aabca','#ca7a7a','#7ac4a0']
function avatarColor(id: string) {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0
  return PALETTE[h % PALETTE.length]
}

function formatTime(d: string) {
  const date = new Date(d)
  if (isToday(date))     return `hoje às ${format(date, 'HH:mm')}`
  if (isYesterday(date)) return `ontem às ${format(date, 'HH:mm')}`
  return format(date, "d 'de' MMM 'às' HH:mm", { locale: ptBR })
}
void formatTime; void ptBR

export default function DMChat({ conversationId, otherUser, onRegisterOptimistic, onReply }: DMChatProps) {
  const queryClient   = useQueryClient()
  const currentUser   = useAuthStore((s) => s.user)
  const bottomRef     = useRef<HTMLDivElement>(null)
  const topRef        = useRef<HTMLDivElement>(null)
  const [shouldScrollToBottom, setShouldScrollToBottom] = useState(true)
  const [optimisticMsgs, setOptimisticMsgs] = useState<OptimisticMessage[]>([])

  const addOptimistic = useCallback((msg: OptimisticMessage) => {
    setOptimisticMsgs((prev) => [...prev, { ...msg, isPending: true }])
    setShouldScrollToBottom(true)
  }, [])
  const removeOptimistic = useCallback((optimisticId: string) => {
    setOptimisticMsgs((prev) => prev.filter((m) => m.optimisticId !== optimisticId))
  }, [])

  const confirmOptimistic = useCallback((optimisticId: string, msg: MessageWithAuthor) => {
    setOptimisticMsgs((prev) => prev.filter((o) => o.optimisticId !== optimisticId))
    queryClient.setQueryData(['dm-messages', conversationId], (old: any) => {
      if (!old) return old
      const [first, ...rest] = old.pages
      if (first.items.some((m: MessageWithAuthor) => m.id === msg.id)) return old
      return { ...old, pages: [{ ...first, items: [...first.items, msg] }, ...rest] }
    })
    setShouldScrollToBottom(true)
  }, [conversationId, queryClient])

  useEffect(() => {
    onRegisterOptimistic(addOptimistic, removeOptimistic, confirmOptimistic)
  }, [onRegisterOptimistic, addOptimistic, removeOptimistic, confirmOptimistic])

  const partnerProfile = useQuery<{ user: { profileTheme?: string | null; bannerColor?: string | null } }>({
    queryKey: ['profile', otherUser.id],
    queryFn:  async () => (await api.get(`/api/profile/${otherUser.id}`)).data.data,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  })
  const partnerTheme = partnerProfile.data?.user?.profileTheme
                   ?? partnerProfile.data?.user?.bannerColor

  const {
    data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading,
  } = useInfiniteQuery({
    queryKey: ['dm-messages', conversationId],
    queryFn: async ({ pageParam }) => {
      const params = new URLSearchParams({ limit: '30' })
      if (pageParam) params.set('cursor', pageParam as string)
      const res = await api.get(`/api/dm/${conversationId}/messages?${params}`)
      return res.data.data as PaginatedResponse<MessageWithAuthor>
    },
    getNextPageParam: (p) => p.nextCursor ?? undefined,
    initialPageParam: undefined as string | undefined,
  })

  const confirmedMessages = data?.pages.slice().reverse().flatMap((p) => p.items) ?? []
  const allMessages       = [...confirmedMessages, ...optimisticMsgs]

  const { otherReads, markRead } = useDMReads()
  const otherLastReadAt = otherReads[conversationId]
    ? new Date(otherReads[conversationId]!).getTime()
    : 0

  useEffect(() => {
    const t = setTimeout(() => { void markRead(conversationId) }, 1500)
    return () => clearTimeout(t)
  }, [conversationId, allMessages.length, markRead])

  useEffect(() => {
    let socket: ReturnType<typeof getSocket>
    try { socket = getSocket() } catch { return }
    trackJoinDM(conversationId)
    socket.emit('join_dm', conversationId)
    return () => {
      trackLeaveDM(conversationId)
      socket.emit('leave_dm', conversationId)
    }
  }, [conversationId])

  useEffect(() => {
    let socket: ReturnType<typeof getSocket>
    try { socket = getSocket() } catch { return }

    const onNewDM = (msg: MessageWithAuthor) => {

      setOptimisticMsgs((prev) =>
        prev.filter((o) => !(
          o.author.id === msg.author.id &&
          o.content   === msg.content   &&
          Math.abs(new Date(msg.createdAt).getTime() - new Date(o.createdAt).getTime()) < 5000
        ))
      )
      queryClient.setQueryData(['dm-messages', conversationId], (old: any) => {
        if (!old) return old
        const [first, ...rest] = old.pages
        if (first.items.some((m: MessageWithAuthor) => m.id === msg.id)) return old
        return { ...old, pages: [{ ...first, items: [...first.items, msg] }, ...rest] }
      })
      queryClient.invalidateQueries({ queryKey: ['dm-list'] })
      setShouldScrollToBottom(true)
    }

    socket.on('new_dm', onNewDM)
    return () => { socket.off('new_dm', onNewDM) }
  }, [conversationId, queryClient])

  useEffect(() => {
    if (shouldScrollToBottom) bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [allMessages.length, shouldScrollToBottom])

  useEffect(() => {
    setShouldScrollToBottom(true)
    setOptimisticMsgs([])
    bottomRef.current?.scrollIntoView()
  }, [conversationId])

  useEffect(() => {
    let cancelled = false
    void getOutboxFor('dm', conversationId).then((items) => {
      if (cancelled) return
      for (const it of items) {
        addOptimistic({
          id: it.id, optimisticId: it.id, content: it.content, edited: false,
          createdAt: new Date(it.createdAt).toISOString(), updatedAt: new Date(it.createdAt).toISOString(),
          isPending: true, author: it.author as any, attachments: [], replyTo: null,
        } as OptimisticMessage)
      }
    })
    return () => { cancelled = true }
  }, [conversationId, addOptimistic])

  useEffect(() => {
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting && hasNextPage && !isFetchingNextPage) {
        fetchNextPage()
        setShouldScrollToBottom(false)
      }
    }, { threshold: 0.1 })
    if (topRef.current) observer.observe(topRef.current)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  if (isLoading) return (
    <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{
        width: 28, height: 28, border: '2px solid var(--border-mid)',
        borderTopColor: 'var(--accent)', borderRadius: '50%',
        animation: 'spin 0.7s linear infinite',
      }} />
      <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
    </div>
  )

  return (
    <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column' }}>
      <div ref={topRef} style={{ height: 4 }} />

      {isFetchingNextPage && (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '10px 0' }}>
          <div style={{ width: 18, height: 18, border: '2px solid var(--border-mid)', borderTopColor: 'var(--accent)', borderRadius: '50%', animation: 'spin 0.7s linear infinite' }} />
        </div>
      )}

      {}
      {!hasNextPage && (
        <div style={{
          padding: '2.5rem 20px 1.5rem', textAlign: 'center',
          animation: 'fadeUp 0.35s var(--ease-spring) both',
          position: 'relative', overflow: 'hidden',
        }}>
          {}
          {partnerTheme && (
            <div
              aria-hidden
              style={{
                position: 'absolute', inset: 0,
                background: partnerTheme,
                opacity: 0.18,
                pointerEvents: 'none',
                WebkitMaskImage: 'linear-gradient(180deg, #000 0%, transparent 85%)',
                        maskImage: 'linear-gradient(180deg, #000 0%, transparent 85%)',
              }}
            />
          )}
          <div style={{
            position: 'relative',
            width: 72, height: 72, borderRadius: '50%', margin: '0 auto 1rem',
            background: avatarColor(otherUser.id) + '22',
            border: `3px solid ${avatarColor(otherUser.id)}44`,
            overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            {otherUser.avatarUrl
              ? <img src={otherUser.avatarUrl} alt="" loading="lazy" decoding="async" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              : <span style={{ fontSize: 24, fontWeight: 700, color: avatarColor(otherUser.id) }}>
                  {otherUser.displayName.slice(0, 1).toUpperCase()}
                </span>
            }
          </div>
          <h3 style={{
            fontFamily: 'var(--font-display)', fontWeight: 400,
            fontSize: '1.4rem', color: 'var(--text-1)', margin: '0 0 4px',
          }}>
            {otherUser.displayName}
          </h3>
          <p style={{ color: 'var(--text-3)', fontSize: 13, margin: '0 0 4px' }}>
            @{otherUser.username}
          </p>
          <p style={{ color: 'var(--text-2)', fontSize: 13, margin: 0 }}>
            Este é o início da sua conversa com{' '}
            <strong style={{ color: 'var(--text-1)' }}>{otherUser.displayName}</strong>.
          </p>
        </div>
      )}

      {}
      <div style={{ display: 'flex', flexDirection: 'column', padding: '0 0 8px' }}>
        {(() => {

          const lastMineIdx = (() => {
            for (let i = allMessages.length - 1; i >= 0; i--) {
              if (allMessages[i].author.id === currentUser?.id) return i
            }
            return -1
          })()
          return allMessages.map((msg, i) => {
            const prev       = allMessages[i - 1]
            const isMine     = msg.author.id === currentUser?.id
            const grouped    =
              prev?.author.id === msg.author.id &&
              new Date(msg.createdAt).getTime() - new Date(prev.createdAt).getTime() < 5 * 60 * 1000
            const isPending  = (msg as OptimisticMessage).isPending ?? false
            const color      = avatarColor(msg.author.id)
            const showSeen =
              isMine && i === lastMineIdx && !isPending &&
              otherLastReadAt >= new Date(msg.createdAt).getTime()

            return (
              <DMMessage
                key={(msg as OptimisticMessage).optimisticId ?? msg.id}
                message={msg}
                isMine={isMine}
                grouped={grouped}
                isPending={isPending}
                color={color}
                delay={Math.min(i * 0.018, 0.25)}
                showSeen={showSeen}
                onReply={onReply}
              />
            )
          })
        })()}
      </div>

      <div ref={bottomRef} />
      <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
    </div>
  )
}

function DMMessage({ message, isMine, grouped, isPending, color, delay, showSeen, onReply }: {
  message:   MessageWithAuthor
  isMine:    boolean
  grouped:   boolean
  isPending: boolean
  color:     string
  delay:     number
  showSeen:  boolean
  onReply?:  (msg: MessageWithAuthor) => void
}) {
  const [hovered, setHovered] = useState(false)
  const { author, content, createdAt } = message
  const attachments = (message as any).attachments as Attachment[] | undefined
  const replyTo     = (message as any).replyTo as { id: string; content: string; authorName: string; authorAvatar: string | null } | null | undefined

  return (
    <div
      className={cn(
        'group flex gap-4 relative border-l-2 transition-[border-color,background-color,opacity] duration-150',
        grouped ? 'pl-4 pr-3' : 'pl-4 pr-3 pt-3 pb-1 mt-2',
        hovered ? 'border-(--accent) bg-(--raised)/40' : 'border-transparent bg-transparent',
        isPending ? 'opacity-60' : 'opacity-100',
      )}
      style={{
        animation: `dmMsgIn 0.38s cubic-bezier(0.34, 1.32, 0.55, 1) ${delay}s both`,
        ['--dm-slide-from' as string]: '-14px',
        paddingTop:    grouped ? 'var(--msg-density-py)' : undefined,
        paddingBottom: grouped ? 'var(--msg-density-py)' : undefined,
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      {}
      {!grouped ? (
        <div style={{
          width: 40, height: 40, flexShrink: 0, borderRadius: '50%',
          background: color + '22', border: `2px solid ${color}44`,
          overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          {author.avatarUrl
            ? <img src={author.avatarUrl} alt="" loading="lazy" decoding="async" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            : <span style={{ fontSize: 15, fontWeight: 700, color }}>
                {author.displayName.slice(0, 1).toUpperCase()}
              </span>}
        </div>
      ) : (
        <div className="w-10 shrink-0 flex items-start justify-end pt-0.5">
          {hovered && !isPending && (
            <span className="text-[10px] text-(--text-3) whitespace-nowrap" style={{ fontFamily: 'var(--font-mono)' }}>
              {format(new Date(createdAt), 'HH:mm')}
            </span>
          )}
        </div>
      )}

      {}
      <div className="flex-1 min-w-0">
        {!grouped && (
          <div className="flex items-baseline gap-2 mb-0.5 flex-wrap">
            <span style={{ color, fontSize: 13, fontWeight: 600, fontFamily: FONT_FAMILY[author.displayFont ?? 'serif'] }}>
              {isMine ? 'Você' : author.displayName}
            </span>
            {!isPending && (
              <span className="text-[10px] text-(--text-3)" style={{ fontFamily: 'var(--font-mono)' }}>
                {format(new Date(createdAt), 'HH:mm')}
              </span>
            )}
          </div>
        )}

        {}
        {replyTo && (
          <div className="flex items-center gap-2 mb-1 max-w-full text-(--text-3)">
            <CornerDownRight className="size-3 text-(--accent)/70 shrink-0" />
            <span className="text-[12px] shrink-0" style={{ fontFamily: 'var(--font-display)', color: 'var(--accent)' }}>
              {replyTo.authorName}
            </span>
            <span className="text-[12px] italic truncate min-w-0">
              {replyTo.content || '— anexo —'}
            </span>
          </div>
        )}

        {}
        {content && (
          <div className="text-body leading-normal text-foreground wrap-break-word m-0">
            {content}
          </div>
        )}

        {}
        {attachments && attachments.length > 0 && (
          <div className={`flex flex-wrap gap-2 ${content ? 'mt-1.5' : ''}`} style={{ maxWidth: '100%' }}>
            {attachments.map((a, i) => (
              <DMAttachment key={`${a.url}-${i}`} att={a} />
            ))}
          </div>
        )}

        {isPending && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginTop: 3 }}>
            <div style={{
              width: 10, height: 10, border: '1.5px solid var(--border-mid)',
              borderTopColor: 'var(--accent)', borderRadius: '50%',
              animation: 'spin 0.7s linear infinite',
            }} />
            <span style={{ color: 'var(--text-3)', fontSize: 10 }}>enviando…</span>
          </div>
        )}
        {showSeen && (
          <span style={{
            color: 'var(--text-3)', fontSize: 10, marginTop: 3, display: 'inline-block',
            fontFamily: 'var(--font-mono)', letterSpacing: '0.04em',
          }}>
            ✓ Visto
          </span>
        )}
      </div>

      {}
      {hovered && !isPending && onReply && (
        <button
          onClick={() => onReply(message)}
          className="absolute top-1 right-2 size-6 grid place-items-center border border-(--border-mid) bg-(--overlay) text-(--text-3) hover:text-(--accent) hover:border-(--accent) transition-colors"
          title="Responder"
        >
          <Reply className="size-3" />
        </button>
      )}
    </div>
  )
}

function DMAttachment({ att }: { att: Attachment }) {
  if (isImage(att)) {
    return (
      <a href={resolveApiUrl(att.url)} target="_blank" rel="noreferrer" className="block">
        <img
          src={resolveApiUrl(att.url)}
          alt={att.name}
          loading="lazy"
          decoding="async"
          className="max-h-72 max-w-full object-cover border border-(--border-mid) hover:brightness-110 transition-[filter] duration-150"
        />
      </a>
    )
  }
  if (isAudio(att)) {
    return (
      <audio
        controls
        src={resolveApiUrl(att.url)}
        className="max-w-64 h-9"
      />
    )
  }
  return (
    <a
      href={resolveApiUrl(att.url)}
      target="_blank"
      rel="noreferrer"
      className="flex items-center gap-2 px-2.5 py-2 border border-(--border-mid) bg-(--raised)/50 hover:border-(--accent) transition-colors max-w-64"
    >
      <FileIcon className="size-4 text-(--text-3) shrink-0" />
      <div className="flex flex-col min-w-0 leading-tight">
        <span className="text-xs text-foreground truncate">{att.name}</span>
        <span className="text-[10px] font-mono text-(--text-3)">{att.size < 1024 * 1024 ? `${(att.size/1024).toFixed(0)}KB` : `${(att.size/1024/1024).toFixed(1)}MB`}</span>
      </div>
    </a>
  )
}