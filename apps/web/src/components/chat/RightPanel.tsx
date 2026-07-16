import { useState, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useVirtualizer } from '@tanstack/react-virtual'
import { Crown, Shield, X, Users as UsersIcon } from 'lucide-react'
import { api } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { usePresenceStore } from '@/store/presenceStore'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { Empty, EmptyIcon, EmptyLabel, EmptyTitle, EmptyDescription } from '@/components/ui/empty'
import { Spinner } from '@/components/ui/spinner'
import ProfileCard from '@/components/ProfileCard'
import { ProfileHoverCard } from '@/components/ProfileHoverCard'
import StatusDot, { type UserStatus } from '@/components/StatusDot'
import { cn } from '@/lib/utils'

interface Member {
  id: string; userId: string; role: 'OWNER'|'ADMIN'|'MEMBER'
  user: { id: string; username: string; displayName: string; avatarUrl: string|null }
  roles?: Array<{ id: string; name: string; color: string|null; position: number; hoist: boolean }>
  topColor?: string | null
}
interface RightPanelProps {
  serverId: string
}

export default function RightPanel({ serverId }: RightPanelProps) {
  const { t }   = useTranslation()
  const open    = useUIStore((s) => s.rightPanelOpen)
  const close   = useUIStore((s) => s.closeRightPanel)

  const [profileId, setProfileId] = useState<string | null>(null)

  if (!open) return null

  return (
    <>
      {}
      <div
        onClick={close}
        className="md:hidden fixed inset-0 z-50 bg-black/60 backdrop-blur-sm anim-fade-in"
      />

      {}
      <aside
        className={cn(

          'shrink-0 md:h-full border-l border-(--border) bg-(--base) flex flex-col z-50',
          'w-72 sm:w-80',

          'fixed top-0 right-0 bottom-0 md:static md:top-auto md:right-auto md:bottom-auto',
        )}
      >
        {}
        <div className="h-12 px-3 flex items-center gap-2 border-b border-(--border) shrink-0">
          <h3
            className="text-sm m-0 font-medium tracking-tight text-foreground truncate flex-1"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            {t('rightPanel.members')}
          </h3>
          <button
            onClick={close}
            className="size-7 flex items-center justify-center text-(--text-3) hover:text-(--accent) transition-colors cursor-pointer"
            aria-label={t('rightPanel.closePanel')}
            title={t('common.close')}
          >
            <X className="size-4" />
          </button>
        </div>

        <div className="flex-1 min-h-0 mt-3">
          <MembersList serverId={serverId} onPickUser={(id) => setProfileId(id)} />
        </div>
      </aside>

      {profileId && <ProfileCard userId={profileId} onClose={() => setProfileId(null)} />}
    </>
  )
}

const STATUS_ORDER: Record<UserStatus, number> = { ONLINE: 0, IDLE: 1, DND: 2, INVISIBLE: 3, OFFLINE: 4 }

function MembersList({ serverId, onPickUser }: { serverId: string; onPickUser: (id: string) => void }) {
  const { t }    = useTranslation()
  const presence = usePresenceStore((s) => s.others)
  const bulkSet  = usePresenceStore((s) => s.bulkSet)

  const { data: members = [], isLoading, isError, error, refetch } = useQuery<Member[]>({
    queryKey: ['members', serverId],
    queryFn:  async () => (await api.get(`/api/servers/${serverId}/members`)).data.data,
    enabled:  !!serverId,
  })

  useEffect(() => {
    if (members.length === 0) return
    const ids = members.map((m) => m.userId).join(',')
    api.get(`/api/profile/presence?ids=${ids}`).then((r) => {
      bulkSet(r.data?.data ?? {})
    }).catch(() => {})
  }, [members, bulkSet])

  if (isLoading) return (
    <div className="flex items-center justify-center gap-2 py-8 text-sm text-(--text-3)">
      <Spinner size={14} /> {t('rightPanel.loadingMembers')}
    </div>
  )
  if (isError) return (
    <Empty>
      <EmptyLabel className="text-(--danger)">{t('rightPanel.errorLabel')}</EmptyLabel>
      <EmptyTitle>{t('rightPanel.loadFail')}</EmptyTitle>
      <EmptyDescription>
        {(error as any)?.response?.data?.error ?? (error as any)?.message ?? t('rightPanel.unknownFail')}
      </EmptyDescription>
      <button onClick={() => refetch()} className="mt-3 text-sm text-(--accent) underline cursor-pointer">
        {t('rightPanel.retry')}
      </button>
    </Empty>
  )
  if (members.length === 0) return (
    <Empty>
      <EmptyIcon><UsersIcon className="size-6" /></EmptyIcon>
      <EmptyLabel>{t('rightPanel.emptyMembersLabel')}</EmptyLabel>
      <EmptyTitle>{t('rightPanel.emptyMembersTitle')}</EmptyTitle>
      <EmptyDescription>{t('rightPanel.emptyMembersDesc')}</EmptyDescription>
    </Empty>
  )

  const getStatus = (id: string): UserStatus => presence[id] ?? 'OFFLINE'

  const sorted = [...members].sort((a, b) => {
    const da = STATUS_ORDER[getStatus(a.userId)]
    const db = STATUS_ORDER[getStatus(b.userId)]
    if (da !== db) return da - db
    return a.user.displayName.localeCompare(b.user.displayName)
  })

  const online  = sorted.filter((m) => getStatus(m.userId) !== 'OFFLINE')
  const offline = sorted.filter((m) => getStatus(m.userId) === 'OFFLINE')

  const grouped = {
    OWNER:  online.filter((m) => m.role === 'OWNER'),
    ADMIN:  online.filter((m) => m.role === 'ADMIN'),
    MEMBER: online.filter((m) => m.role === 'MEMBER'),
  }

  const sections: Array<[string, Member[], React.ReactNode]> = [
    [t('rightPanel.owners'),  grouped.OWNER,  <Crown className="size-3" />],
    [t('rightPanel.admins'),  grouped.ADMIN,  <Shield className="size-3" />],
    [t('rightPanel.online'),  grouped.MEMBER, null],
    [t('rightPanel.offline'), offline,        null],
  ]

  type Row =
    | { kind: 'header'; title: string; count: number; icon: React.ReactNode }
    | { kind: 'member'; m: Member; status: UserStatus }
  const rows: Row[] = []
  for (const [title, arr, icon] of sections) {
    if (arr.length === 0) continue
    rows.push({ kind: 'header', title, count: arr.length, icon })
    for (const m of arr) rows.push({ kind: 'member', m, status: getStatus(m.userId) })
  }

  return <VirtualMembers rows={rows} onPickUser={onPickUser} />
}

function VirtualMembers({
  rows, onPickUser,
}: {
  rows: Array<
    | { kind: 'header'; title: string; count: number; icon: React.ReactNode }
    | { kind: 'member'; m: Member; status: UserStatus }
  >
  onPickUser: (id: string) => void
}) {
  const parentRef = useRef<HTMLDivElement>(null)
  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,

    estimateSize: (i) => (rows[i].kind === 'header' ? 30 : 40),
    overscan: 8,
  })

  return (
    <div ref={parentRef} className="h-full overflow-y-auto px-2 pb-4">
      <div style={{ height: virtualizer.getTotalSize(), position: 'relative', width: '100%' }}>
        {virtualizer.getVirtualItems().map((vi) => {
          const row = rows[vi.index]
          return (
            <div
              key={vi.key}
              data-index={vi.index}
              ref={virtualizer.measureElement}
              style={{ position: 'absolute', top: 0, left: 0, width: '100%', transform: `translateY(${vi.start}px)` }}
            >
              {row.kind === 'header' ? (
                <div className="px-3 py-1.5 flex items-center gap-2">
                  {row.icon}
                  <span className="text-[10px] uppercase tracking-wider text-(--text-3) font-medium">{row.title}</span>
                  <span className="text-[10px] font-mono text-(--text-3) ml-auto">{row.count}</span>
                </div>
              ) : (
                <ProfileHoverCard userId={row.m.userId} side="left" align="start">
                  <button
                    onClick={() => onPickUser(row.m.userId)}
                    className={`w-full flex items-center gap-2.5 px-3 py-1.5 text-left border-l-2 border-transparent hover:border-(--accent) hover:bg-(--raised)/40 transition-colors cursor-pointer ${row.status === 'OFFLINE' ? 'opacity-55' : ''}`}
                  >
                    <div className="relative shrink-0">
                      <Avatar className="size-7">
                        {row.m.user.avatarUrl && <AvatarImage src={row.m.user.avatarUrl} referrerPolicy="no-referrer" />}
                        <AvatarFallback className="text-[10px]">{row.m.user.displayName.slice(0,1).toUpperCase()}</AvatarFallback>
                      </Avatar>
                      <span className="absolute -bottom-0.5 -right-0.5">
                        <StatusDot status={row.status} size={9} bordered borderColor="var(--overlay)" />
                      </span>
                    </div>
                    <span
                      className="text-sm truncate flex-1"
                      style={{ fontFamily: 'var(--font-display)', color: row.m.topColor ?? 'var(--text-2)' }}
                    >
                      {row.m.user.displayName}
                    </span>
                  </button>
                </ProfileHoverCard>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

