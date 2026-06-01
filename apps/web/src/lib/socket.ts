import { io, Socket } from 'socket.io-client'
import { useAuthStore } from '@/store/authStore'

let socket: Socket | null = null
let heartbeatInterval: ReturnType<typeof setInterval> | null = null

export function getSocket(): Socket {
  if (!socket) {
    throw new Error('Socket não inicializado. Chame connectSocket() primeiro.')
  }
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
    console.log('[Socket] Conectado:', socket?.id)

    // Heartbeat: mantém a presença no Redis (TTL de 60s, renova a cada 30s)
    heartbeatInterval = setInterval(() => {
      socket?.emit('heartbeat')
    }, 30_000)
  })

  socket.on('disconnect', (reason) => {
    console.log('[Socket] Desconectado:', reason)
    if (heartbeatInterval) clearInterval(heartbeatInterval)
  })

  socket.on('connect_error', (err) => {
    console.error('[Socket] Erro de conexão:', err.message)
  })

  return socket
}

export function disconnectSocket(): void {
  if (heartbeatInterval) clearInterval(heartbeatInterval)
  socket?.disconnect()
  socket = null
}
