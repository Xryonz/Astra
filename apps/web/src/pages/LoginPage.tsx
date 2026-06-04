import { Link, useSearchParams } from 'react-router-dom'
import GoogleButton from '@/components/auth/GoogleButton'
import LoginForm from '@/components/auth/LoginForm'
import UmbraLogo from '@/components/UmbraLogo'
import { Reveal } from '@/components/anim/Reveal'
import { Separator } from '@/components/ui/separator'
import { Kbd } from '@/components/ui/kbd'

export default function LoginPage() {
  const [searchParams] = useSearchParams()
  const oauthError = searchParams.get('error')

  return (
    <div className="relative min-h-screen flex font-(family-name:--font-body) bg-(--void) overflow-hidden">

      {/* Grain — paper feel */}
      <div className="ed-grain" aria-hidden="true" />

      {/* ── LEFT — editorial title panel (desktop only) ─────── */}
      <aside className="relative hidden lg:flex flex-1 border-r border-(--border) bg-(--base) overflow-hidden">
        <div className="ed-vignette" aria-hidden="true" />

        {/* Ambient orbs */}
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          {[
            { w: 520, h: 520, top: '-18%', left: '-22%', delay: '0s',  dur: '14s', strength: 'glow' },
            { w: 380, h: 380, top: '48%',  left: '55%',  delay: '4s',  dur: '18s', strength: 'dim'  },
            { w: 300, h: 300, top: '78%',  left: '-4%',  delay: '8s',  dur: '12s', strength: 'dim'  },
          ].map((o, i) => (
            <div
              key={i}
              className="absolute rounded-full"
              style={{
                width: o.w, height: o.h, top: o.top, left: o.left,
                background: `radial-gradient(circle, var(--accent-${o.strength}) 0%, transparent 70%)`,
                animation: `orbPulse ${o.dur} ease-in-out infinite`,
                animationDelay: o.delay,
              }}
            />
          ))}
        </div>

        {/* Editorial grid mask */}
        <div
          className="absolute inset-0 pointer-events-none opacity-30"
          style={{
            backgroundImage:
              'linear-gradient(var(--border) 1px, transparent 1px), linear-gradient(90deg, var(--border) 1px, transparent 1px)',
            backgroundSize: '56px 56px',
            maskImage:      'radial-gradient(ellipse 80% 80% at 50% 45%, black 25%, transparent 100%)',
            WebkitMaskImage:'radial-gradient(ellipse 80% 80% at 50% 45%, black 25%, transparent 100%)',
          }}
        />

        <div className="relative z-10 flex flex-col items-start justify-center w-full pl-32 pr-12 py-24">
          <Reveal delay={0.2}>
            <h1
              className="m-0 leading-[0.85] text-(--text-1) tracking-wider mb-10 text-display font-(family-name:--font-script)"
              style={{
                textShadow: '0 8px 80px var(--accent-glow), 0 2px 20px rgba(0,0,0,0.5)',
                fontWeight: 400,
              }}
            >
              Umbra
            </h1>
          </Reveal>

          <Reveal delay={0.5}>
            <p className="ed-dropcap max-w-[30ch] text-(--text-2) leading-[1.75] text-[15px] tracking-wide text-left">
              Onde palavras encontram silêncio, e silêncio se torna conversa — uma plataforma editorial para comunidades que respiram.
            </p>
          </Reveal>

          <Reveal delay={0.8}>
            <div className="ed-hr-accent w-20 mt-8" />
          </Reveal>
        </div>

      </aside>

      {/* ── RIGHT — sign-in chamber ────────────────────────── */}
      <main className="relative w-full lg:max-w-130 flex items-center justify-center px-8 lg:px-16 py-12 bg-(--void)">

        {/* Soft accent glow behind form */}
        <div
          className="absolute pointer-events-none"
          style={{
            top: '32%', left: '50%', transform: 'translate(-50%, -50%)',
            width: 560, height: 560, borderRadius: '50%',
            background: 'radial-gradient(circle, var(--accent-dim) 0%, transparent 65%)',
          }}
        />

        {/* Top-right marginalia */}
        <div className="absolute top-10 right-10 flex items-baseline gap-3">
          <span className="ed-marg">Page</span>
          <span className="font-(family-name:--font-display) text-(--text-2) text-sm italic">i</span>
        </div>

        <div className="relative z-10 w-full max-w-100 flex flex-col">

          <Reveal delay={0.05}>
            <div className="flex items-center gap-3 mb-16">
              <UmbraLogo size={32} />
              <span className="font-(family-name:--font-display) text-lg text-(--text-1) tracking-tight">Umbra</span>
            </div>
          </Reveal>

          <Reveal delay={0.15}>
            <div className="mb-10">
              <div className="flex items-baseline gap-3 mb-2">
                <span className="ed-roman text-2xl">I.</span>
                <span className="ed-marg">Sign in</span>
              </div>
              <h2 className="ed-h text-h2 mb-3">Bem-vindo de volta.</h2>
              <p className="text-(--text-2) text-sm leading-relaxed max-w-[34ch]">
                Continue de onde parou — sua biblioteca de conversas aguarda.
              </p>
              <Separator className="mt-6" />
            </div>
          </Reveal>

          {oauthError && (
            <Reveal delay={0.2}>
              <div className="u-error mb-6" role="alert">
                Falha no login com Google. Tente novamente.
              </div>
            </Reveal>
          )}

          <div className="flex flex-col gap-7">
            <Reveal delay={0.30}>
              <GoogleButton />
            </Reveal>

            <Reveal delay={0.40}>
              <div className="flex items-center gap-4">
                <Separator className="flex-1" />
                <span className="ed-marg shrink-0">or by mail</span>
                <Separator className="flex-1" />
              </div>
            </Reveal>

            <Reveal delay={0.50}>
              <LoginForm />
            </Reveal>

            <Reveal delay={0.60}>
              <p className="text-[11px] text-(--text-3) text-center flex items-center justify-center gap-1.5">
                <Kbd>Enter</Kbd> envia · <Kbd>Tab</Kbd> navega
              </p>
            </Reveal>
          </div>

          <Reveal delay={0.75}>
            <div className="mt-12 pt-6 border-t border-(--border)">
              <p className="text-center text-(--text-3) text-sm">
                <span className="ed-marg block mb-2">Novo aqui?</span>
                <Link
                  to="/register"
                  className="font-(family-name:--font-display) text-(--accent) italic text-base hover:text-(--accent-h) transition-colors duration-300"
                >
                  Criar conta —&gt;
                </Link>
              </p>
            </div>
          </Reveal>
        </div>
      </main>
    </div>
  )
}
