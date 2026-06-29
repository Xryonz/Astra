
import { useTranslation } from 'react-i18next'
import {
  Smile, Reply, MessageSquarePlus, Bookmark, BookmarkCheck,
  Pin, PinOff, Pencil, Trash2,
} from 'lucide-react'
import { Tooltip, TooltipTrigger, TooltipContent } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'

interface Props {
  isMine:            boolean
  isPinned:          boolean
  isBookmarked?:     boolean
  onPickEmoji:       () => void
  onReply?:          () => void
  onCreateThread?:   () => void
  onEdit?:           () => void
  onDelete?:         () => void
  onTogglePin?:      () => void
  onToggleBookmark?: () => void
}

export function MessageToolbar({
  isMine, isPinned, isBookmarked, onPickEmoji, onReply, onCreateThread,
  onEdit, onDelete, onTogglePin, onToggleBookmark,
}: Props) {
  const { t } = useTranslation()
  return (
    <div className="absolute -top-3 right-3 z-10 flex gap-0 px-0 py-0 bg-(--overlay) border border-(--border-mid) shadow-3 animate-in fade-in-0 zoom-in-95 duration-150">
      <ToolBtn title={t('msgActions.react')} onClick={onPickEmoji}><Smile className="size-4" /></ToolBtn>
      {onReply && <ToolBtn title={t('msgActions.reply')} onClick={onReply}><Reply className="size-3.5" /></ToolBtn>}
      {onCreateThread && (
        <ToolBtn title={t('msgActions.thread')} onClick={onCreateThread}><MessageSquarePlus className="size-3.5" /></ToolBtn>
      )}
      {onToggleBookmark && (
        <ToolBtn title={isBookmarked ? t('msgActions.removeSaved') : t('msgActions.save')} onClick={onToggleBookmark}>
          {isBookmarked ? <BookmarkCheck className="size-3.5 text-(--accent)" /> : <Bookmark className="size-3.5" />}
        </ToolBtn>
      )}
      {onTogglePin && (
        <ToolBtn title={isPinned ? t('msgActions.unpin') : t('msgActions.pin')} onClick={onTogglePin}>
          {isPinned ? <PinOff className="size-3.5" /> : <Pin className="size-3.5" />}
        </ToolBtn>
      )}
      {isMine && onEdit   && <ToolBtn title={t('msgActions.edit')}  onClick={onEdit}><Pencil className="size-3.5" /></ToolBtn>}
      {isMine && onDelete && <ToolBtn title={t('msgActions.delete')}  onClick={onDelete} danger><Trash2 className="size-3.5" /></ToolBtn>}
    </div>
  )
}

function ToolBtn({ title, onClick, danger, children }: {
  title:    string
  onClick:  () => void
  danger?:  boolean
  children: React.ReactNode
}) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          onClick={(e) => { e.stopPropagation(); onClick() }}
          aria-label={title}
          className={cn(
            'px-2.5 py-2 cursor-pointer transition-colors border-0 bg-transparent flex items-center justify-center border-r border-(--border) last:border-r-0',
            danger
              ? 'text-(--text-3) hover:bg-(--danger)/15 hover:text-(--danger)'
              : 'text-(--text-3) hover:bg-(--accent-dim) hover:text-(--accent)',
          )}
        >
          {children}
        </button>
      </TooltipTrigger>
      <TooltipContent side="top">{title}</TooltipContent>
    </Tooltip>
  )
}
