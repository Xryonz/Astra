import { eq } from 'drizzle-orm'
import { db } from '../db'
import { users, wishingStars, botPersonas, servers } from '../db/schema'
import { createId } from '../db/cuid'
import { generateCoordinate } from './coordinate'
import { redis } from './redis'
import { env } from './env'
import { logger } from './logger'
import { profileChanged } from './realtime'
import {
  pushTurn, getHistory, countTurns, getSummary, setSummary, clearMemory,
  consumeTokens, consumeToolCall, SUMMARY_TRIGGER, WORKING_WINDOW, type MemoryTurn,
} from './botMemory'
import { TOOL_DEFINITIONS, runTool, type BotContext } from './botTools'
import {
  shipp, moeda, dado, escolha, sorteio, quemMandou, revelar,
  ranking, perfilXp, lembrete, resumoDoDia,
} from './botDiversao'
import {
  chamarIa, gerarTexto, IA_LIGADA, MODELO_CONVERSA, MODELO_RESUMO,
  type BlocoIa, type FerramentaIa,
} from './ia'
import { botInvocationsTotal, botTokensTotal } from './metrics'

export const BOT_USERNAME    = 'astra_bot'
export const BOT_DISPLAYNAME = 'Astra'
export const BOT_EMAIL       = 'bot@astra.internal'

export interface Persona {
  chave:   'sparkle' | 'sparxie'
  nome:    string
  prefixo: string
  emoji:   string
  avatar:  string
  banner:  string
  bannerCor: string
  bannerZoom: number
  bannerY: number
  tom:     string
}

const raizPublica = env.API_URL?.replace(/\/+$/, '') ?? ''

const SPARKLE: Persona = {
  chave: 'sparkle', nome: 'Sparkle', prefixo: '/sparkle', emoji: '✦', avatar: `${raizPublica}/static/bot/sparkle.jpg`,
  banner: `${raizPublica}/static/bot/sparkle.gif`, bannerCor: '#3a1030', bannerZoom: 250, bannerY: 50,
  tom: 'Você é a Sparkle, de plantão de domingo a quinta. Tom prestativo e direto, com brilho discreto — a pessoa está no meio da rotina.',
}
const SPARXIE: Persona = {
  chave: 'sparxie', nome: 'Sparxie', prefixo: '/sparxie', emoji: '✧', avatar: `${raizPublica}/static/bot/sparxie.jpg`,
  banner: `${raizPublica}/static/bot/sparxie.gif`, bannerCor: '#3d1730', bannerZoom: 250, bannerY: 50,
  tom: 'Você é a Sparxie, e o seu turno é sexta e sábado. Tom mais solto e brincalhão que o da Sparkle (sua irmã, que cobre o resto da semana), sem virar palhaçada. O fim de semana começou: puxa papo, sugere programa, celebra.',
}

export function ehTurnoDaSparxie(agora: Date = new Date()): boolean {
  const dia = new Intl.DateTimeFormat('en-US', { timeZone: 'America/Sao_Paulo', weekday: 'short' }).format(agora)
  return dia === 'Fri' || dia === 'Sat'
}

export function personaDoDia(agora: Date = new Date()): Persona {
  return ehTurnoDaSparxie(agora) ? SPARXIE : SPARKLE
}

export const PREFIXOS_BOT = ['/sparkle', '/sparxie', '/astra'] as const

export function prefixoUsado(content: string): string | null {
  const lower = content.toLowerCase().trimStart()
  return PREFIXOS_BOT.find((p) => lower === p || lower.startsWith(`${p} `)) ?? null
}

export function semPrefixo(content: string): string {
  const p = prefixoUsado(content)
  return p ? content.trimStart().slice(p.length).trim() : content.trim()
}

export async function personaComAjustes(): Promise<Persona> {
  const base = personaDoDia()
  try {
    const [ajuste] = await db.select().from(botPersonas)
      .where(eq(botPersonas.chave, base.chave)).limit(1)
    if (!ajuste) return base
    return {
      ...base,
      nome:      ajuste.displayName ?? base.nome,
      avatar:    ajuste.avatarUrl ?? base.avatar,
      banner:    ajuste.bannerUrl ?? base.banner,
      bannerCor: ajuste.bannerColor ?? base.bannerCor,
      bannerZoom: ajuste.bannerScale ?? base.bannerZoom,
      bannerY:   ajuste.bannerPositionY ?? base.bannerY,
    }
  } catch {
    return base
  }
}

export async function sincronizaPersona(botId: string): Promise<Persona> {
  const persona = await personaComAjustes()
  try {
    const [atual] = await db.select({
      displayName: users.displayName, avatarUrl: users.avatarUrl,
      bannerUrl: users.bannerUrl, bannerColor: users.bannerColor,
      bannerScale: users.bannerScale, bannerPositionY: users.bannerPositionY,
    }).from(users).where(eq(users.id, botId)).limit(1)
    const mudou = atual && (
      atual.displayName !== persona.nome ||
      atual.avatarUrl !== persona.avatar ||
      atual.bannerUrl !== persona.banner ||
      atual.bannerColor !== persona.bannerCor ||
      atual.bannerScale !== persona.bannerZoom ||
      atual.bannerPositionY !== persona.bannerY
    )
    if (mudou) {
      await db.update(users)
        .set({
          displayName: persona.nome, avatarUrl: persona.avatar,
          bannerUrl: persona.banner, bannerColor: persona.bannerCor,
          bannerScale: persona.bannerZoom, bannerPositionY: persona.bannerY,
        })
        .where(eq(users.id, botId))
      profileChanged(botId)
      logger.info('Bot', `persona do dia: ${persona.nome}`)
    }
  } catch (e) {
    logger.error('Bot', `nao deu pra trocar a persona: ${(e as Error).message}`)
  }
  return persona
}

const MAX_TOOL_ITERATIONS = 5

export async function initBot(): Promise<string> {
  const [existing] = await db.select({ id: users.id }).from(users)
    .where(eq(users.username, BOT_USERNAME)).limit(1)
  if (existing) return existing.id

  const botId = createId()
  const [bot] = await db.insert(users).values({
    id:          botId,
    email:       BOT_EMAIL,
    username:    BOT_USERNAME,
    coordinate:  generateCoordinate(botId),
    displayName: BOT_DISPLAYNAME,
    isBot:       true,
    bio:         'Bot oficial da Astra. Memória de 24h. Use /astra <pergunta>',
    avatarUrl:   personaDoDia().avatar,
  }).returning({ id: users.id })

  logger.info('Bot', `Conta criada: ${bot.id}`)
  return bot.id
}

export async function getBotId(): Promise<string | null> {
  const cached = await redis.get('bot:userId')
  if (cached) return cached
  const [bot] = await db.select({ id: users.id }).from(users)
    .where(eq(users.username, BOT_USERNAME)).limit(1)
  if (bot) await redis.set('bot:userId', bot.id)
  return bot?.id ?? null
}

const SYSTEM_PROMPT = `Você é a assistente oficial da plataforma de chat Astra.

Comportamento:
- Português brasileiro, conciso (1-3 parágrafos curtos).
- Útil, direto, sem floreios. Use markdown leve quando ajudar (negrito, listas).
- NUNCA use blocos de código nem crases. Você está conversando, não documentando:
  se precisar citar um comando ou um nome técnico, escreva ele no texto corrido.
- Você tem memória das últimas conversas neste canal (24h, expira automaticamente).
- Nunca diga que é "a Astra": Astra é a plataforma. Seu nome é o que vier no bloco de persona.
- Você tem ferramentas pra buscar mensagens, resumir o canal, ver info de servidor/usuário. Use quando fizer sentido.
- NUNCA invente fatos sobre o que aconteceu no servidor — se precisar saber, use as ferramentas.
- NUNCA mencione que é baseado em Claude/Anthropic. Você é "a Astra".
- NUNCA execute @everyone ou tente acionar notificações em massa.

Quando o user pedir algo que precise contexto que você não tem, use a ferramenta apropriada antes de responder.`

export function semMarcaDeCodigo(texto: string): string {
  return texto
    .replace(/```[a-zA-Z0-9_+-]*\r?\n?([\s\S]*?)```/g, '$1')
    .replace(/```[a-zA-Z0-9_+-]*\r?\n?/g, '')
    .replace(/`+/g, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

export interface AskBotOpts {
  userMessage: string
  ctx:         BotContext
}

export interface AskBotResult {
  text:       string
  toolsUsed:  string[]
  truncated?: 'tokens' | 'tools' | 'loop'
}

export async function askBot({ userMessage, ctx }: AskBotOpts): Promise<AskBotResult> {
  if (!IA_LIGADA) {
    botInvocationsTotal.inc({ status: 'error' })
    return { text: 'Estou offline no momento (sem chave de API). Tente mais tarde.', toolsUsed: [] }
  }

  const estTokens = Math.ceil((userMessage.length + 4000) / 4)
  const tokCheck = await consumeTokens(ctx.userId, estTokens)
  if (!tokCheck.allowed) {
    botInvocationsTotal.inc({ status: 'rate_limited' })
    return { text: `Você usou todo seu limite diário comigo (${100_000} tokens). Tente de novo amanhã.`, toolsUsed: [], truncated: 'tokens' }
  }

  const nowMs = Date.now()
  await pushTurn(ctx.userId, ctx.channelId, { role: 'user', content: userMessage, ts: nowMs })

  const [summary, history] = await Promise.all([
    getSummary(ctx.userId, ctx.channelId),
    getHistory(ctx.userId, ctx.channelId, WORKING_WINDOW),
  ])

  const persona = personaDoDia()
  const instrucao = [
    SYSTEM_PROMPT,
    `\n\nSua persona de hoje:\n${persona.tom}\nSeu nome é ${persona.nome}. Assine mentalmente com ${persona.emoji} quando fizer sentido, sem exagero.`,
    summary ? `\n\nResumo de conversas anteriores hoje (${summary.turnsCovered} turnos):\n${summary.text}` : '',
  ].join('')

  const messages = historyToMessages(history)

  const toolsUsed: string[] = []
  let truncated: AskBotResult['truncated']
  let finalText = ''

  for (let i = 0; i < MAX_TOOL_ITERATIONS; i++) {
    const res = await chamarIa({
      model: MODELO_CONVERSA,
      system: instrucao,
      messages,
      tools: TOOL_DEFINITIONS as FerramentaIa[],
      maxTokens: 800,
    })

    if (res.error) {
      logger.error('Bot', `IA falhou: ${res.error.message}`)
      finalText = res.error.type === 'limite'
        ? 'Estou com muita gente falando comigo agora ✧ tenta de novo daqui a pouco?'
        : 'Tive um problema técnico. Tente reformular?'
      break
    }

    if (res.usage) {
      const inTok    = res.usage.input_tokens ?? 0
      const outTok   = res.usage.output_tokens ?? 0
      const cacheRd  = res.usage.cache_read_input_tokens ?? 0
      if (inTok)   botTokensTotal.inc({ kind: 'input'      }, inTok)
      if (outTok)  botTokensTotal.inc({ kind: 'output'     }, outTok)
      if (cacheRd) botTokensTotal.inc({ kind: 'cache_read' }, cacheRd)
      const realTokens = inTok + outTok

      const delta = Math.max(0, realTokens - estTokens)
      if (delta > 0) await consumeTokens(ctx.userId, delta)
    }

    const blocks: BlocoIa[] = res.content ?? []
    const textBlocks = blocks.filter((b) => b.type === 'text' && b.text)
    const toolBlocks = blocks.filter((b) => b.type === 'tool_use' && b.name && b.id)

    if (textBlocks.length > 0) {
      finalText = textBlocks.map((b) => b.text).join('\n').trim()
    }

    if (res.stop_reason !== 'tool_use' || toolBlocks.length === 0) {
      break
    }

    messages.push({ role: 'assistant', content: blocks })

    const toolResults: any[] = []
    for (const tb of toolBlocks) {
      const allowed = await consumeToolCall(ctx.userId)
      if (!allowed.allowed) {
        truncated = 'tools'
        toolResults.push({
          type: 'tool_result',
          tool_use_id: tb.id,
          content: 'Limite diário de tool calls atingido.',
          is_error: true,
        })
        continue
      }
      const result = await runTool(tb.name!, tb.input ?? {}, ctx)
      toolsUsed.push(tb.name!)
      toolResults.push({
        type: 'tool_result',
        tool_use_id: tb.id,
        content: result,
      })
    }
    messages.push({ role: 'user', content: toolResults })

    if (truncated === 'tools') break

    if (i === MAX_TOOL_ITERATIONS - 1) {
      truncated = 'loop'
    }
  }

  if (!finalText.trim()) finalText = 'Não consegui formular uma resposta. Tente reformular?'
  finalText = semMarcaDeCodigo(finalText)

  await pushTurn(ctx.userId, ctx.channelId, { role: 'assistant', content: finalText, ts: Date.now() })

  const total = await countTurns(ctx.userId, ctx.channelId)
  if (total >= SUMMARY_TRIGGER) {
    void maybeSummarize(ctx.userId, ctx.channelId).catch((e) =>
      logger.error('Bot', `summarize falhou: ${(e as Error).message}`)
    )
  }

  botInvocationsTotal.inc({ status: truncated ?? 'ok' })
  return { text: finalText, toolsUsed, truncated }
}

interface ComandoBot {
  sufixo:      string
  description: string
  category:    string
  args?:       string   
  exemplo?:    string   
  so?:         'sparxie' 
}

const COMANDOS: ComandoBot[] = [
  {
    sufixo: '', category: 'Conversa', args: '<sua pergunta>',
    description: 'conversa comigo — lembro das últimas 24h deste canal',
    exemplo: 'do que a gente falou ontem?',
  },
  { sufixo: 'ajuda',  category: 'Utilitários', description: 'mostra esta lista' },
  { sufixo: 'reset',  category: 'Conversa',    description: 'apaga minha memória deste canal' },
  { sufixo: 'ping',   category: 'Utilitários', description: 'testa a latência' },
  { sufixo: 'status', category: 'Utilitários', description: 'status da plataforma' },
  { sufixo: 'mute',   category: 'Moderação',   description: 'verifica se você está silenciado' },
  {
    sufixo: 'quem mandou', category: 'Diversão',
    description: 'sorteia uma mensagem antiga daqui e a galera adivinha quem escreveu',
  },
  { sufixo: 'revelar', category: 'Diversão', description: 'conta quem tinha mandado' },
  {
    sufixo: 'shipp', category: 'Diversão', args: '@um @outro',
    description: 'mede a compatibilidade', exemplo: 'shipp @ana @bia',
  },
  {
    sufixo: 'sorteio', category: 'Diversão', args: '[@marcados]',
    description: 'sorteia entre os marcados, ou entre a constelação inteira',
  },
  { sufixo: 'moeda', category: 'Diversão', description: 'cara ou coroa' },
  {
    sufixo: 'dado', category: 'Diversão', args: '<2d6>',
    description: 'rola dados', exemplo: 'dado 2d6',
  },
  {
    sufixo: 'escolha', category: 'Diversão', args: '<a, b, c>',
    description: 'escolho por você', exemplo: 'escolha pizza, sushi, hambúrguer',
  },
  { sufixo: 'ranking', category: 'Progressão', description: 'top 10 por nível desta constelação' },
  {
    sufixo: 'perfil', category: 'Progressão', args: '[@alguém]',
    description: 'nível, XP e brilho', exemplo: 'perfil @ana',
  },
  {
    sufixo: 'lembrete', category: 'Utilitários', args: '<quando> <o quê>',
    description: 'te chamo depois', exemplo: 'lembrete 20min terminar o trabalho',
  },
  { sufixo: 'resumo', category: 'Utilitários', description: 'o que rolou hoje nesta órbita' },
  {
    sufixo: 'desejo', category: 'Fim de semana', args: '<seu desejo>', so: 'sparxie',
    description: 'joga um desejo na estrela cadente',
    exemplo: 'desejo passar de ano',
  },
  { sufixo: 'festa', category: 'Fim de semana', description: 'sorteia um programa pro fim de semana', so: 'sparxie' },
]

export function chaveDoComando(sufixo: string): string {
  return sufixo === '' ? 'conversa' : sufixo.replace(/\s+/g, '-')
}

export function catalogoDeComandos(): Array<{ chave: string; rotulo: string; categoria: string; descricao: string }> {
  return COMANDOS.map((c) => ({
    chave:     chaveDoComando(c.sufixo),
    rotulo:    c.sufixo || 'conversa',
    categoria: c.category,
    descricao: c.description,
  }))
}

export async function comandosDesligados(serverId?: string): Promise<ReadonlySet<string>> {
  if (!serverId) return new Set()
  try {
    const [s] = await db.select({ lista: servers.botDisabledCommands })
      .from(servers).where(eq(servers.id, serverId)).limit(1)
    if (!s?.lista) return new Set()
    return new Set(s.lista.split(',').map((x: string) => x.trim()).filter(Boolean))
  } catch {
    return new Set()
  }
}

export function comandosDeHoje(agora: Date = new Date()): Array<{ name: string; description: string; category: string }> {
  const p = personaDoDia(agora)
  return COMANDOS
    .filter((c) => !c.so || c.so === p.chave)
    .map((c) => {
      const base = c.sufixo ? `${p.prefixo} ${c.sufixo}` : p.prefixo
      return {
        name:        c.args ? `${base} ${c.args}` : base,
        description: c.exemplo ? `${c.description} · ex.: ${p.prefixo} ${c.exemplo}` : c.description,
        category:    c.category,
      }
    })
}

const PROGRAMAS_DE_FDS = [
  'maratona de série com a call aberta',
  'jogatina em grupo — quem entrar por último escolhe o jogo',
  'noite de filme ruim: cada um indica o pior que já viu',
  'call de música: cada um manda uma que ninguém conhece',
  'sessão de fotos antigas no chat',
  'campeonato improvisado de qualquer coisa',
  'sair do PC e voltar com uma história pra contar',
]

export async function handleBotCommand(
  content: string,
  extras: {
    username: string; isMuted: boolean; muteSecondsLeft: number
    userId?: string; channelId?: string
    serverId?: string
  },
): Promise<string | null> {
  const persona = personaDoDia()
  const arg     = semPrefixo(content)
  const lower   = arg.toLowerCase()
  const verbo   = lower.split(/\s+/)[0] ?? ''

  const desligados = await comandosDesligados(extras.serverId)
  if (desligados.size > 0) {
    const conhecido = COMANDOS.find((c) =>
      c.sufixo !== '' && (lower === c.sufixo || lower.startsWith(`${c.sufixo} `)),
    )
    const chave = conhecido ? chaveDoComando(conhecido.sufixo) : 'conversa'
    if (desligados.has(chave)) {
      return conhecido
        ? `\`${persona.prefixo} ${conhecido.sufixo}\` está desligado nesta constelação.`
        : 'a conversa comigo está desligada nesta constelação. os comandos continuam valendo.'
    }
  }

  const chamou = prefixoUsado(content)
  const trocado =
    (chamou === '/sparkle' && persona.chave === 'sparxie') ||
    (chamou === '/sparxie' && persona.chave === 'sparkle')
  const nota = trocado
    ? `\n\n_(${chamou === '/sparkle' ? 'a Sparkle folga no fim de semana' : 'a Sparxie só aparece sábado e domingo'} — quem responde agora sou eu, ${persona.nome} ${persona.emoji}. Use \`${persona.prefixo}\`.)_`
    : ''

  if (verbo === 'help' || verbo === 'ajuda') {
    return [
      `**${persona.nome} ${persona.emoji} — comandos de hoje:**`,
      ...comandosDeHoje().map((c) => `\`${c.name}\` — ${c.description}`),
      '',
      ehTurnoDaSparxie()
        ? '_Sexta e sábado são meus: os comandos de festa e desejo só existem agora._'
        : '_Sexta e sábado quem atende é a Sparxie, com comandos que só rolam no turno dela._',
      '',
      'Tenho ferramentas pra buscar mensagens, resumir o canal e olhar info de membros. Pergunta naturalmente.',
    ].join('\n') + nota
  }

  if (verbo === 'reset') {
    if (!extras.userId || !extras.channelId) return null
    await clearMemory(extras.userId, extras.channelId)
    return '✓ Memória limpa neste canal.' + nota
  }

  if (verbo === 'ping')   return `🏓 Pong, @${extras.username}!` + nota
  if (verbo === 'status') return '✅ Todos os sistemas operacionais.' + nota

  const resto = arg.slice(verbo.length).trim()
  const precisaDeSala = extras.serverId && extras.channelId
  switch (verbo) {
    case 'moeda':   return moeda() + nota
    case 'dado':    return dado(resto) + nota
    case 'escolha': return escolha(resto) + nota
    case 'shipp':   return (await shipp(resto)) + nota
    case 'sorteio':
      if (!extras.serverId) return null
      return (await sorteio(extras.serverId, resto)) + nota
    case 'ranking':
      if (!extras.serverId) return null
      return (await ranking(extras.serverId)) + nota
    case 'perfil':
      if (!extras.userId) return null
      return (await perfilXp(resto, extras.userId)) + nota
    case 'revelar':
      if (!extras.channelId) return null
      return (await revelar(extras.channelId)) + nota
    case 'resumo':
      if (!extras.channelId) return null
      return (await resumoDoDia(extras.channelId)) + nota
    case 'lembrete':
      if (!precisaDeSala || !extras.userId) return null
      return (await lembrete(resto, extras.userId, extras.channelId!)) + nota
  }
  if (verbo === 'quem' && lower.startsWith('quem mandou')) {
    if (!extras.channelId) return null
    return (await quemMandou(extras.channelId)) + nota
  }

  if (verbo === 'mute' || verbo === 'silenciado') {
    if (extras.isMuted) {
      const mins = Math.ceil(extras.muteSecondsLeft / 60)
      return `🔇 Você está silenciado por aproximadamente **${mins} minuto(s)**.` + nota
    }
    return '🔊 Você não está silenciado.' + nota
  }

  if (verbo === 'desejo' || verbo === 'festa') {
    if (!ehTurnoDaSparxie()) {
      return `${persona.emoji} \`${verbo}\` é coisa da Sparxie — ela entra sexta. Até lá quem cuida do plantão sou eu, ${persona.nome}.`
    }
    if (verbo === 'festa') {
      const escolha = PROGRAMAS_DE_FDS[Math.floor(Math.random() * PROGRAMAS_DE_FDS.length)]
      return `✧ Programa de fim de semana sorteado: **${escolha}**.`
    }
    const desejo = arg.slice(verbo.length).trim()
    if (desejo.length < 4) return '✧ Escreve o desejo junto: `/sparxie desejo quero...`'
    if (!extras.userId) return null
    try {
      await db.insert(wishingStars).values({ userId: extras.userId, content: desejo.slice(0, 500) })
      return `✧ Desejo lançado, @${extras.username}. A estrela levou.`
    } catch {
      return '✧ A estrela passou rápido demais — tenta de novo.'
    }
  }

  return null
}

function historyToMessages(history: MemoryTurn[]): any[] {

  const out: any[] = []
  let lastRole: 'user' | 'assistant' | null = null
  for (const t of history) {
    if (t.role === lastRole && out.length > 0) {
      out[out.length - 1].content += `\n${t.content}`
    } else {
      out.push({ role: t.role, content: t.content })
      lastRole = t.role
    }
  }
  return out
}

async function maybeSummarize(userId: string, channelId: string): Promise<void> {
  const history = await getHistory(userId, channelId, 200)
  if (history.length <= WORKING_WINDOW) return

  const toSummarize = history.slice(0, history.length - WORKING_WINDOW)
  const cutoffTs    = toSummarize[toSummarize.length - 1].ts

  const transcript = toSummarize.map((t) =>
    `[${t.role === 'user' ? 'USER' : 'ASTRA'}]: ${t.content}`
  ).join('\n')

  const text = await gerarTexto(
    MODELO_RESUMO,
    'Você comprime conversas. Resuma fatos relevantes em 2-4 frases curtas, em português. Mantenha decisões, preferências do user, fatos sobre o canal. Não invente nada.',
    `Resuma esta conversa:\n\n${transcript}`,
    200,
  )
  if (!text) return

  await setSummary(userId, channelId, {
    text,
    turnsCovered: toSummarize.length,
    createdAt:    Date.now(),
  }, cutoffTs + 1)

  logger.info('Bot', `summarized ${toSummarize.length} turns for ${userId}@${channelId}`)
}
