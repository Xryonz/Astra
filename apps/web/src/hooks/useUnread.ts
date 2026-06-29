import { useEffect, useState, useCallback, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { getSocket } from '@/lib/socket'

export function useUnread() {

  const { data: reads = {} } = useQuery<Record<string, string>>({
    queryKey: ['reads', 'channels'],
    queryFn:  async () => (await api.get('/api/reads/channels')).data.data,
    staleTime: 30_000,
  })

  const [readsOverride, setReadsOverride] = useState<Record<string, string>>({})
  const [activity, setActivity] = useState<Record<string, string>>({})

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
