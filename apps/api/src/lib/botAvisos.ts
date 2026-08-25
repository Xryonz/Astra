import type { Server as SocketServer } from 'socket.io'
import { and, asc, eq } from 'drizzle-orm'
import { db } from '../db'
import { channels, messages, serverMembers, servers, users } from '../db/schema'
import { redis } from './redis'
import { logger } from './logger'
import { botNaOrbita } from './botScope'
import { getBotId, personaDoDia, sincronizaPersona, type Persona } from './bot'

let io: SocketServer | null = null
export function ligarAvisosDaBot(server: SocketServer) { io = server }

async function canalDeAvisos(serverId: string): Promise<string | null> {
  const [escolha] = await db.select({ id: servers.botNoticeChannelId })
    .from(servers).where(eq(servers.id, serverId)).limit(1)

  if (escolha?.id) {
    const [c] = await db.select({ id: channels.id })
      .from(channels)
      .where(and(
        eq(channels.id, escolha.id),
        eq(channels.serverId, serverId),
        eq(channels.type, 'TEXT'),
        eq(channels.isPrivate, false),
      ))
      .limit(1)
    if (c && (await botNaOrbita(c.id)).fala) return c.id
  }

  const lista = await db.select({ id: channels.id })
    .from(channels)
    .where(and(eq(channels.serverId, serverId), eq(channels.type, 'TEXT'), eq(channels.isPrivate, false)))
    .orderBy(asc(channels.position))

  for (const c of lista) {
    const regra = await botNaOrbita(c.id)
    if (regra.fala) return c.id
  }
  return null
}

async function primeiraVez(chave: string, ttlSegundos: number): Promise<boolean> {
  try {
    return (await redis.set(`bot:disse:${chave}`, '1', 'EX', ttlSegundos, 'NX')) === 'OK'
  } catch {
    return false
  }
}

async function falar(channelId: string, texto: string, persona: Persona, botId: string): Promise<void> {
  const [linha] = await db.insert(messages)
    .values({ content: texto, channelId, authorId: botId })
    .returning()

  io?.to(`channel:${channelId}`).emit('new_message', {
    ...linha,
    author: {
      id: botId, username: 'astra_bot',
      displayName: persona.nome, avatarUrl: persona.avatar,
    },
    reactions: [], mentions: [], attachments: [], replyTo: null,
  })
}

const UM_ANO = 365 * 24 * 60 * 60

export async function saudarNovoMembro(serverId: string, userId: string): Promise<void> {
  try {
    const botId = await getBotId()
    if (!botId || userId === botId) return
    if (!(await primeiraVez(`entrou:${serverId}:${userId}`, UM_ANO))) return

    const channelId = await canalDeAvisos(serverId)
    if (!channelId) return

    const [pessoa] = await db.select({ username: users.username, displayName: users.displayName })
      .from(users).where(eq(users.id, userId)).limit(1)
    if (!pessoa) return

    const persona = await sincronizaPersona(botId)
    const nome = pessoa.displayName || pessoa.username
    await falar(channelId, `${nome} chegou na constelação. Bem-vindo — \`${persona.prefixo} ajuda\` mostra o que eu faço.`, persona, botId)
  } catch (e) {
    logger.error('Bot', `saudacao falhou: ${(e as Error).message}`)
  }
}

const MARCOS = new Set([5, 10, 25, 50, 75, 100])

export async function comemorarNivel(userId: string, channelId: string, nivel: number): Promise<void> {
  try {
    if (!MARCOS.has(nivel)) return
    const botId = await getBotId()
    if (!botId || userId === botId) return
    if (!(await primeiraVez(`nivel:${userId}:${nivel}`, UM_ANO))) return

    const regra = await botNaOrbita(channelId)
    if (!regra.fala) return

    const [pessoa] = await db.select({ username: users.username, displayName: users.displayName })
      .from(users).where(eq(users.id, userId)).limit(1)
    if (!pessoa) return

    const persona = await sincronizaPersona(botId)
    const nome = pessoa.displayName || pessoa.username
    await falar(channelId, `${nome} chegou ao nível ${nivel}.`, persona, botId)
  } catch (e) {
    logger.error('Bot', `comemoracao falhou: ${(e as Error).message}`)
  }
}

const INTERVALO_CHECAGEM_MS = 5 * 60 * 1000

export function chaveDoTurno(agora: Date): string {
  const p = personaDoDia(agora)
  const fmt = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit',
  })
  const dia = 24 * 60 * 60 * 1000
  let inicio = new Date(agora)
  for (let i = 0; i < 7; i++) {
    const anterior = new Date(inicio.getTime() - dia)
    if (personaDoDia(anterior).chave !== p.chave) break
    inicio = anterior
  }
  return `${p.chave}:${fmt.format(inicio)}`
}

const DESPEDIDAS: Record<Persona['chave'], string> = {
  sparkle: 'Fecho por aqui. Boa virada — a Sparxie assume agora.',
  sparxie: 'Fim do meu turno. Semana nova é com a Sparkle.',
}
const CHEGADAS: Record<Persona['chave'], string> = {
  sparkle: 'Voltei. Semana começando — `/sparkle ajuda` se precisar de mim.',
  sparxie: 'Cheguei. O fim de semana é meu: `/sparxie festa` se travar no que fazer.',
}

export async function verificarTrocaDeTurno(): Promise<void> {
  try {
    const agora = new Date()
    const entra = personaDoDia(agora)
    if (!(await primeiraVez(`turno:${chaveDoTurno(agora)}`, 8 * 24 * 60 * 60))) return

    if (await primeiraVez('turno:estreia', UM_ANO)) {
      logger.info('Bot', `avisos de turno ligados — turno atual (${entra.nome}) reservado sem anuncio`)
      return
    }

    const botId = await getBotId()
    if (!botId) return
    const sai = entra.chave === 'sparkle' ? 'sparxie' : 'sparkle'

    const constelacoes = await db.select({ serverId: serverMembers.serverId })
      .from(serverMembers).where(eq(serverMembers.userId, botId))
    if (constelacoes.length === 0) return

    const persona = await sincronizaPersona(botId)

    for (const c of constelacoes) {
      const channelId = await canalDeAvisos(c.serverId)
      if (!channelId) continue
      await falar(channelId, DESPEDIDAS[sai], persona, botId)
      await falar(channelId, CHEGADAS[entra.chave], persona, botId)
    }
    logger.info('Bot', `troca de turno anunciada: entra ${entra.nome}`)
  } catch (e) {
    logger.error('Bot', `troca de turno falhou: ${(e as Error).message}`)
  }
}

export function agendarTrocaDeTurno(): void {
  void verificarTrocaDeTurno()
  const t = setInterval(() => { void verificarTrocaDeTurno() }, INTERVALO_CHECAGEM_MS)
  t.unref?.()
}
