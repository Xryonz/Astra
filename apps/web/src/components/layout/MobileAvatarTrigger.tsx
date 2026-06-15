/**
 * MobileAvatarTrigger — substituto do burger Menu em headers mobile.
 * Tap = abre a sidebar (drawer com servers + canais). Mostra avatar do user.
 * Visível só em mobile (md:hidden).
 */
import { useTranslation } from 'react-i18next'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { useAuthStore } from '@/store/authStore'
import { useUIStore } from '@/store/uiStore'
import { resolveApiUrl } from '@/lib/api'
import { cn } from '@/lib/utils'

interface Props {
  className?: string
}

export default function MobileAvatarTrigger({ className }: Props) {
  const { t }      = useTranslation()
  const user       = useAuthStore((s) => s.user)
  const openSidebar = useUIStore((s) => s.openMobileSidebar)
  const initial    = (user?.displayName ?? 'A').slice(0, 1).toUpperCase()
  return (
    <button
      type="button"
      onClick={openSidebar}
      aria-label={t('sidebar.openConstellations')}
      className={cn(
        'md:hidden shrink-0 size-11 grid place-items-center cursor-pointer rounded-full',
        'transition-transform active:scale-95',
        className,
      )}
    >
      <Avatar className="size-9 border border-(--border-mid)">
        {user?.avatarUrl
          ? <AvatarImage src={resolveApiUrl(user.avatarUrl)} alt={user.displayName} />
          : <AvatarFallback className="bg-(--raised) text-(--text-2) text-sm font-(family-name:--font-display)">
              {initial}
            </AvatarFallback>}
      </Avatar>
    </button>
  )
}
