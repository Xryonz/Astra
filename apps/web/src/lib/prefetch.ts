import type { QueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { MessageWithAuthor, PaginatedResponse } from '@astra/types'

export async function fetchMessagesPage(
  channelId: string, cursor?: string,
): Promise<PaginatedResponse<MessageWithAuthor>> {
  const params = new URLSearchParams({ limit: '30' })
  if (cursor) params.set('cursor', cursor)
  const res = await api.get(`/api/channels/${channelId}/messages?${params}`)
  return res.data.data as PaginatedResponse<MessageWithAuthor>
}

export function prefetchChannelMessages(qc: QueryClient, channelId: string): void {
  void qc.prefetchInfiniteQuery({
    queryKey: ['messages', channelId],
    queryFn: ({ pageParam }) => fetchMessagesPage(channelId, pageParam as string | undefined),
    initialPageParam: undefined as string | undefined,
    staleTime: 15_000,
  })
}
