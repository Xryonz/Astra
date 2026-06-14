import { useEffect } from 'react'
import { motion } from 'motion/react'
import AstraLogo from '@/components/AstraLogo'
import { isNative } from '@/lib/native'

/**
 * Splash mostrado durante bootstrapAuth() — antes do app montar.
 *
 * Intro minimalista estilo Discord: só o logo Astra centralizado no void,
 * com entrada sutil (fade + scale) e respiro lento. Sem texto, sem dots
 * orbitando — handoff limpo a partir do mesmo logo do splash nativo.
 *
 * No app nativo: esconde o splash do Capacitor ao montar (handoff).
 * Fade out controlado pelo parent via prop `visible`.
 */
export default function SplashScreen({ visible = true }: { visible?: boolean }) {
  // Esconde o splash nativo assim que o web está pronto pra assumir
  // (launchAutoHide: false no capacitor.config — fallback de 4s em native.ts).
  useEffect(() => {
    if (!isNative) return
    void import('@capacitor/splash-screen')
      .then(({ SplashScreen: Native }) => Native.hide({ fadeOutDuration: 150 }))
      .catch(() => {})
  }, [])

  return (
    <motion.div
      initial={{ opacity: 1 }}
      animate={{ opacity: visible ? 1 : 0 }}
      transition={{ duration: 0.4 }}
      style={{
        position:       'fixed',
        inset:          0,
        zIndex:         9999,
        display:        'flex',
        alignItems:     'center',
        justifyContent: 'center',
        background:     'var(--void)',
        pointerEvents:  visible ? 'auto' : 'none',
      }}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.92 }}
        animate={{ opacity: 1, scale: [0.92, 1, 1.03, 1] }}
        transition={{
          duration: 2.6,
          ease:     'easeInOut',
          times:    [0, 0.25, 0.6, 1],
          repeat:   Infinity,
          repeatType: 'loop',
        }}
      >
        <AstraLogo size={84} />
      </motion.div>
    </motion.div>
  )
}
