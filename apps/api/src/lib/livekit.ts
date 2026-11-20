import { RoomServiceClient, TwirpError } from 'livekit-server-sdk'
import { and, eq } from 'drizzle-orm'
import { db } from '../db'
import { channels } from '../db/schema'
import { env } from './env'
import { logger } from './logger'

export function getRoomService(): RoomServiceClient | null {
  if (!env.LIVEKIT_URL || !env.LIVEKIT_API_KEY || !env.LIVEKIT_API_SECRET) return null
  return new RoomServiceClient(env.LIVEKIT_URL, env.LIVEKIT_API_KEY, env.LIVEKIT_API_SECRET)
}

function naoEstavaNaSala(erro: unknown): boolean {
  return erro instanceof TwirpError && erro.status === 404
}

export async function tirarDasSalasDeVoz(userId: string, serverId: string): Promise<void> {
  const servico = getRoomService()
  if (!servico) return
  try {
    const salas = await db
      .select({ id: channels.id })
      .from(channels)
      .where(and(eq(channels.serverId, serverId), eq(channels.type, 'VOICE')))
    const corte = BigInt(Math.floor(Date.now() / 1000))
    await Promise.all(
      salas.map((sala) =>
        servico.removeParticipant(`channel:${sala.id}`, userId, { revokeTokenTs: corte }).catch((erro: unknown) => {
          if (!naoEstavaNaSala(erro))
            logger.error('LiveKit', `Falha ao tirar da sala channel:${sala.id}`, erro as Error)
        }),
      ),
    )
  } catch (e) {
    logger.error('LiveKit', 'Falha ao tirar das salas de voz', e as Error)
  }
}
