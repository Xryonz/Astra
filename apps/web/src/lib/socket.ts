import { io, Socket } from 'socket.io-client'
import { useAuthStore } from '@/store/authStore'

let socket: Socket | null = null
let heartbeatInterval: ReturnType<typeof setInterval> | null = null
let unsubAuth: (() => void) | null = null

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
    // Heartbeat: presença no Redis (TTL 60s, renova a cada 30s).
    // Para anterior se reconnect disparou 'connect' de novo — antes acumulava
    // N intervals (memory leak crescente em rede instável).
    stopHeartbeat()
    heartbeatInterval = setInterval(() => socket?.emit('heartbeat'), 30_000)
  })

  socket.on('disconnect', (reason) => {
    if (import.meta.env.DEV) console.log('[Socket] Desconectado:', reason)
    stopHeartbeat()
  })

  socket.on('connect_error', (err) => {
    console.error('[Socket] Erro de conexão:', err.message)
  })

  // Token rotaciona a cada /refresh (15min). Atualiza auth.token in-place
  // pro próximo reconnect — antes o socket continuava com token velho e
  // entrava em loop de connect_error quando expirava.
  unsubAuth?.()
  unsubAuth = useAuthStore.subscribe((s, prev) => {
    if (s.accessToken && s.accessToken !== prev.accessToken && socket) {
      ;(socket.auth as any) = { token: s.accessToken }
    }
  })

  return socket
}

export function disconnectSocket(): void {
  stopHeartbeat()
  unsubAuth?.(); unsubAuth = null
  socket?.disconnect()
  socket = null
}
