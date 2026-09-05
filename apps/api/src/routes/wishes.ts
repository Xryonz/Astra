
import { Router, Request, Response } from 'express'
import { z } from 'zod'
import { and, desc, eq, lt, or } from 'drizzle-orm'
import { db } from '../db'
import { wishingStars, users } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { badRequest } from '../lib/errors'
import {
  DESEJOS_NA_JANELA,
  DESEJO_MAXIMO,
  DESEJO_MINIMO,
  limparDesejo,
  podeDesejar,
} from '../lib/desejo'

const router = Router()

const PostSchema = z.object({
  content: z.string().min(DESEJO_MINIMO).max(DESEJO_MAXIMO),
})

const QuerySchema = z.object({
  limit:  z.coerce.number().int().min(1).max(50).optional().default(20),
  cursor: z.string().max(80).optional(),
})

router.get('/', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const parsed = QuerySchema.safeParse(req.query)
  if (!parsed.success) throw badRequest('Parâmetros inválidos')
  const { limit, cursor } = parsed.data

  let where = undefined
  if (cursor) {
    const [iso, id] = cursor.split('__')
    const dt = new Date(iso)
    if (!Number.isNaN(dt.getTime()) && id) {

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
  if (!podeDesejar(req.userId!)) {
    throw badRequest(
      `Você já deixou ${DESEJOS_NA_JANELA} desejos nos últimos dez minutos. Tente de novo daqui a pouco.`,
    )
  }
  const raw     = (req.body as z.infer<typeof PostSchema>).content
  const content = limparDesejo(raw)
  if (content.length < DESEJO_MINIMO) {
    throw badRequest(`Mínimo ${DESEJO_MINIMO} caracteres de texto real.`)
  }
  if (content.length > DESEJO_MAXIMO) {
    throw badRequest(`Máximo ${DESEJO_MAXIMO} caracteres.`)
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
