import { Router, Request, Response } from 'express'
import sharp from 'sharp'
import { asyncHandler } from '../lib/asyncHandler'
import { activityArtLimiter } from '../middleware/rateLimiter'
import { putAttachment } from '../lib/storage'
import { redis } from '../lib/redis'
import { badRequest } from '../lib/errors'
import { logger } from '../lib/logger'

const APLICATIVO = /^\d{5,25}$/
const IMPRESSAO = /^[0-9a-f]{32}$/

const LADO = 128
const TETO_BAIXADO = 512 * 1024
const ESPERA_MS = 8_000

const VALIDADE_DA_MARCA = 60 * 60 * 24 * 30
const VALIDADE_DO_FRACASSO = 60 * 60 * 6

const SEM_ARTE = 'nao'

const router = Router()

function chaveNoBucket(aplicativo: string, impressao: string): string {
  return `atividade/${aplicativo}-${impressao}.webp`
}

async function baixarDoCatalogo(aplicativo: string, impressao: string): Promise<Buffer | null> {
  const alvo = `https://cdn.discordapp.com/app-icons/${aplicativo}/${impressao}.png?size=${LADO}`
  const corte = AbortSignal.timeout(ESPERA_MS)
  const resposta = await fetch(alvo, { signal: corte, redirect: 'follow' }).catch(() => null)
  if (!resposta?.ok) return null

  const tipo = resposta.headers.get('content-type') ?? ''
  if (!tipo.startsWith('image/')) return null

  const bruto = Buffer.from(await resposta.arrayBuffer())
  return bruto.length > 0 && bruto.length <= TETO_BAIXADO ? bruto : null
}

async function adotarArte(aplicativo: string, impressao: string): Promise<string | null> {
  const bruto = await baixarDoCatalogo(aplicativo, impressao)
  if (!bruto) return null

  const leve = await sharp(bruto)
    .resize({ width: LADO, height: LADO, fit: 'inside', withoutEnlargement: true })
    .webp({ quality: 90, effort: 6, alphaQuality: 100 })
    .toBuffer()

  return putAttachment(chaveNoBucket(aplicativo, impressao), leve, 'image/webp')
}

router.get(
  '/activity-art/:aplicativo/:impressao',
  activityArtLimiter,
  asyncHandler(async (req: Request, res: Response) => {
    const aplicativo = String(req.params.aplicativo)
    const impressao = String(req.params.impressao).replace(/\.webp$/i, '')
    if (!APLICATIVO.test(aplicativo) || !IMPRESSAO.test(impressao)) {
      throw badRequest('identificador de arte inválido')
    }

    const marca = `arte-atividade:${aplicativo}:${impressao}`
    const conhecida = await redis.get(marca).catch(() => null)
    if (conhecida === SEM_ARTE) return res.status(404).end()
    if (conhecida) return res.redirect(302, conhecida)

    const endereco = await adotarArte(aplicativo, impressao).catch((e) => {
      logger.warn('ArteDeAtividade', `não consegui adotar ${aplicativo}: ${e?.message ?? e}`)
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
