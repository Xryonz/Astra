import { useEffect, useMemo, useRef, useState, useCallback, useLayoutEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { useVirtualizer } from '@tanstack/react-virtual'
import { Hash, Feather } from 'lucide-react'
import { api } from '@/lib/api'
import { fetchMessagesPage } from '@/lib/prefetch'
import { hydrateChannelFromCache } from '@/lib/messageCache'
import { getOutboxFor } from '@/lib/outbox'
import { useChannel } from '@/hooks/useSocket'
import { useUnread } from '@/hooks/useUnread'
import MessageItem from './MessageItem'
import { MessageListSkeleton } from '@/components/skeletons/MessageListSkeleton'
import { Empty, EmptyIcon, EmptyLabel, EmptyTitle, EmptyDescription } from '@/components/ui/empty'
import type { MessageWithAuthor } from '@astra/types'

type OptimisticMessage = MessageWithAuthor & { optimisticId?: string; isPending?: boolean }

interface MessageListProps {
  channelId:   string
  channelName: string
  serverId?:   string

  onRegisterOptimistic: (
    add:     (msg: OptimisticMessage) => void,
    remove:  (id: string) => void,
    confirm: (optimisticId: string, msg: MessageWithAuthor) => void,
  ) => void
  onReply?: (msg: MessageWithAuthor) => void
}

export default function MessageList({
  channelId, channelName, serverId, onRegisterOptimistic, onReply,
}: MessageListProps) {
  const { t }       = useTranslation()
  const queryClient = useQueryClient()
  const scrollRef   = useRef<HTMLDivElement>(null)
  const topRef      = useRef<HTMLDivElement>(null)
  const [shouldScrollToBottom, setShouldScrollToBottom] = useState(true)
  const [optimisticMsgs, setOptimisticMsgs] = useState<OptimisticMessage[]>([])

  const addOptimistic = useCallback((msg: OptimisticMessage) => {
    setOptimisticMsgs((prev) => [...prev, { ...msg, isPending: true }])
    setShouldScrollToBottom(true)
  }, [])

  const removeOptimistic = useCallback((optimisticId: string) => {
    setOptimisticMsgs((prev) => prev.filter((m) => m.optimisticId !== optimisticId))
  }, [])

  useEffect(() => {
    setOptimisticMsgs([])
    setShouldScrollToBottom(true)
  }, [channelId])

  useEffect(() => {
    let cancelled = false
    void getOutboxFor('channel', channelId).then((items) => {
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
  }, [channelId, addOptimistic])

  const {
    data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading,
  } = useInfiniteQuery({
    queryKey: ['messages', channelId],

    queryFn: ({ pageParam }) => fetchMessagesPage(channelId, pageParam as string | undefined),
    getNextPageParam: (p) => p.nextCursor ?? undefined,
    initialPageParam: undefined as string | undefined,
  })

  useEffect(() => {
    void hydrateChannelFromCache(queryClient, channelId)
  }, [queryClient, channelId])

  const { data: membersData = [] } = useQuery<Array<{ userId: string; topColor: string|null }>>({
    queryKey: ['members', serverId],
    queryFn:  async () => (await api.get(`/api/servers/${serverId}/members`)).data.data,
    enabled:  !!serverId,
    staleTime: 30_000,
  })

  const colorByUser = useMemo(() => {
    const m = new Map<string, string>()
    for (const x of membersData) if (x.topColor) m.set(x.userId, x.topColor)
    return m
  }, [membersData])

  const confirmedMessages = useMemo(
    () => data?.pages.slice().reverse().flatMap((p) => p.items) ?? [],
    [data?.pages],
  )

  const handleNewMessage = useCallback((msg: MessageWithAuthor & { clientNonce?: string|null }) => {
    setOptimisticMsgs((prev) => {
      if (msg.clientNonce) return prev.filter((o) => o.optimisticId !== msg.clientNonce)
      return prev.filter(
        (o) =>
          !(
            o.author.id === msg.author.id &&
            o.content   === msg.content   &&
            Math.abs(new Date(msg.createdAt).getTime() - new Date(o.createdAt).getTime()) < 5000
          )
      )
    })

    queryClient.setQueryData(['messages', channelId], (old: any) => {
      if (!old) return old
      const [first, ...rest] = old.pages

      if (first.items.some((m: MessageWithAuthor) => m.id === msg.id)) return old
      return { ...old, pages: [{ ...first, items: [...first.items, msg] }, ...rest] }
    })
    setShouldScrollToBottom(true)
  }, [channelId, queryClient])

  useEffect(() => {
    onRegisterOptimistic(
      addOptimistic,
      removeOptimistic,
      (optimisticId, msg) => handleNewMessage({ ...msg, clientNonce: optimisticId }),
    )
  }, [onRegisterOptimistic, addOptimistic, removeOptimistic, handleNewMessage])

  const handleMessageEdited = useCallback(
    (p: { messageId: string; content: string; edited: boolean }) => {
      queryClient.setQueryData(['messages', channelId], (old: any) => {
        if (!old) return old
        return {
          ...old,
          pages: old.pages.map((page: any) => ({
            ...page,
            items: page.items.map((m: MessageWithAuthor) =>
              m.id === p.messageId ? { ...m, content: p.content, edited: p.edited } : m
            ),
          })),
        }
      })
    },
    [channelId, queryClient],
  )

  const handleMessageDeleted = useCallback(
    (p: { messageId: string }) => {
      queryClient.setQueryData(['messages', channelId], (old: any) => {
        if (!old) return old
        return {
          ...old,
          pages: old.pages.map((page: any) => ({
            ...page,
            items: page.items.filter((m: MessageWithAuthor) => m.id !== p.messageId),
          })),
        }
      })
    },
    [channelId, queryClient],
  )

  const handleMessagePinned = useCallback(
    (p: { messageId: string; pinned: boolean }) => {
      queryClient.setQueryData(['messages', channelId], (old: any) => {
        if (!old) return old
        return {
          ...old,
          pages: old.pages.map((page: any) => ({
            ...page,
            items: page.items.map((m: any) =>
              m.id === p.messageId ? { ...m, pinned: p.pinned } : m
            ),
          })),
        }
      })
      queryClient.invalidateQueries({ queryKey: ['pinned', channelId] })
    },
    [channelId, queryClient],
  )

  const handleReactionUpdate = useCallback(
    (p: { messageId: string; reactions: Array<{ emoji: string; count: number; users: string[] }> }) => {
      queryClient.setQueryData(['messages', channelId], (old: any) => {
        if (!old) return old
        return {
          ...old,
          pages: old.pages.map((page: any) => ({
            ...page,
            items: page.items.map((m: any) =>
              m.id === p.messageId ? { ...m, reactions: p.reactions } : m
            ),
          })),
        }
      })
    },
    [channelId, queryClient],
  )

  const handlePollUpdated = useCallback(
    (p: { messageId: string; poll: unknown }) => {
      queryClient.setQueryData(['messages', channelId], (old: any) => {
        if (!old) return old
        return {
          ...old,
          pages: old.pages.map((page: any) => ({
            ...page,
            items: page.items.map((m: any) =>
              m.id === p.messageId ? { ...m, poll: p.poll } : m
            ),
          })),
        }
      })
    },
    [channelId, queryClient],
  )

  useChannel(channelId, {
    onNewMessage:     handleNewMessage,
    onMessageEdited:  handleMessageEdited,
    onMessageDeleted: handleMessageDeleted,
    onMessagePinned:  handleMessagePinned,
    onReactionUpdate: handleReactionUpdate,
    onPollUpdated:    handlePollUpdated,
  })

  const { markRead } = useUnread()
  useEffect(() => {
    const t = setTimeout(() => { void markRead(channelId) }, 1500)
    return () => clearTimeout(t)
  }, [channelId, confirmedMessages.length, markRead])

  const allMessages = useMemo<MessageWithAuthor[]>(
    () => [...confirmedMessages, ...optimisticMsgs],
    [confirmedMessages, optimisticMsgs],
  )

  const virtualizer = useVirtualizer({
    count: allMessages.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => 100,
    overscan: 5,
    getItemKey: (i) => (allMessages[i] as OptimisticMessage).optimisticId ?? allMessages[i].id,
  })

  useLayoutEffect(() => {
    if (!shouldScrollToBottom || allMessages.length === 0) return
    virtualizer.scrollToIndex(allMessages.length - 1, { align: 'end' })
  }, [allMessages.length, shouldScrollToBottom, virtualizer])

  useEffect(() => {
    const onKb = () => {
      const el = scrollRef.current
      if (!el || allMessages.length === 0) return
      const dist = el.scrollHeight - el.scrollTop - el.clientHeight
      if (dist < 200) virtualizer.scrollToIndex(allMessages.length - 1, { align: 'end' })
    }
    window.addEventListener('astra:kb-shown', onKb)
    return () => window.removeEventListener('astra:kb-shown', onKb)
  }, [virtualizer, allMessages.length])

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && hasNextPage && !isFetchingNextPage) {
          fetchNextPage()
          setShouldScrollToBottom(false)
        }
      },
      { threshold: 0.1 },
    )
    if (topRef.current) observer.observe(topRef.current)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  if (isLoading) return <MessageListSkeleton />

  if (allMessages.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Empty className="max-w-md">
          <EmptyIcon className="size-16 border border-(--border) bg-(--raised)/40 grid place-items-center">
            <Hash className="size-7 text-(--accent)" />
          </EmptyIcon>
          <EmptyLabel>{t('chat.feedEmpty.label')}</EmptyLabel>
          <EmptyTitle className="text-2xl">
            {t('chat.feedEmpty.welcome')} <span className="italic text-(--accent)">#{channelName}</span>
          </EmptyTitle>
          <EmptyDescription>
            {t('chat.feedEmpty.desc')}
          </EmptyDescription>
          <div className="mt-4 flex items-center gap-2 text-xs text-(--text-3) font-mono">
            <Feather className="size-3" /> {t('chat.feedEmpty.beFirst')}
          </div>
        </Empty>
      </div>
    )
  }

  const items = virtualizer.getVirtualItems()

  return (
    <div ref={scrollRef} className="flex-1 overflow-y-auto astra-smooth-scroll astra-feed-scroll">
      {}
      <div ref={topRef} className="h-1" />

      {isFetchingNextPage && (
        <div className="flex justify-center py-2.5">
          <div
            className="size-5 rounded-full border-2 border-border animate-spin"
            style={{ borderTopColor: 'var(--accent)' }}
          />
        </div>
      )}

      {!hasNextPage && (
        <div className="px-5 pt-8 pb-6 border-b border-border mb-2">
          <div className="size-13 bg-card border border-border rounded-xl flex items-center justify-center mb-3">
            <span className="text-xl font-bold text-primary">#</span>
          </div>
          <h3 className="text-xl font-normal text-foreground mb-1" style={{ fontFamily: 'var(--font-display)' }}>
            {t('chat.feedStart.title')} <em className="text-primary">#{channelName}</em>
          </h3>
          <p className="text-sm text-muted-foreground m-0">{t('chat.feedStart.desc')}</p>
        </div>
      )}

      {}
      <div
        className="relative px-3 pb-2"
        style={{ height: `${virtualizer.getTotalSize()}px` }}
      >
        {items.map((row) => {
          const msg     = allMessages[row.index]
          const prev    = allMessages[row.index - 1]
          const grouped =
            prev?.author.id === msg.author.id &&
            new Date(msg.createdAt).getTime() - new Date(prev.createdAt).getTime() < 5 * 60 * 1000

          return (
            <div
              key={row.key}
              ref={virtualizer.measureElement}
              data-index={row.index}
              style={{
                position: 'absolute',
                top:      0,
                left:     0,
                right:    0,
                transform: `translateY(${row.start}px)`,

                contain:  'layout',
              }}
            >
              <MessageItem
                message={msg}
                grouped={grouped}
                delay={0}
                isPending={(msg as OptimisticMessage).isPending}
                roleColor={colorByUser.get(msg.author.id) ?? null}
                onReply={onReply}
              />
            </div>
          )
        })}
      </div>
    </div>
  )
}