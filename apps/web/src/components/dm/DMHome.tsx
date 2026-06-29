
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Bell } from 'lucide-react'
import DMList from '@/components/dm/DMList'
import { FriendsPanel } from '@/pages/FriendsPage'
import { NotificationBell } from '@/components/notifications/NotificationBell'
import { useNotificationCount } from '@/hooks/useNotifications'
import { cn } from '@/lib/utils'

interface OtherUser {
  id: string
  username: string
  displayName: string
  avatarUrl: string | null
}

interface Props {
  activeDMId: string | null
  onSelectDM: (dm: { conversationId: string; otherUser: OtherUser }) => void
}

export default function DMHome({ activeDMId, onSelectDM }: Props) {
  const { t }           = useTranslation()
  const [tab, setTab]   = useState<'messages' | 'friends'>('messages')
  const { data: count } = useNotificationCount()
  const unread          = count?.count ?? 0

  return (
    <div className="h-full min-h-0 flex flex-col">
      {}
      <header className="h-14 px-3 flex items-center gap-1 border-b border-(--border) shrink-0">
        {([
          { id: 'messages', label: t('dm.title') },
          { id: 'friends',  label: t('nav.friends') },
        ] as const).map((it) => (
          <button
            key={it.id}
            type="button"
            onClick={() => setTab(it.id)}
            className={cn(
              'relative px-3 py-2.5 text-sm border-b-2 -mb-[1px] transition-colors font-(family-name:--font-display)',
              tab === it.id
                ? 'border-(--accent) text-foreground'
                : 'border-transparent text-(--text-3) hover:text-foreground',
            )}
          >
            {it.label}
          </button>
        ))}

        <div className="ml-auto flex items-center">
          {}
          <button
            type="button"
            onClick={() => window.dispatchEvent(new Event('astra:open-notifications'))}
            aria-label={t('bell.aria')}
            className="md:hidden relative size-9 grid place-items-center text-(--text-3) hover:text-(--accent) transition-colors cursor-pointer"
          >
            <Bell className="size-[18px]" />
            {unread > 0 && (
              <span className="absolute -top-0.5 -right-0.5 min-w-4 h-4 px-1 rounded-full bg-(--danger) text-[9px] font-semibold text-white flex items-center justify-center">
                {unread > 99 ? '99+' : unread}
              </span>
            )}
          </button>
          {}
          <NotificationBell />
        </div>
      </header>

      {}
      {tab === 'messages' ? (
        <DMList activeDMId={activeDMId} onSelectDM={onSelectDM} />
      ) : (
        <div className="flex-1 min-h-0 overflow-y-auto astra-scrollable px-4 py-5">
          <FriendsPanel />
        </div>
      )}
    </div>
  )
}
