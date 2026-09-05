import { Router, Request, Response } from 'express'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { authLimiter } from '../middleware/rateLimiter'
import { cartaoDoLink } from '../lib/unfurl'
import { badRequest } from '../lib/errors'

const router = Router()

router.get(
  '/unfurl',
  requireAuth,
  authLimiter,
  asyncHandler(async (req: Request, res: Response) => {
    const alvo = String(req.query.url ?? '')
    if (!alvo || alvo.length > 2048) throw badRequest('URL ausente ou longa demais')

    const cartao = await cartaoDoLink(alvo)
    res.json({ data: cartao })
  }),
)

export default router
