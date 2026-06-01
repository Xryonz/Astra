/**
 * Tools que o bot pode chamar via Anthropic tool-use.
 *
 * Cada tool tem:
 *  - definition: { name, description, input_schema } — vai pro Claude
 *  - handler: (input, ctx) => result string — executa server-side
 *
 * Read-only por enquanto (Fase 1). Tools com side-effects (createReminder,
 * etc) entram em Fase 2 quando tiver worker pra disparar.
 */
import { and, desc, eq, ilike, isNull } from 'drizzle-orm'
import { db } from '../db'
import { messages, channels, serverMembers, users, servers, reminders } from '../db/schema'
import { getMemberPerms } from './permissions'
import { parseDuration } from './reminders'
import { logger } from './logger'

export interface BotContext {
  userId:    string
  channelId: string
  serverId:  string | null  // null se for DM
  username:  string
}

export interface ToolDefinition {
  name:         string
  description:  string
  input_schema: Record<string, unknown>
}

type ToolHandler = (input: any, ctx: BotContext) => Promise<string>

// ─── search_messages ──────────────────────────────────────────
const searchMessagesDef: ToolDefinition = {
  name: 'search_messages',
  description:
    'Busca mensagens no canal atual por palavra-chave. Use quando o user perguntar "alguém falou X?" ou referir mensagens antigas.',
  input_schema: {
    type: 'object',
    properties: {
      query: { type: 'string', description: 'Palavra-chave (3+ caracteres)', minLength: 3, maxLength: 80 },
      limit: { type: 'integer', description: 'Quantas resultados (max 10)', default: 5, maximum: 10, minimum: 1 },
    },
    required: ['query'],
  },
}
const searchMessages: ToolHandler = async (input, ctx) => {
  const query = String(input?.query ?? '').trim()
  const limit = Math.min(10, Math.max(1, Number(input?.limit ?? 5)))
  if (query.length < 3) return 'Erro: query muito curta. Mínimo 3 caracteres.'

  const rows = await db.select({
    content:    messages.content,
    createdAt:  messages.createdAt,
    authorName: users.displayName,
  })
    .from(messages)
    .innerJoin(users, eq(users.id, messages.authorId))
    .where(and(
      eq(messages.channelId, ctx.channelId),
      isNull(messages.deletedAt),
      ilike(messages.content, `%${query}%`),
    ))
    .orderBy(desc(messages.createdAt))
    .limit(limit)

  if (rows.length === 0) return `Nenhuma mensagem encontrada com "${query}".`
  return rows.map((r, i) =>
    `${i + 1}. [${r.authorName}, ${r.createdAt.toISOString().slice(0, 16).replace('T', ' ')}]: ${r.content.slice(0, 200)}`
  ).join('\n')
}

// ─── summarize_channel ────────────────────────────────────────
const summarizeChannelDef: ToolDefinition = {
  name: 'summarize_channel',
  description:
    'Pega as últimas N mensagens do canal e devolve um resumo factual (não interpretado). Use quando o user pedir "do que tão falando?" ou "resume o que perdi".',
  input_schema: {
    type: 'object',
    properties: {
      last_n: { type: 'integer', description: 'Quantas mensagens recentes considerar', default: 30, maximum: 100, minimum: 5 },
    },
  },
}
const summarizeChannel: ToolHandler = async (input, ctx) => {
  const lastN = Math.min(100, Math.max(5, Number(input?.last_n ?? 30)))
  const rows = await db.select({
    content:    messages.content,
    createdAt:  messages.createdAt,
    authorName: users.displayName,
  })
    .from(messages)
    .innerJoin(users, eq(users.id, messages.authorId))
    .where(and(eq(messages.channelId, ctx.channelId), isNull(messages.deletedAt)))
    .orderBy(desc(messages.createdAt))
    .limit(lastN)

  if (rows.length === 0) return 'O canal está vazio.'

  // Retorna em ordem cronológica (mais antiga primeiro)
  const ordered = rows.reverse()
  const transcript = ordered.map((r) =>
    `[${r.authorName}]: ${r.content.slice(0, 240)}`
  ).join('\n')

  return `Últimas ${ordered.length} mensagens do canal:\n${transcript}`
}

// ─── get_server_info ──────────────────────────────────────────
const getServerInfoDef: ToolDefinition = {
  name: 'get_server_info',
  description:
    'Retorna nome do servidor atual, lista de canais, contagem de membros e suas permissões. Use quando o user perguntar sobre o servidor.',
  input_schema: { type: 'object', properties: {} },
}
const getServerInfo: ToolHandler = async (_input, ctx) => {
  if (!ctx.serverId) return 'Você está em uma DM, não em servidor.'

  const [[srv], chList, memberRows, perms] = await Promise.all([
    db.select({ id: servers.id, name: servers.name, isGroup: servers.isGroup, ownerId: servers.ownerId })
      .from(servers).where(eq(servers.id, ctx.serverId)).limit(1),
    db.select({ id: channels.id, name: channels.name, type: channels.type })
      .from(channels).where(eq(channels.serverId, ctx.serverId)),
    db.select({ id: serverMembers.id }).from(serverMembers).where(eq(serverMembers.serverId, ctx.serverId)),
    getMemberPerms(ctx.userId, ctx.serverId),
  ])

  if (!srv) return 'Servidor não encontrado.'

  return [
    `Servidor: ${srv.name}${srv.isGroup ? ' (grupo)' : ''}`,
    `Canais (${chList.length}): ${chList.map((c) => `#${c.name}`).join(', ')}`,
    `Membros: ${memberRows.length}`,
    `Suas permissões: ${perms.isOwner ? 'OWNER (todas)' : perms.permissions.size > 0 ? Array.from(perms.permissions).join(', ') : 'apenas leitura'}`,
  ].join('\n')
}

// ─── recall_user ──────────────────────────────────────────────
const recallUserDef: ToolDefinition = {
  name: 'recall_user',
  description:
    'Info pública de um usuário por username. Retorna displayName, bio e se é membro do servidor atual. Use quando o user perguntar "quem é @fulano?".',
  input_schema: {
    type: 'object',
    properties: {
      username: { type: 'string', description: 'Username sem @', minLength: 1, maxLength: 30 },
    },
    required: ['username'],
  },
}
const recallUser: ToolHandler = async (input, ctx) => {
  const uname = String(input?.username ?? '').toLowerCase().replace(/^@/, '').trim()
  if (!uname) return 'Erro: username vazio.'

  const [u] = await db.select({
    id: users.id, username: users.username, displayName: users.displayName, bio: users.bio,
  })
    .from(users).where(eq(users.username, uname)).limit(1)
  if (!u) return `Usuário @${uname} não encontrado.`

  let inServer = 'não aplicável (DM)'
  if (ctx.serverId) {
    const [m] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, u.id), eq(serverMembers.serverId, ctx.serverId)))
      .limit(1)
    inServer = m ? 'sim' : 'não'
  }

  return [
    `Username: @${u.username}`,
    `Nome: ${u.displayName}`,
    `Bio: ${u.bio ?? '(sem bio)'}`,
    `Membro do servidor atual: ${inServer}`,
  ].join('\n')
}

// ─── create_reminder ──────────────────────────────────────────
const createReminderDef: ToolDefinition = {
  name: 'create_reminder',
  description:
    'Cria um lembrete pra o user no futuro. Use quando o user pedir "me lembre", "criar reminder", "agendar lembrete". O sistema dispara push + notif no horário.',
  input_schema: {
    type: 'object',
    properties: {
      content:  { type: 'string', description: 'Sobre o que lembrar (max 500 chars)', minLength: 1, maxLength: 500 },
      duration: { type: 'string', description: 'Quanto tempo no futuro. Ex: "10m", "2h", "1d", "1h30m". Min 1min, max 1 ano.' },
    },
    required: ['content', 'duration'],
  },
}
const createReminder: ToolHandler = async (input, ctx) => {
  const content = String(input?.content ?? '').slice(0, 500).trim()
  const duration = String(input?.duration ?? '')
  if (!content) return 'Erro: content vazio.'
  const ms = parseDuration(duration)
  if (ms == null) return `Erro: duração '${duration}' inválida. Use formatos como "10m", "2h", "1d".`

  const dueAt = new Date(Date.now() + ms)
  const [r] = await db.insert(reminders).values({
    creatorId: ctx.userId, targetUserId: ctx.userId, content,
    channelId: ctx.channelId, dueAt,
  }).returning({ id: reminders.id, dueAt: reminders.dueAt })

  return `Lembrete criado: "${content}" — disparo em ${r.dueAt.toLocaleString('pt-BR')}.`
}

// ─── Registry ─────────────────────────────────────────────────

const REGISTRY: Record<string, { def: ToolDefinition; handler: ToolHandler }> = {
  search_messages:    { def: searchMessagesDef,    handler: searchMessages },
  summarize_channel:  { def: summarizeChannelDef,  handler: summarizeChannel },
  get_server_info:    { def: getServerInfoDef,     handler: getServerInfo },
  recall_user:        { def: recallUserDef,        handler: recallUser },
  create_reminder:    { def: createReminderDef,    handler: createReminder },
}

export const TOOL_DEFINITIONS: ToolDefinition[] = Object.values(REGISTRY).map((r) => r.def)

/**
 * Executa uma tool pelo nome. Retorna texto pra mandar de volta ao Claude.
 * Erros são capturados e retornados como texto (Claude lida bem com isso).
 */
export async function runTool(name: string, input: any, ctx: BotContext): Promise<string> {
  const entry = REGISTRY[name]
  if (!entry) return `Erro: tool desconhecida '${name}'.`
  try {
    return await entry.handler(input, ctx)
  } catch (err) {
    logger.error('botTools', `tool ${name} falhou`, (err as Error).message)
    return `Erro ao executar ${name}: ${(err as Error).message}`
  }
}
