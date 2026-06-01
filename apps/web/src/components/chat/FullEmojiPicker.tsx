import { useEffect, useRef } from 'react'
import Picker from '@emoji-mart/react'
import data from '@emoji-mart/data'

/**
 * Full emoji picker via emoji-mart. Estilizado pra casar com o tema editorial:
 *  - tira o padding default do popover do mart
 *  - cor de fundo via var(--overlay) — herda do tema escolhido
 *  - fecha ao clicar fora
 *
 * Posicionamento é responsabilidade do parent (passa `anchor` style props).
 */
interface FullEmojiPickerProps {
  onPick:  (emoji: string) => void
  onClose: () => void
  className?: string
}

export default function FullEmojiPicker({ onPick, onClose, className }: FullEmojiPickerProps) {
  const wrapRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) onClose()
    }
    // Defer para evitar fechar imediatamente pelo clique que abriu
    const t = setTimeout(() => document.addEventListener('mousedown', handler), 50)
    return () => { clearTimeout(t); document.removeEventListener('mousedown', handler) }
  }, [onClose])

  return (
    <div ref={wrapRef} className={className}>
      <Picker
        data={data}
        onEmojiSelect={(e: { native?: string; shortcodes?: string }) => {
          if (e.native) { onPick(e.native); onClose() }
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
