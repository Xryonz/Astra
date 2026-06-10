-- Push nativo via Firebase Cloud Messaging (app Android/iOS).
-- Paralelo ao PushSubscription (web push) — sendPush() despacha pros dois.
CREATE TABLE IF NOT EXISTS "FcmToken" (
  "id"         text PRIMARY KEY NOT NULL,
  "userId"     text NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "token"      text NOT NULL,
  "platform"   text NOT NULL DEFAULT 'android',
  "createdAt"  timestamp (3) NOT NULL DEFAULT now(),
  "lastSeenAt" timestamp (3) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "FcmToken_token_key" ON "FcmToken" ("token");
CREATE INDEX IF NOT EXISTS "FcmToken_userId_idx" ON "FcmToken" ("userId");
