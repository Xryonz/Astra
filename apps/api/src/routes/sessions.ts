import { Router, Request, Response } from 'express'
import { and, desc, eq, isNull, ne } from 'drizzle-orm'
import { db } from '../db'
import { refreshTokens } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { hashToken } from '../lib/jwt'

const router = Router()

router.get(
  '/',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const rows = await db.select({
      id:         refreshTokens.id,
      createdAt:  refreshTokens.createdAt,
      lastUsedAt: refreshTokens.lastUsedAt,
      expiresAt:  refreshTokens.expiresAt,
      userAgent:  refreshTokens.userAgent,
      ip:         refreshTokens.ip,
    })
      .from(refreshTokens)
      .where(and(
        eq(refreshTokens.userId, req.userId!),
        isNull(refreshTokens.revokedAt),
      ))
      .orderBy(desc(refreshTokens.lastUsedAt))

    const now = Date.now()
    const active = rows.filter((r) => r.expiresAt.getTime() > now)

    res.json({ data: { sessions: active } })
  })
)

router.delete(
  '/:id',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const id = req.params.id
    const result = await db.update(refreshTokens)
      .set({ revokedAt: new Date() })
      .where(and(
        eq(refreshTokens.id, id),
        eq(refreshTokens.userId, req.userId!),
        isNull(refreshTokens.revokedAt),
      ))
      .returning({ id: refreshTokens.id })

    if (result.length === 0) {
      return res.status(404).json({ error: 'Sessão não encontrada ou já revogada' })
    }
    res.json({ data: { revoked: result[0].id } })
  })
)

router.post(
  '/revoke-others',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    const refreshTokenRaw = typeof req.body?.refreshToken === 'string' ? req.body.refreshToken : ''
    if (!refreshTokenRaw) {
      return res.status(400).json({ error: 'refreshToken da sessão atual é obrigatório' })
    }
    const tokenHash = hashToken(refreshTokenRaw)

    const result = await db.update(refreshTokens)
      .set({ revokedAt: new Date() })
      .where(and(
        eq(refreshTokens.userId, req.userId!),
        isNull(refreshTokens.revokedAt),
        ne(refreshTokens.token, tokenHash),
      ))
      .returning({ id: refreshTokens.id })

    res.json({ data: { revokedCount: result.length } })
  })
)

export default router
