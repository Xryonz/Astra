import { Router, Request, Response } from 'express'
import { and, desc, eq, inArray, ne, or } from 'drizzle-orm'
import { z } from 'zod'
import { db } from '../db'
import { users, servers, serverMembers, profileNotes, friendships } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { UpdateProfileSchema, ProfileNoteSchema } from '@astra/types'
import { getUserStatus, setUserOnline, redis, presenceKeys, activityKeys, leAtividade } from '../lib/redis'
import { persistDataUri, isOwnStorageUrl } from '../lib/storage'
import { presenceChanged, profileChanged } from '../lib/realtime'

const router = Router()

const ALLOWED_HOSTS = [
  'i.imgur.com',
  'media.giphy.com',
  'cdn.discordapp.com',
  'media.tenor.com',
  'i.postimg.cc',
  'images.unsplash.com',
  'lh3.googleusercontent.com',
  'pbs.twimg.com',
  'media.discordapp.net',
  'cdn.jsdelivr.net',
  'raw.githubusercontent.com',
]

function isAllowedImageUrl(url: string | null | undefined): boolean {
  if (!url) return true
  if (url.startsWith('data:image/')) return true
  if (isOwnStorageUrl(url)) return true // URL que nos mesmos persistimos (R2 / /uploads)
  try {
    const { hostname } = new URL(url)
    return ALLOWED_HOSTS.some((h) => hostname === h || hostname.endsWith(`.${h}`))
  } catch {
    return false
  }
}

// 10MB = o teto que o cliente ja aplica (ImageCrop.HARD_MAX). Subiu de 6MB junto
// com a resolucao de saida (avatar 1024, banner 2560), senao o proprio app produz
// um arquivo que o proprio servidor recusa.
function isDataUriTooLarge(url: string | null | undefined): boolean {
  if (!url || !url.startsWith('data:')) return false
  const bytes = url.length * 0.75
  return bytes > 10 * 1024 * 1024
}

router.get(
  '/lookup',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const raw = String(req.query.ids ?? '')
    const ids = raw.split(',').map((s) => s.trim()).filter(Boolean).slice(0, 50)
    if (ids.length === 0) return res.json({ data: [] })
    const rows = await db.select({
      id:          users.id,
      username:    users.username,
      displayName: users.displayName,
      avatarUrl:   users.avatarUrl,
      bannerColor: users.bannerColor,
    }).from(users).where(inArray(users.id, ids))
    res.json({ data: rows })
  })
)

router.patch(
  '/',
  requireAuth,
  validate(UpdateProfileSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const {
      displayName, username, bio, avatarUrl, bannerUrl, bannerColor, profileTheme,
      bannerPositionY, bannerScale, bannerBorder, bannerTextColor,
      pronouns, statusEmoji, displayFont, dmPrivacy,
    } = req.body

    if (bannerUrl && !isAllowedImageUrl(bannerUrl)) {
      return res.status(422).json({
        error: 'URL do banner não é de um host permitido. Use Imgur, Giphy, Tenor ou envie um arquivo.',
      })
    }
    if (isDataUriTooLarge(bannerUrl)) {
      return res.status(413).json({ error: 'Arquivo muito grande. Máximo 5MB.' })
    }
    if (isDataUriTooLarge(avatarUrl)) {
      return res.status(413).json({ error: 'Avatar muito grande. Máximo 5MB.' })
    }

    if (username) {
      const [conflict] = await db.select({ id: users.id }).from(users)
        .where(and(eq(users.username, username), ne(users.id, req.userId!)))
        .limit(1)
      if (conflict) return res.status(409).json({ error: 'Username já está em uso' })
    }

    const update: Record<string, unknown> = {}
    if (displayName !== undefined) update.displayName = displayName
    if (username    !== undefined) update.username    = username
    if (bio         !== undefined) update.bio         = bio
    // data-URI -> R2 (guarda so a URL); URL/host permitido passa direto.
    if (avatarUrl   !== undefined) update.avatarUrl   = await persistDataUri(avatarUrl)
    if (bannerUrl   !== undefined) update.bannerUrl   = await persistDataUri(bannerUrl)
    if (bannerColor  !== undefined) update.bannerColor  = bannerColor
    if (profileTheme !== undefined) update.profileTheme = profileTheme
    if (bannerPositionY !== undefined) update.bannerPositionY = bannerPositionY
    if (bannerScale     !== undefined) update.bannerScale     = bannerScale
    if (bannerBorder    !== undefined) update.bannerBorder    = bannerBorder
    if (bannerTextColor !== undefined) update.bannerTextColor = bannerTextColor
    if (pronouns         !== undefined) update.pronouns         = pronouns
    if (statusEmoji      !== undefined) update.statusEmoji      = statusEmoji
    if (displayFont      !== undefined) update.displayFont      = displayFont
    // Ajuste de PRIVACIDADE viajando na rota de perfil: e a mesma linha (uma coluna
    // do usuario, um PATCH), e uma rota separada so pra ele seria cerimonia.
    if (dmPrivacy        !== undefined) update.dmPrivacy        = dmPrivacy

    const [user] = await db.update(users).set(update)
      .where(eq(users.id, req.userId!))
      .returning({
        id: users.id, email: users.email, username: users.username,
        displayName: users.displayName, avatarUrl: users.avatarUrl,
        bio: users.bio, bannerUrl: users.bannerUrl, bannerColor: users.bannerColor,
        profileTheme: users.profileTheme,
        bannerPositionY: users.bannerPositionY,
        bannerScale:     users.bannerScale,
        bannerBorder:    users.bannerBorder,
        bannerTextColor: users.bannerTextColor,
        pronouns: users.pronouns, statusEmoji: users.statusEmoji,
        displayFont: users.displayFont,
        dmPrivacy: users.dmPrivacy,
      })

    // Sem isto, o perfil editado só aparecia pros outros (e pra mim em outras
    // telas) depois de reabrir o app — a lista de membros, a barra de sussurros e
    // o autor de cada mensagem seguiam com o nome e a foto velhos.
    profileChanged(req.userId!)
    res.json({ data: { user } })
  })
)

router.get(
  '/presence',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const ids = String(req.query.ids ?? '').split(',').map((s) => s.trim()).filter(Boolean).slice(0, 200)
    if (ids.length === 0) return res.json({ data: {} })

    // UM MGET pra todos os ids (era N x GET, ~1 comando por membro do painel toda
    // vez que a lista carregava). Mesmo padrao do servers.ts. Fail-safe: Redis fora
    // -> todos OFFLINE, a request nao cai.
    const out: Record<string, 'ONLINE'|'IDLE'|'DND'|'OFFLINE'> = {}
    let live: (string | null)[] = []
    try { live = await redis.mget(ids.map((id) => presenceKeys.user(id))) } catch { live = [] }
    ids.forEach((id, i) => {
      const s = live[i]
      if (!s || s === 'INVISIBLE') out[id] = 'OFFLINE'
      else out[id] = s as 'ONLINE'|'IDLE'|'DND'
    })
    res.json({ data: out })
  })
)

// Atividade em lote — o par de /presence, e separado dele de propósito.
//
// Juntar os dois numa resposta só seria mais barato em requisições e mais caro em
// tudo o mais: /presence devolve `Record<string,string>` hoje, e todo cliente já
// declara esse formato. Trocar por um objeto quebraria os quatro de uma vez pra
// economizar um round-trip que acontece uma vez por painel aberto.
//
// Quem não tem atividade simplesmente NÃO APARECE no mapa — nada de string vazia
// pra cada pessoa da lista. O painel de membros manda 200 ids e recebe de volta só
// os poucos que estão em alguma coisa.
router.get(
  '/activity',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const ids = String(req.query.ids ?? '').split(',').map((s) => s.trim()).filter(Boolean).slice(0, 200)
    if (ids.length === 0) return res.json({ data: {} })

    // { texto, desde } e não string pura: o cartão de perfil mostra "há 2h 14min"
    // junto do nome do programa, e o instante de início mora no mesmo lugar que o
    // texto (uma linha só no Redis, ver leAtividade). Mudar o formato é seguro
    // porque só o desktop lê esta rota — ela nasceu com ele.
    const out: Record<string, { text: string; since: number }> = {}
    let live: (string | null)[] = []
    try { live = await redis.mget(ids.map((id) => activityKeys.user(id))) } catch { live = [] }
    ids.forEach((id, i) => {
      const a = leAtividade(live[i])
      if (a) out[id] = { text: a.texto, since: a.desde }
    })
    res.json({ data: out })
  })
)

const PreferencesSchema = z.object({
  preferences: z.record(z.unknown()),
})

router.get(
  '/preferences',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const [row] = await db.select({ preferences: users.preferences })
      .from(users).where(eq(users.id, req.userId!)).limit(1)
    if (!row) return res.status(404).json({ error: 'Usuário não encontrado' })
    let parsed: Record<string, unknown> = {}
    if (row.preferences) {
      try { parsed = JSON.parse(row.preferences) } catch {}
    }
    res.json({ data: { preferences: parsed } })
  })
)

router.patch(
  '/preferences',
  requireAuth,
  validate(PreferencesSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const payload = (req.body as { preferences: Record<string, unknown> }).preferences
    const serialized = JSON.stringify(payload)
    if (serialized.length > 4096) {
      return res.status(413).json({ error: 'Preferências excedem 4KB' })
    }
    await db.update(users).set({ preferences: serialized })
      .where(eq(users.id, req.userId!))
    res.json({ data: { preferences: payload } })
  })
)

const StatusSchema = z.object({ status: z.enum(['ONLINE','IDLE','DND','INVISIBLE']) })

router.patch(
  '/status',
  requireAuth,
  validate(StatusSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const { status } = req.body as { status: 'ONLINE'|'IDLE'|'DND'|'INVISIBLE' }
    await db.update(users).set({ status }).where(eq(users.id, req.userId!))
    await setUserOnline(req.userId!, status)
    // Sem isto o status novo so aparecia pros outros quando eles recarregavam a
    // tela — o caminho por socket (set_status) ja avisava, este nao.
    presenceChanged(req.userId!, status)
    res.json({ data: { status } })
  })
)

router.get(
  '/:userId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const targetId = req.params.userId
    const [user] = await db.select({
      id: users.id, username: users.username, displayName: users.displayName,
      avatarUrl: users.avatarUrl, bio: users.bio,
      bannerUrl: users.bannerUrl, bannerColor: users.bannerColor,
      profileTheme: users.profileTheme,
      bannerPositionY: users.bannerPositionY,
      bannerScale:     users.bannerScale,
      bannerBorder:    users.bannerBorder,
      bannerTextColor: users.bannerTextColor,
      pronouns: users.pronouns, statusEmoji: users.statusEmoji,
      customStatus: users.customStatus,
      displayFont: users.displayFont,
      isBot: users.isBot,
      status: users.status,
      createdAt: users.createdAt,
    }).from(users).where(eq(users.id, targetId)).limit(1)
    if (!user) return res.status(404).json({ error: 'Usuário não encontrado' })

    const liveStatus = await getUserStatus(targetId)
    const isSelf = targetId === req.userId
    let effectiveStatus: 'ONLINE'|'IDLE'|'DND'|'INVISIBLE'|'OFFLINE'
    if (!liveStatus) effectiveStatus = 'OFFLINE'
    else if (liveStatus === 'INVISIBLE' && !isSelf) effectiveStatus = 'OFFLINE'
    else effectiveStatus = liveStatus
    ;(user as any).effectiveStatus = effectiveStatus

    // "EM COMUM" NÃO EXISTE CONSIGO MESMO.
    //
    // Vendo o próprio perfil, TODA constelação sua é comum com você: a conta fecha
    // e o resultado não quer dizer nada. A seção listava de volta o que a pessoa já
    // sabe, sob um rótulo que promete ligação com OUTRA pessoa.
    //
    // O `isSelf` já existia e já guardava os AMIGOS em comum, logo abaixo — as
    // constelações é que ficaram de fora quando a guarda foi escrita. Aqui ela
    // também poupa duas consultas no caso mais comum de todos, que é a pessoa
    // abrir o próprio cartão.
    const [myMems, theirMems] = isSelf
      ? [[] as Array<{ serverId: string }>, [] as Array<{ serverId: string; role: string }>]
      : await Promise.all([
        db.select({ serverId: serverMembers.serverId }).from(serverMembers).where(eq(serverMembers.userId, req.userId!)),
        db.select({ serverId: serverMembers.serverId, role: serverMembers.role }).from(serverMembers).where(eq(serverMembers.userId, targetId)),
      ])
    const mySet = new Set(myMems.map((m) => m.serverId))
    const mutualIds = theirMems.map((m) => m.serverId).filter((id) => mySet.has(id))

    let mutualServers: { id: string; name: string; iconUrl: string|null; isGroup: boolean; role: string }[] = []
    if (mutualIds.length > 0) {
      const srvs = await db.select({ id: servers.id, name: servers.name, iconUrl: servers.iconUrl, isGroup: servers.isGroup })
        .from(servers).where(inArray(servers.id, mutualIds))
      const roleByServer = new Map(theirMems.map((m) => [m.serverId, m.role]))
      mutualServers = srvs.map((s) => ({ ...s, role: roleByServer.get(s.id) ?? 'MEMBER' }))
    }

    // Amigos em comum. A amizade e guardada como UM par (userAId/userBId) sem
    // lado fixo — quem pediu pode estar em qualquer coluna. Entao "os amigos de
    // X" e a uniao das duas colunas, tirando o proprio X.
    //
    // Duas consultas e uma intersecao em memoria, e nao um JOIN: a lista de
    // amigos de uma pessoa e pequena (dezenas), e um self-join com OR nas duas
    // colunas nao usa nenhum dos dois indices por status que a tabela tem.
    const amigosDe = async (id: string) => {
      const rows = await db.select({ a: friendships.userAId, b: friendships.userBId })
        .from(friendships)
        .where(and(eq(friendships.status, 'accepted'), or(eq(friendships.userAId, id), eq(friendships.userBId, id))))
      return new Set(rows.map((r) => (r.a === id ? r.b : r.a)))
    }
    let mutualFriends = 0
    // Os ROSTOS, e não só a contagem: a tela mostra quem são. Vem limitada porque
    // uma conta velha pode ter dezenas em comum e o cartão cabe meia dúzia — o
    // número inteiro continua em `mutualFriends`, então "+12" ainda é dizível sem
    // carregar doze avatares que ninguém vai ver.
    let mutualFriendsList: Array<{ id: string; username: string; displayName: string | null; avatarUrl: string | null }> = []
    const ROSTOS_EM_COMUM = 8
    if (!isSelf) {
      const [meus, deles] = await Promise.all([amigosDe(req.userId!), amigosDe(targetId)])
      const emComum: string[] = []
      for (const id of deles) if (meus.has(id)) { mutualFriends++; emComum.push(id) }
      if (emComum.length > 0) {
        mutualFriendsList = await db.select({
          id: users.id, username: users.username,
          displayName: users.displayName, avatarUrl: users.avatarUrl,
        }).from(users).where(inArray(users.id, emComum.slice(0, ROSTOS_EM_COMUM)))
      }
    }

    res.json({ data: { user, mutualServers, mutualFriends, mutualFriendsList } })
  })
)

router.get(
  '/by-username/:username',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const { username } = req.params
    const [u] = await db.select({
      id: users.id, username: users.username, displayName: users.displayName,
      avatarUrl: users.avatarUrl, bio: users.bio,
      bannerUrl: users.bannerUrl, bannerColor: users.bannerColor,
      isBot: users.isBot,
      customStatus: users.customStatus,
    }).from(users).where(eq(users.username, username)).limit(1)
    if (!u) return res.status(404).json({ error: 'Usuário não encontrado' })

    const liveStatus = await getUserStatus(u.id)
    const effectiveStatus =
      !liveStatus ? 'OFFLINE'
      : liveStatus === 'INVISIBLE' && u.id !== req.userId ? 'OFFLINE'
      : liveStatus
    res.json({ data: { ...u, effectiveStatus } })
  })
)

router.get(
  '/:userId/notes',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const targetId = req.params.userId
    const rows = await db.select({
      id:        profileNotes.id,
      content:   profileNotes.content,
      pinned:    profileNotes.pinned,
      createdAt: profileNotes.createdAt,
      authorId:        users.id,
      authorUsername:  users.username,
      authorDisplay:   users.displayName,
      authorAvatar:    users.avatarUrl,
    })
      .from(profileNotes)
      .innerJoin(users, eq(users.id, profileNotes.authorId))
      .where(eq(profileNotes.profileUserId, targetId))
      .orderBy(desc(profileNotes.pinned), desc(profileNotes.createdAt))
      .limit(40)

    const items = rows.map((r) => ({
      id:        r.id,
      content:   r.content,
      pinned:    r.pinned,
      createdAt: r.createdAt.toISOString(),
      author: {
        id: r.authorId, username: r.authorUsername,
        displayName: r.authorDisplay, avatarUrl: r.authorAvatar,
      },
    }))
    res.json({ data: items })
  })
)

router.post(
  '/:userId/notes',
  requireAuth,
  validate(ProfileNoteSchema),
  asyncHandler(async (req: Request, res: Response) => {
    const targetId = req.params.userId
    const me       = req.userId!
    if (targetId === me) return res.status(400).json({ error: 'Não dá pra escrever no próprio mural' })

    const [a, b] = me < targetId ? [me, targetId] : [targetId, me]
    const [friendRow] = await db.select({ id: friendships.id })
      .from(friendships)
      .where(and(
        eq(friendships.userAId, a), eq(friendships.userBId, b),
        eq(friendships.status, 'accepted'),
      )).limit(1)
    if (!friendRow) return res.status(403).json({ error: 'Apenas amigos podem deixar nota' })

    const { content } = req.body as { content: string }
    const trimmed = content.trim()
    if (!trimmed) return res.status(400).json({ error: 'Nota vazia' })

    const [existing] = await db.select({ id: profileNotes.id })
      .from(profileNotes)
      .where(and(eq(profileNotes.profileUserId, targetId), eq(profileNotes.authorId, me)))
      .limit(1)

    if (existing) {
      await db.update(profileNotes)
        .set({ content: trimmed, createdAt: new Date() })
        .where(eq(profileNotes.id, existing.id))
      return res.json({ data: { id: existing.id, content: trimmed, updated: true } })
    }

    const [inserted] = await db.insert(profileNotes).values({
      profileUserId: targetId,
      authorId:      me,
      content:       trimmed,
    }).returning({ id: profileNotes.id })
    res.json({ data: { id: inserted.id, content: trimmed, updated: false } })
  })
)

router.delete(
  '/notes/:noteId',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const noteId = req.params.noteId
    const me     = req.userId!
    const [note] = await db.select({
      id: profileNotes.id, authorId: profileNotes.authorId, profileUserId: profileNotes.profileUserId,
    }).from(profileNotes).where(eq(profileNotes.id, noteId)).limit(1)
    if (!note) return res.status(404).json({ error: 'Nota não encontrada' })
    if (note.authorId !== me && note.profileUserId !== me) {
      return res.status(403).json({ error: 'Sem permissão' })
    }
    await db.delete(profileNotes).where(eq(profileNotes.id, noteId))
    res.json({ data: { ok: true } })
  })
)

router.patch(
  '/notes/:noteId/pin',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const noteId = req.params.noteId
    const me     = req.userId!
    const [note] = await db.select({
      id: profileNotes.id, pinned: profileNotes.pinned, profileUserId: profileNotes.profileUserId,
    }).from(profileNotes).where(eq(profileNotes.id, noteId)).limit(1)
    if (!note) return res.status(404).json({ error: 'Nota não encontrada' })
    if (note.profileUserId !== me) return res.status(403).json({ error: 'Apenas o dono do mural' })

    if (!note.pinned) {
      await db.update(profileNotes).set({ pinned: false })
        .where(eq(profileNotes.profileUserId, me))
    }
    await db.update(profileNotes).set({ pinned: !note.pinned })
      .where(eq(profileNotes.id, noteId))
    res.json({ data: { pinned: !note.pinned } })
  })
)

export default router
