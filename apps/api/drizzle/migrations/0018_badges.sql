CREATE TABLE "Badge" (
	"id" text PRIMARY KEY NOT NULL,
	"serverId" text NOT NULL,
	"name" text NOT NULL,
	"icon" text NOT NULL,
	"color" text,
	"description" text,
	"createdAt" timestamp(3) DEFAULT now() NOT NULL
);
CREATE TABLE "BadgeGrant" (
	"id" text PRIMARY KEY NOT NULL,
	"badgeId" text NOT NULL,
	"userId" text NOT NULL,
	"grantedBy" text,
	"grantedAt" timestamp(3) DEFAULT now() NOT NULL
);
ALTER TABLE "Badge" ADD CONSTRAINT "Badge_serverId_Server_id_fk" FOREIGN KEY ("serverId") REFERENCES "Server"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "BadgeGrant" ADD CONSTRAINT "BadgeGrant_badgeId_Badge_id_fk" FOREIGN KEY ("badgeId") REFERENCES "Badge"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "BadgeGrant" ADD CONSTRAINT "BadgeGrant_userId_User_id_fk" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE cascade ON UPDATE no action;
CREATE INDEX "Badge_serverId_idx" ON "Badge" ("serverId");
CREATE UNIQUE INDEX "BadgeGrant_badge_user_uq" ON "BadgeGrant" ("badgeId","userId");
CREATE INDEX "BadgeGrant_userId_idx" ON "BadgeGrant" ("userId");
