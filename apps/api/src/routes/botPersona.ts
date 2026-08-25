import { Router, Request, Response } from 'express'
import { eq } from 'drizzle-orm'
import { z } from 'zod'
import { db } from '../db'
import { botPersonas } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { ehDonoDoAstra } from '../lib/donoDoAstra'
import { personaDoDia, sincronizaPersona, getBotId, type Persona } from '../lib/bot'
import { persistDataUri, persistImagemDeExibicao } from '../lib/storage'

const router = Router()

const CHAVES = ['sparkle', 'sparxie'] as const
type Chave = (typeof CHAVES)[number]

const AjusteSchema = z.object({
  displayName:     z.string().trim().min(1).max(32).nullable().optional(),
  avatarUrl:       z.string().max(14_000_000).nullable().optional(),
  bannerUrl:       z.string().max(14_000_000).nullable().optional(),
  bannerColor:     z.string().regex(/^#[0-9a-fA-F]{6}$/).nullable().optional(),
  bannerScale:     z.number().int().min(100).max(300).nullable().optional(),
  bannerPositionY: z.number().int().min(0).max(100).nullable().optional(),
  limpar: z.array(z.enum([
    'displayName', 'avatarUrl', 'bannerUrl', 'bannerColor', 'bannerScale', 'bannerPositionY',
  ])).max(6).optional(),
})

async function exigeDono(req: Request, res: Response): Promise<boolean> {
  if (await ehDonoDoAstra(req.userId)) return true
  res.status(404).json({ error: 'Não encontrado' })
  return false
}

function paraResposta(p: Persona, ajuste: typeof botPersonas.$inferSelect | undefined) {
  return {
    chave: p.chave,
    displayName: p.nome,
    avatarUrl: p.avatar,
    bannerUrl: p.banner,
    bannerColor: p.bannerCor,
    bannerScale: p.bannerZoom,
    bannerPositionY: p.bannerY,
    personalizado: {
      displayName: ajuste?.displayName != null,
      avatarUrl: ajuste?.avatarUrl != null,
      bannerUrl: ajuste?.bannerUrl != null,
      bannerColor: ajuste?.bannerColor != null,
      bannerScale: ajuste?.bannerScale != null,
      bannerPositionY: ajuste?.bannerPositionY != null,
    },
  }
}

async function efetiva(chave: Chave) {
  const base = chave === personaDoDia().chave
    ? personaDoDia()
    : personaDoDia(new Date(chave === 'sparxie' ? '2026-08-14T12:00:00Z' : '2026-08-10T12:00:00Z'))
  const [ajuste] = await db.select().from(botPersonas).where(eq(botPersonas.chave, chave)).limit(1)
  const comAjuste: Persona = ajuste
    ? {
      ...base,
      nome: ajuste.displayName ?? base.nome,
      avatar: ajuste.avatarUrl ?? base.avatar,
      banner: ajuste.bannerUrl ?? base.banner,
      bannerCor: ajuste.bannerColor ?? base.bannerCor,
      bannerZoom: ajuste.bannerScale ?? base.bannerZoom,
      bannerY: ajuste.bannerPositionY ?? base.bannerY,
    }
    : base
  return paraResposta(comAjuste, ajuste)
}

router.get(
  '/',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    if (!(await exigeDono(req, res))) return
    res.json({ data: { personas: await Promise.all(CHAVES.map(efetiva)) } })
  }),
)

router.patch(
  '/:chave',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    if (!(await exigeDono(req, res))) return
    const chave = req.params.chave as Chave
    if (!CHAVES.includes(chave)) return res.status(404).json({ error: 'Persona desconhecida' })

    const parsed = AjusteSchema.safeParse(req.body)
    if (!parsed.success) return res.status(400).json({ error: 'Dados inválidos' })
    const corpo = parsed.data

    const patch: Record<string, unknown> = {}
    for (const campo of ['displayName', 'bannerColor', 'bannerScale', 'bannerPositionY'] as const) {
      if (campo in corpo) patch[campo] = corpo[campo]
    }
    if ('avatarUrl' in corpo) {
      patch.avatarUrl = corpo.avatarUrl ? (await persistImagemDeExibicao(corpo.avatarUrl)).url : null
    }
    if ('bannerUrl' in corpo) patch.bannerUrl = corpo.bannerUrl ? await persistDataUri(corpo.bannerUrl) : null
    for (const campo of corpo.limpar ?? []) patch[campo] = null
    if (Object.keys(patch).length === 0) return res.json({ data: await efetiva(chave) })

    patch.updatedAt = new Date()
    await db.insert(botPersonas)
      .values({ chave, ...patch })
      .onConflictDoUpdate({ target: botPersonas.chave, set: patch })

    if (chave === personaDoDia().chave) {
      const botId = await getBotId()
      if (botId) await sincronizaPersona(botId)
    }
    res.json({ data: await efetiva(chave) })
  }),
)

export default router
