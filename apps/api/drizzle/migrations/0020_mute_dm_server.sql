ALTER TABLE "DMConversation" ADD COLUMN IF NOT EXISTS "mutedByA" timestamp(3);
ALTER TABLE "DMConversation" ADD COLUMN IF NOT EXISTS "mutedByB" timestamp(3);
CREATE TABLE IF NOT EXISTS "ServerNotifPref" (
	"id" text PRIMARY KEY NOT NULL,
	"userId" text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
	"serverId" text NOT NULL REFERENCES "Server"("id") ON DELETE CASCADE,
	"mode" text NOT NULL DEFAULT 'all',
	"updatedAt" timestamp(3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "ServerNotifPref_userId_serverId_key" ON "ServerNotifPref"("userId","serverId");
