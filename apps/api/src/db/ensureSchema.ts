import { pool } from './index'
import { logger } from '../lib/logger'
import { GUARD_DDL } from './guardDdl'

// Guard idempotente de schema, rodado 1x no boot. Existe porque o Render NÃO roda
// migration no deploy: colunas/tabelas que entram no schema.ts mas nunca viram
// migration aplicada deixavam o Neon defasado e a operação dava 500 (ChannelCategory,
// e depois o sistema de cargos — getMemberPerms faz join em ServerRole/ServerMemberRole).
// Toda a DDL vive em guardDdl.ts (fonte única, compartilhada com o script manual).
// Tudo IF NOT EXISTS / IF EXISTS / duplicate_object -> no-op; rodar N vezes é seguro.
export async function ensureCategorySchema(): Promise<void> {
  try {
    await pool.query(GUARD_DDL)
    logger.info('Schema', 'Schema garantido (roles/perms/perfil/categorias — idempotente).')
  } catch (e) {
    logger.error('Schema', 'Falha ao garantir schema', e as Error)
  }
}
