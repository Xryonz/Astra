import { motion } from 'motion/react'
import AstraLogo from '@/components/AstraLogo'

/**
 * Splash mostrado durante bootstrapAuth() — antes do app montar.
 *
 * Sistema "Anéis de Saturno": logo Astra central + 3 anéis elípticos
 * em planos 3D distintos, com iluminação assimétrica (border-top
 * brilhante, border-bottom apagado) que simula luz solar lateral —
 * dá ilusão de profundidade sem rasterizar nada.
 *
 * Performance:
 *   - 100% CSS keyframes — 1 propriedade animada por anel (transform).
 *   - 3 anéis = 3 elementos no compositor. Zero JS no loop.
 *   - perspective no container ativa 3D layer, deixando GPU cuidar.
 *
 * Cada anel:
 *   - rotateX 68-74deg: incline grande pra mostrar a "elipse"
 *   - rotateY ±8-12deg: inclina o plano (não exatamente paralelo)
 *   - rotateZ 0→360 infinito: o spin propriamente dito
 *   - direção alternada (interna→externa) pra evitar movimento monótono
 *
 * Fade out controlado pelo parent via prop `visible`.
 */
export default function SplashScreen({ visible = true }: { visible?: boolean }) {
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
        flexDirection:  'column',
        alignItems:     'center',
        justifyContent: 'center',
        gap:            '1.5rem',
        background:     'var(--void)',
        pointerEvents:  visible ? 'auto' : 'none',
      }}
    >
      <div className="astra-saturn">
        <div className="astra-saturn-core">
          <AstraLogo size={64} animated />
        </div>

        <div className="astra-ring astra-ring-a" />
        <div className="astra-ring astra-ring-b" />
        <div className="astra-ring astra-ring-c" />
      </div>

      <p style={{
        color:         'var(--text-3)',
        fontSize:      '0.75rem',
        fontFamily:    'var(--font-mono)',
        letterSpacing: '0.1em',
        margin:        0,
      }}>
        ASTRA
      </p>

      <style>{`
        .astra-saturn {
          position:        relative;
          width:           200px;
          height:          200px;
          perspective:     900px;
          transform-style: preserve-3d;
        }
        .astra-saturn-core {
          position:  absolute;
          top:       50%;
          left:      50%;
          transform: translate(-50%, -50%);
          z-index:   5;
        }

        /* Anéis: posicionados via top/left 50% + translate dentro do
           keyframe (o translate vive no transform, então tem que estar
           na keyframe junto com rotateX/Y/Z). */
        .astra-ring {
          position:      absolute;
          top:           50%;
          left:          50%;
          border-radius: 50%;
          pointer-events: none;
          /* Iluminação assimétrica: top brilhante = lado iluminado.
             Bottom apagado = sombra. Cria sensação de luz lateral. */
          border-top:    2px   solid var(--accent);
          border-bottom: 1px   solid color-mix(in srgb, var(--accent) 25%, transparent);
          border-left:   1.5px solid color-mix(in srgb, var(--accent) 65%, transparent);
          border-right:  1.5px solid color-mix(in srgb, var(--accent) 65%, transparent);
          box-shadow:    0 0 10px color-mix(in srgb, var(--accent) 18%, transparent);
          animation:     saturnSpin var(--dur, 8s) linear infinite var(--dir, normal);
          will-change:   transform;
        }

        /* 3 anéis em raios crescentes + planos 3D distintos.
           Direções alternam (a normal, b reverse, c normal). */
        .astra-ring-a {
          width:  115px;
          height: 115px;
          margin: -57.5px 0 0 -57.5px;
          --tx:   72deg;
          --ty:   -8deg;
          --dur:  5.5s;
        }
        .astra-ring-b {
          width:  150px;
          height: 150px;
          margin: -75px 0 0 -75px;
          --tx:   68deg;
          --ty:   12deg;
          --dur:  8.5s;
          --dir:  reverse;
          border-top-width: 1.5px;
        }
        .astra-ring-c {
          width:  185px;
          height: 185px;
          margin: -92.5px 0 0 -92.5px;
          --tx:   74deg;
          --ty:   -4deg;
          --dur:  13s;
          border-top-width: 1px;
          opacity: 0.7;
        }

        @keyframes saturnSpin {
          from { transform: rotateX(var(--tx)) rotateY(var(--ty)) rotateZ(0deg);   }
          to   { transform: rotateX(var(--tx)) rotateY(var(--ty)) rotateZ(360deg); }
        }

        @media (prefers-reduced-motion: reduce) {
          .astra-ring { animation: none !important; }
        }
      `}</style>
    </motion.div>
  )
}
