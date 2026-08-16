import { eq, or } from 'drizzle-orm'
import { db } from './../db'
import {
  bookmarks, channelNotifPrefs, channelReads, fcmTokens, friendships,
  mutedMembers, notifications, profileNotes, pushSubscriptions, refreshTokens, reminders,
  serverMembers, serverNotifPrefs, servers, userBlocks, users,
} from '../db/schema'

// APAGAR CONTA — por LÁPIDE, e não por exclusão.
//
// A linha do usuário sobrevive vazia. Some tudo que é dele (e-mail, foto, nome,
// bio, senha, vínculo com o Google) e o que fica é uma casca chamada "conta
// apagada", à qual as mensagens antigas continuam presas.
//
// POR QUE NÃO EXCLUIR DE VERDADE: `Message.authorId` e `DirectMessage.senderId`
// têm `onDelete: 'cascade'`. Apagar a linha levaria junto TODA mensagem que a
// pessoa escreveu — inclusive dentro das conversas de outras pessoas. A conversa
// é de dois, e um dos dois indo embora não deveria abrir buracos no que o outro
// leu e respondeu: sobraria a resposta sem a pergunta.
//
// O que se apaga de verdade é o que só diz respeito a ela: sessões, presença nas
// constelações, amizades, bloqueios, marcadores, lembretes, notificações e
// aparelhos de push. Disso nada é conteúdo de conversa alheia.
// Constelações de que a pessoa é DONA. Enquanto houver uma, apagar é recusado.
//
// Não é só zelo: `Server.ownerId` referencia o usuário SEM cascade, então o banco
// recusaria de qualquer jeito — mas recusaria com um erro de chave estrangeira,
// que não diz nada a ninguém. Aqui a recusa vira uma lista do que resolver.
//
// E o zelo também vale: constelação com gente dentro não pode evaporar porque uma
// pessoa clicou em apagar conta às 3 da manhã.
export async function constelacoesQueImpedem(userId: string) {
  return db.select({ id: servers.id, name: servers.name }).from(servers)
    .where(eq(servers.ownerId, userId))
}

export const NOME_DA_LAPIDE = 'conta apagada'

export async function virarLapide(userId: string): Promise<void> {
  // Sufixo do próprio id: e-mail e nome de usuário são ÚNICOS no banco, então
  // "apagada@..." fixo quebraria na segunda conta apagada. O domínio .invalid é
  // reservado por norma (RFC 2606) justamente pra não existir de verdade — nada
  // de e-mail sai desta caixa por acidente.
  const marca = userId.slice(-12).toLowerCase()

  await db.update(users).set({
    email:        `apagada+${marca}@astra.invalid`,
    username:     `apagada_${marca}`,
    displayName:  NOME_DA_LAPIDE,
    // Sem senha e sem Google = as DUAS portas de entrada fecham. Não há um
    // "bloqueado: sim" pra alguém esquecer de checar num login futuro: o login
    // simplesmente não tem com o que casar.
    passwordHash: null,
    googleId:     null,
    avatarUrl:    null,
    bannerUrl:    null,
    bannerColor:  null,
    profileTheme: null,
    bannerTextColor: null,
    bio:          null,
    pronouns:     null,
    statusEmoji:  null,
    customStatus: null,
    // INVISIVEL e nao OFFLINE: o enum do banco nao tem OFFLINE (ausencia de
    // presenca NAO e um status escolhivel — ver o seletor do rodape).
    status:       'INVISIBLE',
    deletedAt:    new Date(),
  }).where(eq(users.id, userId))

  // Daqui pra baixo é o que só diz respeito a ela. Em série e não em paralelo: é
  // uma vez na vida de uma conta, e a ordem legível vale mais que os milissegundos.
  await db.delete(refreshTokens).where(eq(refreshTokens.userId, userId))
  await db.delete(pushSubscriptions).where(eq(pushSubscriptions.userId, userId))
  await db.delete(fcmTokens).where(eq(fcmTokens.userId, userId))
  // Sair das constelacoes leva os cargos junto: ServerMemberRole aponta pro
  // ServerMember COM cascade, entao apagar a participacao apaga os papeis dela.
  await db.delete(serverMembers).where(eq(serverMembers.userId, userId))
  await db.delete(channelNotifPrefs).where(eq(channelNotifPrefs.userId, userId))
  await db.delete(serverNotifPrefs).where(eq(serverNotifPrefs.userId, userId))
  await db.delete(channelReads).where(eq(channelReads.userId, userId))
  await db.delete(bookmarks).where(eq(bookmarks.userId, userId))
  // Lembretes dos DOIS lados: os que ela pediu e os que pediram pra ela.
  await db.delete(reminders).where(
    or(eq(reminders.creatorId, userId), eq(reminders.targetUserId, userId)),
  )
  await db.delete(notifications).where(eq(notifications.userId, userId))
  await db.delete(profileNotes).where(eq(profileNotes.authorId, userId))
  await db.delete(mutedMembers).where(eq(mutedMembers.userId, userId))
  await db.delete(friendships).where(
    or(eq(friendships.userAId, userId), eq(friendships.userBId, userId)),
  )
  await db.delete(userBlocks).where(
    or(eq(userBlocks.blockerId, userId), eq(userBlocks.blockedId, userId)),
  )
}
