import { Router, Request, Response } from 'express'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { BOT_COMMANDS } from '../lib/bot'

// Catalogo de comandos pro cliente montar a caixinha que abre ao digitar "/".
//
// Vem do MESMO array que o `/astra ajuda` usa (lib/bot.ts). Duplicar a lista no
// app seria mais rapido de escrever e ficaria velha no primeiro comando novo —
// e ninguem perceberia, porque nada quebra: a caixinha so deixaria de mostrar.
const router = Router()

router.get(
  '/commands',
  requireAuth,
  asyncHandler(async (_req: Request, res: Response) => {
    res.json({ data: BOT_COMMANDS })
  })
)

export default router
