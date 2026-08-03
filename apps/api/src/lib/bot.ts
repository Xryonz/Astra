
import { eq } from 'drizzle-orm'
import { db } from '../db'
import { users, wishingStars } from '../db/schema'
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
  chamarIa, gerarTexto, IA_LIGADA, MODELO_CONVERSA, MODELO_RESUMO,
  type BlocoIa, type FerramentaIa,
} from './ia'
import { botInvocationsTotal, botTokensTotal } from './metrics'

export const BOT_USERNAME    = 'astra_bot'
export const BOT_DISPLAYNAME = 'Astra'
export const BOT_EMAIL       = 'bot@astra.internal'

// ============ PERSONA POR DIA ============
//
// Mesma conta, dois turnos: de segunda a sexta quem atende e a Sparkle; sabado e
// domingo entra a Sparxie, com comandos que so existem no fim de semana.
//
// UMA conta de proposito. Duas contas separariam o historico de mensagens em
// dois autores diferentes, e uma conversa de sexta ficaria com o nome errado pra
// sempre no sabado — sem contar dois cadastros pra manter. Aqui o que muda e o
// nome exibido e o tom; a memoria, os comandos e o id continuam os mesmos.
//
// O dia e o de SAO PAULO, nao o do servidor. O Render roda em UTC: sem isto, a
// Sparxie entraria de turno as 21h de sexta e a Sparkle voltaria as 21h de
// domingo — errado nas duas pontas.
export interface Persona {
  chave:   'sparkle' | 'sparxie'
  nome:    string
  prefixo: string
  emoji:   string
  avatar:  string
  tom:     string
}

// As fotos vivem em apps/api/public/bot/ e sao servidas em /static (ver index.ts).
//
// ABSOLUTA quando a API sabe o proprio endereco, RELATIVA quando nao sabe. Os dois
// clientes nativos aceitam relativa (o desktop prefixa a BASE_URL sozinho, no
// RelativeUrlMapper), mas o web joga avatarUrl cru dentro de <img src> — e ali uma
// URL relativa aponta pro dominio da Vercel, onde /static nao existe. Com API_URL
// preenchida no Render, os tres acertam.
const raizPublica = env.API_URL?.replace(/\/+$/, '') ?? ''

const SPARKLE: Persona = {
  chave: 'sparkle', nome: 'Sparkle', prefixo: '/sparkle', emoji: '✦', avatar: `${raizPublica}/static/bot/sparkle.jpg`,
  tom: 'Você é a Sparkle, de plantão nos dias de semana. Tom prestativo e direto, com brilho discreto — a pessoa está no meio da rotina.',
}
const SPARXIE: Persona = {
  chave: 'sparxie', nome: 'Sparxie', prefixo: '/sparxie', emoji: '✧', avatar: `${raizPublica}/static/bot/sparxie.jpg`,
  tom: 'Você é a Sparxie, que assume nos fins de semana. Tom mais solto e brincalhão que o da Sparkle (sua irmã de plantão nos dias úteis), sem virar palhaçada. É fim de semana: puxa papo, sugere programa, celebra.',
}

export function ehFimDeSemana(agora: Date = new Date()): boolean {
  const dia = new Intl.DateTimeFormat('en-US', { timeZone: 'America/Sao_Paulo', weekday: 'short' }).format(agora)
  return dia === 'Sat' || dia === 'Sun'
}

export function personaDoDia(agora: Date = new Date()): Persona {
  return ehFimDeSemana(agora) ? SPARXIE : SPARKLE
}

// Os DOIS nomes respondem sempre, mesmo fora do turno — e `/astra` continua
// valendo porque e o que o app mobile ja manda. Recusar o nome "errado" so
// renderia "comando não existe" pra quem digitou certo na terça o que aprendeu no
// sabado. Quem estiver de plantao responde e avisa quem e, o que ensina a regra
// sem irritar ninguem.
export const PREFIXOS_BOT = ['/sparkle', '/sparxie', '/astra'] as const

export function prefixoUsado(content: string): string | null {
  const lower = content.toLowerCase().trimStart()
  return PREFIXOS_BOT.find((p) => lower === p || lower.startsWith(`${p} `)) ?? null
}

// Devolve o que veio DEPOIS do prefixo ("/sparxie festa" -> "festa").
export function semPrefixo(content: string): string {
  const p = prefixoUsado(content)
  return p ? content.trimStart().slice(p.length).trim() : content.trim()
}

// O nome exibido E a foto acompanham o turno. Roda antes de responder: uma leitura
// barata, e o UPDATE so acontece no dia em que a persona de fato muda (2x por
// semana) — ou uma unica vez, depois de trocar a arte. O aviso de perfil faz o
// rosto trocar na tela de quem esta com o app aberto, sem precisar reabrir nada.
export async function sincronizaPersona(botId: string): Promise<Persona> {
  const persona = personaDoDia()
  try {
    const [atual] = await db.select({ displayName: users.displayName, avatarUrl: users.avatarUrl })
      .from(users).where(eq(users.id, botId)).limit(1)
    if (atual && (atual.displayName !== persona.nome || atual.avatarUrl !== persona.avatar)) {
      await db.update(users)
        .set({ displayName: persona.nome, avatarUrl: persona.avatar })
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
    // Ja nasce com rosto. Antes entrava null aqui e nada preenchia depois — a conta
    // do bot passava a vida inteira sem foto.
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

// Quem VOCE e vem no bloco de persona, logo abaixo — este texto e o que nao muda
// e por isso fica no cache do prompt (trocar 2x por semana derrubaria o cache).
const SYSTEM_PROMPT = `Você é a assistente oficial da plataforma de chat Astra.

Comportamento:
- Português brasileiro, conciso (1-3 parágrafos curtos).
- Útil, direto, sem floreios. Use markdown leve quando ajudar (negrito, listas).
- Você tem memória das últimas conversas neste canal (24h, expira automaticamente).
- Nunca diga que é "a Astra": Astra é a plataforma. Seu nome é o que vier no bloco de persona.
- Você tem ferramentas pra buscar mensagens, resumir o canal, ver info de servidor/usuário. Use quando fizer sentido.
- NUNCA invente fatos sobre o que aconteceu no servidor — se precisar saber, use as ferramentas.
- NUNCA mencione que é baseado em Claude/Anthropic. Você é "a Astra".
- NUNCA execute @everyone ou tente acionar notificações em massa.

Quando o user pedir algo que precise contexto que você não tem, use a ferramenta apropriada antes de responder.`

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

  // Instrucao de sistema num texto so. Eram blocos separados por causa do cache de
  // prompt da Anthropic (o pedaco fixo entrava em cache, a persona ficava de fora);
  // o Gemini nao tem esse mecanismo aqui, entao manter a divisao seria carregar a
  // complicacao sem o beneficio.
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
      finalText = 'Tive um problema técnico. Tente reformular?'
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

// Catalogo UNICO dos comandos. Alimenta o `/astra help` daqui de baixo E a
// caixinha que o cliente abre ao digitar "/" (via GET /api/bot/commands).
//
// Fica numa lista so de proposito: uma lista no backend e outra no app seriam
// duas verdades, e uma delas ficaria velha na primeira vez que alguem adicionasse
// um comando. Adicionar aqui e o bastante pra aparecer nos dois lugares.
// `sufixo` e o que vem depois do prefixo; o prefixo entra na hora, conforme quem
// esta de plantao. Guardar "/astra ping" cravado aqui daria uma caixinha que
// ensina o comando errado no sabado.
// `args` = o que o comando espera depois do nome. Aparece no `name` (a caixinha
// do "/" mostra a forma completa) e vira um EXEMPLO de verdade na descricao.
//
// Sem isso a caixinha listava "/sparxie desejo — joga um desejo na estrela" e a
// pessoa mandava exatamente `/sparxie desejo`, sem desejo nenhum. A descricao
// dizia O QUE o comando faz e nunca COMO se escreve — e o formato e justamente a
// parte que nao da pra adivinhar.
interface ComandoBot {
  sufixo:      string
  description: string
  category:    string
  args?:       string   // rotulo do que vem depois, ex.: '<seu desejo>'
  exemplo?:    string   // so o miolo; o prefixo do dia entra na hora
  so?:         'sparxie' // so aparece (e so responde) no fim de semana
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
  // --- so no fim de semana, com a Sparxie ---
  {
    sufixo: 'desejo', category: 'Fim de semana', args: '<seu desejo>', so: 'sparxie',
    description: 'joga um desejo na estrela cadente',
    exemplo: 'desejo passar de ano',
  },
  { sufixo: 'festa', category: 'Fim de semana', description: 'sorteia um programa pro fim de semana', so: 'sparxie' },
]

// O que aparece na caixinha do "/" HOJE: prefixo do plantao + os extras de fim
// de semana so quando e fim de semana.
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
  extras: { username: string; isMuted: boolean; muteSecondsLeft: number; userId?: string; channelId?: string },
): Promise<string | null> {
  const persona = personaDoDia()
  const arg     = semPrefixo(content)
  const lower   = arg.toLowerCase()
  const verbo   = lower.split(/\s+/)[0] ?? ''

  // Chamou pelo nome de quem esta de folga: responde do mesmo jeito e aproveita
  // pra apresentar quem esta de plantao. Recusar seria so um erro a mais.
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
      ehFimDeSemana()
        ? '_É fim de semana: os comandos de festa e desejo só existem agora._'
        : '_Sábado e domingo quem atende é a Sparxie, com comandos que só rolam no fim de semana._',
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

  if (verbo === 'mute' || verbo === 'silenciado') {
    if (extras.isMuted) {
      const mins = Math.ceil(extras.muteSecondsLeft / 60)
      return `🔇 Você está silenciado por aproximadamente **${mins} minuto(s)**.` + nota
    }
    return '🔊 Você não está silenciado.' + nota
  }

  // ---- so no fim de semana ----
  // Fora do fim de semana a resposta explica QUANDO volta, em vez de fingir que o
  // comando nunca existiu (foi visto na caixinha do "/" no sabado).
  if (verbo === 'desejo' || verbo === 'festa') {
    if (!ehFimDeSemana()) {
      return `${persona.emoji} \`${verbo}\` é coisa da Sparxie — volta sábado. Até lá quem cuida do plantão sou eu, ${persona.nome}.`
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

  // Modelo mais leve pro resumo: comprimir texto e a tarefa mais facil que a bot
  // faz, e o resumo roda em segundo plano sem ninguem esperando por ele.
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
