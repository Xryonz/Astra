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
      initial={{ opacity: 0, x: 18, filter: 'blur(2px)' }}
      animate={{ opacity: 1, x: 0,  filter: 'blur(0px)' }}
      exit={{    opacity: 0, x: -10, filter: 'blur(2px)' }}
      transition={{ duration: 0.34, ease: [0.16, 1, 0.3, 1] }}
      className={className}
      style={{ width: '100%', height: '100%', willChange: 'transform, opacity, filter' }}
    >
      {children}
    </motion.div>
  )
}
