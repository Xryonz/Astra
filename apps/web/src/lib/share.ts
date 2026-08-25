import i18n from '@/i18n'

const SITE_URL: string =
  (import.meta.env.VITE_SITE_URL as string | undefined) ?? window.location.origin

export async function shareInvite(code: string): Promise<'shared' | 'copied'> {
  const apiUrl = import.meta.env.VITE_API_URL as string | undefined
  const url = apiUrl ? `${apiUrl}/i/${code}` : `${SITE_URL}/invite/${code}`

  if (typeof navigator.share === 'function') {
    try {
      await navigator.share({ title: i18n.t('native.shareInvite'), url })
      return 'shared'
    } catch {
    }
  }

  await navigator.clipboard.writeText(url)
  return 'copied'
}
