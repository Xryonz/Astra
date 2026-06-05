/**
 * WishingStar — input pra sugerir o que mudar no Astra + lista global pública.
 *
 *  - Input texto puro (sem markdown render — preserva quebras)
 *  - 4-500 chars, rate-limit server-side (3 a cada 10min)
 *  - Lista paginada (cursor) com avatar do autor + tempo relativo
 *  - Estética: chip ✦ no header, hairline rows estilo editorial
 */
import { useState, useRef, FormEvent } from 'react'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Sparkles, Send } from 'lucide-react'
import { api, resolveApiUrl } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { toast } from '@/components/ui/sonner'
import { Reveal } from '@/components/anim/Reveal'
import { SectionHeader } from './_shared'

interface WishItem {
  id:        string
  content:   string
  createdAt: string
  author: {
    id:          string
    username:    string
    displayName: string
    avatarUrl:   string | null
  }
}
interface WishPage { items: WishItem[]; nextCursor: string | null }

const MIN = 4
const MAX = 500

function relTime(iso: string): string {
  const dt   = new Date(iso).getTime()
  const diff = Date.now() - dt
  if (diff < 60_000)       return 'agora'
  if (diff < 3_600_000)    return `${Math.floor(diff / 60_000)}m`
  if (diff < 86_400_000)   return `${Math.floor(diff / 3_600_000)}h`
  if (diff < 2_592_000_000) return `${Math.floor(diff / 86_400_000)}d`
  return new Date(iso).toLocaleDateString('pt-BR')
}

export default function WishingStarSection() {
  const [content, setContent] = useState('')
  const [error,   setError]   = useState<string | null>(null)
  const taRef = useRef<HTMLTextAreaElement>(null)
  const qc    = useQueryClient()

  const {
    data, hasNextPage, fetchNextPage, isFetchingNextPage, isLoading,
  } = useInfiniteQuery<WishPage, Error>({
    queryKey: ['wishes'],
    queryFn: async ({ pageParam }) => {
      const params = new URLSearchParams({ limit: '20' })
      if (pageParam) params.set('cursor', pageParam as string)
      const res = await api.get(`/api/wishes?${params}`)
      return res.data.data as WishPage
    },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    staleTime: 30_000,
  })

  const create = useMutation({
    mutationFn: async (text: string) => (await api.post('/api/wishes', { content: text })).data.data as Omit<WishItem, 'author'>,
    onSuccess: () => {
      setContent('')
      setError(null)
      toast.success('✦ Sua estrela foi pendurada no céu.')
      qc.invalidateQueries({ queryKey: ['wishes'] })
    },
    onError: (e: any) => {
      const msg = e?.response?.data?.error ?? 'Erro ao enviar sugestão'
      setError(msg)
      toast.error(msg)
    },
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const trimmed = content.trim()
    if (trimmed.length < MIN) { setError(`Mínimo ${MIN} caracteres.`); return }
    if (trimmed.length > MAX) { setError(`Máximo ${MAX} caracteres.`); return }
    create.mutate(trimmed)
  }

  const items = data?.pages.flatMap((p) => p.items) ?? []
  const remaining = MAX - content.length

  return (
    <div>
      <SectionHeader
        title="Wishing Star"
        description="Sugira o que mudar na Astra. Cada pedido é uma estrela visível pra todo mundo — leia os outros, comente os seus."
      />

      {/* ───── Form ───── */}
      <form onSubmit={submit} className="mb-10">
        <div className="relative">
          <textarea
            ref={taRef}
            value={content}
            onChange={(e) => { setContent(e.target.value); setError(null) }}
            placeholder="Penso que seria legal se…"
            maxLength={MAX}
            rows={3}
            className="w-full resize-y min-h-24 px-4 py-3 bg-(--raised)/60 border border-(--border) rounded-xl text-sm text-(--text-1) placeholder:text-(--text-3) focus:outline-none focus:border-(--accent) focus:bg-(--raised) transition-colors font-(family-name:--font-body)"
          />
          <span
            className={`absolute bottom-2 right-3 text-[10px] font-mono ${
              remaining < 0 ? 'text-(--danger)' : remaining < 60 ? 'text-(--accent)' : 'text-(--text-3)'
            }`}
            aria-live="polite"
          >
            {content.length}/{MAX}
          </span>
        </div>

        {error && <p className="mt-2 text-xs text-(--danger) m-0 italic">{error}</p>}

        <div className="mt-3 flex items-center justify-between gap-3 flex-wrap">
          <p className="text-[11px] text-(--text-3) m-0 italic max-w-[44ch]">
            Só texto. Markdown vira texto puro. Lista pública — escolha bem as palavras.
          </p>
          <Button
            type="submit"
            disabled={create.isPending || content.trim().length < MIN}
            className="gap-2"
          >
            <Send className="size-3.5" />
            {create.isPending ? 'Pendurando…' : 'Pendurar estrela'}
          </Button>
        </div>
      </form>

      {/* ───── Lista global ───── */}
      <header className="mb-4 flex items-baseline gap-2">
        <Sparkles className="size-3.5 text-(--accent)" />
        <h3 className="text-sm m-0 font-medium font-(family-name:--font-display)">No céu agora</h3>
        <span className="text-[10px] font-mono text-(--text-3)">— globais</span>
      </header>

      {isLoading ? (
        <p className="text-xs text-(--text-3) italic m-0">Lendo o céu…</p>
      ) : items.length === 0 ? (
        <p className="text-xs text-(--text-3) italic m-0">Nenhuma estrela ainda. Seja a primeira.</p>
      ) : (
        <ul className="border border-(--border) divide-y divide-(--border) rounded-xl overflow-hidden">
          {items.map((w, i) => (
            <li key={w.id} className="px-4 py-3 hover:bg-(--raised)/40 transition-colors">
              <Reveal delay={Math.min(i, 8) * 0.03}>
                <div className="flex items-start gap-3">
                  <Avatar className="size-8 shrink-0 border border-(--border-mid)">
                    {w.author.avatarUrl
                      ? <AvatarImage src={resolveApiUrl(w.author.avatarUrl)} alt={w.author.displayName} />
                      : <AvatarFallback className="text-[11px]">
                          {w.author.displayName.slice(0, 1).toUpperCase()}
                        </AvatarFallback>}
                  </Avatar>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-baseline gap-2 flex-wrap">
                      <span className="text-sm font-medium font-(family-name:--font-display) text-foreground truncate">
                        {w.author.displayName}
                      </span>
                      <span className="text-[10px] font-mono text-(--text-3) truncate">@{w.author.username}</span>
                      <span className="text-[10px] font-mono text-(--text-3) ml-auto shrink-0">{relTime(w.createdAt)}</span>
                    </div>
                    <p className="mt-1 text-sm text-(--text-2) m-0 whitespace-pre-wrap break-words leading-relaxed">
                      {w.content}
                    </p>
                  </div>
                </div>
              </Reveal>
            </li>
          ))}
        </ul>
      )}

      {hasNextPage && (
        <div className="mt-4 flex justify-center">
          <Button
            variant="ghost"
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
          >
            {isFetchingNextPage ? 'Carregando…' : 'Ver estrelas mais antigas'}
          </Button>
        </div>
      )}
    </div>
  )
}
