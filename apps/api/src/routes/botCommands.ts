import { Router, Request, Response } from 'express'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { comandosDeHoje, catalogoDeComandos } from '../lib/bot'

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

// O CATALOGO CRU, com a chave estavel de cada comando. Rota separada da de cima
// de proposito: aquela e "o que digitar hoje" (muda com o plantao e traz prefixo e
// exemplo), esta e "o que existe pra ligar e desligar" — a mesma lista serviria
// mal as duas, porque a chave nao pode mudar com o dia da semana.
router.get(
  '/catalog',
  requireAuth,
  asyncHandler(async (_req: Request, res: Response) => {
    res.json({ data: { comandos: catalogoDeComandos() } })
  })
)

export default router
