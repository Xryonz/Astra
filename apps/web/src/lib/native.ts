import { Capacitor } from '@capacitor/core'
import i18n from '@/i18n'
import { completeOAuthLogin } from '@/lib/oauth'
import { runBackHandlers } from '@/lib/backHandler'

export const isNative = Capacitor.isNativePlatform()

const staticShortcuts = () => [
  { id: 'dms',     title: i18n.t('native.dmsTitle'),     description: i18n.t('native.dmsDesc') },
  { id: 'friends', title: i18n.t('native.friendsTitle'), description: i18n.t('native.friendsDesc') },
]

export function setDmShortcuts(dms: { id: string; title: string }[]): void {
  if (!isNative || dms.length === 0) return
  void import('@capawesome/capacitor-app-shortcuts')
    .then(({ AppShortcuts }) => AppShortcuts.set({
      shortcuts: [
        ...dms.slice(0, 3).map((d) => ({
          id: `dm-${d.id}`,
          title: d.title,
          description: i18n.t('native.recentConvo'),
        })),
        ...staticShortcuts(),
      ],
    }))
    .catch(() => {})
}

export async function initNativeApp(): Promise<void> {
  if (!isNative) return

  document.documentElement.classList.add('astra-native')

  void import('@capgo/capacitor-updater')
    .then(({ CapacitorUpdater }) => CapacitorUpdater.notifyAppReady())
    .catch(() => {})

  setTimeout(() => {
    void import('@capacitor/splash-screen')
      .then(({ SplashScreen }) => SplashScreen.hide({ fadeOutDuration: 200 }))
      .catch(() => {})
  }, 4000)

  try {
    const { Keyboard } = await import('@capacitor/keyboard')
    const root = document.documentElement
    const freeze   = () => root.classList.add('astra-kb-resizing')
    const unfreeze = () => root.classList.remove('astra-kb-resizing')

    void Keyboard.addListener('keyboardWillShow', () => { freeze(); root.classList.add('astra-kb-open') })
    void Keyboard.addListener('keyboardDidShow', () => {
      unfreeze()

      root.classList.add('astra-kb-open')

      window.dispatchEvent(new Event('astra:kb-shown'))
    })
    void Keyboard.addListener('keyboardWillHide', () => { freeze(); root.classList.remove('astra-kb-open') })
    void Keyboard.addListener('keyboardDidHide', () => { unfreeze(); root.classList.remove('astra-kb-open') })
  } catch { }

  try {
    const { AppShortcuts } = await import('@capawesome/capacitor-app-shortcuts')
    await AppShortcuts.set({ shortcuts: staticShortcuts() })
    await AppShortcuts.addListener('click', ({ shortcutId }) => {
      if (shortcutId === 'dms')     window.location.href = '/app/dm'
      if (shortcutId === 'friends') window.location.href = '/app/friends'
      if (shortcutId.startsWith('dm-')) window.location.href = `/app/dm/${shortcutId.slice(3)}`
    })
  } catch { }

  try {
    const { StatusBar, Style } = await import('@capacitor/status-bar')
    await StatusBar.setOverlaysWebView({ overlay: false })
    await StatusBar.setBackgroundColor({ color: '#06060e' })
    await StatusBar.setStyle({ style: Style.Dark })
  } catch { }

  const { App } = await import('@capacitor/app')

  App.addListener('appUrlOpen', async ({ url }) => {
    if (!url.startsWith('astra://')) return

    try {
      const { Browser } = await import('@capacitor/browser')
      await Browser.close()
    } catch { }

    if (url.startsWith('astra://auth/callback')) {
      const fragment = url.split('#')[1] ?? ''
      const refresh  = new URLSearchParams(fragment).get('refresh')
      if (!refresh) { window.location.href = '/login?error=oauth_failed'; return }
      try {
        await completeOAuthLogin(refresh)
        window.location.href = '/app'
      } catch {
        window.location.href = '/login?error=oauth_failed'
      }
      return
    }

    if (url.startsWith('astra://login')) {

      const query = url.split('?')[1] ?? ''
      window.location.href = query ? `/login?${query}` : '/login'
    }
  })

  App.addListener('backButton', ({ canGoBack }) => {

    const openOverlay = document.querySelector(
      '[role="dialog"][data-state="open"], [role="alertdialog"][data-state="open"]',
    )
    if (openOverlay) {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      return
    }

    if (runBackHandlers()) return
    if (canGoBack) {
      window.history.back()
    } else {

      void App.minimizeApp()
    }
  })
}

export async function openGoogleLogin(): Promise<void> {
  const base = `${import.meta.env.VITE_API_URL}/api/auth/google`
  if (!isNative) {
    window.location.href = base
    return
  }
  const { Browser } = await import('@capacitor/browser')
  await Browser.open({ url: `${base}?platform=mobile` })
}

let lastPip = false
export function setPipEnabled(enabled: boolean): void {
  if (!isNative || enabled === lastPip) return
  lastPip = enabled
  void import('@capacitor/core')
    .then(({ registerPlugin }) => {
      const AstraPip = registerPlugin<{ setEnabled(o: { enabled: boolean }): Promise<void> }>('AstraPip')
      return AstraPip.setEnabled({ enabled })
    })
    .catch(() => { lastPip = false })
}

let lastCallActive = false
export function setCallActive(active: boolean): void {
  if (!isNative || active === lastCallActive) return
  lastCallActive = active
  void import('@capacitor/core')
    .then(({ registerPlugin }) => {
      const Svc = registerPlugin<{ setActive(o: { active: boolean }): Promise<void> }>('AstraCallService')
      return Svc.setActive({ active })
    })
    .catch(() => { lastCallActive = false })
}

const SITE_URL: string =
  (import.meta.env.VITE_SITE_URL as string | undefined) ?? window.location.origin

export async function shareInvite(code: string): Promise<'shared' | 'copied'> {
  const apiUrl = import.meta.env.VITE_API_URL as string | undefined
  const url = apiUrl ? `${apiUrl}/i/${code}` : `${SITE_URL}/invite/${code}`
  if (isNative) {
    try {
      const { Share } = await import('@capacitor/share')
      await Share.share({ title: i18n.t('native.shareInvite'), url })
      return 'shared'
    } catch { }
  }
  await navigator.clipboard.writeText(url)
  return 'copied'
}
