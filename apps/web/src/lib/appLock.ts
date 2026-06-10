/**
 * App lock por biometria (digital/face) — só no app nativo.
 * Preferência em localStorage; a verificação real é do OS via
 * @capgo/capacitor-native-biometric. Web: sempre desbloqueado.
 */
import { isNative } from '@/lib/native'

const LOCK_KEY = 'astra-app-lock'

export const isAppLockEnabled = () => isNative && localStorage.getItem(LOCK_KEY) === '1'
export const setAppLockEnabled = (on: boolean) => {
  if (on) localStorage.setItem(LOCK_KEY, '1')
  else    localStorage.removeItem(LOCK_KEY)
}

/** true se o device tem biometria configurada (pra mostrar o toggle). */
export async function isBiometricAvailable(): Promise<boolean> {
  if (!isNative) return false
  try {
    const { NativeBiometric } = await import('@capgo/capacitor-native-biometric')
    const r = await NativeBiometric.isAvailable()
    return r.isAvailable
  } catch { return false }
}

/** Pede a digital. true = desbloqueado (ou lock desligado/indisponível). */
export async function verifyAppLock(): Promise<boolean> {
  if (!isAppLockEnabled()) return true
  try {
    const { NativeBiometric } = await import('@capgo/capacitor-native-biometric')
    const { isAvailable } = await NativeBiometric.isAvailable()
    if (!isAvailable) return true // biometria removida do device — não tranca o user pra fora
    await NativeBiometric.verifyIdentity({
      reason:   'Desbloquear o Astra',
      title:    'Astra bloqueado',
      subtitle: 'Use sua digital pra entrar',
    })
    return true
  } catch {
    return false // cancelou/falhou — fica na tela de lock
  }
}
