
import { db } from '../db'
import { auditLogs } from '../db/schema'

export const AUDIT = {
  SERVER_UPDATE:   'SERVER_UPDATE',
  CHANNEL_CREATE:  'CHANNEL_CREATE',
  CHANNEL_DELETE:  'CHANNEL_DELETE',
  CHANNEL_UPDATE:  'CHANNEL_UPDATE',
  MEMBER_KICK:     'MEMBER_KICK',
  MEMBER_BAN:      'MEMBER_BAN',
  MEMBER_UNBAN:    'MEMBER_UNBAN',
  ROLE_CREATE:     'ROLE_CREATE',
  ROLE_UPDATE:     'ROLE_UPDATE',
  ROLE_DELETE:     'ROLE_DELETE',
  ROLE_ASSIGN:     'ROLE_ASSIGN',
  ROLE_UNASSIGN:   'ROLE_UNASSIGN',
  MESSAGE_DELETE:  'MESSAGE_DELETE',
  MESSAGE_PIN:     'MESSAGE_PIN',
  MESSAGE_UNPIN:   'MESSAGE_UNPIN',
} as const
export type AuditAction = typeof AUDIT[keyof typeof AUDIT]

export function audit(opts: {
  serverId: string
  actorId:  string
  action:   AuditAction
  targetId?: string | null
  metadata?: Record<string, unknown>
}): Promise<void> {
  return db.insert(auditLogs).values({
    serverId: opts.serverId,
    actorId:  opts.actorId,
    action:   opts.action,
    targetId: opts.targetId ?? null,
    metadata: JSON.stringify(opts.metadata ?? {}),
  }).then(() => undefined).catch((err) => {
    console.error('[audit] falha ao gravar:', (err as Error).message)
  })
}
