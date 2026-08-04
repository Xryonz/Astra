import crypto from 'crypto'
import { and, eq, inArray, isNull, sql } from 'drizzle-orm'
import { db } from '../db'
import { userMissions } from '../db/schema'
import { redis } from './redis'
import { logger } from './logger'
import { missaoConcluida } from './realtime'
import { creditarXpDeMissao, diaDeSaoPaulo } from './xp'

// MISSOES — o motivo pra voltar amanha.
//
// XP sozinho recompensa quem ja ia usar o app de qualquer jeito. Missao recompensa
// quem VOLTA, e e por isso que ela existe: tres camadas com ritmos diferentes, pra
// cobrir tres pessoas diferentes.
//
//   diaria    — quem entra hoje                     (some amanha)
//   semanal   — quem entra varios dias              (some no domingo)
//   conquista — quem esta aqui ha meses             (nunca some)
//
// O XP de missao NAO passa pelo teto diario do lib/xp.ts. O teto existe pra que
// ninguem farme conversa fiada; missao e o oposto de farm — ela pede exatamente o
// comportamento que o app quer. Fazer a missao e depois descobrir que o XP nao veio
// porque o dia acabou seria a pior surpresa possivel.

// ---------------- O periodo ----------------

// O fuso e o de Sao Paulo, como no XP: teto que vira as 21h corta a noite da galera
// no melhor momento. -03:00 cravado porque o Brasil acabou com o horario de verao
// em 2019 — se voltar, este e o unico lugar a mexer.
const UM_DIA_MS = 86_400_000

function inicioDoDiaMs(): number {
  return Date.parse(`${diaDeSaoPaulo()}T00:00:00-03:00`)
}

export function periodoDiario(): string {
  return diaDeSaoPaulo()
}

// Semana = balde de 7 dias contado desde a epoch. Nao e a semana ISO (que comeca na
// segunda), e nao precisa ser: o que importa e que o balde seja estavel, igual pra
// todo mundo, e que a tela consiga dizer quando ele vira.
function baldeDaSemana(): number {
  return Math.floor(inicioDoDiaMs() / (7 * UM_DIA_MS))
}

export function periodoSemanal(): string {
  return `S${baldeDaSemana()}`
}

export const PERIODO_SEMPRE = 'sempre'

function renovaDiaria(): number { return inicioDoDiaMs() + UM_DIA_MS }
function renovaSemanal(): number { return (baldeDaSemana() + 1) * 7 * UM_DIA_MS }

// ---------------- O catalogo ----------------

export type EventoMissao = 'mensagem' | 'resposta' | 'call' | 'reacao'

export interface CtxMissao {
  channelId?: string
  /** Hora local de Sao Paulo (0-23), pra missoes que dependem do relogio. */
  hora?: number
}

interface Missao {
  id:     string
  tipo:   'diaria' | 'semanal' | 'conquista'
  titulo: string
  alvo:   number
  xp:     number
  /** '*' = qualquer sinal de vida conta. */
  evento: EventoMissao | '*'
  /** So conta uma vez por valor devolvido (null = nao conta). Ex.: orbitas distintas. */
  distintoPor?: (ctx: CtxMissao, periodo: string) => string | null
  /** Filtro extra antes de contar. */
  so?: (ctx: CtxMissao) => boolean
}

const porCanal = (ctx: CtxMissao) => ctx.channelId ?? null
const porDia   = () => diaDeSaoPaulo()

const DIARIAS: Missao[] = [
  { id: 'd.orbitas2',  tipo: 'diaria', titulo: 'Fale em 2 órbitas diferentes', alvo: 2,  xp: 40, evento: 'mensagem', distintoPor: porCanal },
  { id: 'd.orbitas3',  tipo: 'diaria', titulo: 'Fale em 3 órbitas diferentes', alvo: 3,  xp: 55, evento: 'mensagem', distintoPor: porCanal },
  { id: 'd.msg10',     tipo: 'diaria', titulo: 'Mande 10 mensagens',           alvo: 10, xp: 35, evento: 'mensagem' },
  { id: 'd.msg25',     tipo: 'diaria', titulo: 'Mande 25 mensagens',           alvo: 25, xp: 70, evento: 'mensagem' },
  { id: 'd.call15',    tipo: 'diaria', titulo: '15 minutos em call',           alvo: 15, xp: 60, evento: 'call' },
  { id: 'd.call30',    tipo: 'diaria', titulo: '30 minutos em call',           alvo: 30, xp: 100, evento: 'call' },
  { id: 'd.responda3', tipo: 'diaria', titulo: 'Responda 3 pessoas',           alvo: 3,  xp: 45, evento: 'resposta' },
  { id: 'd.reacao5',   tipo: 'diaria', titulo: 'Reaja a 5 mensagens',          alvo: 5,  xp: 25, evento: 'reacao' },
]

const SEMANAIS: Missao[] = [
  { id: 's.dias5',    tipo: 'semanal', titulo: 'Apareça em 5 dias',            alvo: 5,   xp: 200, evento: '*', distintoPor: porDia },
  { id: 's.call2h',   tipo: 'semanal', titulo: '2 horas em call',              alvo: 120, xp: 250, evento: 'call' },
  { id: 's.msg150',   tipo: 'semanal', titulo: 'Mande 150 mensagens',          alvo: 150, xp: 220, evento: 'mensagem' },
  { id: 's.orbitas8', tipo: 'semanal', titulo: 'Fale em 8 órbitas diferentes', alvo: 8,   xp: 200, evento: 'mensagem', distintoPor: porCanal },
]

// Conquistas aparecem TODAS, sempre. Ver o que ainda falta e metade da graca — uma
// lista que so mostra o que ja foi feito nao da a ninguem pra onde ir.
const CONQUISTAS: Missao[] = [
  { id: 'c.primeira',  tipo: 'conquista', titulo: 'A primeira mensagem',         alvo: 1,    xp: 50,  evento: 'mensagem' },
  { id: 'c.msg100',    tipo: 'conquista', titulo: '100 mensagens',               alvo: 100,  xp: 300, evento: 'mensagem' },
  { id: 'c.msg1000',   tipo: 'conquista', titulo: '1000 mensagens',              alvo: 1000, xp: 800, evento: 'mensagem' },
  { id: 'c.call1',     tipo: 'conquista', titulo: 'A primeira call',             alvo: 1,    xp: 80,  evento: 'call' },
  { id: 'c.call10h',   tipo: 'conquista', titulo: '10 horas em call',            alvo: 600,  xp: 600, evento: 'call' },
  { id: 'c.orbitas20', tipo: 'conquista', titulo: 'Fale em 20 órbitas',          alvo: 20,   xp: 250, evento: 'mensagem', distintoPor: porCanal },
  { id: 'c.reacao100', tipo: 'conquista', titulo: '100 reações',                 alvo: 100,  xp: 200, evento: 'reacao' },
  { id: 'c.coruja',    tipo: 'conquista', titulo: 'Fale depois das 3 da manhã',  alvo: 1,    xp: 100, evento: 'mensagem', so: (c) => (c.hora ?? -1) >= 3 && (c.hora ?? 99) < 6 },
]

// O bonus de fechar as tres. Nao tem evento proprio: ele avanca quando uma diaria
// fecha. Vive como missao de verdade (linha na tabela) so pra herdar o fechamento
// atomico — se fosse calculado na leitura, dois requests simultaneos pagariam duas
// vezes.
const BONUS_DIARIO: Missao = {
  id: 'd.bonus', tipo: 'diaria', titulo: 'Fechar as três do dia', alvo: 3, xp: 100, evento: '*',
}

const POR_ID = new Map<string, Missao>(
  [...DIARIAS, ...SEMANAIS, ...CONQUISTAS, BONUS_DIARIO].map((m) => [m.id, m]),
)

const QUANTAS_DIARIAS  = 3
const QUANTAS_SEMANAIS = 2

// ---------------- O sorteio ----------------

// Deterministico a partir de (pessoa, periodo): mesma pessoa ve as mesmas missoes o
// dia inteiro, e nada precisa ser gravado no primeiro acesso do dia. Duas pessoas
// diferentes veem baralhos diferentes — o que evita a sensacao de "todo mundo
// fazendo a mesma coisa" e distribui melhor o movimento pelo app.
function sorteioEstavel(semente: string, deck: Missao[], quantas: number): Missao[] {
  return [...deck]
    .map((m) => ({ m, k: crypto.createHash('sha256').update(`${semente}|${m.id}`).digest('hex') }))
    .sort((a, b) => (a.k < b.k ? -1 : 1))
    .slice(0, quantas)
    .map((x) => x.m)
}

export function diariasDe(userId: string): Missao[] {
  return sorteioEstavel(`${userId}|${periodoDiario()}`, DIARIAS, QUANTAS_DIARIAS)
}

export function semanaisDe(userId: string): Missao[] {
  return sorteioEstavel(`${userId}|${periodoSemanal()}`, SEMANAIS, QUANTAS_SEMANAIS)
}

// ---------------- Avancar ----------------

function periodoDe(m: Missao): string {
  return m.tipo === 'diaria' ? periodoDiario() : m.tipo === 'semanal' ? periodoSemanal() : PERIODO_SEMPRE
}

interface Avanco { missao: Missao; periodo: string; quanto: number }

// Fecha e paga. O UPDATE com `isNull(concluidaEm)` e o que impede pagamento duplo:
// se dois eventos chegarem juntos e os dois virem progresso >= alvo, so um troca o
// NULL por uma data — o outro volta sem linha e nao paga nada.
async function fechar(userId: string, m: Missao, periodo: string): Promise<boolean> {
  const [linha] = await db.update(userMissions)
    .set({ concluidaEm: new Date() })
    .where(and(
      eq(userMissions.userId, userId),
      eq(userMissions.missionId, m.id),
      eq(userMissions.periodo, periodo),
      isNull(userMissions.concluidaEm),
    ))
    .returning({ missionId: userMissions.missionId })
  if (!linha) return false

  await creditarXpDeMissao(userId, m.xp)
  missaoConcluida(userId, { id: m.id, titulo: m.titulo, xp: m.xp, tipo: m.tipo })
  return true
}

async function aplicar(userId: string, avancos: Avanco[]): Promise<void> {
  if (!avancos.length) return

  // Um INSERT so pra todas: mensagem em canal movimentado dispara ate seis missoes
  // ao mesmo tempo, e seis idas ao Neon por mensagem seria caro por nada.
  const linhas = await db.insert(userMissions)
    .values(avancos.map((a) => ({
      userId, missionId: a.missao.id, periodo: a.periodo, progresso: a.quanto,
    })))
    .onConflictDoUpdate({
      target: [userMissions.userId, userMissions.missionId, userMissions.periodo],
      set: { progresso: sql`${userMissions.progresso} + excluded."progresso"` },
    })
    .returning({
      missionId:   userMissions.missionId,
      periodo:     userMissions.periodo,
      progresso:   userMissions.progresso,
      concluidaEm: userMissions.concluidaEm,
    })

  let diariasFechadas = 0
  for (const l of linhas) {
    if (l.concluidaEm) continue
    const m = POR_ID.get(l.missionId)
    if (!m || l.progresso < m.alvo) continue
    if (await fechar(userId, m, l.periodo) && m.tipo === 'diaria' && m.id !== BONUS_DIARIO.id) {
      diariasFechadas++
    }
  }

  // Recursao de um nivel so, e por construcao: o bonus nao e diaria sorteada, entao
  // fechar ele nao gera outro avanco.
  if (diariasFechadas > 0) {
    await aplicar(userId, [{ missao: BONUS_DIARIO, periodo: periodoDiario(), quanto: diariasFechadas }])
  }
}

// A porta de entrada. Chamada com `void` de dentro do envio de mensagem e do tick de
// call — missao NUNCA pode derrubar nem atrasar o que a pessoa veio fazer, entao
// tudo aqui e best-effort e o catch engole.
export async function eventoDeMissao(userId: string, evento: EventoMissao, ctx: CtxMissao = {}): Promise<void> {
  try {
    const hora = ctx.hora ?? Number(
      new Intl.DateTimeFormat('en-GB', { timeZone: 'America/Sao_Paulo', hour: '2-digit', hour12: false }).format(new Date()),
    )
    const completo: CtxMissao = { ...ctx, hora }

    const candidatas = [...diariasDe(userId), ...semanaisDe(userId), ...CONQUISTAS]
      .filter((m) => (m.evento === '*' || m.evento === evento) && (!m.so || m.so(completo)))

    const avancos: Avanco[] = []
    for (const m of candidatas) {
      const periodo = periodoDe(m)
      if (!m.distintoPor) {
        avancos.push({ missao: m, periodo, quanto: 1 })
        continue
      }
      const chave = m.distintoPor(completo, periodo)
      if (!chave) continue
      // SADD devolve 1 so na primeira vez que aquele valor aparece no periodo. E o
      // que faz "2 orbitas diferentes" contar orbitas, e nao mensagens.
      const novo = await redis.sadd(`missao:${m.id}:${userId}:${periodo}`, chave)
      if (novo !== 1) continue
      // Sobra folga alem do periodo pra chave nao morrer antes da missao virar.
      await redis.expire(`missao:${m.id}:${userId}:${periodo}`, 40 * 86_400)
      avancos.push({ missao: m, periodo, quanto: 1 })
    }

    await aplicar(userId, avancos)
  } catch (e) {
    logger.error('Missoes', `evento ${evento} falhou: ${(e as Error).message}`)
  }
}

// ---------------- Ler ----------------

export interface ItemMissao {
  id:         string
  titulo:     string
  alvo:       number
  xp:         number
  progresso:  number
  concluida:  boolean
}

export interface PainelDeMissoes {
  diarias:    { renovaEm: number; itens: ItemMissao[]; bonus: ItemMissao }
  semanais:   { renovaEm: number; itens: ItemMissao[] }
  conquistas: { itens: ItemMissao[] }
}

export async function painelDe(userId: string): Promise<PainelDeMissoes> {
  const diarias  = diariasDe(userId)
  const semanais = semanaisDe(userId)
  const todas    = [...diarias, BONUS_DIARIO, ...semanais, ...CONQUISTAS]

  const linhas = await db.select({
    missionId:   userMissions.missionId,
    periodo:     userMissions.periodo,
    progresso:   userMissions.progresso,
    concluidaEm: userMissions.concluidaEm,
  })
    .from(userMissions)
    .where(and(
      eq(userMissions.userId, userId),
      inArray(userMissions.missionId, todas.map((m) => m.id)),
      inArray(userMissions.periodo, [periodoDiario(), periodoSemanal(), PERIODO_SEMPRE]),
    ))

  const porChave = new Map(linhas.map((l) => [`${l.missionId}|${l.periodo}`, l]))
  const item = (m: Missao): ItemMissao => {
    const l = porChave.get(`${m.id}|${periodoDe(m)}`)
    return {
      id:        m.id,
      titulo:    m.titulo,
      alvo:      m.alvo,
      xp:        m.xp,
      // O progresso guardado pode passar do alvo (o evento chega antes do fecho);
      // mostrar 27/25 leria como bug.
      progresso: Math.min(l?.progresso ?? 0, m.alvo),
      concluida: !!l?.concluidaEm,
    }
  }

  return {
    diarias:    { renovaEm: renovaDiaria(),  itens: diarias.map(item), bonus: item(BONUS_DIARIO) },
    semanais:   { renovaEm: renovaSemanal(), itens: semanais.map(item) },
    conquistas: { itens: CONQUISTAS.map(item) },
  }
}
