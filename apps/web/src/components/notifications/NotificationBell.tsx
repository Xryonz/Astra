
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import { formatDistanceToNow } from 'date-fns'
import { ptBR } from 'date-fns/locale/pt-BR'
import { enUS } from 'date-fns/locale/en-US'
import { Sparkles } from 'lucide-react'
import {
  useNotificationFeed, useNotificationCount, useMarkRead, useMarkAllRead,
  type NotificationItem, type NotificationType,
} from '@/hooks/useNotifications'
import { Empty, EmptyIcon, EmptyLabel, EmptyTitle, EmptyDescription } from '@/components/ui/empty'
import { Spinner } from '@/components/ui/spinner'
import { resolveApiUrl } from '@/lib/api'

const typeLabel = (t: TFunction, type: NotificationType) =>
  t(`bell.type${type.charAt(0).toUpperCase()}${type.slice(1)}`)

const TYPE_ICON: Record<NotificationType, string> = {
  mention:  '@',
  dm:       '✉',
  reaction: '☆',
  reply:    '↩',
}

export function NotificationBell() {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const wrapRef = useRef<HTMLDivElement>(null)

  const { data: count } = useNotificationCount()
  const unread = count?.count ?? 0

  const [shakeKey, setShakeKey] = useState(0)
  const prevUnreadRef = useRef(unread)
  useEffect(() => {
    if (unread > prevUnreadRef.current) setShakeKey((k) => k + 1)
    prevUnreadRef.current = unread
  }, [unread])

  useEffect(() => {
    if (!open) return
    const onDoc = (e: MouseEvent) => {
      if (!wrapRef.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onDoc)
    document.addEventListener('keydown',   onKey)
    return () => {
      document.removeEventListener('mousedown', onDoc)
      document.removeEventListener('keydown',   onKey)
    }
  }, [open])

  return (

    <div ref={wrapRef} className="relative hidden md:block">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="relative grid size-9 place-items-center rounded-lg border border-border bg-card text-muted-foreground hover:text-foreground hover:bg-(--accent)/5 transition-colors"
        aria-label={unread > 0 ? t('bell.ariaUnread', { count: unread }) : t('bell.aria')}
      >
        <span
          key={shakeKey}
          style={{
            display: 'inline-flex',
            transformOrigin: '50% 10%',
            animation: shakeKey > 0 ? 'bellShake 0.6s cubic-bezier(0.36, 0.07, 0.19, 0.97) both' : undefined,
            willChange: 'transform',
          }}
        >
          <BellIcon />
        </span>
        {unread > 0 && (
          <span
            key={'badge-' + shakeKey}
            className="absolute -top-1 -right-1 min-w-4.5 h-4.5 px-1 rounded-full bg-(--accent) text-[10px] font-semibold text-(--accent-foreground) flex items-center justify-center"
            style={{
              animation: shakeKey > 0 ? 'badgePop 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) both' : undefined,
              willChange: 'transform',
            }}
            aria-hidden
          >
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && <NotificationCenter onClose={() => setOpen(false)} />}
    </div>
  )
}

function BellIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
      <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
    </svg>
  )
}

export function NotificationCenter({
  onClose,

  className = 'absolute right-0 mt-2 w-95 max-h-130 rounded-xl border border-border bg-background shadow-2xl z-50',
}: {
  onClose: () => void
  className?: string
}) {
  const { t } = useTranslation()
  const [filter, setFilter] = useState<'all' | NotificationType>('all')
  const feed = useNotificationFeed()
  const markRead    = useMarkRead()
  const markAllRead = useMarkAllRead()

  const all   = feed.data?.pages.flatMap((p) => p.items) ?? []
  const items = filter === 'all' ? all : all.filter((n) => n.type === filter)

  return (
    <div className={`flex flex-col overflow-hidden ${className}`}>
      <header className="flex items-center justify-between px-4 py-3 border-b border-border">
        <div>
          <h3 className="text-sm font-medium text-foreground" style={{ fontFamily: 'var(--font-display)' }}>
            {t('notif.title')}
          </h3>
          <p className="text-xs text-muted-foreground m-0">{all.length === 0 ? t('bell.nothingYet') : t('bell.unread', { count: all.filter((n) => !n.readAt).length })}</p>
        </div>
        <button
          onClick={() => markAllRead.mutate()}
          disabled={all.every((n) => n.readAt)}
          className="text-xs text-muted-foreground hover:text-foreground disabled:opacity-40 disabled:pointer-events-none transition-colors"
        >
          {t('bell.markAll')}
        </button>
      </header>

      {}
      <div className="flex gap-1 px-3 py-2 border-b border-border overflow-x-auto">
        {(['all', 'mention', 'dm', 'reply', 'reaction'] as const).map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`px-2.5 py-1 text-xs rounded-lg whitespace-nowrap transition-colors ${
              filter === f
                ? 'bg-(--accent)/10 text-(--accent)'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            {f === 'all' ? t('bell.tabAll') : typeLabel(t, f)}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto">
        {feed.isLoading ? (
          <div className="flex items-center justify-center gap-2 py-10 text-xs text-muted-foreground">
            <Spinner size={12} /> {t('bell.loading')}
          </div>
        ) : items.length === 0 ? (
          <Empty>
            <EmptyIcon><Sparkles className="size-6 text-(--accent)" /></EmptyIcon>
            <EmptyLabel>{t('bell.emptyLabel')}</EmptyLabel>
            <EmptyTitle>
              {filter === 'all' ? t('bell.allCaughtUp') : t('bell.noTypeYet', { type: typeLabel(t, filter as NotificationType).toLowerCase() })}
            </EmptyTitle>
            <EmptyDescription>{t('bell.emptyDesc')}</EmptyDescription>
          </Empty>
        ) : (
          <div className="divide-y divide-border" role="list">
            {items.map((n, i) => (
              <div
                key={n.id}
                role="listitem"
                style={{
                  animation: `fadeLeft 0.28s cubic-bezier(0.16,1,0.3,1) ${Math.min(i * 0.022, 0.3)}s both`,
                }}
              >
                <NotificationRow
                  n={n}
                  onActivate={(item) => {
                    if (!item.readAt) markRead.mutate(item.id)
                    navigateTo(item)
                    onClose()
                  }}
                />
              </div>
            ))}
          </div>
        )}

        {feed.hasNextPage && (
          <div className="p-3 text-center">
            <button
              onClick={() => feed.fetchNextPage()}
              disabled={feed.isFetchingNextPage}
              className="text-xs text-muted-foreground hover:text-foreground transition-colors"
            >
              {feed.isFetchingNextPage ? t('bell.loading') : t('bell.seeMore')}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

function NotificationRow({
  n, onActivate,
}: {
  n: NotificationItem
  onActivate: (n: NotificationItem) => void
}) {
  const { t, i18n } = useTranslation()
  const avatar = n.payload?.authorAvatar ? resolveApiUrl(n.payload.authorAvatar) : null
  const author = n.payload?.authorName ?? t('bell.someone')
  const preview = n.payload?.preview ?? ''
  const ts = formatDistanceToNow(new Date(n.createdAt), { addSuffix: true, locale: i18n.language === 'pt' ? ptBR : enUS })

  let summary = ''
  switch (n.type) {
    case 'mention':  summary = t('bell.sumMention', { channel: n.payload.channelName ?? '?' }); break
    case 'dm':       summary = t('bell.sumDm'); break
    case 'reply':    summary = t('bell.sumReply', { channel: n.payload.channelName ?? '?' }); break
    case 'reaction': summary = t('bell.sumReaction', { emoji: n.payload.emoji ?? '☆' }); break
  }

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => onActivate(n)}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onActivate(n) } }}
      className={`flex gap-3 px-4 py-3 cursor-pointer hover:bg-card transition-colors ${
        n.readAt ? '' : 'bg-(--accent)/3'
      }`}
    >
      <div className="shrink-0">
        {avatar ? (
          <img src={avatar} alt={author} loading="lazy" decoding="async" className="size-9 rounded-full object-cover" />
        ) : (
          <div className="size-9 rounded-full bg-card border border-border flex items-center justify-center text-sm text-muted-foreground">
            {TYPE_ICON[n.type]}
          </div>
        )}
      </div>

      <div className="flex-1 min-w-0">
        <p className="text-sm text-foreground m-0 leading-tight">
          <span className="font-medium">{author}</span>
          <span className="text-muted-foreground"> {summary}</span>
        </p>
        {preview && (
          <p className="text-xs text-muted-foreground mt-1 m-0 truncate">{preview}</p>
        )}
        <p className="text-[11px] text-muted-foreground mt-1 m-0">{ts}</p>
      </div>

      {!n.readAt && (
        <span className="shrink-0 mt-1.5 size-1.5 rounded-full bg-(--accent)" aria-label={t('bell.unreadDot')} />
      )}
    </div>
  )
}

function navigateTo(n: NotificationItem) {
  const url = n.type === 'dm'
    ? (n.payload?.conversationId ? `/app/dm/${n.payload.conversationId}` : '/app/dm')
    : '/app'
  if (window.location.pathname !== url) window.location.href = url
}
