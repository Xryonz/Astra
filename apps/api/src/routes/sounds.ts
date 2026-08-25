import { Router, Request, Response } from 'express'
import { Server as SocketServer } from 'socket.io'
import { and, asc, eq } from 'drizzle-orm'
import { db } from '../db'
import { serverSounds } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { PERMS, getMemberPerms } from '../lib/permissions'
import { removeAttachment } from '../lib/storage'

export function createSoundsRouter(io: SocketServer) {
  const router = Router()
  router.use(requireAuth)

  const NOME_MAX = 40
  const TETO_POR_CONSTELACAO = 40

  async function podeGerenciar(userId: string, serverId: string) {
    const m = await getMemberPerms(userId, serverId)
    if (!m.memberId) return { ok: false as const, erro: 'Você não é membro' }
    if (!m.isOwner && !m.permissions.has(PERMS.MANAGE_SERVER)) {
      return { ok: false as const, erro: 'Sem permissão pra mexer nos sons' }
    }
    return { ok: true as const }
  }

  router.get('/:serverId', asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId) return res.status(403).json({ error: 'Você não é membro' })

    const itens = await db.select()
      .from(serverSounds)
      .where(eq(serverSounds.serverId, serverId))
      .orderBy(asc(serverSounds.name))
    res.json({ sounds: itens })
  }))

  router.post('/:serverId', asyncHandler(async (req: Request, res: Response) => {
    const { serverId } = req.params
    const perm = await podeGerenciar(req.userId!, serverId)
    if (!perm.ok) return res.status(403).json({ error: perm.erro })

    const nome = String(req.body?.name ?? '').trim()
    const url = String(req.body?.url ?? '').trim()
    const durationMs = Number(req.body?.durationMs ?? 0)

    if (nome.length < 1 || nome.length > NOME_MAX) {
      return res.status(422).json({ error: `Nome precisa ter de 1 a ${NOME_MAX} caracteres` })
    }
    if (!url) return res.status(422).json({ error: 'Faltou o arquivo' })

    const jaTem = await db.select({ id: serverSounds.id })
      .from(serverSounds).where(eq(serverSounds.serverId, serverId))
    if (jaTem.length >= TETO_POR_CONSTELACAO) {
      return res.status(409).json({ error: `Limite de ${TETO_POR_CONSTELACAO} sons por constelação` })
    }

    try {
      const [criado] = await db.insert(serverSounds).values({
        serverId,
        name: nome,
        url,
        durationMs: Number.isFinite(durationMs) ? Math.max(0, Math.trunc(durationMs)) : 0,
        createdBy: req.userId!,
      }).returning()
      res.status(201).json(criado)
    } catch {
      res.status(409).json({ error: 'Já existe um som com esse nome' })
    }
  }))

  router.delete('/:serverId/:soundId', asyncHandler(async (req: Request, res: Response) => {
    const { serverId, soundId } = req.params
    const perm = await podeGerenciar(req.userId!, serverId)
    if (!perm.ok) return res.status(403).json({ error: perm.erro })

    const [removido] = await db.delete(serverSounds)
      .where(and(eq(serverSounds.id, soundId), eq(serverSounds.serverId, serverId)))
      .returning({ url: serverSounds.url })
    if (!removido) return res.status(404).json({ error: 'Som não encontrado' })

    await removeAttachment(removido.url)
    res.json({ ok: true })
  }))

  router.post('/:serverId/:soundId/play', asyncHandler(async (req: Request, res: Response) => {
    const { serverId, soundId } = req.params
    const channelId = String(req.body?.channelId ?? '').trim()
    if (!channelId) return res.status(422).json({ error: 'Faltou a órbita' })

    const m = await getMemberPerms(req.userId!, serverId)
    if (!m.memberId) return res.status(403).json({ error: 'Você não é membro' })

    const [som] = await db.select()
      .from(serverSounds)
      .where(and(eq(serverSounds.id, soundId), eq(serverSounds.serverId, serverId)))
      .limit(1)
    if (!som) return res.status(404).json({ error: 'Som não encontrado' })

    io.to(`channel:${channelId}`).emit('soundboard_play', {
      channelId,
      soundId: som.id,
      name: som.name,
      url: som.url,
      byUserId: req.userId!,
    })
    res.json({ ok: true })
  }))

  return router
}
