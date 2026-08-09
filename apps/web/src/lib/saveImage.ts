export type SaveResult = 'saved' | 'downloaded' | 'error'

// Salvar imagem. O ramo 'saved' era a galeria do Android via Capacitor; sobrou o
// download do navegador, que e o que o web sempre fez. O tipo mantem 'saved' pra
// nao mexer em quem le o resultado — hoje ele so nao acontece mais.
export async function saveImageToGallery(url: string, name: string): Promise<SaveResult> {
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
