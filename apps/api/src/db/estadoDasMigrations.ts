import 'dotenv/config'
import { Pool } from 'pg'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

async function main() {
  const url = process.env.DATABASE_URL
  if (!url) {
    console.error('DATABASE_URL não definido.')
    process.exit(1)
  }
  const local = url.includes('localhost') || url.includes('127.0.0.1')
  const pool = new Pool({
    connectionString: url,
    ssl: local ? false : { rejectUnauthorized: false },
  })

  try {
    const { rows: tabela } = await pool.query(
      `select to_regclass('drizzle.__drizzle_migrations') as drizzle,
              to_regclass('public.__drizzle_migrations') as publico,
              to_regclass('public."User"')                as usuario`,
    )
    const onde = tabela[0].drizzle ? 'drizzle' : tabela[0].publico ? 'public' : null
    console.log('tabela de controle :', onde ?? 'NAO EXISTE')
    console.log('tabela User existe :', tabela[0].usuario ? 'sim' : 'nao')

    if (onde) {
      const { rows } = await pool.query(
        `select hash, created_at from ${onde}.__drizzle_migrations order by created_at`,
      )
      console.log('registros           :', rows.length)
      rows.forEach((r, i) => console.log(`  ${String(i).padStart(2, '0')}  ${r.hash}`))
    }

    const journal = JSON.parse(
      readFileSync(resolve(__dirname, '../../drizzle/migrations/meta/_journal.json'), 'utf8'),
    ) as { entries: Array<{ tag: string }> }
    console.log('migrations no repo  :', journal.entries.length)
    console.log('  primeira:', journal.entries[0]?.tag)
    console.log('  ultima  :', journal.entries[journal.entries.length - 1]?.tag)
  } finally {
    await pool.end()
  }
}

main().catch((e) => {
  console.error('falhou:', e.message)
  process.exit(1)
})
