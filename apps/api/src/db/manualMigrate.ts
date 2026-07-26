
import 'dotenv/config'
import { Pool } from 'pg'
import dns from 'dns'
import { GUARD_DDL } from './guardDdl'

dns.setDefaultResultOrder('ipv4first')

const pool = new Pool({
  connectionString:     process.env.DATABASE_URL!,
  ssl:                  { rejectUnauthorized: false },
  connectionTimeoutMillis: 15_000,
})

// A DDL vive em guardDdl.ts (fonte única, também rodada no boot pelo ensureSchema).
// Este script é o caminho manual pra aplicar tudo de uma vez contra o Neon.
const sql = GUARD_DDL

async function main() {
  console.log('[Migrate] Aplicando schema guard (roles/perms/perfil/categorias — idempotente)…')
  if (!process.env.DATABASE_URL) {
    console.error('[Migrate] ❌ DATABASE_URL ausente — verifique apps/api/.env')
    process.exit(1)
  }
  console.log('[Migrate] DATABASE_URL host:',
    process.env.DATABASE_URL.replace(/:\/\/[^@]*@/, '://***:***@').slice(0, 80) + '…')

  try {

    await pool.query('SELECT 1')
    console.log('[Migrate] ✓ Conexão DB ok')

    await pool.query(sql)
    console.log('[Migrate] ✓ Schema garantido')
  } catch (err: any) {

    console.error('[Migrate] ❌ erro ao migrar:')
    console.error('  message:    ', err?.message || '(vazio)')
    console.error('  code:       ', err?.code)
    console.error('  errno:      ', err?.errno)
    console.error('  syscall:    ', err?.syscall)
    console.error('  hostname:   ', err?.hostname)
    console.error('  address:    ', err?.address)
    console.error('  detail:     ', err?.detail)
    console.error('  hint:       ', err?.hint)
    console.error('  where:      ', err?.where)
    console.error('  routine:    ', err?.routine)
    console.error('  position:   ', err?.position)
    console.error('  severity:   ', err?.severity)
    if (err?.stack) console.error('  stack:\n', err.stack.split('\n').slice(0, 6).join('\n'))
    process.exit(1)
  } finally {
    await pool.end()
  }
}

main()
