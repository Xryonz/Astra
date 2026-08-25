import crypto from 'crypto'
import { and, eq, inArray, isNull, sql } from 'drizzle-orm'
import { db } from '../db'
import { userMissions } from '../db/schema'
import { redis } from './redis'
import { logger } from './logger'
import { missaoConcluida } from './realtime'
import { creditarXpDeMissao, diaDeSaoPaulo } from './xp'

const UM_DIA_MS = 86_400_000

function inicioDoDiaMs(): number {
  return Date.parse(`${diaDeSaoPaulo()}T00:00:00-03:00`)
}

export function periodoDiario(): string {
  return diaDeSaoPaulo()
}

function baldeDaSemana(): number {
  return Math.floor(inicioDoDiaMs() / (7 * UM_DIA_MS))
}

export function periodoSemanal(): string {
  return `S${baldeDaSemana()}`
}

export const PERIODO_SEMPRE = 'sempre'

function renovaDiaria(): number { return inicioDoDiaMs() + UM_DIA_MS }
function renovaSemanal(): number { return (baldeDaSemana() + 1) * 7 * UM_DIA_MS }

export type EventoMissao = 'mensagem' | 'resposta' | 'call' | 'reacao'

export interface CtxMissao {
  channelId?: string
  hora?: number
}

interface Missao {
  id:     string
  tipo:   'diaria' | 'semanal' | 'conquista'
  titulo: string
  alvo:   number
  xp:     number
  evento: EventoMissao | '*'
  distintoPor?: (ctx: CtxMissao, periodo: string) => string | null
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

const BONUS_DIARIO: Missao = {
  id: 'd.bonus', tipo: 'diaria', titulo: 'Fechar as três do dia', alvo: 3, xp: 100, evento: '*',
}

const POR_ID = new Map<string, Missao>(
  [...DIARIAS, ...SEMANAIS, ...CONQUISTAS, BONUS_DIARIO].map((m) => [m.id, m]),
)

const QUANTAS_DIARIAS  = 3
const QUANTAS_SEMANAIS = 2

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

function periodoDe(m: Missao): string {
  return m.tipo === 'diaria' ? periodoDiario() : m.tipo === 'semanal' ? periodoSemanal() : PERIODO_SEMPRE
}

interface Avanco { missao: Missao; periodo: string; quanto: number }

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

  if (diariasFechadas > 0) {
    await aplicar(userId, [{ missao: BONUS_DIARIO, periodo: periodoDiario(), quanto: diariasFechadas }])
  }
}

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
      const novo = await redis.sadd(`missao:${m.id}:${userId}:${periodo}`, chave)
      if (novo !== 1) continue
      await redis.expire(`missao:${m.id}:${userId}:${periodo}`, 40 * 86_400)
      avancos.push({ missao: m, periodo, quanto: 1 })
    }

    await aplicar(userId, avancos)
  } catch (e) {
    logger.error('Missoes', `evento ${evento} falhou: ${(e as Error).message}`)
  }
}

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
