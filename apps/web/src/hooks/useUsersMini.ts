/**
 * useUsersMini — lookup batch de avatar/displayName por user id.
 *
 * Usado pra renderizar listas de participantes (voice presence, mentions, etc)
 * sem N+1 requests. Cache 60s; chave estável via sort.
 */
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'

export interface UserMini {
  id:           string
  username:     string
  displayName:  string
  avatarUrl:    string | null
  bannerColor?: string | null
}

export function useUsersMini(ids: string[]) {
  const sorted = [...new Set(ids)].sort()
  const key    = sorted.join(',')
  return useQuery<UserMini[]>({
    queryKey: ['users-mini', key],
    queryFn:  async () => {
      if (!key) return []
      const res = await api.get(`/api/profile/lookup?ids=${encodeURIComponent(key)}`)
      return res.data.data as UserMini[]
    },
    enabled:   sorted.length > 0,
    staleTime: 60_000,
  })
}
