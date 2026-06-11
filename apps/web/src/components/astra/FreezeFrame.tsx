/**
 * FreezeFrame — mostra o 1º frame de uma imagem (congela GIF/WebP animado)
 * até live=true, quando troca pro <img> vivo.
 *
 * Por quê: 15 ícones de servidor animando ao mesmo tempo na strip derrete
 * bateria. Norma Discord: anima só no hover/ativo. GIF não tem "pause" —
 * o truque é desenhar o frame atual num canvas no onload (= frame 0).
 *
 * Cross-origin: NÃO seta crossOrigin — desenhar imagem tainted no canvas
 * é permitido (só leitura de volta é bloqueada, e nunca lemos). Se a
 * imagem falhar, cai pro <img> normal (anima sempre — degradação suave).
 */
import { useEffect, useRef, useState } from 'react'

interface Props {
  src:        string
  alt?:       string
  live?:      boolean
  className?: string
}

export function FreezeFrame({ src, alt = '', live = false, className }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    if (live || failed) return
    const canvas = canvasRef.current
    if (!canvas) return
    let alive = true
    const img = new Image()
    img.referrerPolicy = 'no-referrer'
    img.onload = () => {
      if (!alive || !canvasRef.current) return
      canvas.width  = img.naturalWidth
      canvas.height = img.naturalHeight
      try { canvas.getContext('2d')?.drawImage(img, 0, 0) } catch { setFailed(true) }
    }
    img.onerror = () => { if (alive) setFailed(true) }
    img.src = src
    return () => { alive = false }
  }, [src, live, failed])

  if (live || failed) {
    return <img src={src} alt={alt} referrerPolicy="no-referrer" className={className} />
  }
  return <canvas ref={canvasRef} aria-label={alt} role="img" className={className} />
}
