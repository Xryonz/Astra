import type { Server as SocketServer } from 'socket.io'
import { and, asc, eq, gte, inArray, isNull } from 'drizzle-orm'
import { db } from '../db'
import { channels, messages, serverMembers, servers, users } from '../db/schema'
import { redis } from './redis'
import { logger } from './logger'
import { botNaOrbita } from './botScope'
import { getBotId, personaComAjustes, personaDoDia, personaPorChave, sincronizaPersona, type Persona } from './bot'
import { identidadeParaGuardar } from './autorDaMensagem'

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

export const KIND_PASSAGEM_DE_TURNO = 'passagem'

async function falar(
  channelId: string,
  texto: string,
  persona: Persona,
  botId: string,
  kind: string | null = null,
): Promise<void> {
  const [linha] = await db.insert(messages)
    .values({
      content: texto,
      channelId,
      authorId: botId,
      kind,
      ...identidadeParaGuardar({ displayName: persona.nome, avatarUrl: persona.avatar }),
    })
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

    const quemEntra = await sincronizaPersona(botId)
    const quemSai = await personaComAjustes(personaPorChave(sai))
    const passagem = `${quemSai.nome} passou o turno para ${quemEntra.nome}`

    for (const c of constelacoes) {
      const channelId = await canalDeAvisos(c.serverId)
      if (!channelId) continue
      await falar(channelId, passagem, quemEntra, botId, KIND_PASSAGEM_DE_TURNO)
    }
    logger.info('Bot', `troca de turno anunciada: entra ${entra.nome}`)
  } catch (e) {
    logger.error('Bot', `troca de turno falhou: ${(e as Error).message}`)
  }
}

const INICIO_DAS_PERSONAS = new Date('2026-08-01T17:30:58Z')
const VIRADA_PARA_SEXTA_E_SABADO = new Date('2026-08-09T19:27:23Z')

export function chaveNaEpoca(instante: Date): Persona['chave'] {
  const dia = new Intl.DateTimeFormat('en-US', { timeZone: 'America/Sao_Paulo', weekday: 'short' }).format(instante)
  const daSparxie = instante < VIRADA_PARA_SEXTA_E_SABADO
    ? dia === 'Sat' || dia === 'Sun'
    : dia === 'Fri' || dia === 'Sat'
  return daSparxie ? 'sparxie' : 'sparkle'
}

export function quemDisse(conteudo: string, instante: Date): Persona['chave'] {
  const despediu = (Object.keys(DESPEDIDAS) as Persona['chave'][]).find((c) => DESPEDIDAS[c] === conteudo)
  return despediu ?? chaveNaEpoca(instante)
}

export async function corrigirAutoriaAntigaDaBot(): Promise<void> {
  try {
    const botId = await getBotId()
    if (!botId) return
    const antigas = await db.select({ id: messages.id, content: messages.content, createdAt: messages.createdAt })
      .from(messages)
      .where(and(
        eq(messages.authorId, botId),
        isNull(messages.authorName),
        gte(messages.createdAt, INICIO_DAS_PERSONAS),
      ))
    if (antigas.length === 0) return

    const porPersona: Record<Persona['chave'], string[]> = { sparkle: [], sparxie: [] }
    for (const m of antigas) porPersona[quemDisse(m.content, m.createdAt)].push(m.id)

    for (const chave of ['sparkle', 'sparxie'] as const) {
      const ids = porPersona[chave]
      if (ids.length === 0) continue
      const persona = await personaComAjustes(personaPorChave(chave))
      await db.update(messages)
        .set(identidadeParaGuardar({ displayName: persona.nome, avatarUrl: persona.avatar }))
        .where(inArray(messages.id, ids))
    }
    logger.info('Bot', `autoria antiga corrigida: ${porPersona.sparkle.length} da Sparkle, ${porPersona.sparxie.length} da Sparxie`)
  } catch (e) {
    logger.error('Bot', `correcao da autoria antiga falhou: ${(e as Error).message}`)
  }
}

export function agendarTrocaDeTurno(): void {
  void verificarTrocaDeTurno()
  const t = setInterval(() => { void verificarTrocaDeTurno() }, INTERVALO_CHECAGEM_MS)
  t.unref?.()
}
