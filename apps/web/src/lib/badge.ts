
import { isNative } from '@/lib/native'

export function setAppBadge(count: number): void {
  if (isNative) {
    void import('@capawesome/capacitor-badge')
      .then(({ Badge }) => count > 0 ? Badge.set({ count }) : Badge.clear())
      .catch(() => {})
    return
  }

  const nav = navigator as Navigator & {
    setAppBadge?: (n: number) => Promise<void>
    clearAppBadge?: () => Promise<void>
  }
  if (count > 0) void nav.setAppBadge?.(count).catch(() => {})
  else void nav.clearAppBadge?.().catch(() => {})
}
