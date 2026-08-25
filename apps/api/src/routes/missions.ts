import { Router, Request, Response } from 'express'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { painelDe } from '../lib/missoes'

const router = Router()

router.get('/', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  res.json({ data: await painelDe(req.userId!) })
}))

export default router
