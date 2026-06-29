import { useState } from 'react'
import { Constellation } from '@/components/astra/Constellation'

interface Props {
  bannerUrl?:        string | null
  bannerColor?:      string | null
  fallbackGradient:  string

  username?:         string

  positionY?:        number

  scale?:            number
}

export function ProfileBanner({
  bannerUrl, bannerColor, fallbackGradient, username, positionY = 50, scale = 100,
}: Props) {
  const [imgError, setImgError] = useState(false)
  const [imgLoaded, setImgLoaded] = useState(false)
  const showImage = bannerUrl && !imgError

  const bg = (!showImage || !imgLoaded) ? (bannerColor ?? fallbackGradient) : undefined

  return (
    <div className="relative h-48 overflow-hidden shrink-0" style={{ background: bg }}>
      {}
      {!showImage && username && (
        <Constellation
          name={username}
          animated
          className="absolute inset-0 w-full h-full text-white/35 mix-blend-screen"
        />
      )}
      {showImage && (
        <img
          src={bannerUrl!}
          alt=""
          referrerPolicy="no-referrer"
          onError={() => setImgError(true)}
          onLoad={() => setImgLoaded(true)}
          className="absolute inset-0 w-full h-full object-cover transition-opacity duration-500 ease-(--ease-out-soft)"
          style={{
            opacity:        imgLoaded ? 1 : 0,
            objectPosition: `center ${positionY}%`,
            transform:      `scale(${scale / 100})`,
            transformOrigin: 'center center',
          }}
        />
      )}
      {}
      <div className="absolute bottom-0 left-0 right-0 h-20 bg-linear-to-t from-black/55 to-transparent pointer-events-none" />
    </div>
  )
}
