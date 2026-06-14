import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useLocation } from 'react-router-dom'
import {
  ArrowLeft, User, Image as ImageIcon, Palette, Bell, Shield, Users as UsersIcon, Database, Brush, Sparkles, Languages, X, ChevronRight,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import AccountSection      from '@/components/settings/sections/AccountSection'
import ProfileSection      from '@/components/settings/sections/ProfileSection'
import CustomizationSection from '@/components/settings/sections/CustomizationSection'
import AppearanceSection   from '@/components/settings/sections/AppearanceSection'
import NameColorsSection   from '@/components/settings/sections/NameColorsSection'
import NotificationsSection from '@/components/settings/sections/NotificationsSection'
import SessionsSection     from '@/components/settings/sections/SessionsSection'
import DataSection         from '@/components/settings/sections/DataSection'
import WishingStarSection  from '@/components/settings/sections/WishingStarSection'
import LanguageSection      from '@/components/settings/sections/LanguageSection'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Reveal } from '@/components/anim/Reveal'
import { motion, AnimatePresence } from 'motion/react'
import {
  Breadcrumb, BreadcrumbList, BreadcrumbItem, BreadcrumbLink, BreadcrumbPage, BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'

type SectionId =
  | 'account'
  | 'profile'
  | 'customization'
  | 'appearance'
  | 'name-colors'
  | 'notifications'
  | 'language'
  | 'sessions'
  | 'data'
  | 'wishing'

// label = chave i18n (settings.nav.*); resolvida com t() no render.
interface NavItem { id: SectionId; label: string; icon: React.ReactNode; group: 'pessoal' | 'app' | 'privacidade' | 'comunidade' }

const NAV: NavItem[] = [
  { id: 'account',       label: 'settings.nav.account',       icon: <User className="size-3.5" />,      group: 'pessoal' },
  { id: 'profile',       label: 'settings.nav.profile',       icon: <ImageIcon className="size-3.5" />, group: 'pessoal' },
  { id: 'customization', label: 'settings.nav.customization', icon: <Brush className="size-3.5" />,     group: 'pessoal' },
  { id: 'appearance',    label: 'settings.nav.appearance',    icon: <Palette className="size-3.5" />,   group: 'app' },
  { id: 'name-colors',   label: 'settings.nav.name-colors',   icon: <UsersIcon className="size-3.5" />, group: 'app' },
  { id: 'notifications', label: 'settings.nav.notifications', icon: <Bell className="size-3.5" />,      group: 'app' },
  { id: 'language',      label: 'settings.nav.language',      icon: <Languages className="size-3.5" />, group: 'app' },
  { id: 'wishing',       label: 'settings.nav.wishing',       icon: <Sparkles className="size-3.5" />,  group: 'comunidade' },
  { id: 'sessions',      label: 'settings.nav.sessions',      icon: <Shield className="size-3.5" />,    group: 'privacidade' },
  { id: 'data',          label: 'settings.nav.data',          icon: <Database className="size-3.5" />,  group: 'privacidade' },
]

/**
 * SettingsPage com sidebar editorial.
 *
 * Layout:
 *  - md+: sidebar fixa esquerda com nav agrupada
 *  - mobile (<md): sidebar escondida, nav vira <Select> no topo do conteúdo
 *
 * URL hash (#profile) sincronizado pra deep-link / back-forward.
 */
export default function SettingsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useTranslation()

  const hashSection = location.hash.slice(1)
  const validHash = NAV.some((n) => n.id === hashSection) ? (hashSection as SectionId) : null
  const [section, setSection] = useState<SectionId>(validHash ?? 'account')
  // Mobile: drill-down. null = home (cards agrupados); set = seção aberta.
  // Deep-link via hash abre direto a seção. Desktop ignora (usa só `section`).
  const [mobileOpen, setMobileOpen] = useState<SectionId | null>(validHash)
  const pickSection = (id: SectionId) => { setSection(id); setMobileOpen(id) }

  useEffect(() => {
    if (location.hash.slice(1) !== section) {
      window.history.replaceState(null, '', `${location.pathname}#${section}`)
    }
  }, [section, location.pathname])

  const currentLabelKey = NAV.find((n) => n.id === section)?.label
  const currentLabel = currentLabelKey ? t(currentLabelKey) : t('settings.title')

  return (
    <main className="flex-1 flex h-full font-(family-name:--font-body) overflow-hidden">
      {/* ─── Sidebar (md+) ─── */}
      <aside className="hidden md:flex w-60 lg:w-64 shrink-0 border-r border-(--border) bg-(--raised)/30 flex-col overflow-hidden">
        <header className="h-14 px-4 flex items-center gap-2 border-b border-(--border) shrink-0">
          <button
            onClick={() => navigate(-1)}
            className="size-8 flex items-center justify-center text-(--text-3) hover:text-(--accent) transition-colors cursor-pointer rounded-lg"
            aria-label="Voltar"
            title="Voltar"
          >
            <ArrowLeft className="size-4" />
          </button>
          <h1
            className="text-base m-0 font-normal tracking-tight text-foreground"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            {t('settings.title')}
          </h1>
        </header>

        <ScrollArea className="flex-1">
          <nav className="py-4">
            {(['pessoal', 'app', 'comunidade', 'privacidade'] as const).map((grp, gi) => (
              <div key={grp} className="mb-5">
                <Reveal delay={gi * 0.06}>
                  <p className="px-5 mb-2 text-[10px] uppercase tracking-wider text-(--text-3) font-mono m-0">
                    — {t(`settings.groups.${grp}`)}
                  </p>
                </Reveal>
                <ul className="flex flex-col gap-0.5">
                  {NAV.filter((n) => n.group === grp).map((n, i) => {
                    const active = section === n.id
                    return (
                      <li
                        key={n.id}
                        style={{ animation: `fadeLeft 0.25s var(--ease-spring) ${Math.min(0.08 + gi * 0.06 + i * 0.04, 0.4)}s both` }}
                      >
                        <button
                          onClick={() => setSection(n.id)}
                          className={cn(
                            'group w-full flex items-center gap-3 px-5 py-2 text-left text-sm cursor-pointer transition-colors border-l-2 rounded-r-lg',
                            active
                              ? 'bg-(--accent-dim) text-(--accent) border-(--accent)'
                              : 'text-(--text-2) hover:bg-(--raised)/60 hover:text-foreground border-transparent',
                          )}
                        >
                          <span className={active ? 'text-(--accent)' : 'text-(--text-3) group-hover:text-(--text-2)'}>
                            {n.icon}
                          </span>
                          <span>{t(n.label)}</span>
                        </button>
                      </li>
                    )
                  })}
                </ul>
              </div>
            ))}
          </nav>
        </ScrollArea>
      </aside>

      {/* ─── Content ─── */}
      <section className="flex-1 overflow-y-auto relative">
        <div className="ed-vignette" aria-hidden />

        {/* Sticky header: breadcrumb (desktop) ou back + select (mobile) */}
        <div className="sticky top-0 z-10 backdrop-blur bg-(--base)/90 border-b border-(--border)">
          {/* Mobile header: home (X + título) ou seção aberta (voltar + título) */}
          <div className="md:hidden flex items-center gap-2 px-4 py-3">
            {mobileOpen === null ? (
              <>
                <button
                  onClick={() => navigate(-1)}
                  className="size-10 -ml-1 grid place-items-center text-(--text-2) hover:text-(--accent) transition-colors cursor-pointer shrink-0"
                  aria-label="Fechar configurações"
                >
                  <X className="size-5" />
                </button>
                <h1 className="flex-1 text-lg m-0 font-normal tracking-tight text-foreground" style={{ fontFamily: 'var(--font-display)' }}>
                  {t('settings.title')}
                </h1>
              </>
            ) : (
              <>
                <button
                  onClick={() => setMobileOpen(null)}
                  className="size-10 -ml-1 grid place-items-center text-(--text-2) hover:text-(--accent) transition-colors cursor-pointer shrink-0"
                  aria-label="Voltar"
                >
                  <ArrowLeft className="size-5" />
                </button>
                <h1 className="flex-1 text-lg m-0 font-normal tracking-tight text-foreground truncate" style={{ fontFamily: 'var(--font-display)' }}>
                  {currentLabel}
                </h1>
              </>
            )}
          </div>

          {/* Desktop breadcrumb */}
          <div className="hidden md:block">
            <div className="max-w-3xl mx-auto px-6 sm:px-10 lg:px-12 py-3.5">
              <Breadcrumb>
                <BreadcrumbList>
                  <BreadcrumbItem>
                    <BreadcrumbLink onClick={() => navigate(-1)} className="cursor-pointer">App</BreadcrumbLink>
                  </BreadcrumbItem>
                  <BreadcrumbSeparator />
                  <BreadcrumbItem>
                    <BreadcrumbLink>{t('settings.title')}</BreadcrumbLink>
                  </BreadcrumbItem>
                  <BreadcrumbSeparator />
                  <BreadcrumbItem>
                    <BreadcrumbPage>{currentLabel}</BreadcrumbPage>
                  </BreadcrumbItem>
                </BreadcrumbList>
              </Breadcrumb>
            </div>
          </div>
        </div>

        {/* Mobile: home com cards agrupados (quando nenhuma seção aberta) */}
        {mobileOpen === null && <MobileSettingsList onPick={pickSection} />}

        {/* Conteúdo da seção — desktop sempre; mobile só com uma seção aberta */}
        <div className={cn(
          'max-w-3xl mx-auto px-5 sm:px-8 lg:px-12 py-8 sm:py-12 lg:py-16 pb-safe relative',
          mobileOpen === null ? 'hidden md:block' : 'block',
        )}>
          <AnimatePresence mode="wait">
            <motion.div
              key={section}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{    opacity: 0, y: -8 }}
              transition={{ duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
            >
              {section === 'account'       && <AccountSection />}
              {section === 'profile'       && <ProfileSection />}
              {section === 'customization' && <CustomizationSection />}
              {section === 'appearance'    && <AppearanceSection />}
              {section === 'name-colors'   && <NameColorsSection />}
              {section === 'notifications' && <NotificationsSection />}
              {section === 'language'      && <LanguageSection />}
              {section === 'wishing'       && <WishingStarSection />}
              {section === 'sessions'      && <SessionsSection />}
              {section === 'data'          && <DataSection />}
            </motion.div>
          </AnimatePresence>
        </div>
      </section>
    </main>
  )
}

// ─── Mobile: lista de seções em cards agrupados (estilo app nativo, ──────
//     vestido com a Astra: hairline, âmbar, mono nos rótulos). ──────────
function MobileSettingsList({ onPick }: { onPick: (id: SectionId) => void }) {
  const { t } = useTranslation()
  return (
    <div className="md:hidden max-w-xl mx-auto px-4 py-5 pb-safe space-y-6">
      {(['pessoal', 'app', 'comunidade', 'privacidade'] as const).map((grp, gi) => (
        <Reveal key={grp} delay={Math.min(gi * 0.05, 0.2)}>
          <p className="px-1 mb-2 text-[10px] uppercase tracking-wider text-(--text-3) font-mono m-0">
            — {t(`settings.groups.${grp}`)}
          </p>
          <div className="rounded-2xl border border-(--border) bg-(--raised)/30 overflow-hidden divide-y divide-(--border)">
            {NAV.filter((n) => n.group === grp).map((n) => (
              <button
                key={n.id}
                onClick={() => onPick(n.id)}
                className="w-full flex items-center gap-3 px-3.5 py-3.5 text-left transition-colors active:bg-(--raised)/60 cursor-pointer"
              >
                <span className="size-9 rounded-xl bg-(--accent-dim) text-(--accent) grid place-items-center shrink-0">
                  {n.icon}
                </span>
                <span className="flex-1 text-sm text-foreground">{t(n.label)}</span>
                <ChevronRight className="size-4 text-(--text-3) shrink-0" />
              </button>
            ))}
          </div>
        </Reveal>
      ))}
    </div>
  )
}
