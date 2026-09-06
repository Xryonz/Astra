import { Router, Request, Response } from 'express'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { progressoDe, custoDoNivel, estrelasDaTrilha, XP_POR_MENSAGEM, XP_POR_MINUTO_CALL } from '../lib/xp'

const router = Router()

router.get('/me', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  res.json({ data: await progressoDe(req.userId!) })
}))

const NIVEIS_MOSTRADOS = 30
router.get('/regras', requireAuth, asyncHandler(async (_req: Request, res: Response) => {
  const trilha = Array.from({ length: NIVEIS_MOSTRADOS }, (_, i) => ({
    nivel:  i + 1,
    custo:  custoDoNivel(i),
    estrelas: estrelasDaTrilha(i + 1),
  }))
  res.json({
    data: {
      porMensagem:   XP_POR_MENSAGEM,
      porMinutoCall: XP_POR_MINUTO_CALL,
      trilha,
    },
  })
}))

router.get('/:userId', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  res.json({ data: await progressoDe(req.params.userId) })
}))

export default router
