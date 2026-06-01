/**
 * PageTransition — fade + lift sutil entre rotas.
 *
 * Wrap o elemento da rota; usa AnimatePresence no parent (Routes) pra
 * permitir exit animations. Curva spring orgânica.
 *
 * Uso:
 *   <AnimatePresence mode="wait">
 *     <Routes location={location} key={location.pathname}>
 *       <Route ... element={<PageTransition><MyPage /></PageTransition>} />
 *     </Routes>
 *   </AnimatePresence>
 */
import { motion } from 'motion/react'

export function PageTransition({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{    opacity: 0, y: -4 }}
      transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
      className={className}
      style={{ width: '100%', height: '100%' }}
    >
      {children}
    </motion.div>
  )
}
