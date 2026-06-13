/**
 * Barra discreta "Sem conexão" — aparece quando a rede cai e some sozinha
 * quando volta. Slide suave do topo. Só renderiza algo quando offline.
 */
import { WifiOff } from 'lucide-react'
import { AnimatePresence, motion } from 'motion/react'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'

export function OfflineBanner() {
  const online = useOnlineStatus()

  return (
    <AnimatePresence>
      {!online && (
        <motion.div
          initial={{ y: -40, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: -40, opacity: 0 }}
          transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
          role="status"
          className="fixed top-0 inset-x-0 z-9999 flex items-center justify-center gap-2 py-1.5 px-4 bg-(--danger) text-white text-xs font-medium pt-safe pointer-events-none"
        >
          <WifiOff className="size-3.5 shrink-0" />
          Sem conexão — tentando reconectar…
        </motion.div>
      )}
    </AnimatePresence>
  )
}
