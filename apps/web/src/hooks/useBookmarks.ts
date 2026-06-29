
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'

export type BookmarkKind = 'message' | 'dm'

export interface BookmarkSnapshot {
  id:            string
  content:       string
  channelId?:    string
  conversationId?: string
  createdAt:     string
  authorId:      string
  authorName:    string
  authorAvatar:  string | null
}

export interface BookmarkItem {
  id:        string
  targetId:  string
  kind:      BookmarkKind
  note:      string | null
  createdAt: string
  snapshot:  BookmarkSnapshot | null
}

export function useBookmarkList() {
  return useInfiniteQuery<{ items: BookmarkItem[]; nextCursor: string | null }>({
    queryKey: ['bookmarks'],
    queryFn: async ({ pageParam }) => {
      const params = new URLSearchParams({ limit: '30' })
      if (pageParam) params.set('cursor', pageParam as string)
      const res = await api.get(`/api/bookmarks?${params}`)
      return res.data.data
    },
    getNextPageParam: (p) => p.nextCursor ?? undefined,
    initialPageParam: undefined as string | undefined,
    staleTime: 60_000,
  })
}

export function useBookmarkIndex() {
  return useQuery<Set<string>>({
    queryKey: ['bookmarks', 'index'],
    queryFn: async () => {

      const res = await api.get('/api/bookmarks?limit=100')
      const ids = new Set<string>()
      for (const b of res.data.data.items as BookmarkItem[]) {
        ids.add(`${b.kind}:${b.targetId}`)
      }
      return ids
    },
    staleTime: 5 * 60_000,
  })
}

export function useIsBookmarked(targetId: string | null, kind: BookmarkKind): boolean {
  const { data } = useBookmarkIndex()
  if (!data || !targetId) return false
  return data.has(`${kind}:${targetId}`)
}

export function useToggleBookmark() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ targetId, kind, action }: { targetId: string; kind: BookmarkKind; action: 'create' | 'delete'; bookmarkId?: string }) => {
      if (action === 'create') {
        const res = await api.post('/api/bookmarks', { targetId, kind })
        return res.data.data as BookmarkItem
      }

      const res = await api.get('/api/bookmarks?limit=100')
      const found = (res.data.data.items as BookmarkItem[]).find(
        (b) => b.targetId === targetId && b.kind === kind,
      )
      if (found) await api.delete(`/api/bookmarks/${found.id}`)
      return null
    },
    onMutate: async ({ targetId, kind, action }) => {
      const key  = `${kind}:${targetId}`
      const prev = qc.getQueryData<Set<string>>(['bookmarks', 'index'])
      if (prev) {
        const next = new Set(prev)
        if (action === 'create') next.add(key)
        else                     next.delete(key)
        qc.setQueryData(['bookmarks', 'index'], next)
      }
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['bookmarks'] })
    },
  })
}
