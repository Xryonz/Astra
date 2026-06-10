/**
 * useViewTransitionNavigate — hoje é um alias de useNavigate.
 *
 * Histórico: envolvia a navegação em document.startViewTransition(). Foi
 * desligado porque EMPILHAVA com o PageTransition (motion) das rotas:
 * startViewTransition congela o render e tira snapshot da página inteira
 * (caro), roda crossfade de 0.34s, e por dentro o motion ainda animava
 * exit+enter — três animações por navegação = lentidão percebida e jank.
 * Uma transição só (motion, subtree, exit controlado) é mais fluida.
 *
 * O nome/API ficam — call sites intactos; se um dia o PageTransition sair,
 * dá pra religar a VT aqui sem tocar em consumer nenhum.
 */
import { useNavigate } from 'react-router-dom'

type Navigate = ReturnType<typeof useNavigate>

export function useViewTransitionNavigate(): Navigate {
  return useNavigate()
}
