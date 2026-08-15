import { Router, Request, Response } from 'express'
import { Server as SocketServer } from 'socket.io'
import { and, desc, eq, gt, inArray, isNull, lt, or } from 'drizzle-orm'
import { z } from 'zod'
import { db } from '../db'
import { users, dmConversations, directMessages } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { messageLimiter } from '../middleware/rateLimiter'
import { AttachmentSchema, MessageCursorSchema } from '@astra/types'
import { notify } from '../lib/notifications'
import { messagesSentTotal } from '../lib/metrics'
import { getOrCreateConversation } from '../lib/dmCore'
import { haBloqueio } from '../lib/blocks'
import { primeiroAnexoNaoPermitido } from '../lib/storage'
import { getBotId } from '../lib/bot'
import { responderNoSussurro } from '../lib/botSussurro'
import { entregarSussurro } from '../lib/realtime'

const SendDMSchema = z.object({
  content:     z.string().min(0).max(4000),
  // Mesmo schema seguro dos canais: a url passa por SafeUrlSchema (so http(s) ou
  // /relativa) -> bloqueia anexo de DM com data:/javascript: (era url livre).
  attachments: z.array(AttachmentSchema).max(10).optional(),
  replyToId:   z.string().optional(),
  ttlSeconds:  z.number().int().min(60).max(60 * 60 * 24 * 30).optional(),
  clientNonce: z.string().max(64).optional(),
}).refine(
  (d) => (d.content?.trim().length ?? 0) > 0 || (d.attachments?.length ?? 0) > 0,
  { message: 'Mensagem vazia' },
)

function safeJson<T>(raw: string | null | undefined, fallback: T): T {
  if (!raw) return fallback
  try { return JSON.parse(raw) as T } catch { return fallback }
}

export function createDMRouter(io: SocketServer) {
  const router = Router()

  router.get(
    '/',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const userId = req.userId!

      const all = await db.select().from(dmConversations)
        .where(or(eq(dmConversations.userAId, userId), eq(dmConversations.userBId, userId)))
        .orderBy(desc(dmConversations.updatedAt))

      // Conversas FECHADAS por mim somem — mas so enquanto nada acontecer nelas.
      // Mensagem nova bumpa o updatedAt e a conversa volta sozinha, sem precisar de
      // uma acao pra "reabrir". Filtro no JS (nao no SQL) porque qual coluna vale
      // depende de eu ser o lado A ou o B desta conversa.
      const convs = all.filter((c) => {
        const hidden = c.userAId === userId ? c.hiddenByA : c.hiddenByB
        return !hidden || c.updatedAt > hidden
      })

      if (convs.length === 0) return res.json({ data: [] })

      const otherIds = convs.map((c) => (c.userAId === userId ? c.userBId : c.userAId))
      const convIds  = convs.map((c) => c.id)

      const [otherUsers, lastMessages] = await Promise.all([
        db.select({
          id: users.id, username: users.username,
          displayName: users.displayName, avatarUrl: users.avatarUrl,
        }).from(users).where(inArray(users.id, otherIds)),

        db.select().from(directMessages)
          .where(and(inArray(directMessages.conversationId, convIds), isNull(directMessages.deletedAt)))
          .orderBy(desc(directMessages.createdAt)),
      ])

      const usersById = new Map(otherUsers.map((u) => [u.id, u]))
      const lastByConv = new Map<string, typeof lastMessages[number]>()
      for (const m of lastMessages) {
        if (!lastByConv.has(m.conversationId)) lastByConv.set(m.conversationId, m)
      }

      const shaped = convs.map((c) => ({
        id:          c.id,
        otherUser:   usersById.get(c.userAId === userId ? c.userBId : c.userAId) ?? null,
        lastMessage: lastByConv.get(c.id) ?? null,
        updatedAt:   c.updatedAt,
        muted:       (c.userAId === userId ? c.mutedByA : c.mutedByB) != null,
      }))

      res.json({ data: shaped })
    })
  )

  router.post(
    '/open',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const { userId, username } = (req.body ?? {}) as { userId?: string; username?: string }
      if (!userId && !username) return res.status(400).json({ error: 'Informe userId ou username' })

      const [target] = await db.select({
        id: users.id, username: users.username,
        displayName: users.displayName, avatarUrl: users.avatarUrl, bio: users.bio,
      }).from(users)
        .where(userId ? eq(users.id, userId) : eq(users.username, username!))
        .limit(1)

      if (!target) return res.status(404).json({ error: 'Usuário não encontrado' })
      if (target.id === req.userId) return res.status(400).json({ error: 'Não pode abrir DM consigo mesmo' })
      // Mensagem neutra de proposito: quem foi bloqueado nao deve descobrir que
      // foi, e um "você foi bloqueado" aqui contaria.
      if (await haBloqueio(req.userId!, target.id)) {
        return res.status(403).json({ error: 'Não é possível conversar com essa pessoa' })
      }

      const conversation = await getOrCreateConversation(req.userId!, target.id)
      res.json({ data: { conversationId: conversation.id, otherUser: target } })
    })
  )

  router.post(
    '/open/:username',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const [target] = await db.select({
        id: users.id, username: users.username,
        displayName: users.displayName, avatarUrl: users.avatarUrl, bio: users.bio,
      }).from(users).where(eq(users.username, req.params.username)).limit(1)

      if (!target) return res.status(404).json({ error: 'Usuário não encontrado' })
      if (target.id === req.userId) return res.status(400).json({ error: 'Você não pode abrir um DM consigo mesmo' })

      const conversation = await getOrCreateConversation(req.userId!, target.id)
      res.json({ data: { conversationId: conversation.id, otherUser: target } })
    })
  )

  router.get(
    '/:conversationId/messages',
    requireAuth,
    validate(MessageCursorSchema, 'query'),
    asyncHandler(async (req: Request, res: Response) => {
      const { conversationId } = req.params
      const { cursor, limit }  = req.query as unknown as { cursor?: string; limit: number }
      const take = Number(limit) || 30

      const [conv] = await db.select().from(dmConversations)
        .where(and(
          eq(dmConversations.id, conversationId),
          or(eq(dmConversations.userAId, req.userId!), eq(dmConversations.userBId, req.userId!)),
        ))
        .limit(1)
      if (!conv) return res.status(403).json({ error: 'Acesso negado' })

      const conditions = [
        eq(directMessages.conversationId, conversationId),
        isNull(directMessages.deletedAt),
      ]
      if (cursor) conditions.push(lt(directMessages.id, cursor))

      const now = new Date()
      conditions.push(
        or(isNull(directMessages.expiresAt), gt(directMessages.expiresAt, now)) as any,
      )

      const rows = await db.select({
        id:             directMessages.id,
        content:        directMessages.content,
        senderId:       directMessages.senderId,
        receiverId:     directMessages.receiverId,
        conversationId: directMessages.conversationId,
        attachments:    directMessages.attachments,
        replyToId:      directMessages.replyToId,
        // Sem isto a chamada volta do banco como mensagem comum: a linha existe,
        // mas perde o desenho próprio na hora de recarregar a conversa.
        call:           directMessages.call,
        expiresAt:      directMessages.expiresAt,
        edited:         directMessages.edited,
        deletedAt:      directMessages.deletedAt,
        createdAt:      directMessages.createdAt,

        author: {
          id: users.id, username: users.username,
          displayName: users.displayName, avatarUrl: users.avatarUrl,
          displayFont: users.displayFont,
        },
      })
        .from(directMessages)
        .innerJoin(users, eq(users.id, directMessages.senderId))
        .where(and(...conditions))
        .orderBy(desc(directMessages.createdAt))
        .limit(take + 1)

      const hasMore   = rows.length > take
      const items     = hasMore ? rows.slice(0, take) : rows
      const nextCursor = hasMore ? items[items.length - 1].id : null

      const replyIds = items.map((m) => m.replyToId).filter(Boolean) as string[]
      let replyMap = new Map<string, { id: string; content: string; authorName: string; authorAvatar: string | null }>()
      if (replyIds.length > 0) {
        const replies = await db.select({
          id:      directMessages.id,
          content: directMessages.content,
          author: {
            displayName: users.displayName,
            avatarUrl:   users.avatarUrl,
          },
        })
          .from(directMessages)
          .innerJoin(users, eq(users.id, directMessages.senderId))
          .where(inArray(directMessages.id, replyIds))
        replyMap = new Map(replies.map((r) => [r.id, {
          id:           r.id,
          content:      r.content.slice(0, 160),
          authorName:   r.author.displayName,
          authorAvatar: r.author.avatarUrl,
        }]))
      }

      const shaped = items.map((m) => ({
        ...m,
        attachments: safeJson<unknown[]>(m.attachments, []),
        // Objeto, não a string crua da coluna: é assim que a linha chega pelo
        // socket, e o cliente não deve ter dois formatos pra mesma coisa.
        call:        m.call ? safeJson<unknown>(m.call, null) : null,
        replyTo:     m.replyToId ? replyMap.get(m.replyToId) ?? null : null,
      }))

      res.json({ data: { items: shaped.reverse(), nextCursor, hasMore } })
    })
  )

  // Silenciar/dessilenciar a conversa (so o proprio lado). Preserva o
  // updatedAt: mutar nao pode reordenar a lista de DMs.
  router.put(
    '/:conversationId/mute',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const { conversationId } = req.params
      const [conv] = await db.select().from(dmConversations)
        .where(and(
          eq(dmConversations.id, conversationId),
          or(eq(dmConversations.userAId, req.userId!), eq(dmConversations.userBId, req.userId!)),
        ))
        .limit(1)
      if (!conv) return res.status(403).json({ error: 'Acesso negado' })

      const patch = conv.userAId === req.userId
        ? { mutedByA: new Date(), updatedAt: conv.updatedAt }
        : { mutedByB: new Date(), updatedAt: conv.updatedAt }
      await db.update(dmConversations).set(patch).where(eq(dmConversations.id, conv.id))
      res.json({ data: { conversationId: conv.id, muted: true } })
    })
  )

  router.delete(
    '/:conversationId/mute',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const { conversationId } = req.params
      const [conv] = await db.select().from(dmConversations)
        .where(and(
          eq(dmConversations.id, conversationId),
          or(eq(dmConversations.userAId, req.userId!), eq(dmConversations.userBId, req.userId!)),
        ))
        .limit(1)
      if (!conv) return res.status(403).json({ error: 'Acesso negado' })

      const patch = conv.userAId === req.userId
        ? { mutedByA: null, updatedAt: conv.updatedAt }
        : { mutedByB: null, updatedAt: conv.updatedAt }
      await db.update(dmConversations).set(patch).where(eq(dmConversations.id, conv.id))
      res.json({ data: { conversationId: conv.id, muted: false } })
    })
  )

  // "Fechar mensagem direta". NAO apaga nada e nao afeta o outro lado: so marca
  // que EU escondi. A conversa reaparece sozinha na proxima mensagem (ver o filtro
  // na listagem). Preserva o updatedAt de proposito — bumpar aqui faria a conversa
  // voltar pro topo de quem acabou de fecha-la.
  router.delete(
    '/:conversationId',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const { conversationId } = req.params
      const [conv] = await db.select().from(dmConversations)
        .where(and(
          eq(dmConversations.id, conversationId),
          or(eq(dmConversations.userAId, req.userId!), eq(dmConversations.userBId, req.userId!)),
        ))
        .limit(1)
      if (!conv) return res.status(403).json({ error: 'Acesso negado' })

      const now = new Date()
      const patch = conv.userAId === req.userId
        ? { hiddenByA: now, updatedAt: conv.updatedAt }
        : { hiddenByB: now, updatedAt: conv.updatedAt }
      await db.update(dmConversations).set(patch).where(eq(dmConversations.id, conv.id))
      res.json({ data: { conversationId: conv.id, closed: true } })
    })
  )

  router.post(
    '/:conversationId/messages',
    requireAuth,
    messageLimiter,
    validate(SendDMSchema),
    asyncHandler(async (req: Request, res: Response) => {
      const { conversationId } = req.params
      const { content, attachments = [], replyToId, ttlSeconds } = req.body as z.infer<typeof SendDMSchema>

      // Anexo so aponta pro armazenamento do app ou pra CDN de GIF (ver storage.ts).
      // No sussurro isto pesa mais que no canal: sao duas pessoas, entao uma URL
      // externa entrega o IP e o horario de leitura de UMA pessoa especifica.
      const anexoRuim = primeiroAnexoNaoPermitido(attachments)
      if (anexoRuim) return res.status(400).json({ error: `Anexo com URL não permitida: ${anexoRuim}` })

      const [conv] = await db.select().from(dmConversations)
        .where(and(
          eq(dmConversations.id, conversationId),
          or(eq(dmConversations.userAId, req.userId!), eq(dmConversations.userBId, req.userId!)),
        ))
        .limit(1)
      if (!conv) return res.status(403).json({ error: 'Acesso negado' })

      const receiverId = conv.userAId === req.userId ? conv.userBId : conv.userAId
      // Bloqueio vale nos DOIS sentidos: quem bloqueou tambem para de mandar.
      // Checado no envio, e nao so ao abrir a conversa, porque a conversa pode ter
      // sido aberta ANTES do bloqueio e continuar na tela de quem ja estava nela.
      if (await haBloqueio(req.userId!, receiverId)) {
        return res.status(403).json({ error: 'Não é possível conversar com essa pessoa' })
      }

      let validReplyToId: string | null = null
      let replySnapshot: { id: string; content: string; authorName: string; authorAvatar: string | null } | null = null
      if (replyToId) {
        const [r] = await db.select({
          id:        directMessages.id,
          content:   directMessages.content,
          authorName: users.displayName,
          authorAvatar: users.avatarUrl,
        })
          .from(directMessages)
          .innerJoin(users, eq(users.id, directMessages.senderId))
          .where(and(eq(directMessages.id, replyToId), eq(directMessages.conversationId, conversationId)))
          .limit(1)
        if (r) {
          validReplyToId = r.id
          replySnapshot  = { id: r.id, content: r.content.slice(0, 160), authorName: r.authorName, authorAvatar: r.authorAvatar }
        }
      }

      const expiresAt = ttlSeconds ? new Date(Date.now() + ttlSeconds * 1000) : null

      const [insertedRows, authorRows] = await Promise.all([
        db.insert(directMessages).values({
          content, senderId: req.userId!, receiverId, conversationId,
          attachments: JSON.stringify(attachments),
          replyToId:   validReplyToId,
          expiresAt:   expiresAt as any,
        }).returning(),
        db.select({
          id: users.id, username: users.username,
          displayName: users.displayName, avatarUrl: users.avatarUrl,
          displayFont: users.displayFont,
        }).from(users).where(eq(users.id, req.userId!)).limit(1),
      ])
      const inserted = insertedRows[0]
      const author   = authorRows[0]

      const message = {
        ...inserted,
        attachments,
        replyTo: replySnapshot,
        author,
      }

      entregarSussurro(io, conversationId, [req.userId, receiverId], 'new_dm', message)
      messagesSentTotal.inc({ kind: 'dm' })
      res.status(201).json({ data: message })

      setImmediate(() => {
        void (async () => {
          try {
            await db.update(dmConversations).set({ updatedAt: new Date() })
              .where(eq(dmConversations.id, conversationId))

            // Receptor silenciou a conversa: mensagem entra normal (socket ja
            // emitiu), mas sem feed/push/badge.
            const receiverMuted =
              (conv.userAId === receiverId ? conv.mutedByA : conv.mutedByB) != null
            if (receiverMuted) return

            await notify({
              io, userId: receiverId, actorId: req.userId!, type: 'dm',
              payload: {
                messageId:      inserted.id,
                conversationId,
                authorId:       author?.id,
                authorName:     author?.displayName || author?.username,
                authorAvatar:   author?.avatarUrl ?? null,
                preview:        content.slice(0, 140),
              },
              push: {
                title: `Nova DM de ${author?.displayName ?? 'Alguém'}`,
                body:  content.slice(0, 140),

                url:   `/app/dm/${conversationId}`,
                tag:   `dm-${conversationId}`,
                icon:  author?.avatarUrl ?? undefined,
                sender: author?.displayName ?? 'Alguém',
              },
            }).catch(() => {})
          } catch (err) {
            // eslint-disable-next-line no-console
            console.error('[dm POST] background work failed:', err)
          }
        })()
      })

      // A BOT RESPONDE NO SUSSURRO.
      //
      // Ela sempre foi um usuario de verdade, entao abrir conversa com ela ja
      // funcionava — e ficava no vacuo, porque todo o caminho que a faz falar
      // morava no `bot_command` do socket, que exige canal E constelacao.
      //
      // Aqui NAO se exige prefixo, e essa e a diferenca que importa: num canal o
      // prefixo existe pra separar "estou falando com a bot" de "estou falando com
      // a sala". Numa conversa de duas pessoas onde a outra e ela, tudo que se
      // escreve ja e endereçado a ela — pedir `/sparkle` antes de cada frase seria
      // cerimonia sem funcao. Os comandos seguem valendo pra quem quiser usar.
      //
      // Em segundo plano de proposito: a IA leva segundos, e a SUA mensagem tem
      // que aparecer na hora. A resposta chega depois pelo socket, como a de
      // qualquer pessoa que estivesse digitando.
      if (receiverId === (await getBotId())) {
        setImmediate(() => { void responderNoSussurro({ io, conversationId, userId: req.userId!, receiverId, content, username: author?.username ?? 'você' }) })
      }
    })
  )

  router.delete(
    '/:conversationId/messages/:messageId',
    requireAuth,
    asyncHandler(async (req: Request, res: Response) => {
      const { conversationId, messageId } = req.params

      const [message] = await db.select().from(directMessages)
        .where(and(
          eq(directMessages.id, messageId),
          eq(directMessages.conversationId, conversationId),
          isNull(directMessages.deletedAt),
        ))
        .limit(1)

      if (!message) return res.status(404).json({ error: 'Mensagem não encontrada' })
      if (message.senderId !== req.userId) return res.status(403).json({ error: 'Sem permissão' })

      await db.update(directMessages).set({ deletedAt: new Date() }).where(eq(directMessages.id, messageId))

      // Mesma regra do new_dm: quem nao esta com a conversa aberta tambem precisa
      // saber que a mensagem sumiu, senao ela fica na tela do outro ate o reload.
      entregarSussurro(
        io, conversationId, [message.senderId, message.receiverId],
        'dm_deleted', { messageId, conversationId },
      )
      res.json({ message: 'Mensagem removida' })
    })
  )

  return router
}
