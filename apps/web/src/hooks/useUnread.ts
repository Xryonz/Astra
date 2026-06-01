import { useEffect, useState, useCallback, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { getSocket } from '@/lib/socket'

/**
 * Hook único pra unread state:
 *  - lastReadByChannel: pega do GET /api/reads/channels (1x na montagem)
 *  - lastMessageAtByChannel: atualizado via socket 'channel_activity' + mark-read local
 *  - hasUnread(channelId, lastMessageAt): compara as duas datas
 *
 * Chama `markRead(channelId)` quando user entra num canal — POST /channels/:id/read
 * + atualiza estado local optimisticamente.
 */
export function useUnread() {
  // GET /api/reads/channels — { [channelId]: lastReadAt ISO }
  const { data: reads = {} } = useQuery<Record<string, string>>({
    queryKey: ['reads', 'channels'],
    queryFn:  async () => (await api.get('/api/reads/channels')).data.data,
    staleTime: 30_000,
  })

  // Mapa em memória atualizado via socket + mark-read local
  const [readsOverride, setReadsOverride] = useState<Record<string, string>>({})
  const [activity, setActivity] = useState<Record<string, string>>({})

  // Atalho que mescla reads do server com overrides locais (locais ganham)
  const effectiveReads = { ...reads, ...readsOverride }

  useEffect(() => {
    let socket: ReturnType<typeof getSocket>
    try { socket = getSocket() } catch { return }

    const onActivity = (p: { channelId: string; lastMessageAt: string }) => {
      setActivity((prev) => ({ ...prev, [p.channelId]: p.lastMessageAt }))
    }
    socket.on('channel_activity', onActivity)
    return () => { socket.off('channel_activity', onActivity) }
  }, [])

  const markRead = useCallback(async (channelId: string) => {
    const now = new Date().toISOString()
    setReadsOverride((prev) => ({ ...prev, [channelId]: now }))
    try { await api.post(`/api/channels/${channelId}/read`) } catch {}
  }, [])

  /**
   * Tem unread? Considera o "mais recente" entre lastMessageAt (passado pelo
   * caller, vindo da query inicial de channels) e o registrado por socket.
   */
  const hasUnread = useCallback((channelId: string, lastMessageAt: string | null | undefined): boolean => {
    if (!lastMessageAt && !activity[channelId]) return false
    const lastMsg = Math.max(
      lastMessageAt ? new Date(lastMessageAt).getTime() : 0,
      activity[channelId] ? new Date(activity[channelId]).getTime() : 0,
    )
    if (lastMsg === 0) return false
    const lastRead = effectiveReads[channelId] ? new Date(effectiveReads[channelId]).getTime() : 0
    return lastMsg > lastRead
  }, [activity, effectiveReads])

  return { hasUnread, markRead }
}

/**
 * Hook pra DM read receipts. Retorna lastReadByOther (timestamp ISO) por
 * conversationId. Atualiza via socket 'dm_read' do outro lado.
 */
export function useDMReads(): {
  myReads:    Record<string, string | null>
  otherReads: Record<string, string | null>
  markRead:   (conversationId: string) => Promise<void>
} {
  const { data: initial } = useQuery<Record<string, { mine: string | null; other: string | null }>>({
    queryKey: ['reads', 'dm'],
    queryFn:  async () => (await api.get('/api/reads/dm')).data.data,
    staleTime: 30_000,
  })

  const [myReads,    setMyReads]    = useState<Record<string, string | null>>({})
  const [otherReads, setOtherReads] = useState<Record<string, string | null>>({})

  // Sync inicial quando query carrega
  const seededRef = useRef(false)
  useEffect(() => {
    if (!initial || seededRef.current) return
    seededRef.current = true
    const my: Record<string, string | null> = {}
    const ot: Record<string, string | null> = {}
    for (const [convId, r] of Object.entries(initial)) {
      my[convId] = r.mine
      ot[convId] = r.other
    }
    setMyReads(my)
    setOtherReads(ot)
  }, [initial])

  useEffect(() => {
    let socket: ReturnType<typeof getSocket>
    try { socket = getSocket() } catch { return }

    const onRead = (p: { conversationId: string; lastReadAt: string }) => {
      // 'dm_read' chega no user-room do OUTRO lado da conv (o leitor é a contraparte)
      setOtherReads((prev) => ({ ...prev, [p.conversationId]: p.lastReadAt }))
    }
    socket.on('dm_read', onRead)
    return () => { socket.off('dm_read', onRead) }
  }, [])

  const markRead = useCallback(async (conversationId: string) => {
    const now = new Date().toISOString()
    setMyReads((prev) => ({ ...prev, [conversationId]: now }))
    try { await api.post(`/api/dm/${conversationId}/read`) } catch {}
  }, [])

  return { myReads, otherReads, markRead }
}
