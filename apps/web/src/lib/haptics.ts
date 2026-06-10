import { isNative } from '@/lib/native'

/**
 * Feedback tátil — a diferença nº1 entre "site empacotado" e app nativo.
 * No web é no-op puro (early return, sem import). Módulo cacheado após o
 * primeiro uso; falhas são engolidas (haptics nunca pode quebrar UX).
 *
 * Uso: hapticLight() em seleção/toggle; hapticMedium() em ações com
 * peso (long-press abrir menu, deletar).
 */
type HapticsMod = typeof import('@capacitor/haptics')
let mod: Promise<HapticsMod> | null = null
const load = () => (mod ??= import('@capacitor/haptics'))

export function hapticLight(): void {
  if (!isNative) return
  void load().then(({ Haptics, ImpactStyle }) =>
    Haptics.impact({ style: ImpactStyle.Light }),
  ).catch(() => {})
}

export function hapticMedium(): void {
  if (!isNative) return
  void load().then(({ Haptics, ImpactStyle }) =>
    Haptics.impact({ style: ImpactStyle.Medium }),
  ).catch(() => {})
}
