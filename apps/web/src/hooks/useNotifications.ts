/**
 * Feed + prefs do user. Hook único pra centro de notifs (bell).
 *
 *  - useNotificationFeed(): infinite list paginada, sync via socket 'notification'
 *  - useNotificationCount(): só badge unread (poll/cached)
 *  - useNotificationPrefs(): GET + PATCH com optimistic update
 *
 * Socket 'notification' incrementa cache local sem refetch.
 */
import { useEffect } from 'react'
import {
  useInfiniteQuery, useMutation, useQuery, useQueryClient,
} from '@tanstack/react-query'
import { api } from '@/lib/api'
import { getSocket } from '@/lib/socket'

export type NotificationType = 'mention' | 'dm' | 'reaction' | 'reply'

export interface NotificationItem {
  id:        string
  type:      NotificationType
  payload:   Record<string, any>
  readAt:    string | null
  createdAt: string
}

interface FeedPage {
  items:      NotificationItem[]
  nextCursor: string | null
}

export interface NotificationPrefs {
  mentions:   boolean
  dms:        boolean
  reactions:  boolean
  replies:    boolean
  sounds:     boolean
  desktop:    boolean
  quietStart: number | null
  quietEnd:   number | null
}

// ── Feed (paginated) ────────────────────────────────────────────
// Socket sync foi movido pra useNotificationCount (que monta com o sino,
// sempre vivo). Antes só atualizava feed quando popover estava aberto.
export function useNotificationFeed() {
  return useInfiniteQuery<FeedPage>({
    queryKey: ['notifications', 'feed'],
    queryFn: async ({ pageParam }) => {
      const params = new URLSearchParams({ limit: '30' })
      if (pageParam) params.set('cursor', pageParam as string)
      const res = await api.get(`/api/notifications?${params}`)
      return res.data.data
    },
    getNextPageParam: (p) => p.nextCursor ?? undefined,
    initialPageParam: undefined as string | undefined,
    staleTime: 30_000,
  })
}

// ── Badge count ─────────────────────────────────────────────────
export function useNotificationCount() {
  const queryClient = useQueryClient()

  // Sync via socket: feed listener antes ficava em useNotificationFeed (só
  // monta quando popover abre). Aqui o badge fica vivo enquanto o sino tá
  // na tela — evento 'notifications_read' sempre fecha o badge no momento.
  useEffect(() => {
    let sock: ReturnType<typeof getSocket>
    try { sock = getSocket() } catch { return }

    const onNotif = (p: { id: string; type: NotificationType; payload: any; createdAt: string }) => {
      queryClient.setQueryData(['notifications', 'feed'], (old: any) => {
        if (!old) return old
        const [first, ...rest] = old.pages
        if (first.items.some((n: NotificationItem) => n.id === p.id)) return old
        const newItem: NotificationItem = {
          id: p.id, type: p.type, payload: p.payload, readAt: null, createdAt: p.createdAt,
        }
        return { ...old, pages: [{ ...first, items: [newItem, ...first.items] }, ...rest] }
      })
      queryClient.setQueryData(['notifications', 'unread'], (old: any) =>
        old ? { count: old.count + 1 } : { count: 1 }
      )
    }

    const onRead = (p: { ids: string[] }) => {
      const ids = new Set(p.ids)
      queryClient.setQueryData(['notifications', 'feed'], (old: any) => {
        if (!old) return old
        const now = new Date().toISOString()
        return {
          ...old,
          pages: old.pages.map((page: FeedPage) => ({
            ...page,
            items: page.items.map((n) =>
              ids.has(n.id) && !n.readAt ? { ...n, readAt: now } : n
            ),
          })),
        }
      })
      queryClient.setQueryData(['notifications', 'unread'], (old: any) =>
        old ? { count: Math.max(0, old.count - ids.size) } : { count: 0 }
      )
    }

    sock.on('notification',       onNotif)
    sock.on('notifications_read', onRead)
    return () => {
      sock.off('notification',       onNotif)
      sock.off('notifications_read', onRead)
    }
  }, [queryClient])

  return useQuery<{ count: number }>({
    queryKey: ['notifications', 'unread'],
    queryFn:  async () => (await api.get('/api/notifications/unread')).data.data,
    staleTime: 30_000,
    refetchInterval: 60_000, // safety net
  })
}

// ── Mark read ───────────────────────────────────────────────────
export function useMarkRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      await api.post(`/api/notifications/${id}/read`)
    },
    onMutate: async (id) => {
      qc.setQueryData(['notifications', 'feed'], (old: any) => {
        if (!old) return old
        const now = new Date().toISOString()
        return {
          ...old,
          pages: old.pages.map((p: FeedPage) => ({
            ...p,
            items: p.items.map((n) => n.id === id ? { ...n, readAt: now } : n),
          })),
        }
      })
      qc.setQueryData(['notifications', 'unread'], (old: any) =>
        old ? { count: Math.max(0, old.count - 1) } : { count: 0 }
      )
    },
  })
}

export function useMarkAllRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => { await api.post('/api/notifications/read-all') },
    onMutate: async () => {
      const now = new Date().toISOString()
      qc.setQueryData(['notifications', 'feed'], (old: any) => {
        if (!old) return old
        return {
          ...old,
          pages: old.pages.map((p: FeedPage) => ({
            ...p,
            items: p.items.map((n) => n.readAt ? n : { ...n, readAt: now }),
          })),
        }
      })
      qc.setQueryData(['notifications', 'unread'], { count: 0 })
    },
  })
}

// ── Prefs ───────────────────────────────────────────────────────
export function useNotificationPrefs() {
  return useQuery<{ prefs: NotificationPrefs; defaults: NotificationPrefs }>({
    queryKey: ['notifications', 'prefs'],
    queryFn:  async () => (await api.get('/api/notifications/prefs')).data.data,
    staleTime: 5 * 60_000,
  })
}

export function useUpdatePrefs() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (patch: Partial<NotificationPrefs>) => {
      const res = await api.patch('/api/notifications/prefs', patch)
      return res.data.data.prefs as NotificationPrefs
    },
    onSuccess: (prefs) => {
      qc.setQueryData(['notifications', 'prefs'], (old: any) =>
        old ? { ...old, prefs } : { prefs, defaults: prefs }
      )
    },
  })
}
