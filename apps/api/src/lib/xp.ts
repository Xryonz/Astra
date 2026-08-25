import { eq, sql } from 'drizzle-orm'
import { db } from '../db'
import { userXp } from '../db/schema'
import { redis } from './redis'
import { logger } from './logger'
import { getRoomService } from './livekit'
import { xpGanho } from './realtime'
import { TrackType } from '@livekit/protocol'

export const XP_POR_MENSAGEM     = 12
export const XP_POR_MINUTO_CALL  = 8

const ESPERA_MENSAGEM_S = 60

const TETO_DIARIO_MENSAGEM = 40 * XP_POR_MENSAGEM    
const TETO_DIARIO_CALL     = 120 * XP_POR_MINUTO_CALL 

const TETO_NIVEL = 500

export function custoDoNivel(nivel: number): number {
  return 100 + 50 * nivel + 5 * nivel * nivel
}

export interface Progresso {
  xp:           number
  nivel:        number
  noNivel:      number  
  paraOProximo: number  
  brilho:       number
}

export function progressoDoXp(xpTotal: number, brilho = 0): Progresso {
  let nivel = 0
  let restante = Math.max(0, Math.floor(xpTotal))
  while (nivel < TETO_NIVEL && restante >= custoDoNivel(nivel)) {
    restante -= custoDoNivel(nivel)
    nivel++
  }
  return { xp: xpTotal, nivel, noNivel: restante, paraOProximo: custoDoNivel(nivel), brilho }
}

const TRILHA: number[] = [
  20, 25, 30, 40, 45, 55, 60, 70, 80, 90, 100, 120,
]
const TRILHA_CAUDA = 60

export function brilhoDaTrilha(nivel: number): number {
  if (nivel <= 0) return 0
  return TRILHA[nivel - 1] ?? TRILHA_CAUDA
}

export function diaDeSaoPaulo(agora: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(agora)
}

async function dentroDoTeto(chave: string, ganho: number, teto: number): Promise<boolean> {
  const k = `xp:dia:${chave}`
  const total = await redis.incrby(k, ganho)
  if (total === ganho) await redis.expire(k, 36 * 3600)
  return total <= teto
}

export interface GanhoXp {
  ganho:         number
  origem:        'mensagem' | 'call' | 'missao'
  subiuDeNivel:  boolean
  brilhoGanho:   number
  progresso:     Progresso
}

async function creditar(userId: string, ganho: number, origem: GanhoXp['origem']): Promise<GanhoXp | null> {
  try {
    const [linha] = await db.insert(userXp)
      .values({ userId, xp: ganho })
      .onConflictDoUpdate({
        target: userXp.userId,
        set: { xp: sql`${userXp.xp} + ${ganho}`, updatedAt: new Date() },
      })
      .returning({ xp: userXp.xp, brilho: userXp.brilho })
    if (!linha) return null

    const nivelAntes  = progressoDoXp(linha.xp - ganho).nivel
    const nivelDepois = progressoDoXp(linha.xp).nivel

    let brilhoGanho = 0
    for (let n = nivelAntes + 1; n <= nivelDepois; n++) brilhoGanho += brilhoDaTrilha(n)

    let brilho = linha.brilho
    if (brilhoGanho > 0) {
      const [comBrilho] = await db.update(userXp)
        .set({ brilho: sql`${userXp.brilho} + ${brilhoGanho}` })
        .where(eq(userXp.userId, userId))
        .returning({ brilho: userXp.brilho })
      brilho = comBrilho?.brilho ?? brilho + brilhoGanho
    }

    const resultado: GanhoXp = {
      ganho,
      origem,
      subiuDeNivel: nivelDepois > nivelAntes,
      brilhoGanho,
      progresso: progressoDoXp(linha.xp, brilho),
    }
    xpGanho(userId, resultado)
    return resultado
  } catch (e) {
    logger.error('Xp', `falha ao creditar (${origem}): ${(e as Error).message}`)
    return null
  }
}

export async function xpPorMensagem(userId: string): Promise<GanhoXp | null> {
  const primeira = await redis.set(`xp:espera:${userId}`, '1', 'EX', ESPERA_MENSAGEM_S, 'NX')
  if (primeira !== 'OK') return null
  const ok = await dentroDoTeto(`msg:${userId}:${diaDeSaoPaulo()}`, XP_POR_MENSAGEM, TETO_DIARIO_MENSAGEM)
  if (!ok) return null
  return creditar(userId, XP_POR_MENSAGEM, 'mensagem')
}

export async function creditarXpDeMissao(userId: string, ganho: number): Promise<GanhoXp | null> {
  return creditar(userId, ganho, 'missao')
}

async function xpPorMinutoDeCall(userId: string): Promise<GanhoXp | null> {
  const ok = await dentroDoTeto(`call:${userId}:${diaDeSaoPaulo()}`, XP_POR_MINUTO_CALL, TETO_DIARIO_CALL)
  if (!ok) return null
  return creditar(userId, XP_POR_MINUTO_CALL, 'call')
}

function podeGanhar(tracks: { type: TrackType; muted: boolean }[]): boolean {
  return tracks.some((t) => t.type === TrackType.AUDIO && !t.muted)
}

export async function tickXpDeCall(aoMinuto?: (userIds: string[]) => void): Promise<void> {
  const svc = getRoomService()
  if (!svc) return
  try {
    const salas = await svc.listRooms()
    for (const sala of salas) {
      if (sala.numParticipants < 2) continue
      const participantes = await svc.listParticipants(sala.name)
      const vivos = participantes.filter((p) => podeGanhar(p.tracks))
      if (vivos.length < 2) continue
      for (const p of vivos) await xpPorMinutoDeCall(p.identity)
      aoMinuto?.(vivos.map((p) => p.identity))
    }
  } catch (e) {
    logger.error('Xp', `tick de call falhou: ${(e as Error).message}`)
  }
}

const INTERVALO_TICK_MS = 60_000
export function iniciarRelogioDeCall(aoMinuto?: (userIds: string[]) => void): NodeJS.Timeout {
  return setInterval(() => {
    void (async () => {
      const meu = await redis.set('xp:tick', '1', 'EX', 55, 'NX').catch(() => null)
      if (meu !== 'OK') return
      await tickXpDeCall(aoMinuto)
    })()
  }, INTERVALO_TICK_MS)
}

export async function progressoDe(userId: string): Promise<Progresso> {
  const [linha] = await db.select({ xp: userXp.xp, brilho: userXp.brilho })
    .from(userXp).where(eq(userXp.userId, userId)).limit(1)
  return progressoDoXp(linha?.xp ?? 0, linha?.brilho ?? 0)
}
