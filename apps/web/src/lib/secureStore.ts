
import { Capacitor } from '@capacitor/core'

const isNative = Capacitor.isNativePlatform()
const SERVER = 'app.astra.client'
const USER   = 'astra'

export async function saveRefreshNative(token: string): Promise<void> {
  if (!isNative) return
  try {
    const { NativeBiometric } = await import('@capgo/capacitor-native-biometric')
    await NativeBiometric.setCredentials({ username: USER, password: token, server: SERVER })
  } catch { }
}

export async function loadRefreshNative(): Promise<string | null> {
  if (!isNative) return null
  try {
    const { NativeBiometric } = await import('@capgo/capacitor-native-biometric')
    const c = await NativeBiometric.getCredentials({ server: SERVER })
    return c?.password || null
  } catch { return null }
}

export async function clearRefreshNative(): Promise<void> {
  if (!isNative) return
  try {
    const { NativeBiometric } = await import('@capgo/capacitor-native-biometric')
    await NativeBiometric.deleteCredentials({ server: SERVER })
  } catch { }
}
