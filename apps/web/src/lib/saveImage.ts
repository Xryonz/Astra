/**
 * Salvar imagem do chat no dispositivo.
 *  - Nativo: vai pra galeria de fotos via MediaStore (@capacitor-community/media,
 *    que aceita URL direto — baixa e salva nativamente).
 *  - Web: download normal via blob + <a download>.
 *
 * Retorna o modo pro caller dar o feedback certo. Nunca lança.
 */
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

  // Web: baixa o blob (mesma-origem ou CORS liberado) e dispara o download.
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
    // Fallback: abre em nova aba pro user salvar manualmente
    window.open(url, '_blank', 'noopener')
    return 'error'
  }
}
