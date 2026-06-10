import { useEffect } from 'react'
import { motion } from 'motion/react'
import { isNative } from '@/lib/native'

/**
 * Splash de entrada — handoff seamless do splash nativo (Capacitor).
 *
 * No app: o Android mostra o logo estático (splash nativo) → este componente
 * monta com o MESMO logo na mesma região e esconde o nativo → o logo "ganha
 * vida" (glow respira, estrelas acendem) → crossfade pro app. Percepção:
 * uma animação contínua desde o toque no ícone, estilo Discord.
 *
 * No web: mesma animação, sem o handoff.
 * Transform/opacity only — compositor-friendly em qualquer GPU.
 */

// Estrelas decorativas ao redor do logo (posições fixas, determinísticas)
const SPARKS = [
  { x: -110, y: -70,  size: 3, delay: 0.15 },
  { x:  95,  y: -95,  size: 2, delay: 0.30 },
  { x:  130, y: -10,  size: 2, delay: 0.45 },
  { x: -140, y:  30,  size: 2, delay: 0.55 },
  { x:  70,  y:  90,  size: 3, delay: 0.40 },
  { x: -60,  y:  115, size: 2, delay: 0.65 },
  { x:  10,  y: -135, size: 2, delay: 0.25 },
  { x:  150, y:  70,  size: 2, delay: 0.70 },
]

export default function SplashScreen({ visible = true }: { visible?: boolean }) {
  // Esconde o splash nativo assim que o web está pronto pra assumir.
  // fadeOut curto: o frame seguinte já é visualmente idêntico.
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
      transition={{ duration: 0.45, ease: [0.4, 0, 0.2, 1] }}
      style={{
        position:       'fixed',
        inset:          0,
        zIndex:         9999,
        display:        'flex',
        flexDirection:  'column',
        alignItems:     'center',
        justifyContent: 'center',
        gap:            '1.75rem',
        background:     'var(--void)',
        pointerEvents:  visible ? 'auto' : 'none',
      }}
    >
      <div style={{ position: 'relative', width: 140, height: 140, display: 'grid', placeItems: 'center' }}>
        {/* Glow respirando atrás do logo */}
        <motion.div
          aria-hidden
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: [0, 0.55, 0.3, 0.55], scale: [0.8, 1.05, 0.95, 1.05] }}
          transition={{ duration: 2.8, times: [0, 0.35, 0.7, 1], repeat: Infinity, repeatType: 'reverse', ease: 'easeInOut' }}
          style={{
            position:     'absolute',
            inset:        -30,
            borderRadius: '50%',
            background:   'radial-gradient(circle, var(--accent-glow) 0%, transparent 65%)',
          }}
        />

        {/* Logo — mesma arte do splash nativo (handoff invisível) */}
        <motion.img
          src="/favicon.svg"
          alt=""
          width={112}
          height={112}
          draggable={false}
          initial={{ scale: 1, opacity: 1 }}
          animate={{ scale: [1, 1.04, 1] }}
          transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}
          style={{ position: 'relative', userSelect: 'none' }}
        />

        {/* Estrelas acendendo ao redor */}
        {SPARKS.map((s, i) => (
          <motion.span
            key={i}
            aria-hidden
            initial={{ opacity: 0, scale: 0 }}
            animate={{ opacity: [0, 1, 0.5, 1], scale: 1 }}
            transition={{ delay: s.delay, duration: 1.8, repeat: Infinity, repeatType: 'reverse', ease: 'easeInOut' }}
            style={{
              position:     'absolute',
              left:         '50%',
              top:          '50%',
              width:        s.size,
              height:       s.size,
              borderRadius: '50%',
              background:   'var(--accent)',
              boxShadow:    '0 0 6px var(--accent-glow)',
              transform:    `translate(${s.x}px, ${s.y}px)`,
            }}
          />
        ))}
      </div>

      {/* Wordmark entra depois do logo estabilizar */}
      <motion.p
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.5, duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        style={{
          color:         'var(--text-3)',
          fontSize:      '0.8rem',
          fontFamily:    'var(--font-mono)',
          letterSpacing: '0.35em',
          margin:        0,
        }}
      >
        ASTRA
      </motion.p>
    </motion.div>
  )
}
