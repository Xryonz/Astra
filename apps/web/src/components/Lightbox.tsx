import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { X, ChevronLeft, ChevronRight, Download, Check } from 'lucide-react'
import { resolveApiUrl } from '@/lib/api'
import { saveImageToGallery } from '@/lib/saveImage'
import { toast } from '@/components/ui/sonner'

interface LightboxProps {
  images:    Array<{ url: string; name: string }>
  index:     number
  onClose:   () => void
  onNavigate?: (idx: number) => void
}

export default function Lightbox({ images, index, onClose, onNavigate }: LightboxProps) {
  const { t } = useTranslation()
  const current = images[index]
  const [saving, setSaving] = useState(false)
  const [saved, setSaved]   = useState(false)

  const handleSave = async (e: React.MouseEvent) => {
    e.stopPropagation()
    if (saving || !current) return
    setSaving(true)
    const r = await saveImageToGallery(resolveApiUrl(current.url), current.name)
    setSaving(false)
    if (r === 'saved')            { setSaved(true); toast.success(t('lightbox.savedToast')); setTimeout(() => setSaved(false), 2000) }
    else if (r === 'downloaded')  { setSaved(true); toast.success(t('lightbox.downloadedToast'));   setTimeout(() => setSaved(false), 2000) }
    else                          toast.error(t('lightbox.saveFailToast'))
  }

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
      {}
      <div className="absolute top-0 left-0 right-0 flex items-center justify-between px-5 py-4 z-10">
        <div className="flex flex-col">
          <span className="ed-marg text-white/60">{index + 1} / {images.length}</span>
          <span className="text-white/90 text-sm truncate max-w-[60vw]" style={{ fontFamily: 'var(--font-display)' }}>
            {current.name}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleSave}
            disabled={saving}
            className="size-9 flex items-center justify-center border border-white/30 text-white/80 hover:border-white hover:text-white transition-colors cursor-pointer disabled:opacity-50"
            aria-label={t('lightbox.save')}
            title={t('lightbox.saveTitle')}
          >
            {saved ? <Check className="size-4 text-(--success)" /> : <Download className="size-4" />}
          </button>
          <button
            onClick={(e) => { e.stopPropagation(); onClose() }}
            className="size-9 flex items-center justify-center border border-white/30 text-white/80 hover:border-white hover:text-white transition-colors cursor-pointer"
            aria-label={t('lightbox.close')}
          >
            <X className="size-4" />
          </button>
        </div>
      </div>

      {}
      {hasPrev && (
        <button
          onClick={(e) => { e.stopPropagation(); onNavigate?.(index - 1) }}
          className="absolute left-4 sm:left-8 size-12 flex items-center justify-center border border-white/30 text-white/80 hover:border-white hover:text-white transition-colors cursor-pointer z-10"
          aria-label={t('lightbox.prev')}
        >
          <ChevronLeft className="size-5" />
        </button>
      )}

      {}
      <img
        src={src}
        alt={current.name}
        onClick={(e) => e.stopPropagation()}
        referrerPolicy="no-referrer"
        className="max-w-full max-h-[85vh] object-contain cursor-default anim-fade-up"
      />

      {}
      {hasNext && (
        <button
          onClick={(e) => { e.stopPropagation(); onNavigate?.(index + 1) }}
          className="absolute right-4 sm:right-8 size-12 flex items-center justify-center border border-white/30 text-white/80 hover:border-white hover:text-white transition-colors cursor-pointer z-10"
          aria-label={t('lightbox.next')}
        >
          <ChevronRight className="size-5" />
        </button>
      )}
    </div>
  )
}
