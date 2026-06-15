/**
 * MobileNotificationsSheet — centro de notificações como bottom sheet,
 * aberto pela tab "Avisos" do MobileBottomNav (evento astra:open-notifications).
 *
 * Montada 1x no AppPage, em TODAS as rotas — antes o listener vivia no
 * NotificationBell, que só existe no header de canal, então a tab era
 * um botão morto em /app/dm e /app/friends.
 *
 * Também é dona do badge do ícone do app (nativo/PWA): aqui ele espelha o
 * unread em qualquer tela, não só dentro de um canal.
 */
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { motion, AnimatePresence } from 'motion/react'
import { NotificationCenter } from './NotificationBell'
import { useNotificationCount } from '@/hooks/useNotifications'
import { setAppBadge } from '@/lib/badge'

export default function MobileNotificationsSheet() {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)

  useEffect(() => {
    const onOpen = () => setOpen(true)
    window.addEventListener('astra:open-notifications', onOpen)
    return () => window.removeEventListener('astra:open-notifications', onOpen)
  }, [])

  const { data: count } = useNotificationCount()
  const unread = count?.count ?? 0
  useEffect(() => { setAppBadge(unread) }, [unread])

  // Back físico do Android dispara Escape (ver native.ts) — sheet fecha
  // como qualquer dialog em vez de navegar pra trás.
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{    opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={() => setOpen(false)}
            className="md:hidden fixed inset-0 z-40 bg-black/60 backdrop-blur-sm"
          />

          <motion.aside
            role="dialog"
            data-state="open"
            aria-label={t('notif.title')}
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{    y: '100%' }}
            transition={{ type: 'spring', stiffness: 360, damping: 30 }}
            className="md:hidden fixed bottom-0 left-0 right-0 z-50 h-[72dvh] bg-(--base) border-t border-(--border) rounded-t-2xl pb-safe overflow-hidden flex flex-col"
          >
            {/* Drag handle */}
            <div className="pt-2 pb-1.5 flex justify-center shrink-0">
              <span className="block w-10 h-1 rounded-full bg-(--border-mid)" />
            </div>

            <NotificationCenter onClose={() => setOpen(false)} className="flex-1 min-h-0 w-full" />
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  )
}
