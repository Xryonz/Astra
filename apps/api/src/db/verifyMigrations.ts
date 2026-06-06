import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * Valida no startup que TODA migration .sql tem entry no _journal.json
 * do Drizzle. Sem isso, o migrator pula silenciosamente .sql novos e o
 * server sobe com schema TS prometendo colunas que o DB ainda não tem
 * (foi exatamente assim que Google login quebrou — preferences no schema
 * mas sem entry → migration nunca rodou → SELECT * 42703).
 *
 * Comportamento: se houver .sql sem entry, lança ANTES de qualquer query.
 * Falhar rápido > debugar erro críptico em runtime.
 */
export function verifyMigrationsJournal(migrationsFolder: string): void {
  let journal: { entries: Array<{ tag: string }> }
  try {
    journal = JSON.parse(readFileSync(join(migrationsFolder, 'meta', '_journal.json'), 'utf8'))
  } catch (e: any) {
    throw new Error(`[migrate-verify] não consegui ler _journal.json: ${e.message}`)
  }
  const registered = new Set(journal.entries.map((e) => e.tag))

  const sqlFiles = readdirSync(migrationsFolder)
    .filter((f) => f.endsWith('.sql'))
    .map((f) => f.replace(/\.sql$/, ''))

  const missing = sqlFiles.filter((f) => !registered.has(f))
  if (missing.length > 0) {
    throw new Error(
      `[migrate-verify] ${missing.length} migration(s) .sql sem entry no _journal.json:\n` +
        missing.map((m) => `  - ${m}.sql`).join('\n') +
        `\n\nAdicione no journal antes de subir.`,
    )
  }

  // Extra: entries no journal sem .sql correspondente (raro mas indica drift)
  const orphan = [...registered].filter((tag) => !sqlFiles.includes(tag))
  if (orphan.length > 0) {
    console.warn(
      `[migrate-verify] ⚠ ${orphan.length} entry(ies) no journal sem .sql:`,
      orphan,
    )
  }
}
