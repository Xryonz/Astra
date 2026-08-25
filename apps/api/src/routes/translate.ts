import { Router, Request, Response } from 'express'
import { z } from 'zod'
import crypto from 'crypto'
import { requireAuth } from '../middleware/auth'
import { validate } from '../middleware/validate'
import { asyncHandler } from '../lib/asyncHandler'
import { redis } from '../lib/redis'
import { logger } from '../lib/logger'
import { gerarTexto, IA_LIGADA, MODELO_RESUMO } from '../lib/ia'

const router = Router()

const SUPPORTED = ['pt', 'en', 'es', 'fr', 'de', 'it', 'ja', 'zh', 'ru', 'ar'] as const
type Lang = typeof SUPPORTED[number]

const LANG_NAMES: Record<Lang, string> = {
  pt: 'Portuguese (Brazilian)', en: 'English',  es: 'Spanish', fr: 'French',
  de: 'German', it: 'Italian', ja: 'Japanese', zh: 'Chinese (Simplified)',
  ru: 'Russian', ar: 'Arabic',
}

const TranslateSchema = z.object({
  text:       z.string().min(1).max(2000),
  targetLang: z.enum(SUPPORTED),
})

const DAILY_LIMIT      = 50
const CACHE_TTL_SECS   = 24 * 60 * 60

function dayKey(userId: string) {
  const day = new Date().toISOString().slice(0, 10)
  return `translate:quota:${userId}:${day}`
}

router.post('/', requireAuth, validate(TranslateSchema), asyncHandler(async (req: Request, res: Response) => {
  const { text, targetLang } = req.body as z.infer<typeof TranslateSchema>

  if (!IA_LIGADA) {
    return res.status(503).json({ error: 'Tradutor offline (sem chave de API)' })
  }

  const qKey = dayKey(req.userId!)
  const count = await redis.incr(qKey)
  if (count === 1) await redis.expire(qKey, 86_400)
  if (count > DAILY_LIMIT) {
    return res.status(429).json({ error: `Limite diário (${DAILY_LIMIT}) atingido. Tente amanhã.` })
  }

  const hash = crypto.createHash('sha256').update(`${targetLang}\n${text}`).digest('hex').slice(0, 32)
  const cacheKey = `translate:result:${hash}`
  const cached = await redis.get(cacheKey)
  if (cached) {
    return res.json({ data: { translation: cached, cached: true } })
  }

  try {
    const out = await gerarTexto(
      MODELO_RESUMO,
      `You are a translation engine. Translate the user's text into ${LANG_NAMES[targetLang]}. Output ONLY the translation — no quotes, no preamble, no explanation. Preserve markdown, emojis, code blocks, mentions (@user) and urls verbatim. If text already is in ${LANG_NAMES[targetLang]}, output it unchanged.`,
      text,
      800,
    )
    if (!out) return res.status(502).json({ error: 'Tradução indisponível' })

    await redis.setex(cacheKey, CACHE_TTL_SECS, out)
    res.json({ data: { translation: out, cached: false } })
  } catch (e: any) {
    logger.error('Translate', 'falhou', e)
    res.status(500).json({ error: 'Erro na tradução' })
  }
}))

export default router
