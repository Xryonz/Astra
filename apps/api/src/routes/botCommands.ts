import { Router, Request, Response } from 'express'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { comandosDeHoje, catalogoDeComandos } from '../lib/bot'

const router = Router()

router.get(
  '/commands',
  requireAuth,
  asyncHandler(async (_req: Request, res: Response) => {
    res.json({ data: comandosDeHoje() })
  })
)

router.get(
  '/catalog',
  requireAuth,
  asyncHandler(async (_req: Request, res: Response) => {
    res.json({ data: { comandos: catalogoDeComandos() } })
  })
)

export default router
