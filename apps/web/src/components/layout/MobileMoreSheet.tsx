/**
 * MobileMoreSheet — sheet acionada pela tab "Mais" da MobileBottomNav.
 * Atalhos rápidos: perfil, configurações, sair. Estilo editorial-dark.
 */
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'motion/react'
import { User, Settings, LogOut, X, Sparkles, ChevronRight } from 'lucide-react'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { cn } from '@/lib/utils'
import { useUIStore } from '@/store/uiStore'
import { useAuthStore } from '@/store/authStore'
import { useAuth } from '@/hooks/useAuth'
import { resolveApiUrl } from '@/lib/api'

interface Action {
  icon:    React.ReactNode
  label:   string
  hint?:   string
  onClick: () => void
  danger?: boolean
}

export default function MobileMoreSheet() {
  const open      = useUIStore((s) => s.mobileMoreOpen)
  const setOpen   = useUIStore((s) => s.setMobileMoreOpen)
  const user      = useAuthStore((s) => s.user)
  const navigate  = useNavigate()
  const { logout } = useAuth()
  const { t }     = useTranslation()

  const close = () => setOpen(false)

  // Back físico do Android dispara Escape (ver native.ts) — fecha o sheet
  // em vez de navegar pra trás com ele aberto.
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, setOpen])

  const actions: Action[] = [
    {
      icon: <User className="size-4" />,
      label: t('more.profile'),
      hint:  t('more.profileHint'),
      onClick: () => { navigate('/app/profile'); close() },
    },
    {
      icon: <Settings className="size-4" />,
      label: t('more.settings'),
      hint:  t('more.settingsHint'),
      onClick: () => { navigate('/app/settings'); close() },
    },
    {
      icon: <Sparkles className="size-4" />,
      label: t('more.wishing'),
      hint:  t('more.wishingHint'),
      onClick: () => { navigate('/app/settings#wishing'); close() },
    },
    {
      icon: <LogOut className="size-4" />,
      label: t('more.logout'),
      onClick: () => { logout(); close() },
      danger: true,
    },
  ]

  return (
    <AnimatePresence>
      {open && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{    opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={close}
            className="md:hidden fixed inset-0 z-40 bg-black/60 backdrop-blur-sm"
          />

          {/* Sheet */}
          <motion.aside
            role="dialog"
            data-state="open"
            aria-label={t('more.title')}
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{    y: '100%' }}
            transition={{ type: 'spring', stiffness: 360, damping: 30 }}
            className="md:hidden fixed bottom-0 left-0 right-0 z-50 bg-(--base) border-t border-(--border) rounded-t-2xl pb-safe overflow-hidden"
          >
            {/* Drag handle */}
            <div className="pt-2 pb-1.5 flex justify-center">
              <span className="block w-10 h-1 rounded-full bg-(--border-mid)" />
            </div>

            <header className="px-4 pb-3 flex items-center gap-3">
              <Avatar className="size-10 border border-(--border-mid)">
                {user?.avatarUrl
                  ? <AvatarImage src={resolveApiUrl(user.avatarUrl)} alt={user.displayName} />
                  : <AvatarFallback className="bg-(--raised) text-(--text-2) font-(family-name:--font-display)">
                      {(user?.displayName ?? 'A').slice(0, 1).toUpperCase()}
                    </AvatarFallback>}
              </Avatar>
              <div className="flex-1 min-w-0 leading-tight">
                <p className="text-sm m-0 font-(family-name:--font-display) text-foreground truncate">
                  {user?.displayName ?? '—'}
                </p>
                <p className="text-[11px] font-mono text-(--text-3) m-0 truncate">
                  @{user?.username ?? '—'}
                </p>
              </div>
              <button
                type="button"
                onClick={close}
                aria-label={t('more.close')}
                className="size-10 grid place-items-center text-(--text-3) hover:text-(--text-1) cursor-pointer"
              >
                <X className="size-4" />
              </button>
            </header>

            <div className="px-4 pt-1 pb-2">
              <ul className="rounded-2xl border border-(--border) bg-(--raised)/30 overflow-hidden divide-y divide-(--border)">
                {actions.map((a, i) => (
                  <li key={i}>
                    <button
                      type="button"
                      onClick={a.onClick}
                      className="w-full flex items-center gap-3 px-3.5 py-3.5 cursor-pointer active:bg-(--raised)/60 transition-colors"
                    >
                      <span className={cn(
                        'size-9 grid place-items-center rounded-xl shrink-0',
                        a.danger ? 'bg-(--danger)/10 text-(--danger)' : 'bg-(--accent-dim) text-(--accent)',
                      )}>
                        {a.icon}
                      </span>
                      <div className="flex-1 min-w-0 text-left">
                        <p className={cn('text-sm m-0', a.danger ? 'text-(--danger)' : 'text-foreground')}>
                          {a.label}
                        </p>
                        {a.hint && <p className="text-[11px] text-(--text-3) m-0 truncate">{a.hint}</p>}
                      </div>
                      {!a.danger && <ChevronRight className="size-4 text-(--text-3) shrink-0" />}
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
