import { Router, Request, Response } from 'express'
import { z } from 'zod'
import { and, asc, eq, inArray, isNull, sql } from 'drizzle-orm'
import { NAO_E_BOT } from '../lib/contagemDeMembros'
import { db } from '../db'
import { servers, serverMembers, channels, channelCategories, channelRolePerms, users, roles, memberRoles, serverBans, auditLogs, messages, friendships, notifications } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { saudarNovoMembro } from '../lib/botAvisos'
import { CreateServerSchema, CreateChannelSchema } from '@astra/types'
import { PERMS, getMemberPerms, filterVisibleChannels } from '../lib/permissions'
import { AUDIT, audit } from '../lib/audit'
import { createId } from '../db/cuid'
import { invalidateMembersCache } from '../lib/membersCache'
import { redis, presenceKeys } from '../lib/redis'
import { unmuteUser } from '../lib/spamDetector'
import { persistDataUri, persistImagemDeExibicao, isOwnStorageUrl } from '../lib/storage'
import { garantirBotNaConstelacao } from '../lib/botMembership'
import { catalogoDeComandos } from '../lib/bot'
import { channelsChanged, membersChanged, joinedServer, serverUpdated, serverGone, leftServer } from '../lib/realtime'

export const serversRouter = Router()

// Conta membros ONLINE por servidor via presenca ao vivo (Redis). Um unico MGET
// pra todos os usuarios distintos -> 1 round-trip. Nunca derruba a request: se o
// Redis falhar, devolve mapa vazio (o cliente cai em 0 online).
async function onlineCountByServer(serverIds: string[]): Promise<Map<string, number>> {
  const result = new Map<string, number>()
  if (serverIds.length === 0) return result
  try {
    const memberRows = await db.select({ serverId: serverMembers.serverId, userId: serverMembers.userId })
      .from(serverMembers)
      .where(inArray(serverMembers.serverId, serverIds))
    const uniqUserIds = [...new Set(memberRows.map((m) => m.userId))]
    if (uniqUserIds.length === 0) return result
    const values = await redis.mget(uniqUserIds.map((id) => presenceKeys.user(id)))
    const online = new Set<string>()
    uniqUserIds.forEach((id, i) => {
      const v = values[i]
      if (v && v !== 'INVISIBLE') online.add(id)
    })
    for (const m of memberRows) {
      if (online.has(m.userId)) result.set(m.serverId, (result.get(m.serverId) ?? 0) + 1)
    }
  } catch (e) {
    console.warn('[servers] online count indisponivel:', (e as Error).message)
  }
  return result
}

// Le categorias sem derrubar a request se a tabela ChannelCategory ainda nao
// existir no banco (o boot ensureCategorySchema deve cria-la, mas nao dependemos
// disso: categorias sao opcionais, uma constelacao sem elas so mostra canais soltos).
async function safeCategoryRows(serverIds: string[]) {
  if (serverIds.length === 0) return []
  try {
    return await db.select().from(channelCategories)
      .where(inArray(channelCategories.serverId, serverIds))
      .orderBy(asc(channelCategories.position))
  } catch (e) {
    console.warn('[servers] categorias indisponiveis:', (e as Error).message)
    return []
  }
}

async function listServersForUser(userId: string) {

  const myMemberships = await db.select({ serverId: serverMembers.serverId })
    .from(serverMembers)
    .where(eq(serverMembers.userId, userId))

  const serverIds = myMemberships.map((m) => m.serverId)
  if (serverIds.length === 0) return []

  const [srvRows, chRows, countRows, catRows, onlineByServer] = await Promise.all([
    db.select().from(servers).where(inArray(servers.id, serverIds)).orderBy(asc(servers.createdAt)),
    db.select().from(channels).where(inArray(channels.serverId, serverIds)).orderBy(asc(channels.position), asc(channels.createdAt)),
    db.select({ serverId: serverMembers.serverId, count: sql<number>`count(*)::int` })
      .from(serverMembers)
      .where(and(inArray(serverMembers.serverId, serverIds), NAO_E_BOT))
      .groupBy(serverMembers.serverId),
    safeCategoryRows(serverIds),
    onlineCountByServer(serverIds),
  ])

  const channelIds = chRows.map((c) => c.id)
  let lastByChannel = new Map<string, Date>()
  if (channelIds.length > 0) {
    const lastRows = await db.select({
      channelId: messages.channelId,
      lastAt:    sql<Date>`MAX(${messages.createdAt})`.as('lastAt'),
    })
      .from(messages)
      .where(inArray(messages.channelId, channelIds))
      .groupBy(messages.channelId)
    lastByChannel = new Map(lastRows.map((r) => [r.channelId, r.lastAt]))
  }

  const visible = await filterVisibleChannels(userId, channelIds)
  const channelsByServer = new Map<string, Array<typeof chRows[number] & { lastMessageAt: Date | null }>>()
  for (const c of chRows) {
    if (!visible.has(c.id)) continue
    const enriched = { ...c, lastMessageAt: lastByChannel.get(c.id) ?? null }
    const arr = channelsByServer.get(c.serverId) ?? []
    arr.push(enriched)
    channelsByServer.set(c.serverId, arr)
  }
  const countByServer = new Map(countRows.map((r) => [r.serverId, r.count]))
  const catsByServer = new Map<string, Array<typeof catRows[number]>>()
  for (const c of catRows) {
    const arr = catsByServer.get(c.serverId) ?? []
    arr.push(c)
    catsByServer.set(c.serverId, arr)
  }

  return srvRows.map((s) => ({
    ...s,
    channels:   channelsByServer.get(s.id) ?? [],
    categories: catsByServer.get(s.id) ?? [],
    _count:     { members: countByServer.get(s.id) ?? 0, online: onlineByServer.get(s.id) ?? 0 },
  }))
}

async function serverWithChannelsAndCount(serverId: string) {
  const [srv] = await db.select().from(servers).where(eq(servers.id, serverId)).limit(1)
  if (!srv) return null
  const [chRows, [countRow], catRows, onlineMap] = await Promise.all([
    db.select().from(channels).where(eq(channels.serverId, serverId)).orderBy(asc(channels.position), asc(channels.createdAt)),
    db.select({ count: sql<number>`count(*)::int` })
      .from(serverMembers)
      .where(and(eq(serverMembers.serverId, serverId), NAO_E_BOT)),
    safeCategoryRows([serverId]),
    onlineCountByServer([serverId]),
  ])
  return { ...srv, channels: chRows, categories: catRows, _count: { members: countRow?.count ?? 0, online: onlineMap.get(serverId) ?? 0 } }
}

serversRouter.get(
  '/',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const list = await listServersForUser(req.userId!)
    res.json({ data: list })
  })
)

serversRouter.post(
  '/',
  requireAuth,
  validate(CreateServerSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { name, iconUrl, isGroup = false } = req.body

    // O ICONE DA CRIACAO PASSAVA DIRETO PARA A COLUNA, e o PATCH logo abaixo ja fazia
    // tudo isto. Duas portas para a mesma coisa, uma trancada e outra nao.
    //
    // O `iconUrl` do CreateServerSchema e `z.string().url()`, e `url()` ACEITA data-URI —
    // `new URL('data:image/png;base64,...')` e valido. Sem limite de tamanho no schema, o
    // teto era o corpo da requisicao: 16mb. Ou seja, dava para nascer uma constelacao com
    // megabytes de base64 dentro da coluna `iconUrl`.
    //
    // E a coluna e lida com `db.select()` sem projecao, entao esse peso seria arrastado
    // em TODA listagem de constelacoes da pessoa, para sempre. O sintoma nao apontaria
    // para ca: seria "a barra lateral demora a abrir".
    //
    // Hoje nenhum cliente manda icone na criacao (o desktop manda so nome e tipo), o que
    // explica isto ter passado — mas rota aberta e rota que um dia alguem usa.
    if (iconUrl && !isAllowedIcon(iconUrl)) return res.status(422).json({ error: 'URL de ícone não permitida' })
    if (isIconTooBig(iconUrl)) return res.status(413).json({ error: 'Ícone muito grande (max 10MB)' })
    const icone = await persistImagemDeExibicao(iconUrl ?? null)

    const server = await db.transaction(async (tx) => {
      const [s] = await tx.insert(servers).values({
        name, iconUrl: icone.url, iconFullUrl: icone.original, isGroup, ownerId: req.userId!,
      }).returning()
      await tx.insert(serverMembers).values({ userId: req.userId!, serverId: s.id, role: 'OWNER' })
      // COM O QUE UMA CONSTELACAO NASCE.
      //
      // Nascia so com "geral", e faltava o principal: NAO HAVIA ONDE FALAR. Num app
      // em que a voz e metade do produto, quem criava e chamava os amigos descobria
      // que precisava criar canal antes de conseguir uma call — o primeiro ato do
      // grupo esbarrava numa tela de configuracao.
      //
      // "anuncios" vem junto porque ele so tem valor se ja existir quando a
      // constelacao cresce: criado depois, ninguem migra o habito pra ele.
      //
      // Os tres numa insercao so: sao a MESMA decisao ("o que existe no dia zero"),
      // e tres chamadas separadas convidariam alguem a mexer numa sem as outras.
      await tx.insert(channels).values([
        { name: 'geral',    type: 'TEXT',  serverId: s.id, position: 0 },
        { name: 'anuncios', type: 'TEXT',  serverId: s.id, position: 1 },
        // Nome com espaco e maiuscula de proposito: sala de voz nao e endereco de
        // texto (nao se digita "#sala-de-estar" pra mencionar), entao ela nao herda
        // a convencao de minuscula-com-hifen das orbitas de texto.
        { name: 'Sala de estar', type: 'VOICE', serverId: s.id, position: 2 },
      ])
      return s
    })

    // Sem categoria default: a constelacao nasce com as tres orbitas SOLTAS
    // (decisao do dono). Categorias ("tabelas") sao criadas depois, na mao.

    // A bot entra junto, com o cargo BOT. Fora da transacao de proposito: se algo
    // falhar aqui, a constelacao ja existe e vale — perder a criacao inteira porque
    // a bot nao entrou seria trocar um detalhe por um desastre. O guard de boot
    // alcanca quem ficou pra tras.
    void garantirBotNaConstelacao(server.id)

    const full = await serverWithChannelsAndCount(server.id)
    res.status(201).json({ data: full })
  })
)

const ALLOWED_ICON_HOSTS = [
  'i.imgur.com','media.giphy.com','cdn.discordapp.com','media.tenor.com',
  'i.postimg.cc','images.unsplash.com','lh3.googleusercontent.com',
  'pbs.twimg.com','media.discordapp.net','cdn.jsdelivr.net','raw.githubusercontent.com',
]
function isAllowedIcon(url: string | null | undefined): boolean {
  if (!url) return true
  if (url.startsWith('data:image/')) return true
  if (isOwnStorageUrl(url)) return true // URL que nos mesmos persistimos (R2 / /uploads)
  try { const { hostname } = new URL(url); return ALLOWED_ICON_HOSTS.some((h) => hostname === h || hostname.endsWith(`.${h}`)) }
  catch { return false }
}
// 10MB nos dois: e o teto que o cliente ja respeita (ImageCrop.HARD_MAX). Subiu de
// 5/8MB junto com a resolucao de saida (1024 no icone, 2560 no banner) — no tamanho
// novo, PNG com transparencia estourava o limite antigo e o salvar falhava.
function isIconTooBig(url: string | null | undefined): boolean {
  if (!url || !url.startsWith('data:')) return false
  return url.length * 0.75 > 10 * 1024 * 1024
}

function isBannerTooBig(url: string | null | undefined): boolean {
  if (!url || !url.startsWith('data:')) return false
  return url.length * 0.75 > 10 * 1024 * 1024
}

const UpdateServerSchema = z.object({
  name:      z.string().min(1).max(100).optional(),
  iconUrl:   z.string().optional().nullable(),
  bannerUrl: z.string().optional().nullable(),
  messageRetentionDays: z.number().int().min(0).max(365).optional().nullable(),
  isPublic:    z.boolean().optional(),
  description: z.string().max(200).optional().nullable(),
  bannerPositionY: z.number().int().min(0).max(100).optional(),
  // 50..300, a mesma faixa do banner de perfil (packages/types). Aqui o piso era
  // 100, la era 50 e o teto 200, e o slider do desktop e o mesmo componente nos
  // dois — entao o mesmo gesto passava num lugar e era recusado no outro.
  bannerScale:     z.number().int().min(50).max(300).optional(),
  iconScale:       z.number().int().min(100).max(300).optional(),
  // Comandos da bot DESLIGADOS aqui. Lista de chaves; vazia = tudo ligado.
  // Chega como array e e guardada como texto separado por virgula: sao poucos
  // itens e uma coluna simples evita mais uma tabela pra uma lista de chaveamento.
  botDisabledCommands: z.array(z.string().max(40)).max(60).optional(),
  // Órbita dos avisos da bot. null (ou "") = volta a escolher sozinha.
  botNoticeChannelId: z.string().optional().nullable(),
})

serversRouter.patch(
  '/:serverId',
  requireAuth,
  validate(UpdateServerSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const { name, iconUrl, bannerUrl, messageRetentionDays, isPublic, description, bannerPositionY, bannerScale, iconScale, botNoticeChannelId, botDisabledCommands } = req.body as {
      name?: string; iconUrl?: string | null; bannerUrl?: string | null
      messageRetentionDays?: number | null; isPublic?: boolean; description?: string | null
      bannerPositionY?: number; bannerScale?: number; iconScale?: number; botDisabledCommands?: string[]
      botNoticeChannelId?: string | null
    }

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId) return res.status(403).json({ error: 'Você não é membro' })
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_SERVER)) {
      return res.status(403).json({ error: 'Sem permissão pra editar o servidor' })
    }

    if (iconUrl && !isAllowedIcon(iconUrl)) return res.status(422).json({ error: 'URL de ícone não permitida' })
    if (isIconTooBig(iconUrl)) return res.status(413).json({ error: 'Ícone muito grande (max 5MB)' })
    if (bannerUrl && !isAllowedIcon(bannerUrl)) return res.status(422).json({ error: 'URL de banner não permitida' })
    if (isBannerTooBig(bannerUrl)) return res.status(413).json({ error: 'Banner muito grande (max 8MB)' })

    const patch: Record<string, unknown> = {}
    if (name      !== undefined) patch.name      = name
    // data-URI -> R2 (guarda so a URL); URL/host permitido passa direto.
    //
    // O ICONE ENCOLHE E O BANNER NAO, pelo mesmo motivo do perfil: o icone e desenhado a
    // 54dp na barra lateral — que mostra TODAS as constelacoes da pessoa de uma vez —, e o
    // banner e desenhado grande no topo. Ver persistImagemDeExibicao.
    if (iconUrl !== undefined) {
      const { url, original } = await persistImagemDeExibicao(iconUrl)
      patch.iconUrl = url
      if (original !== null) patch.iconFullUrl = original
    }
    if (bannerUrl !== undefined) patch.bannerUrl = await persistDataUri(bannerUrl)
    if (bannerPositionY !== undefined) patch.bannerPositionY = bannerPositionY
    if (bannerScale     !== undefined) patch.bannerScale     = bannerScale
    if (iconScale       !== undefined) patch.iconScale       = iconScale
    // Guarda so as chaves conhecidas: um item inventado ficaria na coluna pra
    // sempre, sem nada pra desligar e sem forma de aparecer na tela pra ser tirado.
    if (botDisabledCommands !== undefined) {
      const validas = new Set(catalogoDeComandos().map((c) => c.chave))
      const limpa = [...new Set(botDisabledCommands.filter((c) => validas.has(c)))]
      patch.botDisabledCommands = limpa.length ? limpa.join(',') : null
    }
    if (messageRetentionDays !== undefined)
      patch.messageRetentionDays = messageRetentionDays === 0 ? null : messageRetentionDays
    if (isPublic    !== undefined) patch.isPublic    = isPublic
    if (description !== undefined) patch.description = description?.trim() || null
    // Órbita dos avisos da bot. Confere que o canal é DESTA constelação antes de
    // gravar: sem isso um id de outro servidor entraria na tabela e o dono acharia
    // que escolheu — a checagem na hora de falar rejeitaria em silêncio, todo aviso,
    // pra sempre. Vazio = volta ao automático.
    if (botNoticeChannelId !== undefined) {
      const alvo = botNoticeChannelId?.trim() || null
      if (alvo) {
        const [c] = await db.select({ id: channels.id })
          .from(channels)
          .where(and(eq(channels.id, alvo), eq(channels.serverId, serverId), eq(channels.type, 'TEXT')))
          .limit(1)
        if (!c) return res.status(422).json({ error: 'Essa órbita não é de texto desta constelação' })
      }
      patch.botNoticeChannelId = alvo
    }
    if (Object.keys(patch).length === 0) return res.status(400).json({ error: 'Nada para atualizar' })

    await db.update(servers).set(patch).where(eq(servers.id, serverId))
    void audit({
      serverId, actorId: req.userId!, action: AUDIT.SERVER_UPDATE,
      targetId: serverId, metadata: { fields: Object.keys(patch) },
    })
    // Nome/icone/banner aparecem na rail e no cabecalho de TODO mundo — sem este
    // aviso, so quem editou via a mudanca ate os outros reabrirem o app.
    serverUpdated(serverId)
    const updated = await serverWithChannelsAndCount(serverId)
    res.json({ data: updated })
  })
)

serversRouter.post(
  '/:serverId/regenerate-invite',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId) return res.status(403).json({ error: 'Você não é membro' })
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_SERVER)) {
      return res.status(403).json({ error: 'Sem permissão pra regenerar o convite' })
    }

    const newCode = createId()
    await db.update(servers).set({ inviteCode: newCode }).where(eq(servers.id, serverId))
    void audit({
      serverId, actorId: req.userId!, action: AUDIT.SERVER_UPDATE,
      targetId: serverId, metadata: { fields: ['inviteCode'] },
    })

    res.json({ data: { inviteCode: newCode } })
  })
)

const AddFriendSchema = z.object({
  friendUserId: z.string().min(1, 'friendUserId obrigatório'),
})

serversRouter.post(
  '/:serverId/add-friend',
  requireAuth,
  validate(AddFriendSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const { friendUserId } = req.body as { friendUserId: string }
    const callerId = req.userId!

    const [callerMember] = await db.select({ id: serverMembers.id })
      .from(serverMembers)
      .where(and(eq(serverMembers.serverId, serverId), eq(serverMembers.userId, callerId)))
      .limit(1)
    if (!callerMember) return res.status(403).json({ error: 'Você não é membro deste servidor' })

    const [a, b] = callerId < friendUserId ? [callerId, friendUserId] : [friendUserId, callerId]
    const [friendship] = await db.select({ id: friendships.id, status: friendships.status })
      .from(friendships)
      .where(and(eq(friendships.userAId, a), eq(friendships.userBId, b)))
      .limit(1)
    if (!friendship || friendship.status !== 'accepted') {
      return res.status(403).json({ error: 'Você só pode adicionar amigos aceitos' })
    }

    const [banned] = await db.select({ id: serverBans.id }).from(serverBans)
      .where(and(eq(serverBans.serverId, serverId), eq(serverBans.userId, friendUserId)))
      .limit(1)
    if (banned) return res.status(403).json({ error: 'Esse amigo está banido do servidor' })

    const [already] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.serverId, serverId), eq(serverMembers.userId, friendUserId)))
      .limit(1)
    if (already) return res.status(409).json({ error: 'Esse amigo já é membro' })

    const [server] = await db.select({ id: servers.id, name: servers.name, isGroup: servers.isGroup })
      .from(servers).where(eq(servers.id, serverId)).limit(1)
    if (!server) return res.status(404).json({ error: 'Servidor não encontrado' })

    await db.insert(serverMembers).values({ userId: friendUserId, serverId })
    void invalidateMembersCache(serverId)
    joinedServer(friendUserId, serverId)
    membersChanged(serverId)
    void saudarNovoMembro(serverId, friendUserId)
    await db.insert(notifications).values({
      userId: friendUserId,
      type:   'server_invite',
      payload: JSON.stringify({
        serverId,
        serverName: server.name,
        isGroup:    server.isGroup,
        addedBy:    callerId,
      }),
    })
    void audit({
      serverId, actorId: callerId, action: AUDIT.SERVER_UPDATE,
      targetId: friendUserId, metadata: { kind: 'add_friend' },
    })

    res.json({ data: { ok: true, friendUserId } })
  })
)

const RoleSchema = z.object({ role: z.enum(['ADMIN', 'MEMBER']) })

serversRouter.patch(
  '/:serverId/members/:memberId',
  requireAuth,
  validate(RoleSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, memberId } = req.params
    const { role } = req.body as { role: 'ADMIN' | 'MEMBER' }

    const [server] = await db.select({ ownerId: servers.ownerId }).from(servers)
      .where(eq(servers.id, serverId)).limit(1)
    if (!server) return res.status(404).json({ error: 'Servidor não encontrado' })
    if (server.ownerId !== req.userId) return res.status(403).json({ error: 'Apenas o dono pode mudar cargos' })

    const [target] = await db.select({ id: serverMembers.id, role: serverMembers.role, userId: serverMembers.userId })
      .from(serverMembers)
      .where(and(eq(serverMembers.id, memberId), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (!target) return res.status(404).json({ error: 'Membro não encontrado' })
    if (target.role === 'OWNER') return res.status(400).json({ error: 'Não é possível alterar o cargo do dono' })

    await db.update(serverMembers).set({ role }).where(eq(serverMembers.id, memberId))
    // Virar ADMIN muda o que a pessoa PODE fazer: sem o aviso, ela continuava
    // vendo a tela de membro comum ate reabrir o app.
    membersChanged(serverId)
    res.json({ data: { id: memberId, role } })
  })
)

// Destravar quem o anti-spam silenciou. O unmuteUser existe desde sempre mas
// NENHUMA rota o chamava: uma vez auto-silenciado nao havia saida, so esperar o
// prazo. Alcada = dono ou MANAGE_MESSAGES (e moderacao de mensagem).
// Precisa vir ANTES do DELETE '/:serverId/members/:memberId', senao o Express
// casa a rota mais generica primeiro e 'mute' viraria um memberId.
serversRouter.delete(
  '/:serverId/members/:memberId/mute',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, memberId } = req.params

    const requester = await getMemberPerms(req.userId!, serverId)
    if (!requester.memberId) return res.status(403).json({ error: 'Você não é membro' })
    if (!requester.isOwner && !requester.permissions.has(PERMS.MANAGE_MESSAGES))
      return res.status(403).json({ error: 'Sem permissão para remover o silenciamento' })

    const [target] = await db.select({ userId: serverMembers.userId })
      .from(serverMembers)
      .where(and(eq(serverMembers.id, memberId), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (!target) return res.status(404).json({ error: 'Membro não encontrado' })

    await unmuteUser(target.userId, serverId)
    res.json({ data: { memberId, muted: false } })
  })
)

serversRouter.delete(
  '/:serverId/members/:memberId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, memberId } = req.params

    const requester = await getMemberPerms(req.userId!, serverId)
    if (!requester.memberId) return res.status(403).json({ error: 'Você não é membro' })
    if (!requester.isOwner && !requester.permissions.has(PERMS.KICK_MEMBERS))
      return res.status(403).json({ error: 'Sem permissão para remover membros' })

    const [target] = await db.select({ id: serverMembers.id, role: serverMembers.role, userId: serverMembers.userId })
      .from(serverMembers)
      .where(and(eq(serverMembers.id, memberId), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (!target) return res.status(404).json({ error: 'Membro não encontrado' })
    if (target.role === 'OWNER') return res.status(400).json({ error: 'Não é possível remover o dono' })
    if (target.userId === req.userId) return res.status(400).json({ error: 'Use sair do servidor para se remover' })

    if (!requester.isOwner) {
      const targetPerms = await getMemberPerms(target.userId, serverId)
      if (targetPerms.isOwner || targetPerms.permissions.has(PERMS.KICK_MEMBERS))
        return res.status(403).json({ error: 'Não pode remover alguém com mesma permissão' })
    }

    await db.delete(serverMembers).where(eq(serverMembers.id, memberId))
    void invalidateMembersCache(serverId)
    membersChanged(serverId)
    // Quem foi expulso precisa saber TAMBEM: senao a constelacao continua na rail
    // dele, e clicar so devolve erro.
    leftServer(target.userId, serverId, 'expulso')
    void audit({
      serverId, actorId: req.userId!, action: AUDIT.MEMBER_KICK,
      targetId: target.userId,
    })
    res.json({ message: 'Membro removido' })
  })
)

serversRouter.delete(
  '/:serverId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params

    const [server] = await db.select({ ownerId: servers.ownerId }).from(servers)
      .where(eq(servers.id, serverId)).limit(1)
    if (!server) return res.status(404).json({ error: 'Servidor não encontrado' })

    if (server.ownerId !== req.userId) {
      return res.status(403).json({ error: 'Apenas o dono pode excluir o servidor' })
    }

    // ANTES do delete: depois dele nao ha mais membros no banco pra avisar, e a
    // constelacao ficaria de fantasma na rail de todo mundo ate o proximo boot.
    serverGone(serverId)
    await db.delete(servers).where(eq(servers.id, serverId))
    res.json({ message: 'Servidor excluído com sucesso' })
  })
)

serversRouter.delete(
  '/:serverId/leave',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params

    const [server] = await db.select({ ownerId: servers.ownerId }).from(servers)
      .where(eq(servers.id, serverId)).limit(1)
    if (!server) return res.status(404).json({ error: 'Servidor não encontrado' })

    if (server.ownerId === req.userId) {
      return res.status(400).json({
        error: 'O dono não pode sair do servidor. Exclua-o ou transfira a propriedade.',
      })
    }

    const [membership] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, req.userId!), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (!membership) return res.status(404).json({ error: 'Você não é membro deste servidor' })

    await db.delete(serverMembers).where(eq(serverMembers.id, membership.id))
    void invalidateMembersCache(serverId)
    membersChanged(serverId)
    res.json({ message: 'Você saiu do servidor' })
  })
)

serversRouter.post(
  '/join/:inviteCode',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const [server] = await db.select().from(servers)
      .where(eq(servers.inviteCode, req.params.inviteCode)).limit(1)
    if (!server) return res.status(404).json({ error: 'Convite inválido' })

    const [banned] = await db.select({ id: serverBans.id }).from(serverBans)
      .where(and(eq(serverBans.userId, req.userId!), eq(serverBans.serverId, server.id)))
      .limit(1)
    if (banned) return res.status(403).json({ error: 'Você está banido deste servidor' })

    const [already] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, req.userId!), eq(serverMembers.serverId, server.id)))
      .limit(1)
    if (already) return res.status(409).json({ error: 'Você já é membro deste servidor' })

    await db.insert(serverMembers).values({ userId: req.userId!, serverId: server.id })
    void invalidateMembersCache(server.id)
    membersChanged(server.id)
    void saudarNovoMembro(server.id, req.userId!)
    res.json({ data: server })
  })
)

serversRouter.get(
  '/:serverId/members',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const [me] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, req.userId!), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (!me) return res.status(403).json({ error: 'Acesso negado' })

    const members = await db.select({
      id:        serverMembers.id,
      userId:    serverMembers.userId,
      serverId:  serverMembers.serverId,
      role:      serverMembers.role,
      nameColor: serverMembers.nameColor,
      joinedAt:  serverMembers.joinedAt,
      user: {
        id:          users.id,
        username:    users.username,
        displayName: users.displayName,
        avatarUrl:   users.avatarUrl,
        bio:         users.bio,
      },
    })
      .from(serverMembers)
      .innerJoin(users, eq(users.id, serverMembers.userId))
      .where(eq(serverMembers.serverId, serverId))
      .orderBy(asc(serverMembers.joinedAt))

    const memberIds = members.map((m) => m.id)
    let rolesByMember = new Map<string, Array<{ id: string; name: string; color: string|null; iconUrl: string|null; position: number; hoist: boolean }>>()
    if (memberIds.length > 0) {
      const assignments = await db.select({
        memberId: memberRoles.memberId,
        roleId:   roles.id,
        name:     roles.name,
        color:    roles.color,
        iconUrl:  roles.iconUrl,
        position: roles.position,
        hoist:    roles.hoist,
      })
        .from(memberRoles)
        .innerJoin(roles, eq(roles.id, memberRoles.roleId))
        .where(eq(roles.serverId, serverId))

      for (const a of assignments) {
        if (!rolesByMember.has(a.memberId)) rolesByMember.set(a.memberId, [])
        rolesByMember.get(a.memberId)!.push({
          id: a.roleId, name: a.name, color: a.color, iconUrl: a.iconUrl, position: a.position, hoist: a.hoist,
        })
      }

      for (const arr of rolesByMember.values()) arr.sort((a, b) => b.position - a.position)
    }

    const enriched = members.map((m) => {
      const rs = rolesByMember.get(m.id) ?? []
      const topColored = rs.find((r) => r.color)
      return { ...m, roles: rs, topColor: topColored?.color ?? null }
    })

    res.json({ data: enriched })
  })
)

serversRouter.post(
  '/:serverId/invite/:username',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, username } = req.params

    const [server] = await db.select({ id: servers.id }).from(servers)
      .where(eq(servers.id, serverId)).limit(1)
    if (!server) return res.status(404).json({ error: 'Servidor não encontrado' })

    const requester = await getMemberPerms(req.userId!, serverId)
    if (!requester.memberId) return res.status(403).json({ error: 'Você não é membro' })
    if (!requester.isOwner && !requester.permissions.has(PERMS.MANAGE_SERVER))
      return res.status(403).json({ error: 'Sem permissão pra adicionar membros' })

    const [target] = await db.select({ id: users.id, displayName: users.displayName }).from(users)
      .where(eq(users.username, username)).limit(1)
    if (!target) return res.status(404).json({ error: 'Usuário não encontrado' })

    const [tBan] = await db.select({ id: serverBans.id }).from(serverBans)
      .where(and(eq(serverBans.userId, target.id), eq(serverBans.serverId, serverId)))
      .limit(1)
    if (tBan) return res.status(403).json({ error: 'Usuário está banido deste servidor' })

    const [already] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, target.id), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (already) return res.status(409).json({ error: 'Usuário já é membro' })

    await db.insert(serverMembers).values({ userId: target.id, serverId, role: 'MEMBER' })
    void invalidateMembersCache(serverId)
    joinedServer(target.id, serverId)
    membersChanged(serverId)
    void saudarNovoMembro(serverId, target.id)
    res.json({ message: `${target.displayName} adicionado com sucesso` })
  })
)

const NameColorSchema = z.object({
  nameColor: z.string().regex(/^(#[0-9a-fA-F]{6}|gradient:\d+:#[0-9a-fA-F]{6}:#[0-9a-fA-F]{6})$/, 'Formato inválido').nullable(),
})

serversRouter.patch(
  '/:serverId/my-color',
  requireAuth,
  validate(NameColorSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params

    const [member] = await db.select({ id: serverMembers.id }).from(serverMembers)
      .where(and(eq(serverMembers.userId, req.userId!), eq(serverMembers.serverId, serverId)))
      .limit(1)
    if (!member) return res.status(403).json({ error: 'Você não é membro deste servidor' })

    const [updated] = await db.update(serverMembers)
      .set({ nameColor: req.body.nameColor })
      .where(eq(serverMembers.id, member.id))
      .returning({ nameColor: serverMembers.nameColor })

    res.json({ data: { nameColor: updated.nameColor } })
  })
)

serversRouter.get(
  '/:serverId/audit',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const limit = Math.min(Number(req.query.limit) || 50, 200)

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_SERVER))
      return res.status(403).json({ error: 'Sem permissão pra ver audit log' })

    const rows = await db.select({
      id:        auditLogs.id,
      action:    auditLogs.action,
      actorId:   auditLogs.actorId,
      targetId:  auditLogs.targetId,
      metadata:  auditLogs.metadata,
      createdAt: auditLogs.createdAt,
      actor: {
        id:          users.id,
        username:    users.username,
        displayName: users.displayName,
        avatarUrl:   users.avatarUrl,
      },
    })
      .from(auditLogs)
      .innerJoin(users, eq(users.id, auditLogs.actorId))
      .where(eq(auditLogs.serverId, serverId))
      .orderBy(sql`${auditLogs.createdAt} DESC`)
      .limit(limit)

    const shaped = rows.map((r) => ({
      ...r,
      metadata: safeParseObj(r.metadata),
    }))
    res.json({ data: shaped })
  })
)

function safeParseObj(raw: unknown): Record<string, unknown> {
  if (typeof raw !== 'string') return {}
  try { const v = JSON.parse(raw); return v && typeof v === 'object' ? v as Record<string, unknown> : {} } catch { return {} }
}

serversRouter.get(
  '/:serverId/me',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId && !m.isOwner) return res.status(403).json({ error: 'Você não é membro' })
    res.json({ data: {
      isOwner:     m.isOwner,
      isAdmin:     m.isAdmin,
      permissions: Array.from(m.permissions),
    } })
  })
)

export const channelsRouter = Router()

channelsRouter.post(
  '/:serverId/channels',
  requireAuth,
  validate(CreateChannelSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const { name, type } = req.body
    const categoryId = (req.body as { categoryId?: string | null }).categoryId

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId) return res.status(403).json({ error: 'Você não é membro' })
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão pra criar canais' })

    if (categoryId) {
      const [cat] = await db.select({ id: channelCategories.id })
        .from(channelCategories)
        .where(and(eq(channelCategories.id, categoryId), eq(channelCategories.serverId, serverId)))
        .limit(1)
      if (!cat) return res.status(400).json({ error: 'Categoria inválida' })
    }

    // Position incremental dentro do grupo (categoria ou soltos) -> canais nascem
    // com position DISTINTA. Sem isto ficavam todos em 0 e o reordenar nao tinha o
    // que permutar; e um canal novo entrava no topo em vez de no fim.
    const [{ maxPos } = { maxPos: -1 }] = await db
      .select({ maxPos: sql<number>`COALESCE(MAX(${channels.position}), -1)::int` })
      .from(channels)
      .where(and(
        eq(channels.serverId, serverId),
        categoryId ? eq(channels.categoryId, categoryId) : isNull(channels.categoryId),
      ))

    const [channel] = await db.insert(channels)
      .values({ name, type, serverId, position: (maxPos ?? -1) + 1, ...(categoryId ? { categoryId } : {}) })
      .returning()
    void audit({
      serverId, actorId: req.userId!, action: AUDIT.CHANNEL_CREATE,
      targetId: channel.id, metadata: { name, type, categoryId },
    })
    channelsChanged(serverId)
    res.status(201).json({ data: channel })
  })
)

channelsRouter.get(
  '/:serverId/channels/:channelId/visibility',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, channelId } = req.params
    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão' })

    const [ch] = await db.select({ id: channels.id, isPrivate: channels.isPrivate })
      .from(channels)
      .where(and(eq(channels.id, channelId), eq(channels.serverId, serverId)))
      .limit(1)
    if (!ch) return res.status(404).json({ error: 'Canal não encontrado' })

    const perms = await db.select({ roleId: channelRolePerms.roleId })
      .from(channelRolePerms).where(eq(channelRolePerms.channelId, channelId))
    res.json({ data: { isPrivate: ch.isPrivate, roleIds: perms.map((p) => p.roleId) } })
  })
)

const VisibilitySchema = z.object({
  isPrivate: z.boolean(),
  roleIds:   z.array(z.string()).max(50).optional(),
})

channelsRouter.patch(
  '/:serverId/channels/:channelId/visibility',
  requireAuth,
  validate(VisibilitySchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, channelId } = req.params
    const { isPrivate, roleIds = [] } = req.body as z.infer<typeof VisibilitySchema>

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão' })

    const [ch] = await db.select({ id: channels.id })
      .from(channels)
      .where(and(eq(channels.id, channelId), eq(channels.serverId, serverId)))
      .limit(1)
    if (!ch) return res.status(404).json({ error: 'Canal não encontrado' })

    let validRoleIds: string[] = []
    if (roleIds.length > 0) {
      const validRoles = await db.select({ id: roles.id }).from(roles)
        .where(and(eq(roles.serverId, serverId), inArray(roles.id, roleIds)))
      validRoleIds = validRoles.map((r) => r.id)
    }

    await db.transaction(async (tx) => {
      await tx.update(channels).set({ isPrivate }).where(eq(channels.id, channelId))
      await tx.delete(channelRolePerms).where(eq(channelRolePerms.channelId, channelId))
      if (validRoleIds.length > 0) {
        await tx.insert(channelRolePerms).values(
          validRoleIds.map((roleId) => ({ channelId, roleId })),
        )
      }
    })

    void audit({
      serverId, actorId: req.userId!, action: AUDIT.CHANNEL_UPDATE,
      targetId: channelId, metadata: { isPrivate, roleIds: validRoleIds },
    })
    channelsChanged(serverId)
    res.json({ data: { isPrivate, roleIds: validRoleIds } })
  })
)

const UpdateChannelSchema = z.object({
  name:       z.string().min(1).max(50).optional(),
  categoryId: z.string().nullable().optional(),
  position:   z.number().int().min(0).optional(),
  // null = "nao decidi" (herda da categoria). Nao e o mesmo que false.
  botEnabled: z.boolean().nullable().optional(),
  // Guardar a conversa com a bot no historico. Sem null: nao ha heranca aqui.
  botKeepReplies: z.boolean().optional(),
})
channelsRouter.patch(
  '/:serverId/channels/:channelId',
  requireAuth,
  validate(UpdateChannelSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, channelId } = req.params
    const { name, categoryId, position, botEnabled, botKeepReplies } = req.body as z.infer<typeof UpdateChannelSchema>

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão' })

    if (categoryId) {
      const [cat] = await db.select({ id: channelCategories.id })
        .from(channelCategories)
        .where(and(eq(channelCategories.id, categoryId), eq(channelCategories.serverId, serverId)))
        .limit(1)
      if (!cat) return res.status(400).json({ error: 'Categoria inválida' })
    }

    const set: Partial<{ name: string; categoryId: string | null; position: number; botEnabled: boolean | null; botKeepReplies: boolean }> = {}
    if (name !== undefined) set.name = name
    if (categoryId !== undefined) set.categoryId = categoryId
    if (position !== undefined) set.position = position
    if (botEnabled !== undefined) set.botEnabled = botEnabled
    if (botKeepReplies !== undefined) set.botKeepReplies = botKeepReplies
    if (Object.keys(set).length === 0) return res.status(400).json({ error: 'Nada pra atualizar' })

    const r = await db.update(channels)
      .set(set)
      .where(and(eq(channels.id, channelId), eq(channels.serverId, serverId)))
      .returning({ id: channels.id, name: channels.name, categoryId: channels.categoryId, position: channels.position, botEnabled: channels.botEnabled, botKeepReplies: channels.botKeepReplies })
    if (r.length === 0) return res.status(404).json({ error: 'Canal não encontrado' })

    void audit({
      serverId, actorId: req.userId!, action: AUDIT.CHANNEL_UPDATE,
      targetId: channelId, metadata: { name, categoryId, position },
    })
    channelsChanged(serverId)
    res.json({ data: r[0] })
  })
)

channelsRouter.delete(
  '/:serverId/channels/:channelId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, channelId } = req.params

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId && !m.isOwner) return res.status(403).json({ error: 'Você não é membro' })
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão pra excluir canais' })

    const r = await db.delete(channels)
      .where(and(eq(channels.id, channelId), eq(channels.serverId, serverId)))
      .returning({ id: channels.id, name: channels.name })
    if (r.length === 0) return res.status(404).json({ error: 'Canal não encontrado' })

    void audit({
      serverId, actorId: req.userId!, action: AUDIT.CHANNEL_DELETE,
      targetId: channelId, metadata: { name: r[0].name },
    })
    channelsChanged(serverId)
    res.json({ message: 'Canal excluído' })
  })
)

const CreateCategorySchema = z.object({ name: z.string().min(1).max(50) })
channelsRouter.post(
  '/:serverId/categories',
  requireAuth,
  validate(CreateCategorySchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const { name } = req.body as z.infer<typeof CreateCategorySchema>

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId) return res.status(403).json({ error: 'Você não é membro' })
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão pra criar categorias' })

    const existing = await db.select({ position: channelCategories.position })
      .from(channelCategories).where(eq(channelCategories.serverId, serverId))
    const nextPos = existing.length ? Math.max(...existing.map((e) => e.position)) + 1 : 0

    const [cat] = await db.insert(channelCategories)
      .values({ name, serverId, position: nextPos }).returning()
    channelsChanged(serverId)
    res.status(201).json({ data: cat })
  })
)

const UpdateCategorySchema = z.object({
  name:     z.string().min(1).max(50).optional(),
  position: z.number().int().min(0).optional(),
  // Desligar aqui alcanca TODAS as orbitas da categoria que nao decidiram nada.
  botEnabled: z.boolean().nullable().optional(),
})
channelsRouter.patch(
  '/:serverId/categories/:categoryId',
  requireAuth,
  validate(UpdateCategorySchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, categoryId } = req.params
    const { name, position, botEnabled } = req.body as z.infer<typeof UpdateCategorySchema>

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão' })

    const set: Partial<{ name: string; position: number; botEnabled: boolean | null }> = {}
    if (name !== undefined) set.name = name
    if (position !== undefined) set.position = position
    if (botEnabled !== undefined) set.botEnabled = botEnabled
    if (Object.keys(set).length === 0) return res.status(400).json({ error: 'Nada pra atualizar' })

    const r = await db.update(channelCategories)
      .set(set)
      .where(and(eq(channelCategories.id, categoryId), eq(channelCategories.serverId, serverId)))
      .returning()
    if (r.length === 0) return res.status(404).json({ error: 'Categoria não encontrada' })
    channelsChanged(serverId)
    res.json({ data: r[0] })
  })
)

channelsRouter.delete(
  '/:serverId/categories/:categoryId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { serverId, categoryId } = req.params

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_CHANNELS))
      return res.status(403).json({ error: 'Sem permissão' })

    const r = await db.delete(channelCategories)
      .where(and(eq(channelCategories.id, categoryId), eq(channelCategories.serverId, serverId)))
      .returning({ id: channelCategories.id })
    if (r.length === 0) return res.status(404).json({ error: 'Categoria não encontrada' })
    channelsChanged(serverId)
    res.json({ message: 'Categoria excluída' })
  })
)
