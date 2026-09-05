import { Router, Request, Response } from 'express'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { painelDe, resgatar, resgatarTudo } from '../lib/missoes'

const router = Router()

router.get('/', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  res.json({ data: await painelDe(req.userId!) })
}))

router.post('/resgatar', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  res.json({ data: { resgates: await resgatarTudo(req.userId!) } })
}))

router.post('/:missionId/resgatar', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const feito = await resgatar(req.userId!, req.params.missionId)
  if (!feito) return res.status(409).json({ error: 'Missão não está pronta para resgate' })
  res.json({ data: feito })
}))

export default router
