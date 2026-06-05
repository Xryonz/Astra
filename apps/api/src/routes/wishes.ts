/**
 * WishingStar — sugestões públicas globais do que mudar no Astra.
 *
 *   GET  /api/wishes?limit=20&cursor=<isoDate-id>   lista global ordenada
 *   POST /api/wishes        { content: string }    cria um wish
 *
 * Sanitização: só texto. Newlines preservados, mas markdown/HTML escapado
 * no client (não vamos renderizar como rich). Limite 4–500 chars.
 *
 * Anti-spam: rate-limit 3 wishes / 10min por user via in-memory throttle.
 */
import { Router, Request, Response } from 'express'
import { z } from 'zod'
import { and, desc, eq, lt, or } from 'drizzle-orm'
import { db } from '../db'
import { wishingStars, users } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { badRequest } from '../lib/errors'

const router = Router()

const WISH_MIN = 4
const WISH_MAX = 500

const PostSchema = z.object({
  content: z.string().min(WISH_MIN).max(WISH_MAX),
})

const QuerySchema = z.object({
  limit:  z.coerce.number().int().min(1).max(50).optional().default(20),
  cursor: z.string().max(80).optional(),
})

// Remove control chars (preserva \t \n \r), normaliza unicode, colapsa whitespace.
// Markdown/HTML são escapados no render do client — aqui apenas garantimos texto puro.
// eslint-disable-next-line no-control-regex
const CONTROL_CHAR_RE = /[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g
function sanitizeContent(raw: string): string {
  return raw
    .normalize('NFKC')
    .replace(CONTROL_CHAR_RE, '')
    .replace(/[ \t]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

// Rate-limit por user: max 3 wishes em 10min
const RATE_WINDOW_MS = 10 * 60_000
const RATE_MAX       = 3
const recent         = new Map<string, number[]>()
function rateOk(userId: string): boolean {
  const now    = Date.now()
  const stamps = (recent.get(userId) ?? []).filter((t) => now - t < RATE_WINDOW_MS)
  if (stamps.length >= RATE_MAX) {
    recent.set(userId, stamps)
    return false
  }
  stamps.push(now)
  recent.set(userId, stamps)
  return true
}

router.get('/', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const parsed = QuerySchema.safeParse(req.query)
  if (!parsed.success) throw badRequest('Parâmetros inválidos')
  const { limit, cursor } = parsed.data

  // Cursor format: <createdAtIso>__<id>
  let where = undefined
  if (cursor) {
    const [iso, id] = cursor.split('__')
    const dt = new Date(iso)
    if (!Number.isNaN(dt.getTime()) && id) {
      // Itens criados ANTES do cursor (pagina pra trás na ordem DESC)
      where = or(
        lt(wishingStars.createdAt, dt),
        and(eq(wishingStars.createdAt, dt), lt(wishingStars.id, id)),
      )
    }
  }

  const rows = await db
    .select({
      id:        wishingStars.id,
      content:   wishingStars.content,
      createdAt: wishingStars.createdAt,
      author: {
        id:          users.id,
        username:    users.username,
        displayName: users.displayName,
        avatarUrl:   users.avatarUrl,
      },
    })
    .from(wishingStars)
    .innerJoin(users, eq(users.id, wishingStars.userId))
    .where(where)
    .orderBy(desc(wishingStars.createdAt), desc(wishingStars.id))
    .limit(limit + 1)

  const hasMore   = rows.length > limit
  const items     = hasMore ? rows.slice(0, limit) : rows
  const last      = items[items.length - 1]
  const nextCursor = hasMore && last
    ? `${last.createdAt.toISOString()}__${last.id}`
    : null

  res.json({ data: { items, nextCursor } })
}))

router.post('/', requireAuth, validate(PostSchema), asyncHandler(async (req: Request, res: Response) => {
  if (!rateOk(req.userId!)) {
    throw badRequest('Você atingiu o limite de sugestões por hora. Tenta de novo mais tarde.')
  }
  const raw     = (req.body as z.infer<typeof PostSchema>).content
  const content = sanitizeContent(raw)
  if (content.length < WISH_MIN) {
    throw badRequest(`Mínimo ${WISH_MIN} caracteres de texto real.`)
  }
  if (content.length > WISH_MAX) {
    throw badRequest(`Máximo ${WISH_MAX} caracteres.`)
  }

  const [inserted] = await db.insert(wishingStars).values({
    userId:  req.userId!,
    content,
  }).returning({
    id:        wishingStars.id,
    content:   wishingStars.content,
    createdAt: wishingStars.createdAt,
  })

  res.status(201).json({ data: inserted })
}))

export default router
