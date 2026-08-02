import { RoomServiceClient } from 'livekit-server-sdk'
import { env } from './env'

// Cliente de administracao do LiveKit, num lugar so.
//
// Nasceu privado dentro de routes/voice.ts e agora tem um segundo consumidor: o
// XP de call (lib/xp.ts) pergunta ao LiveKit quem ESTA de fato numa sala. Rota
// exportando funcao pra dentro de lib/ seria a seta apontando pro lado errado.
//
// Devolve null quando a voz nao esta configurada — quem chama decide o que fazer
// (a rota responde 503, o tick de XP simplesmente nao roda).
export function getRoomService(): RoomServiceClient | null {
  if (!env.LIVEKIT_URL || !env.LIVEKIT_API_KEY || !env.LIVEKIT_API_SECRET) return null
  return new RoomServiceClient(env.LIVEKIT_URL, env.LIVEKIT_API_KEY, env.LIVEKIT_API_SECRET)
}
