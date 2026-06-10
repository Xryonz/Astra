/**
 * Badge no ícone do app — número de não-lidas. No web tenta a Badging API
 * (Chrome instalado/PWA); no app usa o plugin nativo. Falhas silenciosas:
 * badge é enfeite, nunca pode quebrar nada.
 */
import { isNative } from '@/lib/native'

export function setAppBadge(count: number): void {
  if (isNative) {
    void import('@capawesome/capacitor-badge')
      .then(({ Badge }) => count > 0 ? Badge.set({ count }) : Badge.clear())
      .catch(() => {})
    return
  }
  // Web: Badging API (só tem efeito em PWA instalada)
  const nav = navigator as Navigator & {
    setAppBadge?: (n: number) => Promise<void>
    clearAppBadge?: () => Promise<void>
  }
  if (count > 0) void nav.setAppBadge?.(count).catch(() => {})
  else void nav.clearAppBadge?.().catch(() => {})
}
