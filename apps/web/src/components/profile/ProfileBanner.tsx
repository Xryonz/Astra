import { useState } from 'react'

/**
 * Banner do perfil — imagem com fallback pra gradient/cor sólida.
 * Gradient overlay no rodapé pra contraste do texto/avatar sobreposto.
 * Sem animated borders (dropado no overhaul 2026-06-02).
 */
interface Props {
  bannerUrl?:        string | null
  bannerColor?:      string | null  // hex ou gradient string
  fallbackGradient:  string         // gradient determinístico do user.id
}

export function ProfileBanner({ bannerUrl, bannerColor, fallbackGradient }: Props) {
  const [imgError, setImgError] = useState(false)
  const showImage = bannerUrl && !imgError
  const bg = showImage ? undefined : (bannerColor ?? fallbackGradient)

  return (
    <div className="relative h-48 overflow-hidden shrink-0" style={{ background: bg }}>
      {showImage && (
        <img
          src={bannerUrl!}
          alt=""
          referrerPolicy="no-referrer"
          onError={() => setImgError(true)}
          className="absolute inset-0 w-full h-full object-cover"
        />
      )}
      {/* Overlay pra contraste de texto/avatar acima */}
      <div className="absolute bottom-0 left-0 right-0 h-20 bg-linear-to-t from-black/55 to-transparent pointer-events-none" />
    </div>
  )
}
