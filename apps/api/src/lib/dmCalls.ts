import { Server as SocketServer } from 'socket.io'
import { and, eq, or } from 'drizzle-orm'
import { db } from '../db'
import { dmConversations, directMessages, users } from '../db/schema'
import { haBloqueio } from './blocks'
import { getBotId } from './bot'
import { entregarSussurro } from './realtime'

const TOQUE_MS = 45_000

type Chamada = {
  conversationId: string
  quemLigou: string
  quemRecebe: string
  video: boolean
  atendidaEm: number | null
  relogio: NodeJS.Timeout
}

const emCurso = new Map<string, Chamada>()

type FimDaChamada = 'missed' | 'ended'

async function ladosDaConversa(conversationId: string, userId: string) {
  const [conv] = await db.select({
    a: dmConversations.userAId, b: dmConversations.userBId,
  }).from(dmConversations)
    .where(and(
      eq(dmConversations.id, conversationId),
      or(eq(dmConversations.userAId, userId), eq(dmConversations.userBId, userId)),
    ))
    .limit(1)
  if (!conv) return null
  return { eu: userId, outro: conv.a === userId ? conv.b : conv.a }
}

function rotuloDeDuracao(seg: number): string {
  if (seg < 60) return `${seg}s`
  const min = Math.floor(seg / 60)
  if (min < 60) return `${min} min`
  return `${Math.floor(min / 60)}h ${min % 60}min`
}

async function gravarRegistro(io: SocketServer, c: Chamada, fim: FimDaChamada) {
  const duracaoSeg = c.atendidaEm ? Math.max(1, Math.round((Date.now() - c.atendidaEm) / 1000)) : 0
  const texto = fim === 'ended' ? `Chamada de ${rotuloDeDuracao(duracaoSeg)}` : 'Chamada perdida'

  const registro = { status: fim, video: c.video, durationSec: duracaoSeg }

  const [inserida] = await db.insert(directMessages).values({
    content:        texto,
    senderId:       c.quemLigou,
    receiverId:     c.quemRecebe,
    conversationId: c.conversationId,
    call:           JSON.stringify(registro),
  }).returning()
  if (!inserida) return

  const [autor] = await db.select({
    id: users.id, username: users.username,
    displayName: users.displayName, avatarUrl: users.avatarUrl,
  }).from(users).where(eq(users.id, c.quemLigou)).limit(1)

  entregarSussurro(io, c.conversationId, [c.quemLigou, c.quemRecebe], 'new_dm', {
    ...inserida, call: registro, attachments: [], replyTo: null, author: autor,
  })
  await db.update(dmConversations).set({ updatedAt: new Date() })
    .where(eq(dmConversations.id, c.conversationId))
}

async function encerrar(io: SocketServer, conversationId: string, fim: FimDaChamada) {
  const c = emCurso.get(conversationId)
  if (!c) return
  emCurso.delete(conversationId)
  clearTimeout(c.relogio)

  for (const quem of [c.quemLigou, c.quemRecebe]) {
    io.to(`user:${quem}`).emit('dm_call_reject', { conversationId, byUserId: c.quemRecebe })
    io.to(`user:${quem}`).emit('dm_call_ended', { conversationId, status: fim })
  }

  await gravarRegistro(io, c, fim).catch(() => {})
}

export function registrarChamadasDeSussurro(io: SocketServer, socket: any, userId: string) {
  socket.on('dm_call_invite', async (p: { conversationId?: string; video?: boolean }) => {
    const conversationId = String(p?.conversationId ?? '')
    if (!conversationId) return

    const lados = await ladosDaConversa(conversationId, userId)
    if (!lados) return
    if (await haBloqueio(lados.eu, lados.outro)) return

    const botId = await getBotId()
    if (botId && lados.outro === botId) {
      io.to(`user:${lados.eu}`).emit('dm_call_ended', { conversationId, status: 'missed' })
      return
    }

    if (emCurso.has(conversationId)) return

    const video = !!p?.video
    emCurso.set(conversationId, {
      conversationId,
      quemLigou: lados.eu,
      quemRecebe: lados.outro,
      video,
      atendidaEm: null,
      relogio: setTimeout(() => { void encerrar(io, conversationId, 'missed') }, TOQUE_MS),
    })

    const [quem] = await db.select({
      username: users.username, displayName: users.displayName, avatarUrl: users.avatarUrl,
    }).from(users).where(eq(users.id, lados.eu)).limit(1)

    io.to(`user:${lados.outro}`).emit('dm_call_invite', {
      conversationId,
      fromUserId:      lados.eu,
      fromUsername:    quem?.username ?? socket.data?.username ?? '',
      fromDisplayName: quem?.displayName ?? socket.data?.displayName ?? '',
      fromAvatarUrl:   quem?.avatarUrl ?? null,
      video,
      msRestantes:     TOQUE_MS,
    })
  })

  socket.on('dm_call_accept', async (p: { conversationId?: string }) => {
    const conversationId = String(p?.conversationId ?? '')
    const c = emCurso.get(conversationId)
    if (!c || c.quemRecebe !== userId || c.atendidaEm) return

    c.atendidaEm = Date.now()
    clearTimeout(c.relogio)
    for (const quem of [c.quemLigou, c.quemRecebe]) {
      io.to(`user:${quem}`).emit('dm_call_accept', { conversationId, byUserId: userId })
    }
  })

  const desligar = async (p: { conversationId?: string }) => {
    const conversationId = String(p?.conversationId ?? '')
    const c = emCurso.get(conversationId)
    if (!c || (c.quemRecebe !== userId && c.quemLigou !== userId)) return
    await encerrar(io, conversationId, c.atendidaEm ? 'ended' : 'missed')
  }
  socket.on('dm_call_reject', desligar)
  socket.on('dm_call_end', desligar)
}
