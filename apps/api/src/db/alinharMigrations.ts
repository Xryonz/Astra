import { resolve } from 'node:path'
import { readMigrationFiles } from 'drizzle-orm/migrator'
import { migrate } from 'drizzle-orm/node-postgres/migrator'
import { drizzle } from 'drizzle-orm/node-postgres'
import { pool } from './index'
import { logger } from '../lib/logger'

const PASTA = resolve(__dirname, '../../drizzle/migrations')

async function bancoJaTemEsquema(): Promise<boolean> {
  const { rows } = await pool.query(`select to_regclass('public."User"') as alvo`)
  return rows[0]?.alvo != null
}

async function garantirTabelaDeControle(): Promise<void> {
  await pool.query(`create schema if not exists "drizzle"`)
  await pool.query(
    `create table if not exists "drizzle"."__drizzle_migrations" (
       id serial primary key,
       hash text not null,
       created_at bigint
     )`,
  )
}

async function ultimoRegistro(): Promise<number> {
  const { rows } = await pool.query(
    `select created_at from "drizzle"."__drizzle_migrations" order by created_at desc limit 1`,
  )
  return rows.length ? Number(rows[0].created_at) : 0
}

export async function alinharMigrations(): Promise<void> {
  const arquivos = readMigrationFiles({ migrationsFolder: PASTA })
  if (arquivos.length === 0) return

  const temEsquema = await bancoJaTemEsquema()
  if (temEsquema) {
    await garantirTabelaDeControle()
    const ate = await ultimoRegistro()
    const atrasadas = arquivos.filter((m) => m.folderMillis > ate)
    if (atrasadas.length > 0) {
      for (const m of atrasadas) {
        await pool.query(
          `insert into "drizzle"."__drizzle_migrations" ("hash", "created_at") values ($1, $2)`,
          [m.hash, m.folderMillis],
        )
      }
      logger.warn(
        'Migrations',
        `banco ja tinha o esquema e ${atrasadas.length} migration(s) sem registro — ` +
          'carimbadas como aplicadas sem executar. Daqui em diante o migrator manda.',
      )
    }
  }

  await migrate(drizzle(pool), { migrationsFolder: PASTA })
  logger.info('Migrations', temEsquema ? 'em dia' : 'banco novo — esquema criado do zero')
}
