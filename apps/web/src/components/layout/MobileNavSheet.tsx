/**
 * MobileNavSheet — sheet de navegação acionada pelo botão constelação da
 * barra de perfil (substituiu a bottom nav de 5 tabs). Contém os destinos:
 * Constelações (drawer) · Estrelas (DMs) · Amigos · Avisos.
 *
 * Mesmo idioma visual do MobileMoreSheet (tiles âmbar + card arredondado +
 * chevrons), com realce de ativo e badge de não-lidos.
 */
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'motion/react'
import { Sparkles, Users, Bell, X, ChevronRight } from 'lucide-react'
import { ConstellationIcon } from '@/components/icons/ConstellationIcon'
import { useViewTransitionNavigate } from '@/hooks/useViewTransitionNavigate'
import { useUIStore } from '@/store/uiStore'
import { useNotificationCount } from '@/hooks/useNotifications'
import { hapticLight } from '@/lib/haptics'
import { cn } from '@/lib/utils'

interface NavRow {
  id:      string
  icon:    React.ReactNode
  label:   string
  hint?:   string
  onClick: () => void
  active:  boolean
  badge?:  number
}

export default function MobileNavSheet() {
  const open            = useUIStore((s) => s.mobileNavOpen)
  const setOpen         = useUIStore((s) => s.setMobileNavOpen)
  const sidebarOpen     = useUIStore((s) => s.mobileSidebarOpen)
  const openSidebar     = useUIStore((s) => s.openMobileSidebar)
  const closeSidebar    = useUIStore((s) => s.closeMobileSidebar)
  const navigate        = useViewTransitionNavigate()
  const location        = useLocation()
  const { t }           = useTranslation()
  const { data: count } = useNotificationCount()
  const unread          = count?.count ?? 0

  const close = () => setOpen(false)
  const path  = location.pathname

  // Back físico do Android dispara Escape (ver native.ts) — fecha o sheet.
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, setOpen])

  const rows: NavRow[] = [
    {
      id: 'constellations',
      icon: <ConstellationIcon className="size-5" />,
      label: t('nav.constellations'),
      hint:  t('navSheet.constellationsHint'),
      onClick: () => { openSidebar(); close() },
      active: sidebarOpen,
    },
    {
      id: 'stars',
      icon: <Sparkles className="size-5" />,
      label: t('nav.stars'),
      hint:  t('navSheet.starsHint'),
      onClick: () => { closeSidebar(); navigate('/app/dm'); close() },
      active: !sidebarOpen && path.startsWith('/app/dm'),
    },
    {
      id: 'friends',
      icon: <Users className="size-5" />,
      label: t('nav.friends'),
      hint:  t('navSheet.friendsHint'),
      onClick: () => { closeSidebar(); navigate('/app/friends'); close() },
      active: !sidebarOpen && path.startsWith('/app/friends'),
    },
    {
      id: 'alerts',
      icon: <Bell className="size-5" />,
      label: t('nav.alerts'),
      hint:  t('navSheet.alertsHint'),
      onClick: () => { closeSidebar(); close(); window.dispatchEvent(new Event('astra:open-notifications')) },
      active: false,
      badge: unread,
    },
  ]

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{    opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={close}
            className="md:hidden fixed inset-0 z-40 bg-black/60 backdrop-blur-sm"
          />

          <motion.aside
            role="dialog"
            data-state="open"
            aria-label={t('navSheet.title')}
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{    y: '100%' }}
            transition={{ type: 'spring', stiffness: 360, damping: 30 }}
            className="md:hidden fixed bottom-0 left-0 right-0 z-50 bg-(--base) border-t border-(--border) rounded-t-2xl pb-safe overflow-hidden"
          >
            <div className="pt-2 pb-1.5 flex justify-center">
              <span className="block w-10 h-1 rounded-full bg-(--border-mid)" />
            </div>

            <header className="px-4 pb-3 flex items-center gap-2">
              <span className="text-(--accent)"><ConstellationIcon className="size-4" /></span>
              <p className="flex-1 text-sm m-0 font-(family-name:--font-display) text-foreground">
                {t('navSheet.title')}
              </p>
              <button
                type="button"
                onClick={close}
                aria-label={t('more.close')}
                className="size-9 grid place-items-center text-(--text-3) hover:text-(--text-1) cursor-pointer"
              >
                <X className="size-4" />
              </button>
            </header>

            <div className="px-4 pt-1 pb-2">
              <ul className="rounded-2xl border border-(--border) bg-(--raised)/30 overflow-hidden divide-y divide-(--border)">
                {rows.map((r) => (
                  <li key={r.id}>
                    <button
                      type="button"
                      onClick={() => { hapticLight(); r.onClick() }}
                      className="w-full flex items-center gap-3 px-3.5 py-3.5 cursor-pointer active:bg-(--raised)/60 transition-colors"
                    >
                      <span className={cn(
                        'relative size-9 grid place-items-center rounded-xl shrink-0',
                        r.active ? 'bg-(--accent) text-(--void)' : 'bg-(--accent-dim) text-(--accent)',
                      )}>
                        {r.icon}
                        {(r.badge ?? 0) > 0 && (
                          <span className="absolute -top-1 -right-1 min-w-4 h-4 px-1 rounded-full bg-(--danger) text-[9px] font-semibold text-white flex items-center justify-center">
                            {r.badge! > 99 ? '99+' : r.badge}
                          </span>
                        )}
                      </span>
                      <div className="flex-1 min-w-0 text-left">
                        <p className={cn('text-sm m-0', r.active ? 'text-(--accent)' : 'text-foreground')}>
                          {r.label}
                        </p>
                        {r.hint && <p className="text-[11px] text-(--text-3) m-0 truncate">{r.hint}</p>}
                      </div>
                      <ChevronRight className="size-4 text-(--text-3) shrink-0" />
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  )
}
