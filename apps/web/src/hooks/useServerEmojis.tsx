/**
 * Custom emojis por servidor. Mantém cache 5min + invalidate em upload/delete.
 * Renderização de `:name:` -> <img> via emojiMap (lookup O(1) por nome).
 */
import { createContext, useContext, useMemo, type ReactNode } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, resolveApiUrl } from '@/lib/api'

export interface ServerEmoji {
  id:        string
  serverId:  string
  name:      string
  url:       string
  createdBy: string
  createdAt: string
}

export function useServerEmojis(serverId?: string | null) {
  return useQuery<ServerEmoji[]>({
    queryKey: ['emojis', serverId],
    queryFn:  async () => (await api.get(`/api/servers/${serverId}/emojis`)).data.data,
    enabled:  !!serverId,
    staleTime: 5 * 60_000,
  })
}

export function useUploadEmoji(serverId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ file, name }: { file: File; name: string }) => {
      const form = new FormData()
      form.append('file', file)
      form.append('name', name)
      const r = await api.post(`/api/servers/${serverId}/emojis`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      return r.data.data as ServerEmoji
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['emojis', serverId] }) },
  })
}

export function useDeleteEmoji(serverId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (emojiId: string) => {
      await api.delete(`/api/servers/${serverId}/emojis/${emojiId}`)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['emojis', serverId] }) },
  })
}

/**
 * Substitui ocorrências de :name: por <img>. Output: array de strings/elementos
 * pronto pra React renderizar. Linhas/markdown preservados (caller cuida disso).
 */
export function renderWithEmojis(
  text: string,
  emojiMap: Map<string, ServerEmoji>,
  size = 22,
): React.ReactNode[] {
  if (!text || emojiMap.size === 0) return [text]
  const parts: React.ReactNode[] = []
  const re = /:([a-z0-9_]{2,32}):/gi
  let lastIdx = 0
  let match: RegExpExecArray | null
  let key = 0
  while ((match = re.exec(text)) !== null) {
    const [whole, name] = match
    const e = emojiMap.get(name.toLowerCase())
    if (!e) continue  // não é emoji custom do server — deixa como texto
    if (match.index > lastIdx) parts.push(text.slice(lastIdx, match.index))
    parts.push(
      <img
        key={`e${key++}`}
        src={resolveApiUrl(e.url)}
        alt={`:${e.name}:`}
        title={`:${e.name}:`}
        width={size}
        height={size}
        style={{ display: 'inline-block', verticalAlign: '-0.25em', margin: '0 1px' }}
        loading="lazy"
        decoding="async"
      />,
    )
    lastIdx = match.index + whole.length
  }
  if (lastIdx < text.length) parts.push(text.slice(lastIdx))
  return parts.length > 0 ? parts : [text]
}

/** Cache helper: Map<lower-name, ServerEmoji> derivado da query. */
export function emojiMapOf(list: ServerEmoji[] | undefined): Map<string, ServerEmoji> {
  const m = new Map<string, ServerEmoji>()
  for (const e of list ?? []) m.set(e.name.toLowerCase(), e)
  return m
}

// ─── Context pra disponibilizar o emojiMap no subtree do chat ─
// MessageList fornece, MessageItem (e quem mais precisar) consome.
// Decoupling: renderInline (função pura) recebe o Map por argumento;
// componentes consomem via useEmojiMap() em vez de subscribir a query
// individualmente — 1 query por canal em vez de N por mensagem.

const ServerEmojiCtx = createContext<Map<string, ServerEmoji>>(new Map())

export function ServerEmojiProvider({
  serverId, children,
}: { serverId?: string | null; children: ReactNode }) {
  const { data } = useServerEmojis(serverId)
  const map = useMemo(() => emojiMapOf(data), [data])
  return <ServerEmojiCtx.Provider value={map}>{children}</ServerEmojiCtx.Provider>
}

/** Map<lower-name, ServerEmoji> — vazio se fora de provider ou sem dados. */
export function useEmojiMap(): Map<string, ServerEmoji> {
  return useContext(ServerEmojiCtx)
}
