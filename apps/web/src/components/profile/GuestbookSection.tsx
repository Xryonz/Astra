/**
 * GuestbookSection — mural editorial dentro do ProfileCard.
 *
 * Lista notas que outros amigos deixaram. Owner pode pin/delete.
 * Author pode editar/delete sua própria. Não-amigos veem read-only.
 *
 * Aparece como lista compacta com timestamp; click no pin do owner
 * destaca a nota. Input para nova nota só aparece pra amigos.
 */
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { formatDistanceToNow } from 'date-fns'
import { ptBR } from 'date-fns/locale/pt-BR'
import { Pin, X, Send } from 'lucide-react'
import { api, resolveApiUrl } from '@/lib/api'
import { toast } from '@/components/ui/sonner'
import { BioMarkdown } from '@/lib/bioMarkdown'
import { useAuthStore } from '@/store/authStore'
import type { ProfileNote } from '@umbra/types'

interface Props {
  userId:    string
  accentColor: string
  isSelf:    boolean
}

export function GuestbookSection({ userId, accentColor, isSelf }: Props) {
  const me = useAuthStore((s) => s.user?.id)
  const qc = useQueryClient()
  const [draft, setDraft] = useState('')

  const notesQ = useQuery<ProfileNote[]>({
    queryKey: ['profile-notes', userId],
    queryFn:  async () => (await api.get(`/api/profile/${userId}/notes`)).data.data,
    staleTime: 20_000,
  })

  const post = useMutation({
    mutationFn: async () => {
      const content = draft.trim()
      if (!content) return null
      return (await api.post(`/api/profile/${userId}/notes`, { content })).data.data
    },
    onSuccess: () => {
      setDraft('')
      qc.invalidateQueries({ queryKey: ['profile-notes', userId] })
      toast.success('Nota enviada')
    },
    onError: (e: any) => {
      toast.error(e?.response?.data?.error ?? 'Erro ao enviar nota')
    },
  })

  const remove = useMutation({
    mutationFn: async (noteId: string) =>
      (await api.delete(`/api/profile/notes/${noteId}`)).data.data,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['profile-notes', userId] }),
  })

  const pin = useMutation({
    mutationFn: async (noteId: string) =>
      (await api.patch(`/api/profile/notes/${noteId}/pin`)).data.data,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['profile-notes', userId] }),
  })

  const notes = notesQ.data ?? []
  // Não exibe seção quando vazia + você é não-amigo → mantém perfil limpo
  if (notes.length === 0 && isSelf) return null

  return (
    <div>
      <div className="flex items-center gap-2 mb-2">
        <span className="ed-label">— Mural</span>
        <span className="text-[10px] font-mono text-(--text-3)">{notes.length}</span>
      </div>

      {/* Input pra novas notas (só não-self) */}
      {!isSelf && (
        <form
          onSubmit={(e) => { e.preventDefault(); post.mutate() }}
          className="flex items-stretch gap-2 mb-3"
        >
          <input
            type="text"
            value={draft}
            onChange={(e) => setDraft(e.target.value.slice(0, 120))}
            placeholder="Deixe uma nota… (até 120 chars)"
            maxLength={120}
            className="flex-1 px-3 py-2 text-sm rounded-lg border border-(--border-mid) bg-(--raised)/40 text-(--text-1) placeholder:text-(--text-3) focus:outline-none focus:border-(--accent) transition-colors"
          />
          <button
            type="submit"
            disabled={!draft.trim() || post.isPending}
            className="px-3 rounded-lg bg-(--accent) text-(--text-inv) text-xs font-medium uppercase tracking-wider transition-all hover:scale-105 disabled:opacity-50 disabled:pointer-events-none cursor-pointer"
            aria-label="Enviar nota"
          >
            <Send className="size-3.5" />
          </button>
        </form>
      )}

      {notes.length === 0 ? (
        <p className="text-xs text-(--text-3) italic m-0">Mural vazio. Seja o primeiro a deixar uma nota.</p>
      ) : (
        <ul className="flex flex-col gap-2 m-0 p-0 list-none">
          {notes.slice(0, 10).map((n) => {
            const isMine    = n.author.id === me
            const canDelete = isMine || isSelf
            const canPin    = isSelf
            return (
              <li
                key={n.id}
                className="group relative rounded-lg p-3 border bg-(--raised)/40"
                style={{
                  borderColor: n.pinned
                    ? 'color-mix(in srgb, ' + accentColor + ' 60%, transparent)'
                    : 'var(--border)',
                  background: n.pinned
                    ? 'color-mix(in srgb, ' + accentColor + ' 8%, var(--raised))'
                    : undefined,
                }}
              >
                <div className="flex items-start gap-2.5">
                  {n.author.avatarUrl ? (
                    <img
                      src={resolveApiUrl(n.author.avatarUrl)}
                      alt={n.author.displayName}
                      loading="lazy"
                      className="size-7 rounded-full object-cover shrink-0"
                    />
                  ) : (
                    <div className="size-7 rounded-full bg-(--popover) grid place-items-center text-[10px] font-mono text-(--text-3) shrink-0">
                      {n.author.displayName.slice(0,1).toUpperCase()}
                    </div>
                  )}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5 mb-1">
                      <span className="text-xs font-medium text-(--text-1)">{n.author.displayName}</span>
                      <span className="text-[10px] text-(--text-3) font-mono">·</span>
                      <span className="text-[10px] text-(--text-3)">
                        {formatDistanceToNow(new Date(n.createdAt), { addSuffix: true, locale: ptBR })}
                      </span>
                      {n.pinned && (
                        <Pin className="size-2.5 text-(--accent) ml-auto" aria-label="Nota fixada" />
                      )}
                    </div>
                    <p className="text-sm text-(--text-2) leading-snug m-0 wrap-break-word">
                      <BioMarkdown text={n.content} />
                    </p>
                  </div>
                </div>
                {(canPin || canDelete) && (
                  <div className="absolute top-1 right-1 flex gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                    {canPin && (
                      <button
                        type="button"
                        onClick={() => pin.mutate(n.id)}
                        className="size-6 grid place-items-center rounded-md text-(--text-3) hover:text-(--accent) hover:bg-(--accent)/10 cursor-pointer"
                        aria-label={n.pinned ? 'Despinar' : 'Pinar'}
                        title={n.pinned ? 'Despinar' : 'Pinar'}
                      >
                        <Pin className="size-3" />
                      </button>
                    )}
                    {canDelete && (
                      <button
                        type="button"
                        onClick={() => remove.mutate(n.id)}
                        className="size-6 grid place-items-center rounded-md text-(--text-3) hover:text-(--danger) hover:bg-(--danger)/10 cursor-pointer"
                        aria-label="Remover nota"
                        title="Remover"
                      >
                        <X className="size-3" />
                      </button>
                    )}
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
