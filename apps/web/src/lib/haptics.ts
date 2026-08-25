const HAPTICS_KEY = 'astra-haptics'
export const isHapticsEnabled = () => localStorage.getItem(HAPTICS_KEY) !== '0'
export const setHapticsEnabled = (on: boolean) => {
  if (on) localStorage.removeItem(HAPTICS_KEY)
  else    localStorage.setItem(HAPTICS_KEY, '0')
}

function vibrar(ms: number): void {
  if (!isHapticsEnabled()) return
  if (typeof navigator.vibrate !== 'function') return
  try { navigator.vibrate(ms) } catch {  }
}

export function hapticLight(): void  { vibrar(10) }
export function hapticMedium(): void { vibrar(20) }
