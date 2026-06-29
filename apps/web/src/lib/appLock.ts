
import { isNative } from '@/lib/native'

const LOCK_KEY = 'astra-app-lock'

export const isAppLockEnabled = () => isNative && localStorage.getItem(LOCK_KEY) === '1'
export const setAppLockEnabled = (on: boolean) => {
  if (on) localStorage.setItem(LOCK_KEY, '1')
  else    localStorage.removeItem(LOCK_KEY)
}

export async function isBiometricAvailable(): Promise<boolean> {
  if (!isNative) return false
  try {
    const { NativeBiometric } = await import('@capgo/capacitor-native-biometric')
    const r = await NativeBiometric.isAvailable()
    return r.isAvailable
  } catch { return false }
}

let verifying = false

export const isVerifyingAppLock = () => verifying

export async function verifyAppLock(): Promise<boolean> {
  if (!isAppLockEnabled()) return true
  if (verifying) return false
  verifying = true
  try {
    const { NativeBiometric } = await import('@capgo/capacitor-native-biometric')
    const { isAvailable } = await NativeBiometric.isAvailable()
    if (!isAvailable) return true
    await NativeBiometric.verifyIdentity({
      reason:   'Desbloquear o Astra',
      title:    'Astra bloqueado',
      subtitle: 'Use sua digital pra entrar',

      useFallback: true,
      maxAttempts: 2,
    })
    return true
  } catch {
    return false
  } finally {
    verifying = false
  }
}
