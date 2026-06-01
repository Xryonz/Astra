import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'

export type Permission =
  | 'MANAGE_SERVER'
  | 'MANAGE_ROLES'
  | 'MANAGE_CHANNELS'
  | 'KICK_MEMBERS'
  | 'BAN_MEMBERS'
  | 'MANAGE_MESSAGES'
  | 'MENTION_EVERYONE'

interface MyPerms {
  isOwner:     boolean
  isAdmin:     boolean
  permissions: Permission[]
}

export function useMyPerms(serverId?: string) {
  const q = useQuery<MyPerms>({
    queryKey: ['perms', serverId],
    queryFn:  async () => (await api.get(`/api/servers/${serverId}/me`)).data.data,
    enabled:  !!serverId,
    staleTime: 30_000,
  })
  const data = q.data ?? { isOwner: false, isAdmin: false, permissions: [] as Permission[] }
  return {
    ...q,
    isOwner: data.isOwner,
    isAdmin: data.isAdmin,
    has: (p: Permission) => data.isOwner || data.permissions.includes(p),
  }
}
