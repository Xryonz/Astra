
import { isNative } from '@/lib/native'

export type SaveResult = 'saved' | 'downloaded' | 'error'

export async function saveImageToGallery(url: string, name: string): Promise<SaveResult> {
  if (isNative) {
    try {
      const { Media } = await import('@capacitor-community/media')
      await Media.savePhoto({ path: url, fileName: name })
      return 'saved'
    } catch {
      return 'error'
    }
  }

  try {
    const res  = await fetch(url)
    const blob = await res.blob()
    const href = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = href
    a.download = name || 'imagem'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(href)
    return 'downloaded'
  } catch {

    window.open(url, '_blank', 'noopener')
    return 'error'
  }
}
