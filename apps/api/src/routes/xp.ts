import { Router, Request, Response } from 'express'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { progressoDe, custoDoNivel, brilhoDaTrilha, XP_POR_MENSAGEM, XP_POR_MINUTO_CALL } from '../lib/xp'

const router = Router()

// O anel do rodape le isto uma vez ao abrir o app. Depois disso quem manda e o
// evento `xp_gain` do socket — sem poll: ganho de XP e raro (1x por minuto no
// maximo) e perguntar de tempos em tempos custaria mais do que o proprio ganho.
router.get('/me', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  res.json({ data: await progressoDe(req.userId!) })
}))

// As REGRAS, servidas pelo servidor.
//
// A tela que explica "como ganhar XP" tem que dizer os numeros de verdade. Cravar
// 12 e 8 no cliente daria uma tela que mente no dia em que eu ajustar a taxa — e
// mentira sobre progressao e a que mais irrita.
const NIVEIS_MOSTRADOS = 30
router.get('/regras', requireAuth, asyncHandler(async (_req: Request, res: Response) => {
  const trilha = Array.from({ length: NIVEIS_MOSTRADOS }, (_, i) => ({
    nivel:  i + 1,
    custo:  custoDoNivel(i),
    brilho: brilhoDaTrilha(i + 1),
  }))
  res.json({
    data: {
      porMensagem:   XP_POR_MENSAGEM,
      porMinutoCall: XP_POR_MINUTO_CALL,
      trilha,
    },
  })
}))

// O progresso de OUTRA pessoa, pro cartão de perfil completo.
//
// Mesma função do /me — não há cálculo paralelo pra divergir. Nada aqui é privado:
// nível e XP são vaidade pública, do mesmo naipe de "membro desde". O que NÃO sai
// (e por isso a rota devolve o progresso e nada mais) é de ONDE o XP veio: quantas
// mensagens, em quais órbitas, quanto tempo em call. Isso desenharia a rotina da
// pessoa pra quem abrisse o perfil dela.
//
// POR ÚLTIMO, obrigatoriamente: `/:userId` casa com qualquer coisa, inclusive com
// as palavras "me" e "regras". Declarada antes delas, engoliria as duas.
router.get('/:userId', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  res.json({ data: await progressoDe(req.params.userId) })
}))

export default router
