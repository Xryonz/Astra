import { and, desc, eq, gte, inArray, sql } from 'drizzle-orm'
import crypto from 'crypto'
import { db } from '../db'
import { messages, serverMembers, users, userXp, reminders } from '../db/schema'
import { redis } from './redis'
import { parseDuration } from './reminders'
import { progressoDoXp } from './xp'

// OS COMANDOS QUE NAO PRECISAM DE IA.
//
// Existe porque a conversa livre depende de uma chave de API que o dono nao
// consegue ter — mas a bot que ele queria desde o comeco era estilo Loritta, e a
// Loritta e isto: sortear, apostar, rankear, zoar. Nada disso precisa de modelo
// nenhum, e tudo isso funciona pra sempre, de graca, sem cadastro.
//
// Todo comando aqui e deterministico ou usa dado que o Astra JA TEM. E a vantagem
// que uma IA generica nunca teria: a bot fala do SEU servidor, das SUAS mensagens,
// do XP das pessoas que estao ali.

// ---------------- utilidades ----------------

// Sorteio estavel: o mesmo par sempre da o mesmo numero. E o que faz o shipp ter
// graca — se cada chamada desse um valor novo, ninguem levaria a serio o resultado
// e o comando morreria na segunda vez.
function porcentagemEstavel(a: string, b: string): number {
  const chave = [a, b].sort().join('|')
  const h = crypto.createHash('sha256').update(chave).digest()
  return h.readUInt16BE(0) % 101
}

function sortear<T>(lista: T[]): T | null {
  return lista.length ? lista[Math.floor(Math.random() * lista.length)] : null
}

// Nomes citados no comando: aceita "@fulano" ou "fulano". So os @ importam pra
// nao confundir palavra comum com apelido.
function usernamesDe(texto: string): string[] {
  return [...texto.matchAll(/@([a-zA-Z0-9_.-]{2,30})/g)].map((m) => m[1])
}

async function nomesDe(usernames: string[]): Promise<Map<string, string>> {
  if (!usernames.length) return new Map()
  const linhas = await db.select({ username: users.username, displayName: users.displayName })
    .from(users).where(inArray(users.username, usernames))
  return new Map(linhas.map((l) => [l.username, l.displayName]))
}

// ---------------- zoeira ----------------

const FAIXAS_SHIPP: Array<[number, string]> = [
  [10,  'nem o universo forcando'],
  [30,  'melhor cada um pro seu lado'],
  [50,  'tem chance num universo paralelo'],
  [70,  'olha… ta ali'],
  [85,  'isso vai dar em alguma coisa'],
  [95,  'escrito nas estrelas'],
  [100, 'e o par perfeito, aceita'],
]

export async function shipp(arg: string): Promise<string> {
  const nomes = usernamesDe(arg)
  if (nomes.length < 2) return '✧ Marca duas pessoas: `shipp @fulano @ciclano`'
  const [a, b] = nomes
  if (a.toLowerCase() === b.toLowerCase()) return '✧ Autoamor e 100%, mas isso a gente ja sabia.'
  const mapa = await nomesDe([a, b])
  const pct = porcentagemEstavel(a.toLowerCase(), b.toLowerCase())
  const veredito = FAIXAS_SHIPP.find(([teto]) => pct <= teto)?.[1] ?? ''
  const barra = '█'.repeat(Math.round(pct / 10)) + '░'.repeat(10 - Math.round(pct / 10))
  return `✧ **${mapa.get(a) ?? a}** + **${mapa.get(b) ?? b}**\n\`${barra}\` **${pct}%** — _${veredito}_`
}

export function moeda(): string {
  return Math.random() < 0.5 ? '✧ **Cara**.' : '✧ **Coroa**.'
}

// "2d6", "d20", "3d10". Teto baixo de proposito: o comando e piada, e alguem ia
// tentar 9999d9999 no primeiro dia so pra ver o que acontece.
export function dado(arg: string): string {
  const m = /^(\d{0,2})d(\d{1,3})$/i.exec(arg.trim() || 'd6')
  if (!m) return '✧ Formato: `dado 2d6`, `dado d20`.'
  const qtd = Math.min(Math.max(parseInt(m[1] || '1', 10), 1), 20)
  const lados = Math.min(Math.max(parseInt(m[2], 10), 2), 100)
  const rolagens = Array.from({ length: qtd }, () => 1 + Math.floor(Math.random() * lados))
  const total = rolagens.reduce((s, n) => s + n, 0)
  return qtd === 1
    ? `✧ 🎲 **${total}** _(d${lados})_`
    : `✧ 🎲 ${rolagens.join(' + ')} = **${total}** _(${qtd}d${lados})_`
}

export function escolha(arg: string): string {
  const opcoes = arg.split(/\s*,\s*/).map((s) => s.trim()).filter(Boolean)
  if (opcoes.length < 2) return '✧ Separa com virgula: `escolha pizza, hamburguer, sushi`'
  return `✧ Eu escolho: **${sortear(opcoes)}**`
}

export async function sorteio(serverId: string, arg: string): Promise<string> {
  const marcados = usernamesDe(arg)
  if (marcados.length >= 2) {
    const mapa = await nomesDe(marcados)
    const g = sortear(marcados)!
    return `✧ Entre ${marcados.length}, a estrela apontou pra **${mapa.get(g) ?? g}**.`
  }
  // Ninguem marcado: sorteia entre a constelacao inteira.
  const membros = await db.select({ displayName: users.displayName })
    .from(serverMembers).innerJoin(users, eq(users.id, serverMembers.userId))
    .where(and(eq(serverMembers.serverId, serverId), eq(users.isBot, false)))
  const g = sortear(membros)
  if (!g) return '✧ Nao achei ninguem pra sortear.'
  return `✧ Entre ${membros.length} da constelacao, a estrela apontou pra **${g.displayName}**.`
}

// ---------------- quem mandou ----------------

// A resposta fica no Redis por 5 minutos, por CANAL. Foi o jeito de ter um jogo de
// duas etapas sem inventar tabela nem segurar estado na memoria do processo (que o
// Render derruba a cada deploy, no meio da brincadeira).
const CHAVE_QUIZ = (channelId: string) => `bot:quemmandou:${channelId}`
const VIDA_QUIZ_S = 300
const MIN_LETRAS = 25

export async function quemMandou(channelId: string): Promise<string> {
  // Amostra das ultimas 200 e sorteia uma, em vez de ORDER BY random() na tabela
  // inteira: mensagem velha demais ninguem lembra, e o random do Postgres varre
  // tudo. Aqui a brincadeira e sobre o que rolou recentemente.
  const candidatas = await db.select({
    content: messages.content,
    autor:   users.displayName,
  })
    .from(messages)
    .innerJoin(users, eq(users.id, messages.authorId))
    .where(and(eq(messages.channelId, channelId), eq(users.isBot, false)))
    .orderBy(desc(messages.createdAt))
    .limit(200)

  const boas = candidatas.filter((m) =>
    m.content.length >= MIN_LETRAS && !m.content.startsWith('/') && !m.content.startsWith('http'),
  )
  const escolhida = sortear(boas)
  if (!escolhida) return '✧ Ainda nao tem conversa suficiente nesta orbita pra brincar disso.'

  await redis.setex(CHAVE_QUIZ(channelId), VIDA_QUIZ_S, escolhida.autor)
  return [
    '✧ **Quem mandou isto?**',
    `> ${escolhida.content.slice(0, 300)}`,
    '',
    '_Chutem! Quem quiser a resposta manda `revelar`._',
  ].join('\n')
}

export async function revelar(channelId: string): Promise<string> {
  const autor = await redis.get(CHAVE_QUIZ(channelId))
  if (!autor) return '✧ Nao tem nenhuma rodada aberta. Comeca uma com `quem mandou`.'
  await redis.del(CHAVE_QUIZ(channelId))
  return `✧ Era **${autor}**.`
}

// ---------------- XP: ranking e perfil ----------------

// O ranking e o que faz o XP virar social. Numero que so a propria pessoa ve nao
// motiva ninguem — o placar e o motivo de alguem querer subir.
export async function ranking(serverId: string): Promise<string> {
  const linhas = await db.select({
    nome: users.displayName,
    xp:   userXp.xp,
  })
    .from(userXp)
    .innerJoin(serverMembers, eq(serverMembers.userId, userXp.userId))
    .innerJoin(users, eq(users.id, userXp.userId))
    .where(and(eq(serverMembers.serverId, serverId), eq(users.isBot, false)))
    .orderBy(desc(userXp.xp))
    .limit(10)

  if (!linhas.length) return '✧ Ninguem pontuou ainda. Manda mensagem, entra numa call — o XP vem sozinho.'

  const medalha = ['🥇', '🥈', '🥉']
  const corpo = linhas.map((l, i) => {
    const p = progressoDoXp(l.xp)
    return `${medalha[i] ?? `**${i + 1}.**`} ${l.nome} — nivel **${p.nivel}** _(${l.xp} xp)_`
  })
  return ['✧ **Ranking da constelacao**', ...corpo].join('\n')
}

export async function perfilXp(arg: string, quemPediu: string): Promise<string> {
  const marcado = usernamesDe(arg)[0]
  const alvo = marcado
    ? (await db.select({ id: users.id, nome: users.displayName })
        .from(users).where(eq(users.username, marcado)).limit(1))[0]
    : (await db.select({ id: users.id, nome: users.displayName })
        .from(users).where(eq(users.id, quemPediu)).limit(1))[0]
  if (!alvo) return `✧ Nao achei @${marcado}.`

  const [linha] = await db.select({ xp: userXp.xp, brilho: userXp.brilho })
    .from(userXp).where(eq(userXp.userId, alvo.id)).limit(1)
  const p = progressoDoXp(linha?.xp ?? 0, linha?.brilho ?? 0)
  const cheio = Math.round((p.noNivel / p.paraOProximo) * 10)
  const barra = '█'.repeat(cheio) + '░'.repeat(10 - cheio)
  return [
    `✧ **${alvo.nome}**`,
    `nivel **${p.nivel}** \`${barra}\` ${p.noNivel}/${p.paraOProximo}`,
    `${p.xp} xp no total · ${p.brilho} de brilho`,
  ].join('\n')
}

// ---------------- lembrete ----------------

export async function lembrete(arg: string, userId: string, channelId: string): Promise<string> {
  const m = /^(\S+)\s+(.+)$/s.exec(arg.trim())
  if (!m) return '✧ Assim: `lembrete 20min terminar o trabalho`'
  const ms = parseDuration(m[1])
  if (ms == null) return `✧ Nao entendi "${m[1]}". Use \`20min\`, \`2h\`, \`1d\`.`
  const conteudo = m[2].slice(0, 500).trim()
  const [r] = await db.insert(reminders).values({
    creatorId: userId, targetUserId: userId, content: conteudo, channelId,
    dueAt: new Date(Date.now() + ms),
  }).returning({ dueAt: reminders.dueAt })
  return `✧ Anotado: "${conteudo}" — te chamo em ${r.dueAt.toLocaleString('pt-BR')}.`
}

// ---------------- resumo do dia ----------------

// Resumo por CONTAGEM, nao por IA. Nao conta o que foi dito, conta o que aconteceu
// — e isso e util de um jeito que um paragrafo gerado nao e: da pra ver de relance
// se a orbita esteve viva hoje e quem puxou a conversa.
export async function resumoDoDia(channelId: string): Promise<string> {
  const inicio = new Date()
  inicio.setHours(0, 0, 0, 0)

  const linhas = await db.select({
    nome:  users.displayName,
    total: sql<number>`count(*)::int`,
  })
    .from(messages)
    .innerJoin(users, eq(users.id, messages.authorId))
    .where(and(
      eq(messages.channelId, channelId),
      gte(messages.createdAt, inicio),
      eq(users.isBot, false),
    ))
    .groupBy(users.displayName)
    .orderBy(desc(sql`count(*)`))
    .limit(5)

  const total = linhas.reduce((s, l) => s + l.total, 0)
  if (!total) return '✧ Hoje ainda nao rolou nada por aqui.'

  const podio = linhas.map((l, i) => `**${i + 1}.** ${l.nome} — ${l.total}`)
  return [
    `✧ **Hoje nesta orbita:** ${total} mensagens de ${linhas.length} pessoa(s).`,
    ...podio,
  ].join('\n')
}
