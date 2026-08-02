import { eq, sql } from 'drizzle-orm'
import { db } from '../db'
import { userXp } from '../db/schema'
import { redis } from './redis'
import { logger } from './logger'
import { getRoomService } from './livekit'
import { xpGanho } from './realtime'
import { TrackType } from '@livekit/protocol'

// PROGRESSAO DA ASTRA — todas as regras moram aqui.
//
// Um lugar so de proposito: taxa de XP e a coisa mais dolorosa de mudar depois que
// alguem ja acumulou, entao ela tem que ser facil de LER antes de ser mexida.
//
// Dois numeros, nao quatro: XP e o que se ganha, Brilho e o que se gasta. A trilha
// destrava sozinha por nivel (nao se compra), e a loja gasta Brilho. Quatro moedas
// fariam nada parecer valioso.

// ---------------- As taxas ----------------

export const XP_POR_MENSAGEM     = 12
export const XP_POR_MINUTO_CALL  = 8

// Uma mensagem por minuto conta. Sem isso, "aaa/bbb/ccc" no #geral vale mais que
// uma conversa de verdade — e no dia em que alguem descobre isso, o placar de todo
// mundo perde o sentido.
const ESPERA_MENSAGEM_S = 60

// Tetos diarios. Sao a defesa que NAO depende de eu ter previsto o truque: por mais
// criativo que seja o jeito de farmar, o dia acaba no mesmo lugar.
const TETO_DIARIO_MENSAGEM = 40 * XP_POR_MENSAGEM    // ~40 min de conversa ativa
const TETO_DIARIO_CALL     = 120 * XP_POR_MINUTO_CALL // 2h de call

// Trava de sanidade da curva: sem isso, um xp absurdo (bug, ajuste manual) poria o
// laco de progressoDoXp pra rodar por muito tempo dentro de um request.
const TETO_NIVEL = 500

// ---------------- A curva ----------------

// Custo pra ir do nivel n ao n+1. Quadratica suave: os primeiros niveis vem rapido
// (a pessoa precisa sentir que anda no primeiro dia) e o passo cresce sem virar
// muro. L0->1 = 100, L4->5 = 380, L10->11 = 1100, L20->21 = 3100.
export function custoDoNivel(nivel: number): number {
  return 100 + 50 * nivel + 5 * nivel * nivel
}

export interface Progresso {
  xp:           number
  nivel:        number
  noNivel:      number  // xp acumulado DENTRO do nivel atual
  paraOProximo: number  // quanto o nivel atual custa por inteiro
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

// ---------------- A trilha ----------------

// Recompensa por nivel, DE GRACA — chegou no 4, ganha a 4. Nao ha "resgatar" e nao
// ha o que gastar: e o que separa uma trilha de uma loja.
//
// A lista cobre o comeco, onde a curva e mais interessante; dali pra frente TODO
// nivel da a mesma coisa, pra sempre. Essa cauda infinita e o que impede a trilha de
// virar uma esteira de conteudo que eu teria que reabastecer toda temporada — e
// tambem o que garante que o nivel 80 ainda valha alguma coisa.
const TRILHA: number[] = [
  // nivel 1..12 (indice 0 = recompensa de chegar ao nivel 1)
  20, 25, 30, 40, 45, 55, 60, 70, 80, 90, 100, 120,
]
const TRILHA_CAUDA = 60

export function brilhoDaTrilha(nivel: number): number {
  if (nivel <= 0) return 0
  return TRILHA[nivel - 1] ?? TRILHA_CAUDA
}

// ---------------- Travas ----------------

// O dia e o de SAO PAULO, nao o do servidor: o Render roda em UTC, e um teto que
// vira as 21h corta a noite da galera exatamente no melhor momento.
// 'en-CA' porque e o locale que formata como AAAA-MM-DD.
function diaDeSaoPaulo(agora: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(agora)
}

// INCR primeiro, compara depois: e a unica forma atomica. Ler-e-entao-escrever
// deixaria duas mensagens simultaneas passarem as duas pelo teto.
async function dentroDoTeto(chave: string, ganho: number, teto: number): Promise<boolean> {
  const k = `xp:dia:${chave}`
  const total = await redis.incrby(k, ganho)
  if (total === ganho) await redis.expire(k, 36 * 3600)
  return total <= teto
}

// ---------------- Creditar ----------------

export interface GanhoXp {
  ganho:         number
  origem:        'mensagem' | 'call'
  subiuDeNivel:  boolean
  brilhoGanho:   number
  progresso:     Progresso
}

// Um UPDATE por credito, sem lote. Com a trava de 60s, o teto de escrita por pessoa
// e ~2 por minuto (uma de mensagem, uma de call) — o Neon nem sente. Eu tinha
// planejado acumular no Redis e descarregar em lote; a trava tornou isso complexidade
// sem ganho.
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

    // Subiu mais de um nivel de uma vez? Paga a trilha de CADA um. So acontece se a
    // curva for ajustada pra baixo, mas pular recompensa em silencio seria o tipo de
    // bug que ninguem reporta e todo mundo sente.
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
    // Progressao NUNCA pode derrubar o envio de mensagem nem a call.
    logger.error('Xp', `falha ao creditar (${origem}): ${(e as Error).message}`)
    return null
  }
}

// ---------------- Ganhar ----------------

// Mensagem em canal de servidor. NAO vale em DM: conversa privada entre duas pessoas
// e o farm mais facil que existe (e o mais chato de flagrar, porque ninguem ve).
// Tambem nao vale reagir, entrar em constelacao nem trocar status — sao acoes de um
// clique, repetiveis a vontade, e recompensar clique e pedir pra virar clique.
export async function xpPorMensagem(userId: string): Promise<GanhoXp | null> {
  // NX = so cria se nao existir. Se ja existe, a ultima mensagem que valeu foi ha
  // menos de um minuto e esta nao conta.
  const primeira = await redis.set(`xp:espera:${userId}`, '1', 'EX', ESPERA_MENSAGEM_S, 'NX')
  if (primeira !== 'OK') return null
  const ok = await dentroDoTeto(`msg:${userId}:${diaDeSaoPaulo()}`, XP_POR_MENSAGEM, TETO_DIARIO_MENSAGEM)
  if (!ok) return null
  return creditar(userId, XP_POR_MENSAGEM, 'mensagem')
}

async function xpPorMinutoDeCall(userId: string): Promise<GanhoXp | null> {
  const ok = await dentroDoTeto(`call:${userId}:${diaDeSaoPaulo()}`, XP_POR_MINUTO_CALL, TETO_DIARIO_CALL)
  if (!ok) return null
  return creditar(userId, XP_POR_MINUTO_CALL, 'call')
}

// ---------------- O relogio da call ----------------

// Quem PODE ganhar XP numa sala. Duas condicoes, e as duas existem por um motivo
// concreto:
//
//  - 2+ pessoas: sem isso, deixar uma call vazia aberta a noite inteira e o farm
//    perfeito. Call e pra conversar com alguem.
//  - microfone publicado e sem mudo: quem entra mudo e vai dormir nao esta em
//    conversa nenhuma. (Somado ao teto diario, cobre ate os dois amigos que deixem a
//    call aberta de proposito.)
//
// A verdade vem do LiveKit, nao do cliente. O cliente poderia jurar que esta numa
// call que nao existe; o LiveKit sabe quem esta conectado de verdade, e um cliente
// que travou some da lista sozinho.
function podeGanhar(tracks: { type: TrackType; muted: boolean }[]): boolean {
  return tracks.some((t) => t.type === TrackType.AUDIO && !t.muted)
}

export async function tickXpDeCall(): Promise<void> {
  const svc = getRoomService()
  if (!svc) return
  try {
    const salas = await svc.listRooms()
    for (const sala of salas) {
      if (sala.numParticipants < 2) continue
      const participantes = await svc.listParticipants(sala.name)
      const vivos = participantes.filter((p) => podeGanhar(p.tracks))
      // Recontagem: `numParticipants` conta quem esta na sala, mas quem vale pro
      // "tem gente conversando" e quem esta com o microfone aberto. Dois mudos numa
      // sala nao sao uma conversa.
      if (vivos.length < 2) continue
      // A identity do token e o proprio userId (routes/voice.ts).
      for (const p of vivos) await xpPorMinutoDeCall(p.identity)
    }
  } catch (e) {
    logger.error('Xp', `tick de call falhou: ${(e as Error).message}`)
  }
}

// Um tick por minuto. O lock existe pro dia em que houver mais de uma instancia:
// sem ele, duas instancias creditariam o mesmo minuto duas vezes. TTL menor que o
// intervalo pra nunca pular um minuto legitimo.
const INTERVALO_TICK_MS = 60_000
export function iniciarRelogioDeCall(): NodeJS.Timeout {
  return setInterval(() => {
    void (async () => {
      const meu = await redis.set('xp:tick', '1', 'EX', 55, 'NX').catch(() => null)
      if (meu !== 'OK') return
      await tickXpDeCall()
    })()
  }, INTERVALO_TICK_MS)
}

// ---------------- Ler ----------------

export async function progressoDe(userId: string): Promise<Progresso> {
  const [linha] = await db.select({ xp: userXp.xp, brilho: userXp.brilho })
    .from(userXp).where(eq(userXp.userId, userId)).limit(1)
  return progressoDoXp(linha?.xp ?? 0, linha?.brilho ?? 0)
}
