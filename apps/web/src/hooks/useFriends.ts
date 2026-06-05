/**
 * Hook pra friends/requests/outgoing + actions (request, accept, remove).
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'

export interface FriendUser {
  id:           string
  username:     string
  displayName:  string
  avatarUrl:    string | null
  customStatus?: string | null
}

export interface FriendEntry {
  friendshipId: string
  user:         FriendUser
  presence:     'ONLINE' | 'IDLE' | 'DND' | 'INVISIBLE' | 'OFFLINE'
  since:        string | null
}

export interface PendingEntry {
  friendshipId: string
  user:         FriendUser
  createdAt:    string
}

export function useFriends() {
  return useQuery<FriendEntry[]>({
    queryKey: ['friends'],
    queryFn:  async () => (await api.get('/api/friends')).data.data,
    staleTime: 30_000,
  })
}

export function useFriendRequests() {
  return useQuery<PendingEntry[]>({
    queryKey: ['friends', 'requests'],
    queryFn:  async () => (await api.get('/api/friends/requests')).data.data,
    staleTime: 30_000,
  })
}

export function useFriendOutgoing() {
  return useQuery<PendingEntry[]>({
    queryKey: ['friends', 'outgoing'],
    queryFn:  async () => (await api.get('/api/friends/outgoing')).data.data,
    staleTime: 30_000,
  })
}

export type FriendRequestInput =
  | { username: string }
  | { coordinate: string }

export function useSendFriendRequest() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (input: FriendRequestInput) => {
      const res = await api.post('/api/friends/request', input)
      return res.data.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['friends'] })
    },
  })
}

export function useAcceptFriend() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => { await api.post(`/api/friends/${id}/accept`) },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['friends'] }),
  })
}

export function useRemoveFriend() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => { await api.delete(`/api/friends/${id}`) },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['friends'] }),
  })
}

export function useUpdateCustomStatus() {
  return useMutation({
    mutationFn: async (text: string | null) => {
      await api.patch('/api/friends/custom-status', { customStatus: text })
    },
  })
}
