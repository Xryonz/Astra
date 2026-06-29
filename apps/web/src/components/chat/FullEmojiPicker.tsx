import { useEffect, useMemo, useRef } from 'react'
import Picker from '@emoji-mart/react'
import data from '@emoji-mart/data'
import { useEmojiMap } from '@/hooks/useServerEmojis'
import { resolveApiUrl } from '@/lib/api'

interface FullEmojiPickerProps {
  onPick:  (emoji: string) => void
  onClose: () => void
  className?: string
}

export default function FullEmojiPicker({ onPick, onClose, className }: FullEmojiPickerProps) {
  const wrapRef  = useRef<HTMLDivElement>(null)
  const emojiMap = useEmojiMap()

  const customCategories = useMemo(() => {
    if (emojiMap.size === 0) return undefined
    const emojis = Array.from(emojiMap.values()).map((e) => ({
      id:       e.name,
      name:     e.name,
      keywords: [e.name],
      skins:    [{ src: resolveApiUrl(e.url) }],
    }))
    return [{ id: 'astra', name: 'Astra', emojis }]
  }, [emojiMap])

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) onClose()
    }

    const t = setTimeout(() => document.addEventListener('mousedown', handler), 50)
    return () => { clearTimeout(t); document.removeEventListener('mousedown', handler) }
  }, [onClose])

  return (
    <div ref={wrapRef} className={className}>
      <Picker
        data={data}
        custom={customCategories}
        onEmojiSelect={(e: { native?: string; id?: string; name?: string }) => {

          if (e.native) { onPick(e.native); onClose(); return }
          const id = e.id ?? e.name
          if (id) { onPick(`:${id}:`); onClose() }
        }}
        theme="dark"
        previewPosition="none"
        skinTonePosition="search"
        locale="pt"
        perLine={8}
        maxFrequentRows={2}
      />
    </div>
  )
}
