import { Router, Request, Response } from 'express'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { painelDe } from '../lib/missoes'

const router = Router()

// A tela le isto ao abrir e nao volta mais: o avanco chega pelo socket
// (`mission_done`). Poll aqui seria caro por nada — missao muda no maximo algumas
// vezes por dia, e a maior parte do tempo a resposta seria identica a anterior.
router.get('/', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  res.json({ data: await painelDe(req.userId!) })
}))

export default router
