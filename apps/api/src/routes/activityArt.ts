import { Router, Request, Response } from 'express'
import sharp from 'sharp'
import { asyncHandler } from '../lib/asyncHandler'
import { activityArtLimiter } from '../middleware/rateLimiter'
import { putAttachment } from '../lib/storage'
import { redis } from '../lib/redis'
import { badRequest } from '../lib/errors'
import { logger } from '../lib/logger'
import {
  ArteDeAtividade,
  LADO_DA_ARTE,
  parDaArte,
  enderecoNoCatalogo,
  chaveNoBucket,
  marcaNoRedis,
} from '../lib/arteDeAtividade'

const TETO_BAIXADO = 512 * 1024
const ESPERA_MS = 8_000

const VALIDADE_DA_MARCA = 60 * 60 * 24 * 30
const VALIDADE_DO_FRACASSO = 60 * 60 * 6

const SEM_ARTE = 'nao'

const router = Router()

async function baixarDoCatalogo(par: ArteDeAtividade): Promise<Buffer | null> {
  const corte = AbortSignal.timeout(ESPERA_MS)
  const resposta = await fetch(enderecoNoCatalogo(par), { signal: corte, redirect: 'follow' })
    .catch(() => null)
  if (!resposta?.ok) return null

  const tipo = resposta.headers.get('content-type') ?? ''
  if (!tipo.startsWith('image/')) return null

  const bruto = Buffer.from(await resposta.arrayBuffer())
  return bruto.length > 0 && bruto.length <= TETO_BAIXADO ? bruto : null
}

async function adotarArte(par: ArteDeAtividade): Promise<string | null> {
  const bruto = await baixarDoCatalogo(par)
  if (!bruto) return null

  const leve = await sharp(bruto)
    .resize({ width: LADO_DA_ARTE, height: LADO_DA_ARTE, fit: 'inside', withoutEnlargement: true })
    .webp({ quality: 90, effort: 6, alphaQuality: 100 })
    .toBuffer()

  return putAttachment(chaveNoBucket(par), leve, 'image/webp')
}

router.get(
  '/activity-art/:aplicativo/:impressao',
  activityArtLimiter,
  asyncHandler(async (req: Request, res: Response) => {
    const par = parDaArte(req.params.aplicativo, req.params.impressao)
    if (!par) throw badRequest('identificador de arte inválido')

    const marca = marcaNoRedis(par)
    const conhecida = await redis.get(marca).catch(() => null)
    if (conhecida === SEM_ARTE) return res.status(404).end()
    if (conhecida) return res.redirect(302, conhecida)

    const endereco = await adotarArte(par).catch((e) => {
      logger.warn('ArteDeAtividade', `não consegui adotar ${par.aplicativo}: ${e?.message ?? e}`)
      return null
    })

    await redis
      .set(
        marca,
        endereco ?? SEM_ARTE,
        'EX',
        endereco ? VALIDADE_DA_MARCA : VALIDADE_DO_FRACASSO,
      )
      .catch(() => {})

    if (!endereco) return res.status(404).end()
    res.redirect(302, endereco)
  }),
)

export default router
