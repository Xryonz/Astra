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

export async function ensureCategorySchema(): Promise<void> {
  try {
    await pool.query(CATEGORY_DDL)
    logger.info('Schema', 'ChannelCategory garantida (idempotente).')
  } catch (e) {
    logger.error('Schema', 'Falha ao garantir ChannelCategory', e as Error)
  }
}
