import { sql } from 'drizzle-orm'
import { serverMembers } from '../db/schema'
import { BOT_USERNAME } from './bot'

// A BOT NÃO É GENTE, E A CONTAGEM DEVE DIZER ISSO (pedido do dono).
//
// Ela entra em toda constelação com cargo próprio — precisa disso pra falar, pra
// aparecer no painel e pra ter permissão. O efeito colateral era que toda
// constelação nascia dizendo "1 membro" com ninguém dentro, e uma de duas pessoas
// aparecia como três.
//
// FILTRAR POR USERNAME e não por id resolvido em código, e a razão é a quantidade
// de lugares: são seis consultas de contagem espalhadas (descoberta, prévia de
// convite, entrar por convite, lista de constelações, constelação única). Buscar o
// id da bot antes de cada uma seria seis `await` a mais e seis chances de alguém
// esquecer o filtro na sétima consulta. Aqui é um pedaço de SQL só, importado.
//
// O subselect não custa por linha: o Postgres o resolve UMA vez (InitPlan), porque
// não depende da linha sendo avaliada.
export const NAO_E_BOT = sql`${serverMembers.userId} <> (select "id" from "User" where "username" = ${BOT_USERNAME})`

// A mesma regra em SQL cru, pra quem monta a contagem como subconsulta
// correlacionada (a Descoberta) e não tem como usar o fragmento acima.
export const NAO_E_BOT_CRU = sql`"ServerMember"."userId" <> (select "id" from "User" where "username" = ${BOT_USERNAME})`
