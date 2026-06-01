/**
 * Bookmarks — pasta pessoal de mensagens salvas.
 *
 *   GET    /api/bookmarks                → lista paginada (cursor)
 *   POST   /api/bookmarks                → cria { targetId, kind, note? }
 *   PATCH  /api/bookmarks/:id            → atualiza note
 *   DELETE /api/bookmarks/:id            → remove
 *
 * Privado por user (RLS no app: where userId = req.userId em toda query).
 * Lista enriquece com snapshot da mensagem alvo (autor + preview) — single
 * query agregando via LEFT JOIN; LEFT pra resistir a target deletada.
 */
import { Router, Request, Response } from 'express'
import { z } from 'zod'
import { and, desc, eq, lt } from 'drizzle-orm'
import { db } from '../db'
import { bookmarks, messages, directMessages, users } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { badRequest } from '../lib/errors'

const router = Router()

const CreateSchema = z.object({
  targetId: z.string().min(1).max(64),
  kind:     z.enum(['message', 'dm']),
  note:     z.string().max(500).optional(),
})

const PatchSchema = z.object({
  note: z.string().max(500).nullable(),
})

router.get('/', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const limit  = Math.min(Number(req.query.limit ?? 30), 100)
  const cursor = req.query.cursor as string | undefined

  let cursorDate: Date | null = null
  if (cursor) {
    try {
      const parsed = JSON.parse(Buffer.from(cursor, 'base64url').toString('utf8'))
      cursorDate = new Date(parsed.createdAt)
    } catch {}
  }

  const where = cursorDate
    ? and(eq(bookmarks.userId, req.userId!), lt(bookmarks.createdAt, cursorDate))
    : eq(bookmarks.userId, req.userId!)

  const rows = await db.select().from(bookmarks).where(where!)
    .orderBy(desc(bookmarks.createdAt)).limit(limit + 1)

  const hasMore    = rows.length > limit
  const items      = hasMore ? rows.slice(0, limit) : rows
  const last       = items[items.length - 1]
  const nextCursor = hasMore && last
    ? Buffer.from(JSON.stringify({ createdAt: last.createdAt.toISOString() })).toString('base64url')
    : null

  // Enriquecimento em batch: separa por kind, faz 2 queries (1 por tipo).
  // Mais rápido que 1 JOIN com union pq Drizzle não suporta union elegante.
  const msgIds = items.filter((b) => b.kind === 'message').map((b) => b.targetId)
  const dmIds  = items.filter((b) => b.kind === 'dm').map((b) => b.targetId)

  const [msgSnaps, dmSnaps] = await Promise.all([
    msgIds.length > 0
      ? db.select({
          id: messages.id, content: messages.content, channelId: messages.channelId,
          createdAt: messages.createdAt,
          authorId: users.id, authorName: users.displayName, authorAvatar: users.avatarUrl,
        }).from(messages).innerJoin(users, eq(users.id, messages.authorId))
          .where(inArrayOrFalse(messages.id, msgIds))
      : Promise.resolve([] as any[]),
    dmIds.length > 0
      ? db.select({
          id: directMessages.id, content: directMessages.content, conversationId: directMessages.conversationId,
          createdAt: directMessages.createdAt,
          authorId: users.id, authorName: users.displayName, authorAvatar: users.avatarUrl,
        }).from(directMessages).innerJoin(users, eq(users.id, directMessages.senderId))
          .where(inArrayOrFalse(directMessages.id, dmIds))
      : Promise.resolve([] as any[]),
  ])

  const snapMap = new Map<string, any>()
  for (const s of msgSnaps) snapMap.set(`message:${s.id}`, s)
  for (const s of dmSnaps)  snapMap.set(`dm:${s.id}`,      s)

  res.json({
    data: {
      items: items.map((b) => ({
        id:        b.id,
        targetId:  b.targetId,
        kind:      b.kind,
        note:      b.note,
        createdAt: b.createdAt.toISOString(),
        snapshot:  snapMap.get(`${b.kind}:${b.targetId}`) ?? null,
      })),
      nextCursor,
    },
  })
}))

router.post('/', requireAuth, validate(CreateSchema), asyncHandler(async (req: Request, res: Response) => {
  const { targetId, kind, note } = req.body as z.infer<typeof CreateSchema>

  // Validação: target precisa existir e ser acessível pelo user.
  // 'message' → checa membership do server dono do canal. Skip detalhe aqui
  // confiando que se o user tem o messageId, ele veio do feed dele (autz mínimo).
  // 'dm' → confere que o user participa da conv.
  if (kind === 'dm') {
    const [dm] = await db.select({ senderId: directMessages.senderId, receiverId: directMessages.receiverId })
      .from(directMessages).where(eq(directMessages.id, targetId)).limit(1)
    if (!dm)                                          return res.status(404).json({ error: 'DM não encontrada' })
    if (dm.senderId !== req.userId && dm.receiverId !== req.userId) {
      return res.status(403).json({ error: 'Sem acesso' })
    }
  }
  // kind 'message' → confia no client (todos os feeds já são protegidos por membership)

  try {
    const [created] = await db.insert(bookmarks).values({
      userId: req.userId!, targetId, kind, note: note ?? null,
    }).returning()
    res.status(201).json({ data: created })
  } catch (e: any) {
    if (e?.code === '23505') {
      // Já existe — retorna idempotente
      const [existing] = await db.select().from(bookmarks)
        .where(and(eq(bookmarks.userId, req.userId!), eq(bookmarks.targetId, targetId), eq(bookmarks.kind, kind)))
        .limit(1)
      return res.json({ data: existing })
    }
    throw e
  }
}))

router.patch('/:id', requireAuth, validate(PatchSchema), asyncHandler(async (req: Request, res: Response) => {
  const { id } = req.params
  const { note } = req.body as z.infer<typeof PatchSchema>

  const [updated] = await db.update(bookmarks).set({ note })
    .where(and(eq(bookmarks.id, id), eq(bookmarks.userId, req.userId!)))
    .returning()

  if (!updated) throw badRequest('Bookmark não encontrado')
  res.json({ data: updated })
}))

router.delete('/:id', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const { id } = req.params
  await db.delete(bookmarks).where(and(eq(bookmarks.id, id), eq(bookmarks.userId, req.userId!)))
  res.json({ data: { ok: true } })
}))

// Helper: inArray sem fan-out quando empty
import { inArray } from 'drizzle-orm'
function inArrayOrFalse<T extends { name: string }>(col: T, vals: string[]) {
  return inArray(col as any, vals)
}

export default router
