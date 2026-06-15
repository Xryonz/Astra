import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { X, History } from 'lucide-react'
import { api } from '@/lib/api'

interface EditEntry {
  id:       string
  content:  string
  editedAt: string
}

interface EditHistoryPopoverProps {
  channelId:      string
  messageId:      string
  currentContent: string
  onClose:        () => void
}

/**
 * Popover que mostra histórico de edições da mensagem. Clica no marcador
 * "(editada)" pra abrir. Mostra versões em ordem cronológica reversa
 * (mais recente primeiro) + a versão atual no topo como referência.
 */
export default function EditHistoryPopover({
  channelId, messageId, currentContent, onClose,
}: EditHistoryPopoverProps) {
  const { t, i18n } = useTranslation()
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose()
    }
    const onEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    const t = setTimeout(() => {
      document.addEventListener('mousedown', handler)
      document.addEventListener('keydown', onEsc)
    }, 50)
    return () => {
      clearTimeout(t)
      document.removeEventListener('mousedown', handler)
      document.removeEventListener('keydown', onEsc)
    }
  }, [onClose])

  const { data: edits = [], isLoading } = useQuery<EditEntry[]>({
    queryKey: ['edits', messageId],
    queryFn:  async () => (await api.get(`/api/channels/${channelId}/messages/${messageId}/edits`)).data.data,
    staleTime: 10_000,
  })

  return (
    <div
      ref={ref}
      role="dialog"
      aria-label={t('chat.editHistory.aria')}
      className="fixed sm:absolute z-40 right-2 sm:right-0 top-16 sm:top-auto sm:mt-1 w-[calc(100vw-1rem)] sm:w-80 max-h-96 flex flex-col bg-(--overlay) border border-(--border-mid) shadow-2xl animate-in fade-in-0 slide-in-from-top-1 duration-200"
    >
      <header className="shrink-0 px-3 py-2 border-b border-(--border) flex items-center gap-2">
        <History className="size-3.5 text-(--accent)" />
        <p className="m-0 text-sm font-medium flex-1" style={{ fontFamily: 'var(--font-display)' }}>
          {t('chat.editHistory.title')}
        </p>
        <button
          onClick={onClose}
          className="size-6 flex items-center justify-center text-(--text-3) hover:text-(--accent) cursor-pointer"
          aria-label={t('common.close')}
        >
          <X className="size-3.5" />
        </button>
      </header>

      <div className="flex-1 overflow-y-auto px-3 py-2.5 flex flex-col gap-2.5">
        {/* Versão atual sempre no topo */}
        <article className="border-l-2 border-(--accent) pl-2.5 py-1">
          <p className="m-0 text-[10px] uppercase tracking-wider text-(--accent) font-medium mb-0.5">
            {t('chat.editHistory.current')}
          </p>
          <p className="m-0 text-sm text-foreground wrap-break-word leading-snug">{currentContent}</p>
        </article>

        {isLoading && (
          <div className="flex justify-center py-3">
            <div className="size-4 border-2 border-(--border-mid) border-t-(--accent) rounded-full animate-spin" />
          </div>
        )}

        {!isLoading && edits.length === 0 && (
          <p className="text-xs text-(--text-3) italic m-0 px-1">{t('chat.editHistory.empty')}</p>
        )}

        {edits.map((e) => (
          <article key={e.id} className="border-l-2 border-(--border-mid) pl-2.5 py-1">
            <p className="m-0 text-[10px] font-mono text-(--text-3) mb-0.5">
              {new Date(e.editedAt).toLocaleString(i18n.language === 'pt' ? 'pt-BR' : 'en-US', {
                day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit',
              })}
            </p>
            <p className="m-0 text-sm text-(--text-2) wrap-break-word leading-snug">{e.content}</p>
          </article>
        ))}
      </div>
    </div>
  )
}
