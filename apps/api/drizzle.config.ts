import 'dotenv/config'
import { defineConfig } from 'drizzle-kit'
import { ehBancoLocal } from './src/lib/bancoLocal'

const banco = process.env.DATABASE_URL ?? ''
const bancoLocal = ehBancoLocal(banco)

if (!bancoLocal) {
  console.warn('[drizzle] O DATABASE_URL não é local: push, migrate e studio ficam sem banco; o generate segue.')
}

export default defineConfig({
  dialect: 'postgresql',
  schema:  './src/db/schema.ts',
  out:     './drizzle/migrations',
  ...(bancoLocal ? { dbCredentials: { url: banco } } : {}),
  verbose: true,
  strict:  true,
})
