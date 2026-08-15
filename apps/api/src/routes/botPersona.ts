import { Router, Request, Response } from 'express'
import { eq } from 'drizzle-orm'
import { z } from 'zod'
import { db } from '../db'
import { botPersonas } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { ehDonoDoAstra } from '../lib/donoDoAstra'
import { personaDoDia, sincronizaPersona, getBotId, type Persona } from '../lib/bot'
import { persistDataUri } from '../lib/storage'

// APARENCIA DAS BOTS — so o dono do Astra.
//
// A Sparkle e a Sparxie sao UMA conta compartilhada por todas as constelacoes: a
// cara delas e a mesma em qualquer lugar. Por isso isto nao e permissao de
// constelacao (quem administra uma estaria mudando a bot pra todo mundo) e sim uma
// lista fora do banco — ver lib/donoDoAstra.ts.
const router = Router()

const CHAVES = ['sparkle', 'sparxie'] as const
type Chave = (typeof CHAVES)[number]

const AjusteSchema = z.object({
  // null EXPLICITO = "volta pro que esta no codigo". E por isso que os campos sao
  // nullable e nao so opcionais: omitir e "nao mexi", mandar null e "desfaz". Sem
  // essa diferenca nao haveria como voltar atras sem saber o valor original.
  displayName:     z.string().trim().min(1).max(32).nullable().optional(),
  avatarUrl:       z.string().max(14_000_000).nullable().optional(),
  bannerUrl:       z.string().max(14_000_000).nullable().optional(),
  bannerColor:     z.string().regex(/^#[0-9a-fA-F]{6}$/).nullable().optional(),
  bannerScale:     z.number().int().min(100).max(300).nullable().optional(),
  bannerPositionY: z.number().int().min(0).max(100).nullable().optional(),
  // VOLTAR AO ORIGINAL, por campo.
  //
  // Existe porque o cliente NAO consegue mandar null explicito: o Json do app usa
  // `encodeDefaults=false`, entao campo nulo nem entra no corpo — "nao mexi" e
  // "desfaz" chegariam iguais. Uma lista de nomes contorna isso sem inventar um
  // formato novo pro resto dos campos.
  limpar: z.array(z.enum([
    'displayName', 'avatarUrl', 'bannerUrl', 'bannerColor', 'bannerScale', 'bannerPositionY',
  ])).max(6).optional(),
})

async function exigeDono(req: Request, res: Response): Promise<boolean> {
  if (await ehDonoDoAstra(req.userId)) return true
  // 404 e nao 403: pra quem nao e dono, esta rota nao existe. Um 403 confirmaria
  // que ha um painel de bots pra ser encontrado.
  res.status(404).json({ error: 'Não encontrado' })
  return false
}

function paraResposta(p: Persona, ajuste: typeof botPersonas.$inferSelect | undefined) {
  return {
    chave: p.chave,
    // O EFETIVO (codigo + ajuste) e o que a tela desenha…
    displayName: p.nome,
    avatarUrl: p.avatar,
    bannerUrl: p.banner,
    bannerColor: p.bannerCor,
    bannerScale: p.bannerZoom,
    bannerPositionY: p.bannerY,
    // …e este diz o que foi MEXIDO, pra a tela poder mostrar "voltar ao original"
    // so nos campos que de fato tem para onde voltar.
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

// Monta a persona efetiva de uma chave ESPECIFICA (e nao a do dia): o painel edita
// as duas irmas, inclusive a que esta fora de turno.
async function efetiva(chave: Chave) {
  const base = chave === personaDoDia().chave
    ? personaDoDia()
    // A outra irma. Um Date de sexta ou de segunda escolhe a que falta sem
    // precisar exportar as constantes do bot.ts.
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

    // Imagem chega como data-uri e vai pro bucket ANTES de tocar no banco: guardar
    // megabytes de base64 numa coluna faria toda leitura da persona arrastar o
    // arquivo inteiro junto — e a persona e lida a cada mensagem da bot.
    const patch: Record<string, unknown> = {}
    for (const campo of ['displayName', 'bannerColor', 'bannerScale', 'bannerPositionY'] as const) {
      if (campo in corpo) patch[campo] = corpo[campo]
    }
    if ('avatarUrl' in corpo) patch.avatarUrl = corpo.avatarUrl ? await persistDataUri(corpo.avatarUrl) : null
    if ('bannerUrl' in corpo) patch.bannerUrl = corpo.bannerUrl ? await persistDataUri(corpo.bannerUrl) : null
    // Depois dos campos normais de proposito: se os dois vierem no mesmo pedido,
    // "voltar ao original" vence. E o unico que a pessoa pediu explicitamente.
    for (const campo of corpo.limpar ?? []) patch[campo] = null
    if (Object.keys(patch).length === 0) return res.json({ data: await efetiva(chave) })

    patch.updatedAt = new Date()
    await db.insert(botPersonas)
      .values({ chave, ...patch })
      .onConflictDoUpdate({ target: botPersonas.chave, set: patch })

    // Se a irma editada e a de PLANTAO, a conta da bot muda agora: sem isto o
    // ajuste so apareceria na proxima vez que ela falasse, e a pessoa que acabou
    // de salvar veria o rosto antigo achando que nao pegou.
    if (chave === personaDoDia().chave) {
      const botId = await getBotId()
      if (botId) await sincronizaPersona(botId)
    }
    res.json({ data: await efetiva(chave) })
  }),
)

export default router
