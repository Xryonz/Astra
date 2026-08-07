import { and, eq } from 'drizzle-orm'
import { db } from '../db'
import { serverMembers, roles, memberRoles, servers } from '../db/schema'
import { getBotId } from './bot'
import { invalidateMembersCache } from './membersCache'
import { logger } from './logger'

// A BOT COMO MEMBRO DE VERDADE.
//
// Ate agora ela so existia como AUTOR de mensagem: postava, respondia comando, e
// nunca aparecia no painel de membros — porque nunca esteve na tabela. Quem olhava
// a lista via uma constelacao onde a Sparkle falava mas nao morava.
//
// Alem do vinculo, um cargo "BOT" com hoist: hoist e o que separa o grupo no painel
// em vez de misturar a bot no meio das pessoas. Sem ele o vinculo existiria e
// continuaria invisivel na pratica.
//
// Idempotente de ponta a ponta: pode rodar na criacao da constelacao E no boot pra
// alcancar as que ja existiam, sem duplicar nada.

const NOME_CARGO = 'BOT'
// Ambar da marca. Nome de bot com a cor do accent le como "isto e do sistema", nao
// como mais um membro que escolheu uma cor bonita.
const COR_CARGO = '#c9a96e'

export async function garantirBotNaConstelacao(serverId: string): Promise<void> {
  const botId = await getBotId()
  if (!botId) return

  // 1. Vinculo. `role` fica MEMBER: quem manda na constelacao e gente, e dar cargo
  // administrativo pra uma conta automatizada seria superficie de ataque de graca.
  let [membro] = await db.select({ id: serverMembers.id })
    .from(serverMembers)
    .where(and(eq(serverMembers.serverId, serverId), eq(serverMembers.userId, botId)))
    .limit(1)

  if (!membro) {
    const [novo] = await db.insert(serverMembers)
      .values({ userId: botId, serverId, role: 'MEMBER' })
      .returning({ id: serverMembers.id })
    membro = novo
  }
  if (!membro) return

  // 2. Cargo. Procurado pelo NOME porque e o unico identificador estavel entre
  // constelacoes — cada uma tem o seu, com id proprio.
  let [cargo] = await db.select({ id: roles.id })
    .from(roles)
    .where(and(eq(roles.serverId, serverId), eq(roles.name, NOME_CARGO)))
    .limit(1)

  if (!cargo) {
    const [novo] = await db.insert(roles).values({
      serverId,
      name: NOME_CARGO,
      color: COR_CARGO,
      hoist: true,
      position: 0,
      // Sem permissao nenhuma: o cargo existe pra AGRUPAR e pintar, nao pra dar
      // poder. A bot ja faz o que precisa pelas rotas dela.
      permissions: '[]',
    }).returning({ id: roles.id })
    cargo = novo
  }
  if (!cargo) return

  // 3. Vinculo membro <-> cargo.
  const [temCargo] = await db.select({ id: memberRoles.id })
    .from(memberRoles)
    .where(and(eq(memberRoles.memberId, membro.id), eq(memberRoles.roleId, cargo.id)))
    .limit(1)

  if (!temCargo) {
    await db.insert(memberRoles).values({ memberId: membro.id, roleId: cargo.id })
  }

  void invalidateMembersCache(serverId)
}

// Alcanca as constelacoes que ja existiam antes desta feature. Roda uma vez no
// boot; com o numero de constelacoes deste app, e uma passada barata. Falha de uma
// nao derruba as outras — pior cenario e uma constelacao sem a bot no painel, que
// e exatamente onde ja estavamos.
export async function garantirBotEmTodas(): Promise<void> {
  try {
    const todas = await db.select({ id: servers.id }).from(servers)
    let feitas = 0
    for (const s of todas) {
      try {
        await garantirBotNaConstelacao(s.id)
        feitas++
      } catch { /* segue pras outras */ }
    }
    logger.info('Bot', `Bot garantida em ${feitas}/${todas.length} constelações.`)
  } catch (e) {
    logger.error('Bot', 'Falha ao garantir a bot nas constelações', e as Error)
  }
}
