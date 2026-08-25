import { sql } from 'drizzle-orm'
import { serverMembers } from '../db/schema'
import { BOT_USERNAME } from './bot'

export const NAO_E_BOT = sql`${serverMembers.userId} <> (select "id" from "User" where "username" = ${BOT_USERNAME})`

export const NAO_E_BOT_CRU = sql`"ServerMember"."userId" <> (select "id" from "User" where "username" = ${BOT_USERNAME})`
