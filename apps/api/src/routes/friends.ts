/**
 * Friends (amizades).
 *
 *   GET    /api/friends             aceitos (com presence)
 *   GET    /api/friends/requests    pedidos recebidos pendentes
 *   GET    /api/friends/outgoing    pedidos enviados pendentes
 *   POST   /api/friends/request     { username } cria pending
 *   POST   /api/friends/:id/accept  receiver aceita
 *   DELETE /api/friends/:id         remove/reject (qualquer lado)
 *
 * Normalização: pair sempre (userAId < userBId). requesterId é quem mandou.
 */
import { Router, Request, Response } from 'express'
import { z } from 'zod'
import { and, eq, or } from 'drizzle-orm'
import { db } from '../db'
import { friendships, users } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { badRequest, notFound } from '../lib/errors'
import { redis } from '../lib/redis'
import { getOrCreateConversation } from '../lib/dmCore'
import { isValidCoordinate, normalizeCoordinate } from '../lib/coordinate'

const router = Router()

function normalize(a: string, b: string): [string, string] {
  return a < b ? [a, b] : [b, a]
}

async function presenceFor(userId: string) {
  const v = await redis.get(`presence:user:${userId}`)
  if (!v) return 'OFFLINE'
  if (v === 'INVISIBLE') return 'OFFLINE'
  return v
}

// ── GET friends (aceitos) ───────────────────────────────────────
router.get('/', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const rows = await db.select().from(friendships)
    .where(and(
      eq(friendships.status, 'accepted'),
      or(eq(friendships.userAId, req.userId!), eq(friendships.userBId, req.userId!)),
    ))
  if (rows.length === 0) return res.json({ data: [] })

  const otherIds = rows.map((r) => r.userAId === req.userId ? r.userBId : r.userAId)
  const otherUsers = await db.select({
    id: users.id, username: users.username, displayName: users.displayName,
    avatarUrl: users.avatarUrl, customStatus: users.customStatus,
  }).from(users).where(or(...otherIds.map((id) => eq(users.id, id))))

  const userMap = new Map(otherUsers.map((u) => [u.id, u]))

  const enriched = await Promise.all(rows.map(async (r) => {
    const otherId = r.userAId === req.userId ? r.userBId : r.userAId
    const u = userMap.get(otherId)
    if (!u) return null
    return {
      friendshipId: r.id,
      user: u,
      presence: await presenceFor(otherId),
      since: r.acceptedAt?.toISOString() ?? null,
    }
  }))

  res.json({ data: enriched.filter(Boolean) })
}))

// ── Pending recebidos ──────────────────────────────────────────
router.get('/requests', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const rows = await db.select().from(friendships)
    .where(and(
      eq(friendships.status, 'pending'),
      or(eq(friendships.userAId, req.userId!), eq(friendships.userBId, req.userId!)),
    ))
  const incoming = rows.filter((r) => r.requesterId !== req.userId)
  if (incoming.length === 0) return res.json({ data: [] })

  const otherIds = incoming.map((r) => r.requesterId)
  const us = await db.select({
    id: users.id, username: users.username, displayName: users.displayName, avatarUrl: users.avatarUrl,
  }).from(users).where(or(...otherIds.map((id) => eq(users.id, id))))
  const m = new Map(us.map((u) => [u.id, u]))

  res.json({
    data: incoming.map((r) => ({
      friendshipId: r.id,
      user: m.get(r.requesterId) ?? null,
      createdAt: r.createdAt.toISOString(),
    })),
  })
}))

// ── Pending enviados ──────────────────────────────────────────
router.get('/outgoing', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const rows = await db.select().from(friendships)
    .where(and(
      eq(friendships.status, 'pending'),
      eq(friendships.requesterId, req.userId!),
    ))
  if (rows.length === 0) return res.json({ data: [] })

  const otherIds = rows.map((r) => r.userAId === req.userId ? r.userBId : r.userAId)
  const us = await db.select({
    id: users.id, username: users.username, displayName: users.displayName, avatarUrl: users.avatarUrl,
  }).from(users).where(or(...otherIds.map((id) => eq(users.id, id))))
  const m = new Map(us.map((u) => [u.id, u]))

  res.json({
    data: rows.map((r) => ({
      friendshipId: r.id,
      user: m.get(r.userAId === req.userId ? r.userBId : r.userAId) ?? null,
      createdAt: r.createdAt.toISOString(),
    })),
  })
}))

// ── Request ──────────────────────────────────────────────────
// Aceita identificação via username OU coordenada (Astra ID, ex: A7F2-9B).
const RequestSchema = z
  .object({
    username:   z.string().min(1).max(64).optional(),
    coordinate: z.string().refine(isValidCoordinate, 'Coordenada inválida').optional(),
  })
  .refine((d) => !!d.username || !!d.coordinate, {
    message: 'Informe username ou coordenada',
  })

router.post('/request', requireAuth, validate(RequestSchema), asyncHandler(async (req: Request, res: Response) => {
  const { username, coordinate } = req.body as z.infer<typeof RequestSchema>
  const [target] = await db.select({ id: users.id }).from(users)
    .where(coordinate
      ? eq(users.coordinate, normalizeCoordinate(coordinate))
      : eq(users.username, username!))
    .limit(1)
  if (!target) throw notFound('Usuário não encontrado')
  if (target.id === req.userId) throw badRequest('Não pode adicionar você mesmo')

  const [a, b] = normalize(req.userId!, target.id)

  // Idempotente: se já existe, retorna
  const [existing] = await db.select().from(friendships)
    .where(and(eq(friendships.userAId, a), eq(friendships.userBId, b))).limit(1)
  if (existing) {
    if (existing.status === 'accepted') return res.json({ data: { status: 'accepted', id: existing.id } })
    // Se outro mandou pra mim → auto-accept
    if (existing.requesterId !== req.userId) {
      const [accepted] = await db.update(friendships)
        .set({ status: 'accepted', acceptedAt: new Date() })
        .where(eq(friendships.id, existing.id))
        .returning()
      // Garante conversa DM (best-effort)
      getOrCreateConversation(req.userId!, target.id).catch(() => {})
      return res.json({ data: accepted })
    }
    return res.json({ data: existing })
  }

  const [created] = await db.insert(friendships).values({
    userAId: a, userBId: b, requesterId: req.userId!, status: 'pending',
  }).returning()
  res.status(201).json({ data: created })
}))

router.post('/:id/accept', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const { id } = req.params
  const [row] = await db.select().from(friendships).where(eq(friendships.id, id)).limit(1)
  if (!row) throw notFound('Pedido não encontrado')
  // Quem aceita NÃO pode ser o requester
  if (row.requesterId === req.userId) throw badRequest('Você que mandou — espere ele aceitar')
  if (row.userAId !== req.userId && row.userBId !== req.userId) throw badRequest('Sem acesso')
  if (row.status === 'accepted') return res.json({ data: row })

  const [updated] = await db.update(friendships)
    .set({ status: 'accepted', acceptedAt: new Date() })
    .where(eq(friendships.id, id))
    .returning()
  // Auto-cria DM conversation entre os dois (best-effort)
  const otherId = row.userAId === req.userId ? row.userBId : row.userAId
  getOrCreateConversation(req.userId!, otherId).catch(() => {})
  res.json({ data: updated })
}))

router.delete('/:id', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const { id } = req.params
  const [row] = await db.select().from(friendships).where(eq(friendships.id, id)).limit(1)
  if (!row) return res.json({ data: { ok: true } }) // idempotente
  if (row.userAId !== req.userId && row.userBId !== req.userId) throw badRequest('Sem acesso')
  await db.delete(friendships).where(eq(friendships.id, id))
  res.json({ data: { ok: true } })
}))

// ── PATCH /api/me/custom-status ───────────────────────────────
// Convenientemente aqui pra co-habitar com friends (perfil social).
const StatusSchema = z.object({ customStatus: z.string().max(100).nullable() })

router.patch('/custom-status', requireAuth, validate(StatusSchema), asyncHandler(async (req: Request, res: Response) => {
  const { customStatus } = req.body as z.infer<typeof StatusSchema>
  await db.update(users).set({ customStatus: customStatus?.trim() || null }).where(eq(users.id, req.userId!))
  res.json({ data: { customStatus: customStatus?.trim() || null } })
}))

export default router
