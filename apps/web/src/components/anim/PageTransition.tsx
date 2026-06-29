
import { motion } from 'motion/react'

const COARSE = typeof window !== 'undefined' && window.matchMedia('(pointer: coarse)').matches

const VARIANTS = {
  initial: { opacity: 0, x: COARSE ? 10 : 14 },
  animate: { opacity: 1, x: 0 },
  exit:    { opacity: 0, transition: { duration: 0.07 } },
}

const DURATION = COARSE ? 0.15 : 0.2

export function PageTransition({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <motion.div
      initial={VARIANTS.initial}
      animate={VARIANTS.animate}
      exit={VARIANTS.exit}
      transition={{ duration: DURATION, ease: [0.16, 1, 0.3, 1] }}
      className={className}
      style={{ width: '100%', height: '100%', willChange: 'transform, opacity' }}
    >
      {children}
    </motion.div>
  )
}
