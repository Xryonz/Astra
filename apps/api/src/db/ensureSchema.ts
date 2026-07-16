import { pool } from './index'
import { logger } from '../lib/logger'

// Guard idempotente de schema, rodado 1x no boot. Existe porque a tabela
// ChannelCategory (+ Channel.categoryId + Channel.position) entrou no schema.ts mas nunca virou
// migration/snapshot — o Neon ficou sem ela e criar constelacao dava 500.
// DDL espelha o estilo do Drizzle (nomes de constraint/index iguais) pra um
// futuro db:push nao ver drift. Tudo IF NOT EXISTS / duplicate_object -> no-op.
const CATEGORY_DDL = `
CREATE TABLE IF NOT EXISTS "ChannelCategory" (
	"id" text PRIMARY KEY NOT NULL,
	"name" text NOT NULL,
	"serverId" text NOT NULL,
	"position" integer DEFAULT 0 NOT NULL,
	"createdAt" timestamp (3) DEFAULT now() NOT NULL
);
CREATE INDEX IF NOT EXISTS "ChannelCategory_serverId_idx" ON "ChannelCategory" ("serverId");
ALTER TABLE "Channel" ADD COLUMN IF NOT EXISTS "categoryId" text;
ALTER TABLE "Channel" ADD COLUMN IF NOT EXISTS "position" integer NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS "Channel_categoryId_idx" ON "Channel" ("categoryId");
DO $$ BEGIN
	ALTER TABLE "ChannelCategory" ADD CONSTRAINT "ChannelCategory_serverId_Server_id_fk"
		FOREIGN KEY ("serverId") REFERENCES "Server"("id") ON DELETE cascade ON UPDATE no action;
EXCEPTION WHEN duplicate_object THEN null; END $$;
DO $$ BEGIN
	ALTER TABLE "Channel" ADD CONSTRAINT "Channel_categoryId_ChannelCategory_id_fk"
		FOREIGN KEY ("categoryId") REFERENCES "ChannelCategory"("id") ON DELETE set null ON UPDATE no action;
EXCEPTION WHEN duplicate_object THEN null; END $$;
`

// Remocao da feature de threads (2026-07-12): a tabela Thread + Message.threadId
// sairam do schema.ts; aqui garantimos o DROP no banco de prod, porque o Render
// NAO roda migration no deploy (mesmo motivo do CATEGORY_DDL). Idempotente
// (IF EXISTS -> no-op apos a 1a vez). As mensagens que eram respostas de thread
// viram mensagens normais do canal (nao se perde conteudo; so a associacao).
const THREADS_DROP_DDL = `
DROP INDEX IF EXISTS "Message_threadId_idx";
ALTER TABLE "Message" DROP COLUMN IF EXISTS "threadId";
DROP TABLE IF EXISTS "Thread";
`

export async function ensureCategorySchema(): Promise<void> {
  try {
    await pool.query(CATEGORY_DDL)
    logger.info('Schema', 'ChannelCategory garantida (idempotente).')
  } catch (e) {
    logger.error('Schema', 'Falha ao garantir ChannelCategory', e as Error)
  }
  try {
    await pool.query(THREADS_DROP_DDL)
    logger.info('Schema', 'Threads removida (idempotente).')
  } catch (e) {
    logger.error('Schema', 'Falha ao remover threads', e as Error)
  }
}
