import { useState, useRef, useCallback, useEffect, lazy, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { Routes, Route, useLocation } from 'react-router-dom'
import { useViewTransitionNavigate } from '@/hooks/useViewTransitionNavigate'
import Sidebar from '@/components/layout/Sidebar'
import MobileProfileBar from '@/components/layout/MobileProfileBar'
import MobileMoreSheet from '@/components/layout/MobileMoreSheet'
import MobileNotificationsSheet from '@/components/notifications/MobileNotificationsSheet'
import MobileAvatarTrigger from '@/components/layout/MobileAvatarTrigger'
import { PageTransition } from '@/components/anim/PageTransition'
import { AnimatePresence } from 'motion/react'
import { Pin, Search, Users as UsersIcon, Bookmark, MoreHorizontal } from 'lucide-react'
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem,
} from '@/components/ui/dropdown-menu'
import { useUIStore } from '@/store/uiStore'
import { hapticLight } from '@/lib/haptics'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { useUnread } from '@/hooks/useUnread'
import type { ServerWithChannels } from '@astra/types'
import { usePresenceListener } from '@/hooks/usePresence'
import { useInAppNotifications } from '@/hooks/useInAppNotifications'
import MessageList from '@/components/chat/MessageList'
import MessageInput from '@/components/chat/MessageInput'
import TypingIndicator from '@/components/chat/TypingIndicator'
import ChannelNotifButton, { ChannelNotifMenuItems } from '@/components/chat/ChannelNotifButton'
import { ServerEmojiProvider } from '@/hooks/useServerEmojis'
import MentionBanner from '@/components/chat/MentionBanner'
import { NotificationBell } from '@/components/notifications/NotificationBell'
import type { MessageWithAuthor } from '@astra/types'

const VoiceCallPanel    = lazy(() => import('@/components/voice/VoiceCallPanel').then((m) => ({ default: m.VoiceCallPanel })))
const IncomingCallModal = lazy(() => import('@/components/voice/IncomingCallModal').then((m) => ({ default: m.IncomingCallModal })))
const DMPage              = lazy(() => import('@/pages/DMPage'))
const ProfilePage         = lazy(() => import('@/pages/ProfilePage'))
const SettingsPage        = lazy(() => import('@/pages/SettingsPage'))
const ServerSettingsPage  = lazy(() => import('@/pages/ServerSettingsPage'))
const CommandPalette      = lazy(() => import('@/components/CommandPalette'))
const PinnedMessagesSheet = lazy(() => import('@/components/chat/PinnedMessagesSheet'))
const RightPanel          = lazy(() => import('@/components/chat/RightPanel'))
const BookmarksSheet      = lazy(() => import('@/components/bookmarks/BookmarksSheet'))
const FriendsPage         = lazy(() => import('@/pages/FriendsPage'))
const DiscoverPage        = lazy(() => import('@/pages/DiscoverPage'))
const CosmicOnboarding    = lazy(() => import('@/components/astra/CosmicOnboarding').then((m) => ({ default: m.CosmicOnboarding })))
const LatencyOverlay      = lazy(() => import('@/components/dev/LatencyOverlay').then((m) => ({ default: m.LatencyOverlay })))

type OptimisticMessage = MessageWithAuthor & { optimisticId?: string; isPending?: boolean }
interface ActiveChannel { id: string; name: string; serverId: string }

function ChannelView() {
  const { t }        = useTranslation()
  const location     = useLocation()
  const locationState = location.state as ActiveChannel | null
  const [activeChannel, setActiveChannel] = useState<ActiveChannel | null>(locationState ?? null)
  const openCommandPalette = useUIStore((s) => s.openCommandPalette)
  const openRightPanel     = useUIStore((s) => s.openRightPanel)
  const [pinnedOpen, setPinnedOpen]     = useState(false)
  const [bookmarksOpen, setBookmarksOpen] = useState(false)
  const [replyingTo, setReplyingTo]     = useState<MessageWithAuthor | null>(null)

  useEffect(() => { setReplyingTo(null) }, [activeChannel?.id])

  useEffect(() => {
    if (locationState && locationState.id !== activeChannel?.id) {
      setActiveChannel(locationState)
    }
  }, [locationState, activeChannel?.id])

  const addOptimisticRef     = useRef<((msg: OptimisticMessage) => void) | null>(null)
  const removeOptimisticRef  = useRef<((id: string) => void) | null>(null)
  const confirmOptimisticRef = useRef<((id: string, msg: MessageWithAuthor) => void) | null>(null)

  const handleRegisterOptimistic = useCallback(
    (
      add:     (m: OptimisticMessage) => void,
      remove:  (id: string) => void,
      confirm: (id: string, msg: MessageWithAuthor) => void,
    ) => {
      addOptimisticRef.current     = add
      removeOptimisticRef.current  = remove
      confirmOptimisticRef.current = confirm
    }, []
  )
  const handleOptimisticMessage   = useCallback((m: OptimisticMessage) => addOptimisticRef.current?.(m), [])
  const handleOptimisticFailed    = useCallback((id: string) => removeOptimisticRef.current?.(id), [])
  const handleOptimisticConfirmed = useCallback((id: string, msg: MessageWithAuthor) => confirmOptimisticRef.current?.(id, msg), [])

  const handleMentionNavigate = useCallback((channelId: string, channelName: string, serverId: string) => {
    setActiveChannel({ id: channelId, name: channelName, serverId })
  }, [])

  const navigate = useViewTransitionNavigate()
  const unread   = useUnread()
  const { data: kbServers } = useQuery<ServerWithChannels[]>({
    queryKey: ['servers'],
    queryFn: async () => (await api.get('/api/servers')).data.data,
    staleTime: 5 * 60_000,
  })
  useEffect(() => {
    if (!activeChannel) return
    const onKey = (e: KeyboardEvent) => {
      if (e.altKey && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
        const chans = kbServers
          ?.find((s) => s.id === activeChannel.serverId)
          ?.channels?.filter((c) => c.type === 'TEXT') ?? []
        const idx = chans.findIndex((c) => c.id === activeChannel.id)
        if (idx === -1 || chans.length < 2) return
        e.preventDefault()
        const dir  = e.key === 'ArrowDown' ? 1 : -1
        const next = chans[(idx + dir + chans.length) % chans.length]
        navigate('/app', { state: { id: next.id, name: next.name, serverId: activeChannel.serverId } })
      }

      if (e.key === 'Escape' && !document.querySelector('[role="dialog"][data-state="open"]')) {
        unread.markRead(activeChannel.id)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [activeChannel, kbServers, navigate, unread])

  return (
    <div className="flex-1 flex min-w-0 h-full min-h-0 overflow-hidden">
      <div className="flex-1 flex flex-col min-w-0">
        {activeChannel ? (
          <>
            {}
            <header
              key={activeChannel.id + '-hdr'}
              className="shrink-0 h-14 px-3 sm:px-5 flex items-center gap-2 border-b border-(--border) bg-(--base)"
            >
              {}
              <MobileAvatarTrigger className="-ml-1" />

              <span className="text-(--text-3) text-sm font-mono">#</span>
              <h2
                className="text-sm sm:text-base m-0 font-medium tracking-tight text-foreground truncate"
                style={{ fontFamily: 'var(--font-display)' }}
              >
                {activeChannel.name}
              </h2>

              {}
              <div className="ml-auto flex items-center gap-0.5 shrink-0">
                <button
                  onClick={openCommandPalette}
                  className="size-11 sm:size-8 flex items-center justify-center text-(--text-3) hover:text-(--accent) transition-colors cursor-pointer"
                  aria-label={t('chat.header.search')}
                  title={t('chat.header.search')}
                >
                  <Search className="size-4" />
                </button>
                {}
                <button
                  onClick={() => openRightPanel('members')}
                  className="size-8 hidden md:flex items-center justify-center text-(--text-3) hover:text-(--accent) transition-colors cursor-pointer"
                  aria-label={t('chat.header.membersThreads')}
                  title={t('chat.header.membersThreads')}
                >
                  <UsersIcon className="size-4" />
                </button>
                <button
                  onClick={() => setPinnedOpen(true)}
                  className="size-8 hidden md:flex items-center justify-center text-(--text-3) hover:text-(--accent) transition-colors cursor-pointer"
                  aria-label={t('chat.header.pinned')}
                  title={t('chat.header.pinned')}
                >
                  <Pin className="size-4" />
                </button>
                <button
                  onClick={() => setBookmarksOpen(true)}
                  className="size-8 hidden md:flex items-center justify-center text-(--text-3) hover:text-(--accent) transition-colors cursor-pointer"
                  aria-label={t('chat.header.saved')}
                  title={t('chat.header.saved')}
                >
                  <Bookmark className="size-4" />
                </button>
                <ChannelNotifButton channelId={activeChannel.id} />

                {}
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <button
                      className="size-11 md:hidden flex items-center justify-center text-(--text-3) hover:text-(--accent) transition-colors cursor-pointer"
                      aria-label={t('chat.header.moreActions')}
                    >
                      <MoreHorizontal className="size-5" />
                    </button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem onSelect={() => openRightPanel('members')}>
                      <UsersIcon className="size-3.5" /> {t('chat.header.membersThreads')}
                    </DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => setPinnedOpen(true)}>
                      <Pin className="size-3.5" /> {t('chat.header.pinned')}
                    </DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => setBookmarksOpen(true)}>
                      <Bookmark className="size-3.5" /> {t('chat.header.saved')}
                    </DropdownMenuItem>
                    {}
                    <ChannelNotifMenuItems channelId={activeChannel.id} />
                  </DropdownMenuContent>
                </DropdownMenu>

                <NotificationBell />
              </div>
            </header>

            {pinnedOpen && (
              <Suspense fallback={null}>
                <PinnedMessagesSheet
                  channelId={activeChannel.id}
                  channelName={activeChannel.name}
                  open={pinnedOpen}
                  onClose={() => setPinnedOpen(false)}
                />
              </Suspense>
            )}

            {bookmarksOpen && (
              <Suspense fallback={null}>
                <BookmarksSheet open={bookmarksOpen} onClose={() => setBookmarksOpen(false)} />
              </Suspense>
            )}

            <ServerEmojiProvider serverId={activeChannel.serverId}>
              <MessageList
                key={activeChannel.id}
                channelId={activeChannel.id}
                channelName={activeChannel.name}
                serverId={activeChannel.serverId}
                onRegisterOptimistic={handleRegisterOptimistic}
                onReply={setReplyingTo}
              />
              <TypingIndicator channelId={activeChannel.id} />
              <MessageInput
                channelId={activeChannel.id}
                channelName={activeChannel.name}
                serverId={activeChannel.serverId}
                replyingTo={replyingTo}
                onCancelReply={() => setReplyingTo(null)}
                onOptimisticMessage={handleOptimisticMessage}
                onOptimisticFailed={handleOptimisticFailed}
                onOptimisticConfirmed={handleOptimisticConfirmed}
              />
            </ServerEmojiProvider>
          </>
        ) : (

          <Suspense fallback={<div className="flex-1 min-w-0 h-full" />}>
            <DMPage />
          </Suspense>
        )}
      </div>

      {}
      <MentionBanner onNavigate={handleMentionNavigate} />

      {}
      {activeChannel && (
        <Suspense fallback={null}>
          <RightPanel serverId={activeChannel.serverId} channelId={activeChannel.id} />
        </Suspense>
      )}
    </div>
  )
}

export default function AppPage() {
  const navigate = useViewTransitionNavigate()
  const location = useLocation()
  const activeId = (location.state as ActiveChannel | null)?.id ?? null
  const toggleCommandPalette = useUIStore((s) => s.toggleCommandPalette)

  usePresenceListener()
  useInAppNotifications()

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        toggleCommandPalette()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [toggleCommandPalette])

  useEffect(() => {
    const ric = (window as any).requestIdleCallback ?? ((cb: () => void) => setTimeout(cb, 1200))
    const id = ric(() => {
      import('@/pages/DMPage')
      import('@/pages/FriendsPage')
      import('@/pages/SettingsPage')
      import('@/components/CommandPalette')
    })
    const cancel = (window as any).cancelIdleCallback
    return () => { if (cancel) cancel(id); else clearTimeout(id) }
  }, [])

  useEffect(() => {
    const onTop = () => {
      document.querySelectorAll('[data-radix-scroll-area-viewport], .astra-scrollable')
        .forEach((el) => (el as HTMLElement).scrollTo({ top: 0, behavior: 'smooth' }))
    }
    window.addEventListener('astra:scroll-top', onTop)
    return () => window.removeEventListener('astra:scroll-top', onTop)
  }, [])

  useEffect(() => {
    if (!window.matchMedia('(pointer: coarse)').matches) return
    let start: { x: number; y: number } | null = null
    const onStart = (e: TouchEvent) => {
      start = null
      if (window.innerWidth >= 768) return
      if (useUIStore.getState().mobileSidebarOpen) return

      if ((e.target as Element).closest('pre, input, textarea, [role="dialog"]')) return
      const t = e.touches[0]
      start = { x: t.clientX, y: t.clientY }
    }
    const onMove = (e: TouchEvent) => {
      if (!start) return
      const t  = e.touches[0]
      const dx = t.clientX - start.x
      const dy = t.clientY - start.y
      if (dx > 0 || Math.abs(dy) > 48) { start = null; return }
      if (dx < -64 && -dx > Math.abs(dy) * 1.8) {
        start = null
        useUIStore.getState().openMobileSidebar()
        hapticLight()
      }
    }
    window.addEventListener('touchstart', onStart, { passive: true })
    window.addEventListener('touchmove',  onMove,  { passive: true })
    return () => {
      window.removeEventListener('touchstart', onStart)
      window.removeEventListener('touchmove',  onMove)
    }
  }, [])

  return (
    <div className="astra-shell flex h-screen-safe overflow-hidden font-(family-name:--font-body) pb-16 md:pb-0">
      {}
      <a
        href="#astra-main"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-9999 focus:px-3 focus:py-2 focus:rounded-lg focus:bg-(--accent) focus:text-(--text-inv) focus:text-sm focus:font-medium focus:shadow-3"
      >
        Pular para o conteúdo
      </a>

      {}
      <Sidebar
        activeChannelId={activeId}
        onSelectChannel={(id, name, serverId) =>
          navigate('/app', { state: { id, name, serverId } })
        }
      />

      {}
      <main id="astra-main" tabIndex={-1} className="contents">
        <Suspense fallback={<div className="flex-1 min-w-0 h-full" />}>
          <AnimatePresence mode="wait" initial={false}>
            <Routes location={location} key={location.pathname.split('/').slice(0, 3).join('/')}>
              <Route path="dm/*"    element={<PageTransition className="flex-1 min-w-0 h-full"><DMPage /></PageTransition>} />
              <Route path="friends" element={<PageTransition><FriendsPage /></PageTransition>} />
              <Route path="discover" element={<PageTransition><DiscoverPage /></PageTransition>} />
              <Route path="profile" element={<PageTransition><ProfilePage /></PageTransition>} />
              <Route path="settings" element={<PageTransition><SettingsPage /></PageTransition>} />
              <Route path="servers/:serverId/settings" element={<PageTransition><ServerSettingsPage /></PageTransition>} />
              <Route path="*"       element={<PageTransition><ChannelView /></PageTransition>} />
            </Routes>
          </AnimatePresence>
        </Suspense>
      </main>

      <Suspense fallback={null}>
        <CommandPalette />
      </Suspense>

      {}
      <Suspense fallback={null}>
        <VoiceCallPanel />
        <IncomingCallModal />
      </Suspense>

      {}
      <MobileProfileBar />
      <MobileMoreSheet />
      <MobileNotificationsSheet />

      {}
      <Suspense fallback={null}>
        <CosmicOnboarding />
      </Suspense>

      {}
      <Suspense fallback={null}>
        <LatencyOverlay />
      </Suspense>
    </div>
  )
}