import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Sheet, SheetContent, SheetTitle, SheetDescription } from '@/components/ui/sheet'
import { Smile, Reply, Pencil, Pin, PinOff, Trash2, Copy, MessageSquarePlus, Bookmark, BookmarkCheck } from 'lucide-react'
import { hapticLight, hapticMedium } from '@/lib/haptics'

interface MessageMobileActionsProps {
  open:        boolean
  onClose:     () => void
  isMine:      boolean
  isPinned:    boolean
  isBookmarked?: boolean
  authorName:  string
  contentPreview: string
  onPickEmoji: () => void
  onReply:     () => void
  onEdit?:     () => void
  onTogglePin: () => void
  onToggleBookmark?: () => void
  onDelete?:   () => void
  onCopy?:     () => void
  onCreateThread?: () => void
}

/**
 * Bottom sheet de ações pra mensagem em mobile — disparado por long-press.
 * Sem hover. Toque grande pra dedo.
 */
export default function MessageMobileActions({
  open, onClose, isMine, isPinned, isBookmarked, authorName, contentPreview,
  onPickEmoji, onReply, onEdit, onTogglePin, onToggleBookmark, onDelete, onCopy, onCreateThread,
}: MessageMobileActionsProps) {
  const { t } = useTranslation()
  const QUICK = ['👍','❤️','😂','😮','😢','🔥']

  // Feedback tátil no long-press que abriu o sheet (no-op no web)
  useEffect(() => { if (open) hapticMedium() }, [open])

  const wrap = (fn?: () => void) => () => { fn?.(); onClose() }

  return (
    <Sheet open={open} onOpenChange={(o: boolean) => !o && onClose()}>
      <SheetContent side="bottom" className="p-0 max-h-[80vh] gap-0">
        <div className="px-5 py-4 border-b border-(--border)">
          <SheetTitle className="text-sm m-0 truncate font-(family-name:--font-display)">
            {authorName}
          </SheetTitle>
          <SheetDescription className="text-xs truncate text-(--text-3) italic m-0 mt-1">
            {contentPreview.slice(0, 80)}{contentPreview.length > 80 ? '…' : ''}
          </SheetDescription>
        </div>

        {/* Reações rápidas */}
        <div className="flex justify-between gap-2 px-5 py-4 border-b border-(--border)">
          {QUICK.map((e) => (
            <button
              key={e}
              onClick={() => { hapticLight(); onPickEmoji(); onClose() }}
              className="flex-1 text-2xl py-2 transition-transform active:scale-90"
              aria-label={t('msgActions.reactEmoji', { emoji: e })}
            >{e}</button>
          ))}
        </div>

        {/* Lista de ações */}
        <ul className="flex flex-col py-2">
          <ActionRow icon={<Smile className="size-4" />}  label={t('msgActions.moreEmojis')}   onClick={wrap(onPickEmoji)} />
          <ActionRow icon={<Reply className="size-4" />}  label={t('msgActions.reply')}     onClick={wrap(onReply)} />
          {onCreateThread && (
            <ActionRow icon={<MessageSquarePlus className="size-4" />} label={t('msgActions.threadShort')} onClick={wrap(onCreateThread)} />
          )}
          <ActionRow
            icon={isPinned ? <PinOff className="size-4" /> : <Pin className="size-4" />}
            label={isPinned ? t('msgActions.unpin') : t('msgActions.pin')}
            onClick={wrap(onTogglePin)}
          />
          {onToggleBookmark && (
            <ActionRow
              icon={isBookmarked ? <BookmarkCheck className="size-4" /> : <Bookmark className="size-4" />}
              label={isBookmarked ? t('msgActions.removeSaved') : t('msgActions.save')}
              onClick={wrap(onToggleBookmark)}
            />
          )}
          {onCopy && <ActionRow icon={<Copy className="size-4" />} label={t('msgActions.copyText')} onClick={wrap(onCopy)} />}
          {isMine && onEdit   && <ActionRow icon={<Pencil className="size-4" />} label={t('msgActions.edit')} onClick={wrap(onEdit)} />}
          {isMine && onDelete && <ActionRow icon={<Trash2 className="size-4" />} label={t('msgActions.delete')} onClick={wrap(onDelete)} danger />}
        </ul>
      </SheetContent>
    </Sheet>
  )
}

function ActionRow({ icon, label, onClick, danger }: {
  icon: React.ReactNode; label: string; onClick: () => void; danger?: boolean
}) {
  return (
    <li>
      <button
        onClick={onClick}
        className={`w-full flex items-center gap-3 px-5 py-3.5 text-left text-sm transition-colors active:bg-(--raised) ${danger ? 'text-(--danger)' : 'text-foreground'}`}
      >
        <span className={`size-9 flex items-center justify-center border border-(--border) ${danger ? 'text-(--danger)' : 'text-(--text-2)'}`}>
          {icon}
        </span>
        {label}
      </button>
    </li>
  )
}
