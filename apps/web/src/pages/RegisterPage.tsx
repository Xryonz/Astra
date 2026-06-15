import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import GoogleButton from '@/components/auth/GoogleButton'
import RegisterForm from '@/components/auth/RegisterForm'
import AstraLogo from '@/components/AstraLogo'
import { Reveal } from '@/components/anim/Reveal'
import { Separator } from '@/components/ui/separator'
import { Kbd } from '@/components/ui/kbd'

export default function RegisterPage() {
  const { t } = useTranslation()
  return (
    <div className="relative min-h-screen flex font-(family-name:--font-body) overflow-hidden">

      <div className="ed-grain" aria-hidden="true" />

      {/* ── LEFT panel ──────────────────────────────────────── */}
      <aside className="relative hidden lg:flex flex-1 border-r border-(--border) bg-(--base) overflow-hidden">
        <div className="ed-vignette" aria-hidden="true" />

        {/* Ambient orbs */}
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          {[
            { w: 500, h: 500, top: '-15%', left: '-10%', delay: '0s',  dur: '16s', strength: 'glow' },
            { w: 320, h: 320, top: '55%',  left: '55%',  delay: '5s',  dur: '20s', strength: 'dim'  },
            { w: 250, h: 250, top: '80%',  left: '-5%',  delay: '10s', dur: '13s', strength: 'dim'  },
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

        <div
          className="absolute inset-0 pointer-events-none opacity-30"
          style={{
            backgroundImage:
              'linear-gradient(var(--border) 1px, transparent 1px), linear-gradient(90deg, var(--border) 1px, transparent 1px)',
            backgroundSize: '48px 48px',
            maskImage:      'radial-gradient(ellipse 80% 80% at 50% 50%, black 20%, transparent 100%)',
            WebkitMaskImage:'radial-gradient(ellipse 80% 80% at 50% 50%, black 20%, transparent 100%)',
          }}
        />

        {/* Vertical label */}
        <div className="absolute top-12 left-12 z-10">
          <Reveal delay={0.1}>
            <span className="ed-marg-vertical">Cap. II · Criar</span>
          </Reveal>
        </div>

        <div className="relative z-10 flex flex-col justify-center w-full pl-32 pr-12 py-24 max-w-2xl">
          <Reveal delay={0.15}>
            <div className="mb-8">
              <AstraLogo size={80} />
            </div>
          </Reveal>

          <Reveal delay={0.25}>
            <h1 className="ed-h text-5xl leading-[1.05] m-0 mb-2">{t('auth.heroLine1')}</h1>
          </Reveal>
          <Reveal delay={0.35}>
            <h1 className="ed-h text-5xl leading-[1.05] m-0 mb-2">{t('auth.heroLine2')}</h1>
          </Reveal>
          <Reveal delay={0.45}>
            <h1 className="ed-h italic text-5xl leading-[1.05] m-0 text-(--accent) mb-6">{t('auth.heroLine3')}</h1>
          </Reveal>

          <Reveal delay={0.6}>
            <p className="text-(--text-2) text-base leading-[1.7] max-w-[40ch]">
              {t('auth.heroBody')}
            </p>
          </Reveal>

          <Reveal delay={0.75}>
            <div className="ed-hr-accent w-20 mt-8" />
          </Reveal>

          <Reveal delay={0.9}>
            <div className="mt-10 border border-(--border-mid) bg-(--raised)/40 px-6 py-5 max-w-md">
              <p className="ed-quote text-lg m-0 leading-tight">
                {t('auth.heroQuote')}
              </p>
              <p className="text-(--text-3) text-xs m-0 mt-3 font-mono uppercase tracking-wider">
                {t('auth.heroQuoteAuthor')}
              </p>
            </div>
          </Reveal>
        </div>
      </aside>

      {/* ── RIGHT — form chamber ────────────────────────────── */}
      <main className="relative w-full lg:max-w-130 flex items-center justify-center px-8 lg:px-16 py-12 overflow-y-auto">

        <div
          className="absolute pointer-events-none"
          style={{
            top: '30%', left: '50%', transform: 'translate(-50%, -50%)',
            width: 400, height: 400, borderRadius: '50%',
            background: 'radial-gradient(circle, var(--accent-dim) 0%, transparent 65%)',
          }}
        />

        <div className="absolute top-10 right-10 flex items-baseline gap-3">
          <span className="ed-marg">Page</span>
          <span className="font-(family-name:--font-display) text-(--text-2) text-sm italic">ii</span>
        </div>

        <div className="relative z-10 w-full max-w-100 flex flex-col py-4">

          <Reveal delay={0.05}>
            <div className="flex items-center gap-3 mb-12">
              <AstraLogo size={32} />
              <span className="font-(family-name:--font-display) text-lg text-(--text-1) tracking-tight">Astra</span>
            </div>
          </Reveal>

          <Reveal delay={0.15}>
            <div className="mb-8">
              <div className="flex items-baseline gap-3 mb-2">
                <span className="ed-roman text-2xl">II.</span>
                <span className="ed-marg">Sign up</span>
              </div>
              <h2 className="ed-h text-3xl mb-3">{t('auth.registerTitle')}</h2>
              <p className="text-(--text-2) text-sm leading-relaxed max-w-[34ch]">
                {t('auth.registerSub')}
              </p>
              <Separator className="mt-6" />
            </div>
          </Reveal>

          <div className="flex flex-col gap-6">
            <Reveal delay={0.25}>
              <GoogleButton />
            </Reveal>

            <Reveal delay={0.35}>
              <div className="flex items-center gap-4">
                <Separator className="flex-1" />
                <span className="ed-marg shrink-0">or by mail</span>
                <Separator className="flex-1" />
              </div>
            </Reveal>

            <Reveal delay={0.45}>
              <RegisterForm />
            </Reveal>

            <Reveal delay={0.55}>
              <p className="text-[11px] text-(--text-3) text-center flex items-center justify-center gap-1.5">
                <Kbd>Enter</Kbd> {t('auth.kbdSubmit')} · <Kbd>Tab</Kbd> {t('auth.kbdNavigate')}
              </p>
            </Reveal>
          </div>

          <Reveal delay={0.7}>
            <div className="mt-10 pt-6 border-t border-(--border)">
              <p className="text-center text-(--text-3) text-sm">
                <span className="ed-marg block mb-2">{t('auth.haveAccount')}</span>
                <Link
                  to="/login"
                  className="font-(family-name:--font-display) text-(--accent) italic text-base hover:text-(--accent-h) transition-colors duration-300"
                >
                  {t('auth.goSignIn')}
                </Link>
              </p>
            </div>
          </Reveal>
        </div>
      </main>
    </div>
  )
}
