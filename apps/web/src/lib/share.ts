import i18n from '@/i18n'

const SITE_URL: string =
  (import.meta.env.VITE_SITE_URL as string | undefined) ?? window.location.origin

// Compartilhar convite. Era o Share do Capacitor com clipboard de reserva; agora
// e a Web Share API com o mesmo clipboard atras. O navegador que nao tiver share
// (praticamente todo desktop) cai na copia, que e o caminho que o web sempre usou.
export async function shareInvite(code: string): Promise<'shared' | 'copied'> {
  const apiUrl = import.meta.env.VITE_API_URL as string | undefined
  const url = apiUrl ? `${apiUrl}/i/${code}` : `${SITE_URL}/invite/${code}`

  if (typeof navigator.share === 'function') {
    try {
      await navigator.share({ title: i18n.t('native.shareInvite'), url })
      return 'shared'
    } catch {
      // Cancelar o menu de compartilhar cai aqui: segue pra copia.
    }
  }

  await navigator.clipboard.writeText(url)
  return 'copied'
}
