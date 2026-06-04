/**
 * MessageReactions — chips de reactions sob a mensagem.
 *
 * Cada chip: emoji + count, com glow accent quando user reagiu.
 * Punch animation no click: pulse 1→1.22→1 + ring glow accent
 * expandindo. Re-triggerable via state com timestamp key.
 *
 * Extraído de MessageItem (overhaul Fase 4d).
 */
import { memo, useState } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { useAuthStore } from '@/store/authStore'
import { cn } from '@/lib/utils'

export interface Reaction {
  emoji:  string
  count:  number
  users:  string[]
}

interface Props {
  reactions: Reaction[]
  onReact:   (emoji: string) => void
}

export const MessageReactions = memo(function MessageReactions({ reactions, onReact }: Props) {
  const currentUserId = useAuthStore((s) => s.user?.id)
  const [punchKey, setPunchKey] = useState<string | null>(null)
  if (!reactions?.length) return null

  const handleClick = (emoji: string) => {
    setPunchKey(emoji + ':' + Date.now())
    onReact(emoji)
  }

  return (
    <div className="flex flex-wrap gap-1.5 mt-2">
      <AnimatePresence initial={false} mode="popLayout">
        {reactions.map((r) => {
          const reacted    = r.users.includes(currentUserId ?? '')
          const isPunching = punchKey?.startsWith(r.emoji + ':')
          return (
            <motion.button
              key={r.emoji}
              layout
              initial={{ opacity: 0, scale: 0.4, y: 6 }}
              animate={isPunching
                ? { opacity: 1, scale: [1, 1.22, 1], y: 0 }
                : { opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.4, y: -6 }}
              transition={isPunching
                ? { duration: 0.42, times: [0, 0.35, 1], ease: [0.16, 1, 0.3, 1] }
                : { type: 'spring', stiffness: 520, damping: 24 }}
              whileTap={{ scale: 0.92 }}
              onClick={() => handleClick(r.emoji)}
              onAnimationComplete={() => { if (isPunching) setPunchKey(null) }}
              className={cn(
                'relative flex items-center gap-1.5 px-2.5 py-0.5 rounded-full cursor-pointer text-sm border isolate',
                'transition-[background-color,border-color,color] duration-150',
                reacted
                  ? 'bg-(--accent-dim) border-(--accent) text-(--accent)'
                  : 'bg-(--raised)/40 border-(--border) text-(--text-3) hover:border-(--accent) hover:text-(--text-2)',
              )}
            >
              {isPunching && (
                <motion.span
                  aria-hidden
                  className="absolute inset-0 rounded-full pointer-events-none"
                  initial={{ opacity: 0.6, scale: 1, boxShadow: '0 0 0 0 var(--accent)' }}
                  animate={{ opacity: 0, scale: 1.4, boxShadow: '0 0 0 8px transparent' }}
                  transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
                  style={{ border: '1.5px solid var(--accent)' }}
                />
              )}
              <span className="relative">{r.emoji}</span>
              <motion.span
                key={r.count}
                initial={{ scale: 1.3, opacity: 0.6 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ type: 'spring', stiffness: 600, damping: 26 }}
                className="font-mono text-[10px] font-medium tracking-wide relative"
              >
                {r.count}
              </motion.span>
            </motion.button>
          )
        })}
      </AnimatePresence>
    </div>
  )
})
