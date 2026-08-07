import { Router, Request, Response } from 'express'
import { and, asc, eq } from 'drizzle-orm'
import { db } from '../db'
import { serverStickers } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { PERMS, getMemberPerms } from '../lib/permissions'
import { removeAttachment } from '../lib/storage'

// FIGURINHAS DA CONSTELACAO.
//
// Mesmo desenho do soundboard (routes/sounds.ts): esta rota NAO recebe bytes. O
// arquivo sobe pelo /api/upload, que ja sabe guardar no bucket, medir e gerar
// blurhash — aqui so registramos a URL. Um lugar so pra upload continua sendo um
// lugar so.
//
// Nao ha rota de "enviar figurinha": mandar figurinha e mandar MENSAGEM, com um
// anexo marcado `sticker: true`. Criar um caminho proprio duplicaria resposta,
// reacao, exclusao e notificacao — tudo que uma mensagem ja tem.
export const stickersRouter = Router()
stickersRouter.use(requireAuth)

const NOME_MAX = 40
const TETO_POR_CONSTELACAO = 60

async function podeGerenciar(userId: string, serverId: string) {
  const m = await getMemberPerms(userId, serverId)
  if (!m.memberId) return { ok: false as const, erro: 'Você não é membro' }
  if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_SERVER)) {
    return { ok: false as const, erro: 'Sem permissão pra mexer nas figurinhas' }
  }
  return { ok: true as const }
}

// Lista — qualquer MEMBRO ve, porque qualquer membro pode mandar.
stickersRouter.get('/:serverId', asyncHandler(async (req: Request, res: Response) => {
  const { serverId } = req.params
  const m = await getMemberPerms(req.userId!, serverId)
  if (!m.memberId) return res.status(403).json({ error: 'Você não é membro' })

  const itens = await db.select()
    .from(serverStickers)
    .where(eq(serverStickers.serverId, serverId))
    .orderBy(asc(serverStickers.name))
  res.json({ stickers: itens })
}))

stickersRouter.post('/:serverId', asyncHandler(async (req: Request, res: Response) => {
  const { serverId } = req.params
  const perm = await podeGerenciar(req.userId!, serverId)
  if (!perm.ok) return res.status(403).json({ error: perm.erro })

  const nome = String(req.body?.name ?? '').trim()
  const url = String(req.body?.url ?? '').trim()
  const width = Number(req.body?.width ?? 0)
  const height = Number(req.body?.height ?? 0)

  if (nome.length < 1 || nome.length > NOME_MAX) {
    return res.status(422).json({ error: `Nome precisa ter de 1 a ${NOME_MAX} caracteres` })
  }
  if (!url) return res.status(422).json({ error: 'Faltou a imagem' })

  const jaTem = await db.select({ id: serverStickers.id })
    .from(serverStickers).where(eq(serverStickers.serverId, serverId))
  if (jaTem.length >= TETO_POR_CONSTELACAO) {
    return res.status(409).json({ error: `Limite de ${TETO_POR_CONSTELACAO} figurinhas por constelação` })
  }

  const inteiro = (n: number) => (Number.isFinite(n) ? Math.max(0, Math.trunc(n)) : 0)

  try {
    const [criada] = await db.insert(serverStickers).values({
      serverId,
      name: nome,
      url,
      width: inteiro(width),
      height: inteiro(height),
      createdBy: req.userId!,
    }).returning()
    res.status(201).json(criada)
  } catch {
    // Indice unico (serverId, name). Mensagem propria em vez de 500 generico.
    res.status(409).json({ error: 'Já existe uma figurinha com esse nome' })
  }
}))

stickersRouter.delete('/:serverId/:stickerId', asyncHandler(async (req: Request, res: Response) => {
  const { serverId, stickerId } = req.params
  const perm = await podeGerenciar(req.userId!, serverId)
  if (!perm.ok) return res.status(403).json({ error: perm.erro })

  const [removida] = await db.delete(serverStickers)
    .where(and(eq(serverStickers.id, stickerId), eq(serverStickers.serverId, serverId)))
    .returning({ url: serverStickers.url })
  if (!removida) return res.status(404).json({ error: 'Figurinha não encontrada' })

  // Some do bucket junto — senao o arquivo fica ocupando espaco pra sempre,
  // invisivel. Mesmo vazamento que os anexos ja tiveram.
  //
  // As mensagens antigas que usavam esta figurinha ficam com a imagem quebrada, e
  // isso e proposital: a alternativa (guardar pra sempre) faz o dono da
  // constelacao pagar espaco por figurinha que ele mandou apagar.
  await removeAttachment(removida.url)
  res.json({ ok: true })
}))
