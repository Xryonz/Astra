import { Router, Response } from 'express'
import type { Request } from '../lib/requisicao'
import { desc, eq, sql } from 'drizzle-orm'
import { z } from 'zod'
import { db } from '../db'
import { crashReports } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { ehDonoDoAstra } from '../lib/donoDoAstra'
import { crashLimiter } from '../middleware/rateLimiter'

const router = Router()

const TETO_CURTO = 200
const TETO_DA_MENSAGEM = 2_000
const TETO_DO_TEXTO_LONGO = 12_000
const PAGINA_PADRAO = 50
const PAGINA_MAXIMA = 200
const RETENCAO_DIAS = 60

const RelatoSchema = z.object({
  instalacao: z.string().trim().min(8).max(64),
  versao:     z.string().trim().min(1).max(TETO_CURTO),
  so:         z.string().trim().max(TETO_CURTO).optional(),
  placa:      z.string().trim().max(TETO_CURTO).optional(),
  tipo:       z.string().trim().min(1).max(TETO_CURTO),
  mensagem:   z.string().max(TETO_DA_MENSAGEM).optional(),
  rastro:     z.string().min(1).max(TETO_DO_TEXTO_LONGO),
  arranque:   z.string().max(TETO_DO_TEXTO_LONGO).optional(),
})

async function exigeDono(req: Request, res: Response): Promise<boolean> {
  if (await ehDonoDoAstra(req.userId)) return true
  res.status(404).json({ error: 'Não encontrado' })
  return false
}

router.post('/', crashLimiter, asyncHandler(async (req: Request, res: Response) => {
  const dados = RelatoSchema.safeParse(req.body)
  if (!dados.success) return res.status(400).json({ error: 'Relato inválido' })

  await db.insert(crashReports).values(dados.data)
  await db.delete(crashReports).where(
    sql`"createdAt" < now() - interval '${sql.raw(String(RETENCAO_DIAS))} days'`,
  )
  res.status(204).end()
}))

router.get('/', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  if (!(await exigeDono(req, res))) return

  const limite = Math.min(Number(req.query.limit) || PAGINA_PADRAO, PAGINA_MAXIMA)
  const versao = typeof req.query.versao === 'string' ? req.query.versao : null

  const base = db.select().from(crashReports)
  const linhas = await (versao ? base.where(eq(crashReports.versao, versao)) : base)
    .orderBy(desc(crashReports.createdAt))
    .limit(limite)

  res.json({ data: linhas })
}))

router.get('/resumo', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  if (!(await exigeDono(req, res))) return

  const linhas = await db.select({
    versao:    crashReports.versao,
    tipo:      crashReports.tipo,
    quantos:   sql<number>`count(*)::int`,
    maquinas:  sql<number>`count(distinct "instalacao")::int`,
    ultimo:    sql<string>`max("createdAt")`,
  })
    .from(crashReports)
    .groupBy(crashReports.versao, crashReports.tipo)
    .orderBy(sql`max("createdAt") desc`)
    .limit(PAGINA_MAXIMA)

  res.json({ data: linhas })
}))

export default router
