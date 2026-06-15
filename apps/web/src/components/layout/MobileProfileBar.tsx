/**
 * MobileProfileBar — barra fixa no bottom (md:hidden) que substituiu a
 * bottom nav de 5 tabs. Modelo Discord (user-panel):
 *
 *   [avatar] DisplayName ............... [✦]
 *            @username
 *
 * Esquerda (flex-1): toque → sheet "Mais" (perfil/config/sair).
 * Direita: botão constelação → sheet de navegação (Constelações/Estrelas/
 * Amigos/Avisos). Badge de não-lidos sinaliza Avisos lá dentro.
 *
 * Posição: fixed bottom + safe-area. Conteúdo das pages tem pb-16.
 * z-30 — abaixo de modais/sheets (40+), acima do conteúdo.
 */
import { useTranslation } from 'react-i18next'
import { ConstellationIcon } from '@/components/icons/ConstellationIcon'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { useUIStore } from '@/store/uiStore'
import { useAuthStore } from '@/store/authStore'
import { useNotificationCount } from '@/hooks/useNotifications'
import { resolveApiUrl } from '@/lib/api'
import { hapticLight } from '@/lib/haptics'
import { cn } from '@/lib/utils'

export default function MobileProfileBar() {
  const { t }           = useTranslation()
  const user            = useAuthStore((s) => s.user)
  const setMoreOpen     = useUIStore((s) => s.setMobileMoreOpen)
  const setNavOpen      = useUIStore((s) => s.setMobileNavOpen)
  const navOpen         = useUIStore((s) => s.mobileNavOpen)
  const { data: count } = useNotificationCount()
  const unread          = count?.count ?? 0

  return (
    <nav
      aria-label={t('nav.mobileBarAria')}
      className={cn(
        // astra-bottom-nav: escondida via CSS quando o teclado abre (.astra-kb-open)
        'astra-bottom-nav md:hidden fixed bottom-0 left-0 right-0 z-30',
        'border-t border-(--border) bg-(--base) pb-safe',
      )}
    >
      <div className="h-16 px-2.5 flex items-center gap-2">
        {/* Perfil — abre sheet Mais (conta) */}
        <button
          type="button"
          onClick={() => { hapticLight(); setMoreOpen(true) }}
          aria-label={t('more.title')}
          className="flex-1 min-w-0 flex items-center gap-2.5 h-12 px-2 rounded-xl cursor-pointer transition-colors active:bg-(--raised)/60"
        >
          <Avatar className="size-9 border border-(--border-mid) shrink-0">
            {user?.avatarUrl
              ? <AvatarImage src={resolveApiUrl(user.avatarUrl)} alt={user.displayName} />
              : <AvatarFallback className="bg-(--raised) text-(--text-2) text-sm font-(family-name:--font-display)">
                  {(user?.displayName ?? 'A').slice(0, 1).toUpperCase()}
                </AvatarFallback>}
          </Avatar>
          <div className="flex-1 min-w-0 text-left leading-tight">
            <p className="text-sm m-0 font-(family-name:--font-display) text-foreground truncate">
              {user?.displayName ?? '—'}
            </p>
            <p className="text-[11px] font-mono text-(--text-3) m-0 truncate">
              @{user?.username ?? '—'}
            </p>
          </div>
        </button>

        {/* Constelação — abre sheet de navegação */}
        <button
          type="button"
          onClick={() => { hapticLight(); setNavOpen(true) }}
          aria-label={t('navSheet.title')}
          className={cn(
            'relative size-12 grid place-items-center rounded-xl shrink-0 cursor-pointer border transition-colors active:scale-95',
            navOpen
              ? 'border-(--accent) bg-(--accent) text-(--void)'
              : 'border-(--border-mid) bg-(--raised)/50 text-(--text-2) hover:text-(--accent) hover:border-(--accent)',
          )}
        >
          <ConstellationIcon className="size-5" />
          {unread > 0 && (
            <span className="absolute -top-1 -right-1 min-w-4 h-4 px-1 rounded-full bg-(--danger) text-[9px] font-semibold text-white flex items-center justify-center">
              {unread > 99 ? '99+' : unread}
            </span>
          )}
        </button>
      </div>
    </nav>
  )
}
