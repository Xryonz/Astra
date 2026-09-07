import { eq } from 'drizzle-orm'
import { db } from '../db'
import { channels } from '../db/schema'
import { redis } from './redis'

export interface FichaDoCanal {
  serverId: string
  isPrivate: boolean
}

const VALIDADE_SEGUNDOS = 60
const SEM_CANAL = '-'

const chaveDaFicha = (channelId: string) => `canal:ficha:${channelId}`

function fichaDoTexto(cru: string): FichaDoCanal | null {
  try {
    const lida = JSON.parse(cru) as Partial<FichaDoCanal>
    if (typeof lida?.serverId === 'string' && lida.serverId) {
      return { serverId: lida.serverId, isPrivate: !!lida.isPrivate }
    }
  } catch {}
  return null
}

export async function fichaDoCanal(channelId: string): Promise<FichaDoCanal | null> {
  if (typeof channelId !== 'string' || !channelId) return null

  const guardada = await redis.get(chaveDaFicha(channelId)).catch(() => null)
  if (guardada === SEM_CANAL) return null
  if (guardada) {
    const lida = fichaDoTexto(guardada)
    if (lida) return lida
  }

  const [linha] = await db
    .select({ serverId: channels.serverId, isPrivate: channels.isPrivate })
    .from(channels)
    .where(eq(channels.id, channelId))
    .limit(1)

  const ficha: FichaDoCanal | null = linha?.serverId
    ? { serverId: linha.serverId, isPrivate: !!linha.isPrivate }
    : null

  await redis
    .setex(chaveDaFicha(channelId), VALIDADE_SEGUNDOS, ficha ? JSON.stringify(ficha) : SEM_CANAL)
    .catch(() => {})

  return ficha
}

export async function esquecerFichaDoCanal(channelId: string): Promise<void> {
  if (typeof channelId !== 'string' || !channelId) return
  await redis.del(chaveDaFicha(channelId)).catch(() => {})
}
