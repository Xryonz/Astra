const HAPTICS_KEY = 'astra-haptics'
export const isHapticsEnabled = () => localStorage.getItem(HAPTICS_KEY) !== '0'
export const setHapticsEnabled = (on: boolean) => {
  if (on) localStorage.removeItem(HAPTICS_KEY)
  else    localStorage.setItem(HAPTICS_KEY, '0')
}

// Vibracao pela API do navegador, no lugar do Haptics do Capacitor. O Android web
// responde; desktop e iOS ignoram em silencio, que e o comportamento certo — nao
// ha nada a fazer quando o aparelho nao tem motor.
function vibrar(ms: number): void {
  if (!isHapticsEnabled()) return
  if (typeof navigator.vibrate !== 'function') return
  try { navigator.vibrate(ms) } catch { /* alguns navegadores exigem gesto do usuario */ }
}

export function hapticLight(): void  { vibrar(10) }
export function hapticMedium(): void { vibrar(20) }
