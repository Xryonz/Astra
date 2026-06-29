import { isNative } from '@/lib/native'

type HapticsMod = typeof import('@capacitor/haptics')
let mod: Promise<HapticsMod> | null = null
const load = () => (mod ??= import('@capacitor/haptics'))

const HAPTICS_KEY = 'astra-haptics'
export const isHapticsEnabled = () => localStorage.getItem(HAPTICS_KEY) !== '0'
export const setHapticsEnabled = (on: boolean) => {
  if (on) localStorage.removeItem(HAPTICS_KEY)
  else    localStorage.setItem(HAPTICS_KEY, '0')
}

export function hapticLight(): void {
  if (!isNative || !isHapticsEnabled()) return
  void load().then(({ Haptics, ImpactStyle }) =>
    Haptics.impact({ style: ImpactStyle.Light }),
  ).catch(() => {})
}

export function hapticMedium(): void {
  if (!isNative || !isHapticsEnabled()) return
  void load().then(({ Haptics, ImpactStyle }) =>
    Haptics.impact({ style: ImpactStyle.Medium }),
  ).catch(() => {})
}
