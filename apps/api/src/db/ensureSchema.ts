import { pool } from './index'
import { logger } from '../lib/logger'
import { GUARD_DDL } from './guardDdl'

export async function ensureCategorySchema(): Promise<void> {
  try {
    await pool.query(GUARD_DDL)
    logger.info('Schema', 'Schema garantido (roles/perms/perfil/categorias — idempotente).')
  } catch (e) {
    logger.error('Schema', 'Falha ao garantir schema', e as Error)
  }
}
