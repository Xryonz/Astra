/**
 * Espelho seguro do refresh token no Android Keystore / iOS Keychain, via o
 * plugin de biometria que já está instalado (@capgo/capacitor-native-biometric).
 *
 * Porquê: o refresh token (a "chave" da sessão) só vivia no localStorage da
 * WebView, que o Android limpa em update/limpeza de dados/pressão de memória —
 * e aí o cold start não achava a sessão e forçava relogin. O keystore nativo
 * sobrevive a isso. localStorage continua sendo o fast-path (leitura síncrona);
 * o keystore é o backup durável de onde a gente re-hidrata no boot.
 *
 * Web: tudo no-op (fica só no localStorage). getCredentials NÃO pede biometria
 * (só verifyIdentity pede) — a hidratação é silenciosa.
 */
import { Capacitor } from '@capacitor/core'

const isNative = Capacitor.isNativePlatform()
const SERVER = 'app.astra.client'
const USER   = 'astra'

export async function saveRefreshNative(token: string): Promise<void> {
  if (!isNative) return
  try {
    const { NativeBiometric } = await import('@capgo/capacitor-native-biometric')
    await NativeBiometric.setCredentials({ username: USER, password: token, server: SERVER })
  } catch { /* keystore indisponível — segue só com localStorage */ }
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
  } catch { /* ignore */ }
}
