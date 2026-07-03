ALTER TABLE "User" ADD COLUMN "emailVerifiedAt" timestamp(3);
ALTER TABLE "User" ADD COLUMN "emailCode" text;
ALTER TABLE "User" ADD COLUMN "emailCodeExpiresAt" timestamp(3);
UPDATE "User" SET "emailVerifiedAt" = "createdAt";
