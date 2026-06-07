/**
 * useViewTransitionNavigate — wrap react-router's navigate com View
 * Transitions API nativa do browser (Chromium 111+, Safari 18+).
 *
 * Browsers que suportam: a navegação é envolvida em
 * document.startViewTransition() — o compositor cuida do morph (crossfade
 * + animação CSS configurada via ::view-transition-old/new).
 *
 * Browsers que não suportam (Firefox): fallback silencioso pra navigate
 * normal — sem animação extra, mas tudo continua funcionando.
 *
 * Respeita prefers-reduced-motion automaticamente (pula a transição).
 */
import { useNavigate } from 'react-router-dom'
import { useCallback } from 'react'

type Navigate = ReturnType<typeof useNavigate>

export function useViewTransitionNavigate(): Navigate {
  const navigate = useNavigate()
  return useCallback(((...args: Parameters<Navigate>) => {
    const doc = document as Document & { startViewTransition?: (cb: () => void) => unknown }
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (typeof doc.startViewTransition === 'function' && !reduced) {
      doc.startViewTransition(() => {
        ;(navigate as (...a: unknown[]) => void)(...args)
      })
    } else {
      ;(navigate as (...a: unknown[]) => void)(...args)
    }
  }) as Navigate, [navigate])
}
