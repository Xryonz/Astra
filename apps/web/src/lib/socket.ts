import { io, Socket } from 'socket.io-client'
import { useAuthStore } from '@/store/authStore'

let socket: Socket | null = null
let heartbeatInterval: ReturnType<typeof setInterval> | null = null
let unsubAuth: (() => void) | null = null

const joinedChannels = new Set<string>()
const joinedDMs      = new Set<string>()

export function trackJoin(channelId: string) { joinedChannels.add(channelId) }
export function trackLeave(channelId: string) { joinedChannels.delete(channelId) }
export function trackJoinDM(conversationId: string) { joinedDMs.add(conversationId) }
export function trackLeaveDM(conversationId: string) { joinedDMs.delete(conversationId) }

export interface FastSendResult { ok: boolean; error?: string; code?: string; msg?: unknown }

export function fastSendText(
  channelId: string, content: string, clientNonce: string, timeoutMs = 5000,
): Promise<FastSendResult> {
  return new Promise((resolve) => {
    let s: Socket
    try { s = getSocket() } catch { return resolve({ ok: false, error: 'NO_SOCKET' }) }
    if (!s.connected)   return resolve({ ok: false, error: 'DISCONNECTED' })

    let done = false
    const timer = setTimeout(() => {
      if (done) return
      done = true
      resolve({ ok: false, error: 'TIMEOUT' })
    }, timeoutMs)

    s.emit('fast_send_text', { channelId, content, clientNonce }, (r: FastSendResult) => {
      if (done) return
      done = true
      clearTimeout(timer)
      resolve(r ?? { ok: false, error: 'NO_ACK' })
    })
  })
}

function stopHeartbeat() {
  if (heartbeatInterval) { clearInterval(heartbeatInterval); heartbeatInterval = null }
}

export function getSocket(): Socket {
  if (!socket) throw new Error('Socket não inicializado. Chame connectSocket() primeiro.')
  return socket
}

export function connectSocket(): Socket {
  if (socket?.connected) return socket

  const token = useAuthStore.getState().accessToken
  if (!token) throw new Error('Usuário não autenticado')

  socket = io(import.meta.env.VITE_API_URL, {
    auth: { token },
    transports: ['websocket', 'polling'],
    reconnectionDelay: 1000,
    reconnectionDelayMax: 5000,
  })

  socket.on('connect', () => {
    if (import.meta.env.DEV) console.log('[Socket] Conectado:', socket?.id)

    stopHeartbeat()
    heartbeatInterval = setInterval(() => socket?.emit('heartbeat'), 30_000)

    for (const channelId of joinedChannels) {
      socket?.emit('join_channel', channelId)
    }
    for (const conversationId of joinedDMs) {
      socket?.emit('join_dm', conversationId)
    }
  })

  socket.on('disconnect', (reason) => {
    if (import.meta.env.DEV) console.log('[Socket] Desconectado:', reason)
    stopHeartbeat()
  })

  socket.on('connect_error', (err) => {
    console.error('[Socket] Erro de conexão:', err.message)
  })

  unsubAuth?.()
  unsubAuth = useAuthStore.subscribe((s, prev) => {
    if (s.accessToken && s.accessToken !== prev.accessToken && socket) {
      ;(socket.auth as any) = { token: s.accessToken }
    }
  })

  return socket
}

export function reconnectSocketNow(): void {
  if (socket && !socket.connected) socket.connect()
}

export function disconnectSocket(): void {
  stopHeartbeat()
  unsubAuth?.(); unsubAuth = null
  socket?.disconnect()
  socket = null
}
