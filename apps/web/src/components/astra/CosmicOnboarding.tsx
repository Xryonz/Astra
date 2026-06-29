
import { useState, useEffect } from 'react'
import { useTranslation, Trans } from 'react-i18next'
import { motion, AnimatePresence } from 'motion/react'
import { Dialog, DialogContent } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Sparkles, Stars, MessageSquareQuote } from 'lucide-react'

const STORAGE_KEY = 'astra:onboarded'

interface Slide {
  icon:  React.ReactNode
  title: string
  body:  React.ReactNode
}

const accent = { a: <strong className="text-(--accent)" /> }

export function CosmicOnboarding() {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [idx,  setIdx]  = useState(0)

  const SLIDES: Slide[] = [
    {
      icon:  <Stars className="size-7" />,
      title: t('onboarding.slide1Title'),
      body:  <Trans i18nKey="onboarding.slide1Body" components={accent} />,
    },
    {
      icon:  <MessageSquareQuote className="size-7" />,
      title: t('onboarding.slide2Title'),
      body:  <Trans i18nKey="onboarding.slide2Body" components={accent} />,
    },
    {
      icon:  <Sparkles className="size-7" />,
      title: t('onboarding.slide3Title'),
      body:  <Trans i18nKey="onboarding.slide3Body" components={accent} />,
    },
  ]

  useEffect(() => {
    if (typeof window === 'undefined') return
    const done = window.localStorage.getItem(STORAGE_KEY)
    if (!done) setOpen(true)
  }, [])

  const dismiss = () => {
    try { window.localStorage.setItem(STORAGE_KEY, '1') } catch {}
    setOpen(false)
  }

  const slide = SLIDES[idx]
  const isLast = idx === SLIDES.length - 1

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) dismiss() }}>
      <DialogContent className="max-w-[460px]! gap-0 p-0 overflow-hidden">
        <div className="px-7 pt-8 pb-6">
          {}
          <motion.div
            layoutId="cosmic-onboarding-icon"
            transition={{ type: 'spring', stiffness: 380, damping: 32 }}
            className="size-12 rounded-xl flex items-center justify-center mb-5 text-(--accent)"
            style={{
              background:  'var(--accent-dim)',
              border:      '1px solid color-mix(in srgb, var(--accent) 30%, transparent)',
              boxShadow:   '0 4px 24px var(--accent-glow)',
            }}
          >
            <AnimatePresence mode="wait" initial={false}>
              <motion.span
                key={idx}
                initial={{ opacity: 0, scale: 0.6, rotate: -12 }}
                animate={{ opacity: 1, scale: 1,   rotate: 0   }}
                exit={{    opacity: 0, scale: 0.6, rotate: 12  }}
                transition={{ duration: 0.24, ease: [0.16, 1, 0.3, 1] }}
              >
                {slide.icon}
              </motion.span>
            </AnimatePresence>
          </motion.div>
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={`text-${idx}`}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{    opacity: 0, y: -6 }}
              transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
            >
              <h2
                className="text-2xl m-0 mb-3 tracking-tight"
                style={{ fontFamily: 'var(--font-display)' }}
              >
                {slide.title}
              </h2>
              <p className="m-0 text-(--text-2) leading-relaxed">{slide.body}</p>
            </motion.div>
          </AnimatePresence>
        </div>

        {}
        <div className="flex items-center justify-between px-7 py-4 border-t border-(--border) bg-(--raised)/40">
          <div className="flex items-center gap-1.5">
            {SLIDES.map((_, i) => (
              <span
                key={i}
                className="size-1.5 rounded-full transition-all"
                style={{
                  background: i === idx ? 'var(--accent)' : 'var(--text-3)',
                  opacity:    i === idx ? 1 : 0.4,
                  width:      i === idx ? '14px' : '6px',
                }}
              />
            ))}
          </div>
          <div className="flex gap-2">
            {!isLast && (
              <Button variant="ghost" size="sm" onClick={dismiss}>
                {t('onboarding.skip')}
              </Button>
            )}
            <Button size="sm" onClick={() => isLast ? dismiss() : setIdx((i) => i + 1)}>
              {isLast ? t('onboarding.start') : t('onboarding.next')}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
