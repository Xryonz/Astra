/**
 * Sheet "Salvos" — pasta pessoal de bookmarks.
 *
 * Cada item mostra snapshot da msg (autor + preview) + note editável + remover.
 * Lazy-loaded pelo caller (rare interaction).
 */
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Sheet, SheetContent, SheetTitle, SheetDescription } from '@/components/ui/sheet'
import ConstellationEmpty from '@/components/astra/ConstellationEmpty'
import { Spinner } from '@/components/ui/spinner'
import { Input } from '@/components/ui/input'
import { Bookmark, Trash2, NotebookPen, Check, X } from 'lucide-react'
import { formatDistanceToNow } from 'date-fns'
import { ptBR } from 'date-fns/locale/pt-BR'
import { enUS } from 'date-fns/locale/en-US'
import { useBookmarkList, type BookmarkItem } from '@/hooks/useBookmarks'
import { useQueryClient } from '@tanstack/react-query'
import { api, resolveApiUrl } from '@/lib/api'

interface Props { open: boolean; onClose: () => void }

export default function BookmarksSheet({ open, onClose }: Props) {
  const { t } = useTranslation()
  const list = useBookmarkList()
  const qc   = useQueryClient()
  const items = list.data?.pages.flatMap((p) => p.items) ?? []

  const remove = async (id: string) => {
    await api.delete(`/api/bookmarks/${id}`)
    qc.invalidateQueries({ queryKey: ['bookmarks'] })
  }

  const saveNote = async (id: string, note: string) => {
    await api.patch(`/api/bookmarks/${id}`, { note: note.trim() || null })
    qc.invalidateQueries({ queryKey: ['bookmarks'] })
  }

  return (
    <Sheet open={open} onOpenChange={(o: boolean) => !o && onClose()}>
      <SheetContent side="right" className="p-0 sm:max-w-md flex flex-col gap-0">
        <div className="px-5 py-4 border-b border-(--border)">
          <SheetTitle className="flex items-center gap-2 text-base m-0 font-(family-name:--font-display)">
            <Bookmark className="size-4 text-(--accent)" />
            {t('bookmarks.title')}
          </SheetTitle>
          <SheetDescription className="text-xs text-(--text-3) m-0">
            {t('bookmarks.desc')}
          </SheetDescription>
        </div>

        <div className="flex-1 overflow-y-auto">
          {list.isLoading ? (
            <div className="flex items-center justify-center gap-2 py-10 text-sm text-(--text-3)">
              <Spinner size={14} /> {t('common.loading')}
            </div>
          ) : items.length === 0 ? (
            <ConstellationEmpty
              title={t('bookmarks.emptyTitle')}
              description={t('bookmarks.emptyDesc')}
            />
          ) : (
            <div className="divide-y divide-(--border)" role="list">
              {items.map((b, i) => (
                <div
                  key={b.id}
                  role="listitem"
                  style={{
                    animation: `reveal-rise 0.32s cubic-bezier(0.16,1,0.3,1) ${Math.min(i * 0.03, 0.4)}s both`,
                    ['--reveal-distance' as string]: '8px',
                  }}
                >
                  <Row b={b} onRemove={() => remove(b.id)} onSaveNote={(n) => saveNote(b.id, n)} />
                </div>
              ))}
            </div>
          )}

          {list.hasNextPage && (
            <div className="p-4 text-center">
              <button
                onClick={() => list.fetchNextPage()}
                disabled={list.isFetchingNextPage}
                className="text-xs text-(--text-3) hover:text-foreground transition-colors"
              >
                {list.isFetchingNextPage ? t('common.loading') : t('common.seeMore')}
              </button>
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}

function Row({ b, onRemove, onSaveNote }: {
  b: BookmarkItem
  onRemove: () => void
  onSaveNote: (n: string) => void
}) {
  const { t, i18n } = useTranslation()
  const [editing, setEditing] = useState(false)
  const [draft,   setDraft]   = useState(b.note ?? '')
  const snap = b.snapshot
  const author = snap?.authorName ?? t('bookmarks.unavailable')
  const avatar = snap?.authorAvatar ? resolveApiUrl(snap.authorAvatar) : null

  return (
    <div className="p-4 hover:bg-(--raised)/40 transition-colors group">
      <div className="flex gap-3">
        {avatar ? (
          <img src={avatar} alt={author} loading="lazy" decoding="async" className="size-8 rounded-full object-cover shrink-0" />
        ) : (
          <div className="size-8 rounded-full bg-card border border-(--border) shrink-0" />
        )}

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-xs font-medium text-foreground truncate">{author}</span>
            <span className="text-[11px] text-(--text-3)">·</span>
            <span className="text-[11px] text-(--text-3)">
              {formatDistanceToNow(new Date(b.createdAt), { addSuffix: true, locale: i18n.language === 'pt' ? ptBR : enUS })}
            </span>
            <button
              onClick={onRemove}
              className="ml-auto opacity-0 group-hover:opacity-100 text-(--text-3) hover:text-(--danger) transition-opacity"
              title={t('bookmarks.remove')}
            >
              <Trash2 className="size-3.5" />
            </button>
          </div>

          <p className="text-sm text-(--text-2) m-0 line-clamp-3 leading-relaxed">
            {snap?.content ?? <span className="italic text-(--text-3)">{t('bookmarks.removed')}</span>}
          </p>

          {editing ? (
            <div className="mt-2 flex items-center gap-1">
              <Input
                autoFocus
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') { onSaveNote(draft); setEditing(false) }
                  if (e.key === 'Escape') { setDraft(b.note ?? ''); setEditing(false) }
                }}
                placeholder={t('bookmarks.notePlaceholder')}
                className="flex-1 h-7 text-xs"
              />
              <button onClick={() => { onSaveNote(draft); setEditing(false) }} className="size-7 grid place-items-center text-(--accent)" title={t('common.save')}>
                <Check className="size-3.5" />
              </button>
              <button onClick={() => { setDraft(b.note ?? ''); setEditing(false) }} className="size-7 grid place-items-center text-(--text-3)" title={t('common.cancel')}>
                <X className="size-3.5" />
              </button>
            </div>
          ) : b.note ? (
            <button
              onClick={() => setEditing(true)}
              className="mt-2 w-full text-left text-xs text-(--accent) italic border-l-2 border-(--accent) pl-2 py-0.5 hover:bg-(--accent)/5 transition-colors"
            >
              {b.note}
            </button>
          ) : (
            <button
              onClick={() => setEditing(true)}
              className="mt-2 inline-flex items-center gap-1.5 text-[11px] text-(--text-3) hover:text-(--accent) transition-colors"
            >
              <NotebookPen className="size-3" /> {t('bookmarks.addNote')}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
