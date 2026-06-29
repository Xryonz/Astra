
import { sql } from 'drizzle-orm'
import { db } from '.'
import { users, serverMembers } from './schema'
import { eq, and } from 'drizzle-orm'

export const selectAuthorById = db.select({
  id:          users.id,
  username:    users.username,
  displayName: users.displayName,
  avatarUrl:   users.avatarUrl,
  displayFont: users.displayFont,
}).from(users)
  .where(eq(users.id, sql.placeholder('userId')))
  .limit(1)
  .prepare('select_author_by_id')

export const selectMemberColor = db.select({ nameColor: serverMembers.nameColor })
  .from(serverMembers)
  .where(and(
    eq(serverMembers.userId, sql.placeholder('userId')),
    eq(serverMembers.serverId, sql.placeholder('serverId')),
  ))
  .limit(1)
  .prepare('select_member_color')
