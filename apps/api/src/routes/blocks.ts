import { Router, Request, Response } from 'express'
import { and, eq, or } from 'drizzle-orm'
import { db } from '../db'
import { users, userBlocks, friendships, dmConversations } from '../db/schema'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { badRequest, notFound } from '../lib/errors'

const router = Router()

router.get('/', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const linhas = await db.select({
    id:          users.id,
    username:    users.username,
    displayName: users.displayName,
    avatarUrl:   users.avatarUrl,
    blockedAt:   userBlocks.createdAt,
  })
    .from(userBlocks)
    .innerJoin(users, eq(users.id, userBlocks.blockedId))
    .where(eq(userBlocks.blockerId, req.userId!))

  res.json({ data: linhas.map((l) => ({ ...l, blockedAt: l.blockedAt.toISOString() })) })
}))

router.post('/:userId', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const eu = req.userId!
  const alvo = req.params.userId
  if (alvo === eu) throw badRequest('Você não pode se bloquear')

  const [existe] = await db.select({ id: users.id }).from(users).where(eq(users.id, alvo)).limit(1)
  if (!existe) throw notFound('Pessoa não encontrada')

  const [jaBloqueado] = await db.select({ id: userBlocks.id }).from(userBlocks)
    .where(and(eq(userBlocks.blockerId, eu), eq(userBlocks.blockedId, alvo)))
    .limit(1)
  if (!jaBloqueado) {
    await db.insert(userBlocks).values({ blockerId: eu, blockedId: alvo })
  }

  await db.delete(friendships).where(or(
    and(eq(friendships.userAId, eu), eq(friendships.userBId, alvo)),
    and(eq(friendships.userAId, alvo), eq(friendships.userBId, eu)),
  ))

  const [conv] = await db.select({ id: dmConversations.id, userAId: dmConversations.userAId })
    .from(dmConversations)
    .where(or(
      and(eq(dmConversations.userAId, eu), eq(dmConversations.userBId, alvo)),
      and(eq(dmConversations.userAId, alvo), eq(dmConversations.userBId, eu)),
    ))
    .limit(1)
  if (conv) {
    const agora = new Date()
    await db.update(dmConversations)
      .set(conv.userAId === eu ? { hiddenByA: agora } : { hiddenByB: agora })
      .where(eq(dmConversations.id, conv.id))
  }

  res.json({ data: { ok: true, blockedId: alvo } })
}))

router.delete('/:userId', requireAuth, asyncHandler(async (req: Request, res: Response) => {
  const r = await db.delete(userBlocks)
    .where(and(eq(userBlocks.blockerId, req.userId!), eq(userBlocks.blockedId, req.params.userId)))
    .returning({ id: userBlocks.id })
  if (r.length === 0) throw notFound('Essa pessoa não está bloqueada')
  res.json({ data: { ok: true, unblockedId: req.params.userId } })
}))

export default router
