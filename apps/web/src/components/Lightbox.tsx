import { useEffect } from 'react'
import { X, ChevronLeft, ChevronRight, ExternalLink } from 'lucide-react'
import { resolveApiUrl } from '@/lib/api'

interface LightboxProps {
  images:    Array<{ url: string; name: string }>
  index:     number
  onClose:   () => void
  onNavigate?: (idx: number) => void
}

/**
 * Lightbox minimalista pra preview de imagens. ESC fecha, setas navegam,
 * clique fora fecha. Sem overlay pesado — editorial.
 */
export default function Lightbox({ images, index, onClose, onNavigate }: LightboxProps) {
  const current = images[index]

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
      if (e.key === 'ArrowLeft'  && index > 0)                onNavigate?.(index - 1)
      if (e.key === 'ArrowRight' && index < images.length - 1) onNavigate?.(index + 1)
    }
    window.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = '' }
  }, [index, images.length, onClose, onNavigate])

  if (!current) return null

  const src = resolveApiUrl(current.url)
  const hasPrev = index > 0
  const hasNext = index < images.length - 1

  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-[100] bg-black/95 flex items-center justify-center p-4 sm:p-8 anim-fade-in"
    >
      {/* Header bar */}
      <div className="absolute top-0 left-0 right-0 flex items-center justify-between px-5 py-4 z-10">
        <div className="flex flex-col">
          <span className="ed-marg text-white/60">{index + 1} / {images.length}</span>
          <span className="text-white/90 text-sm truncate max-w-[60vw]" style={{ fontFamily: 'var(--font-display)' }}>
            {current.name}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <a
            href={src}
            target="_blank"
            rel="noopener noreferrer"
            onClick={(e) => e.stopPropagation()}
            className="size-9 flex items-center justify-center border border-white/30 text-white/80 hover:border-white hover:text-white transition-colors cursor-pointer"
            aria-label="Abrir em nova aba"
            title="Abrir em nova aba"
          >
            <ExternalLink className="size-4" />
          </a>
          <button
            onClick={(e) => { e.stopPropagation(); onClose() }}
            className="size-9 flex items-center justify-center border border-white/30 text-white/80 hover:border-white hover:text-white transition-colors cursor-pointer"
            aria-label="Fechar"
          >
            <X className="size-4" />
          </button>
        </div>
      </div>

      {/* Prev */}
      {hasPrev && (
        <button
          onClick={(e) => { e.stopPropagation(); onNavigate?.(index - 1) }}
          className="absolute left-4 sm:left-8 size-12 flex items-center justify-center border border-white/30 text-white/80 hover:border-white hover:text-white transition-colors cursor-pointer z-10"
          aria-label="Anterior"
        >
          <ChevronLeft className="size-5" />
        </button>
      )}

      {/* Image */}
      <img
        src={src}
        alt={current.name}
        onClick={(e) => e.stopPropagation()}
        referrerPolicy="no-referrer"
        className="max-w-full max-h-[85vh] object-contain cursor-default anim-fade-up"
      />

      {/* Next */}
      {hasNext && (
        <button
          onClick={(e) => { e.stopPropagation(); onNavigate?.(index + 1) }}
          className="absolute right-4 sm:right-8 size-12 flex items-center justify-center border border-white/30 text-white/80 hover:border-white hover:text-white transition-colors cursor-pointer z-10"
          aria-label="Próxima"
        >
          <ChevronRight className="size-5" />
        </button>
      )}
    </div>
  )
}
