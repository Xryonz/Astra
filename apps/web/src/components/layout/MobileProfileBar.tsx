/**
 * MobileProfileBar — barra fixa no bottom (md:hidden), modelo user-panel do
 * Discord:
 *
 *   [avatar] DisplayName ............... [rail]
 *            • Status
 *
 * Esquerda (flex-1): toque → sheet "Mais" (perfil/config/sair).
 * Direita: botão constelação → abre o drawer da rail de servidores.
 *
 * Posição: fixed bottom + safe-area. Conteúdo das pages tem pb-16.
 * z-30 — abaixo de modais/sheets (40+), acima do conteúdo.
 */
import { useTranslation } from 'react-i18next'
import { ConstellationIcon } from '@/components/icons/ConstellationIcon'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import StatusDot, { STATUS_LABEL_KEY } from '@/components/StatusDot'
import { useUIStore } from '@/store/uiStore'
import { useAuthStore } from '@/store/authStore'
import { usePresenceStore } from '@/store/presenceStore'
import { resolveApiUrl } from '@/lib/api'
import { hapticLight } from '@/lib/haptics'
import { cn } from '@/lib/utils'

export default function MobileProfileBar() {
  const { t }        = useTranslation()
  const user         = useAuthStore((s) => s.user)
  const myStatus     = usePresenceStore((s) => s.myStatus)
  const setMoreOpen  = useUIStore((s) => s.setMobileMoreOpen)
  const openSidebar  = useUIStore((s) => s.openMobileSidebar)
  const sidebarOpen  = useUIStore((s) => s.mobileSidebarOpen)

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
            <p className="text-[11px] m-0 truncate flex items-center gap-1.5 text-(--text-3)">
              <StatusDot status={myStatus} size={8} />
              <span className="truncate">{t(STATUS_LABEL_KEY[myStatus])}</span>
            </p>
          </div>
        </button>

        {/* Constelação — abre o drawer da rail de servidores */}
        <button
          type="button"
          onClick={() => { hapticLight(); openSidebar() }}
          aria-label={t('sidebar.openConstellations')}
          className={cn(
            'relative size-12 grid place-items-center rounded-xl shrink-0 cursor-pointer border transition-colors active:scale-95',
            sidebarOpen
              ? 'border-(--accent) bg-(--accent) text-(--void)'
              : 'border-(--border-mid) bg-(--raised)/50 text-(--text-2) hover:text-(--accent) hover:border-(--accent)',
          )}
        >
          <ConstellationIcon className="size-5" />
        </button>
      </div>
    </nav>
  )
}
