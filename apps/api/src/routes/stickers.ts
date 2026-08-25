import { Router, Request, Response } from 'express'
import { and, asc, eq } from 'drizzle-orm'
import { db } from '../db'
import { serverStickers } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { PERMS, getMemberPerms } from '../lib/permissions'
import { removeAttachment } from '../lib/storage'

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

  await removeAttachment(removida.url)
  res.json({ ok: true })
}))
