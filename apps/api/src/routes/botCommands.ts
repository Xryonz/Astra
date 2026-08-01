import { Router, Request, Response } from 'express'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { comandosDeHoje } from '../lib/bot'

// Catalogo de comandos pro cliente montar a caixinha que abre ao digitar "/".
//
// Vem do MESMO array que o `ajuda` usa (lib/bot.ts). Duplicar a lista no app
// seria mais rapido de escrever e ficaria velha no primeiro comando novo — e
// ninguem perceberia, porque nada quebra: a caixinha so deixaria de mostrar.
//
// A lista muda com o dia (prefixo do plantao + extras de fim de semana), por
// isso e montada a cada chamada e nao pode ser cacheada no cliente.
const router = Router()

router.get(
  '/commands',
  requireAuth,
  asyncHandler(async (_req: Request, res: Response) => {
    res.json({ data: comandosDeHoje() })
  })
)

export default router
