import { PGlite } from '@electric-sql/pglite'
import { drizzle } from 'drizzle-orm/pglite'
import { generateDrizzleJson, generateMigration } from 'drizzle-kit/api'
import * as schema from '../db/schema'

export async function abrirBancoEmMemoria() {
  const cliente = new PGlite()
  const comandos = await generateMigration(generateDrizzleJson({}), generateDrizzleJson(schema))
  for (const comando of comandos) await cliente.exec(comando)
  return drizzle({ client: cliente, schema })
}
