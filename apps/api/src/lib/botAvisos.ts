import type { Server as SocketServer } from 'socket.io'
import { and, asc, eq } from 'drizzle-orm'
import { db } from '../db'
import { channels, messages, serverMembers, servers, users } from '../db/schema'
import { redis } from './redis'
import { logger } from './logger'
import { botNaOrbita } from './botScope'
import { getBotId, personaDoDia, sincronizaPersona, type Persona } from './bot'

// TUDO QUE A BOT DIZ SEM SER CHAMADA.
//
// Ela morava numa constelação com cargo próprio no painel de membros e nunca
// abria a boca sozinha — só respondia a `/sparkle`. Isto aqui é o outro lado:
// chegada de gente, marco de nível e a troca de turno entre as irmãs.
//
// Três regras valem pros três casos, e são o que separa "ela é viva" de "ela é
// spam":
//   1. Uma vez só. Toda fala espontânea passa por uma trava no Redis; nada se
//      repete por reboot, corrida entre requisições ou clique duplo.
//   2. Onde ela pode falar. Respeita o interruptor por órbita/categoria que já
//      existe (botNaOrbita) — quem pediu silêncio continua em silêncio.
//   3. Curto. Uma linha. Aviso espontâneo que ocupa parágrafo vira ruído, e
//      ruído ensina a ignorar.

let io: SocketServer | null = null
export function ligarAvisosDaBot(server: SocketServer) { io = server }

// Onde ela fala numa constelação.
//
// PRIMEIRO a escolha do dono (Server.botNoticeChannelId). Ela vale pra tudo que a
// bot diz sem ser chamada — chegada de gente e troca de turno. Subir de nível não
// passa por aqui de propósito: aquele aviso é sobre a conversa em que a pessoa
// estava, não sobre a constelação.
//
// A escolha é RECONFERIDA a cada aviso, e não confiada. O id pode ter virado uma
// órbita apagada, privada, de voz, ou uma em que a bot foi silenciada depois —
// nada disso avisa esta tabela quando acontece. Se não passar, cai no automático:
// a primeira órbita de texto em que ela tem voz, por posição, que é a mesma ordem
// que a pessoa vê na barra lateral.
//
// Cair no automático (e não calar) é decisão do dono: sair no lugar errado é um
// aviso fora de lugar; não sair é um recurso que morre em silêncio.
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

// Trava de "isto já foi dito". Devolve true UMA vez por chave.
async function primeiraVez(chave: string, ttlSegundos: number): Promise<boolean> {
  try {
    return (await redis.set(`bot:disse:${chave}`, '1', 'EX', ttlSegundos, 'NX')) === 'OK'
  } catch {
    // Redis fora do ar: cala a boca em vez de arriscar repetir. Aviso espontâneo
    // é enfeite — perder um é irrelevante, repetir em loop não é.
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

// ---------------------------------------------------------------- chegou gente

// UMA vez por pessoa por constelação, pra sempre (TTL de um ano). Quem sai e
// volta não é cumprimentado de novo: a segunda vez não é chegada, é retorno — e
// tratar as duas igual entrega que quem fala é uma máquina.
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

// ------------------------------------------------------------------- subiu de nível

// SÓ MARCO REDONDO. Comemorar todo nível transformaria a conversa num placar: com
// XP por mensagem, os primeiros níveis caem em minutos, e a quarta felicitação
// seguida já é ruído. Estes são raros o bastante pra continuarem significando algo.
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

// ------------------------------------------------------------------ troca de turno

// A PASSAGEM DE TURNO, que era invisível.
//
// A Sparkle virava Sparxie em silêncio: mudava o nome e a foto, e pronto. As duas
// irmãs só apareciam COMO DUAS numa nota de erro, quando alguém chamava a que
// estava de folga. O melhor da ideia só se manifestava quando a pessoa errava.
//
// Sexta 00h a Sparxie assume; domingo 00h a Sparkle volta (horário de São Paulo).
//
// NÃO É UM TIMER PRA MEIA-NOITE. O plano free do Render dorme, e um `setTimeout`
// pra 00h00 de sexta simplesmente não dispara se a instância estiver fora do ar
// naquele minuto — e ainda perderia o aviso a cada redeploy. Aqui a checagem é
// preguiçosa e idempotente: roda de tempos em tempos, pergunta "o turno de hoje
// já foi anunciado?" e, se não foi, anuncia. Se a instância acordar 00h07, o
// recado sai 00h07. Atrasar é aceitável; não sair, não.
const INTERVALO_CHECAGEM_MS = 5 * 60 * 1000

// Chave do turno: quem está de plantão + a data em que este turno começou. Muda
// exatamente duas vezes por semana, que é a frequência que se quer.
export function chaveDoTurno(agora: Date): string {
  const p = personaDoDia(agora)
  const fmt = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit',
  })
  // Recua até o primeiro dia deste turno, pra sexta e sábado caírem na MESMA
  // chave (senão a Sparxie se apresentaria duas vezes, uma por dia).
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
  // Quem SAI fala primeiro. A ordem importa: despedida depois da chegada leria
  // como se as duas estivessem no mesmo lugar ao mesmo tempo.
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
    // Uma semana de TTL: a chave só precisa viver até o próximo turno do mesmo
    // nome, e expirar sozinha evita lixo eterno no Redis.
    if (!(await primeiraVez(`turno:${chaveDoTurno(agora)}`, 8 * 24 * 60 * 60))) return

    // PRIMEIRA VEZ NA VIDA: reserva o turno atual e fica calada. Sem isto, o
    // deploy que liga esta função anunciaria uma passagem que não aconteceu —
    // uma despedida de quem não estava lá, no meio de uma terça qualquer. O
    // recurso estreia em silêncio e fala na próxima troca DE VERDADE.
    if (await primeiraVez('turno:estreia', UM_ANO)) {
      logger.info('Bot', `avisos de turno ligados — turno atual (${entra.nome}) reservado sem anuncio`)
      return
    }

    const botId = await getBotId()
    if (!botId) return
    const sai = entra.chave === 'sparkle' ? 'sparxie' : 'sparkle'

    // Só onde a bot mora E onde há órbita em que ela fala.
    const constelacoes = await db.select({ serverId: serverMembers.serverId })
      .from(serverMembers).where(eq(serverMembers.userId, botId))
    if (constelacoes.length === 0) return

    // A persona no banco tem que ser a de AGORA antes de falar: senão a mensagem
    // de chegada sai assinada com o nome de quem está saindo.
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
  // unref: um timer de enfeite não pode ser o motivo de o processo não encerrar.
  t.unref?.()
}

