
import { eq } from 'drizzle-orm'
import { db } from '../db'
import { users, notifications } from '../db/schema'
import { sendPush, type PushPayload } from './push'
import { logger } from './logger'
import { avisoPassa, modoDoCanal } from './silencioDeCanal'
import type { Server as SocketServer } from 'socket.io'

export type NotificationType = 'mention' | 'dm' | 'reaction' | 'reply' | 'friend_request'

export interface NotificationPrefs {

  mentions: boolean

  dms: boolean

  reactions: boolean

  replies: boolean

  sounds: boolean

  desktop: boolean

  quietStart: number | null

  quietEnd: number | null
}

export const DEFAULT_PREFS: NotificationPrefs = {
  mentions:   true,
  dms:        true,
  reactions:  true,
  replies:    true,
  sounds:     true,
  desktop:    true,
  quietStart: null,
  quietEnd:   null,
}

export function parsePrefs(raw: string | null | undefined): NotificationPrefs {
  if (!raw) return DEFAULT_PREFS
  try {
    const parsed = JSON.parse(raw)
    return { ...DEFAULT_PREFS, ...parsed }
  } catch { return DEFAULT_PREFS }
}

function isInQuietHours(prefs: NotificationPrefs, now = new Date()): boolean {
  if (prefs.quietStart == null || prefs.quietEnd == null) return false
  const h = now.getHours()
  const s = prefs.quietStart
  const e = prefs.quietEnd

  if (s < e) return h >= s && h < e

  return h >= s || h < e
}

function typeEnabled(type: NotificationType, prefs: NotificationPrefs): boolean {
  switch (type) {
    case 'mention':  return prefs.mentions
    case 'dm':       return prefs.dms
    case 'reaction': return prefs.reactions
    case 'reply':    return prefs.replies
    // SEM INTERRUPTOR PRÓPRIO, e de propósito. Pedido de amizade é raro, é
    // dirigido a você pessoalmente e é acionável — não tem como virar ruído do
    // jeito que menção e reação têm. Um toggle aqui seria uma linha a mais na
    // tela de configurações protegendo contra um incômodo que não existe.
    // Silêncio e horário de descanso continuam valendo (são checados fora daqui).
    case 'friend_request': return true
  }
}

// O TRECHO que vai no aviso. Num lugar só porque o desktop, o push do celular e o
// sino leem o mesmo campo, e três truncagens diferentes dariam três avisos diferentes
// para a mesma mensagem.
//
// Mensagem só de anexo vira "enviou uma imagem" e não fica vazia: aspas em volta de
// nada lê como falha do app, e o aviso perderia justamente a única coisa que tinha a
// dizer. O limite de 140 é o que cabe num balão do Windows sem ser cortado pelo SO no
// meio de uma palavra.
export function resumoDaMensagem(content: string, quantosAnexos: number): string {
  const limpo = content.trim()
  if (limpo) return limpo.length > 140 ? limpo.slice(0, 139) + '…' : limpo
  if (quantosAnexos > 1) return `enviou ${quantosAnexos} anexos`
  if (quantosAnexos === 1) return 'enviou um anexo'
  return ''
}

export interface NotifyOpts {
  io:         SocketServer
  userId:     string
  actorId?:   string
  type:       NotificationType
  payload:    Record<string, unknown>
  push?:      PushPayload
}

export interface NotifyResult {
  notificationId: string | null
  pushed:         boolean
  skipped:        'self' | 'pref' | 'dnd' | null
}

export async function notify(opts: NotifyOpts): Promise<NotifyResult> {
  const { io, userId, actorId, type, payload, push } = opts

  if (actorId && actorId === userId) {
    return { notificationId: null, pushed: false, skipped: 'self' }
  }

  try {
    const [user] = await db.select({
      notificationPrefs: users.notificationPrefs,
      status:            users.status,
    }).from(users).where(eq(users.id, userId)).limit(1)

    if (!user) return { notificationId: null, pushed: false, skipped: null }

    const prefs = parsePrefs(user.notificationPrefs)

    if (!typeEnabled(type, prefs)) {
      return { notificationId: null, pushed: false, skipped: 'pref' }
    }

    // Órbita silenciada. AQUI e não em cada chamador: todo aviso de canal passa
    // por esta função, então o silêncio vale pro sino, pro contador e pro push de
    // uma vez só — e o próximo tipo de aviso que alguém criar já nasce
    // respeitando, sem precisar lembrar.
    const canalDoAviso = typeof payload.channelId === 'string' ? payload.channelId : null
    if (canalDoAviso && !avisoPassa(await modoDoCanal(userId, canalDoAviso), type)) {
      return { notificationId: null, pushed: false, skipped: 'pref' }
    }

    const isDnd  = user.status === 'DND'
    const quiet  = isInQuietHours(prefs)
    const muted  = isDnd || quiet

    // QUEM mandou entra AQUI, num lugar so.
    //
    // Antes cada chamador montava o proprio authorName e caia num literal
    // 'Alguém' quando a busca do autor não vinha — e o app mostrava "alguém" na
    // lista de notificações, sem jeito de saber de quem era. Preencher no centro
    // torna impossivel esquecer no proximo tipo de notificação que alguem criar.
    // Tambem guarda o @usuario: displayName pode mudar, e o @ e o que identifica
    // a pessoa sem sombra de duvida.
    const enriched = { ...payload }
    if (actorId && !enriched.authorName) {
      const [actor] = await db.select({
        username:    users.username,
        displayName: users.displayName,
        avatarUrl:   users.avatarUrl,
      }).from(users).where(eq(users.id, actorId)).limit(1)
      if (actor) {
        enriched.authorId     = actorId
        enriched.authorName   = actor.displayName || actor.username
        enriched.authorUsername = actor.username
        enriched.authorAvatar = enriched.authorAvatar ?? actor.avatarUrl ?? null
      }
    }

    const [created] = await db.insert(notifications).values({
      userId, type, payload: JSON.stringify(enriched),
    }).returning({ id: notifications.id, createdAt: notifications.createdAt })

    // `enriched`, e não `payload`. Era `payload` — o cru, sem o nome e sem a foto de
    // quem mandou. O banco guardava a versão enriquecida e o sino, que lê do banco,
    // mostrava tudo certo; só o aviso AO VIVO saía anônimo. Isso deixava o defeito
    // praticamente invisível em teste: bastava abrir o sino pra ver o nome lá,
    // parecendo que o problema estava na tela e não no evento.
    io.to(`user:${userId}`).emit('notification', {
      id:        created.id,
      type,
      payload:   enriched,
      createdAt: created.createdAt.toISOString(),
      silent:    muted,
    })

    let pushed = false
    if (push && prefs.desktop && !muted) {
      sendPush(userId, push).catch(() => {})
      pushed = true
    }

    return { notificationId: created.id, pushed, skipped: muted ? 'dnd' : null }
  } catch (e) {
    logger.error('Notify', 'falhou', e)
    return { notificationId: null, pushed: false, skipped: null }
  }
}
