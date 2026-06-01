CREATE TYPE "public"."ChannelType" AS ENUM('TEXT', 'VOICE');--> statement-breakpoint
CREATE TYPE "public"."Role" AS ENUM('OWNER', 'ADMIN', 'MEMBER');--> statement-breakpoint
CREATE TYPE "public"."UserStatus" AS ENUM('ONLINE', 'IDLE', 'DND', 'INVISIBLE');--> statement-breakpoint
CREATE TABLE "Channel" (
	"id" text PRIMARY KEY NOT NULL,
	"name" text NOT NULL,
	"type" "ChannelType" DEFAULT 'TEXT' NOT NULL,
	"serverId" text NOT NULL,
	"createdAt" timestamp (3) DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "DirectMessage" (
	"id" text PRIMARY KEY NOT NULL,
	"content" text NOT NULL,
	"senderId" text NOT NULL,
	"receiverId" text NOT NULL,
	"conversationId" text NOT NULL,
	"edited" boolean DEFAULT false NOT NULL,
	"deletedAt" timestamp (3),
	"createdAt" timestamp (3) DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "DMConversation" (
	"id" text PRIMARY KEY NOT NULL,
	"userAId" text NOT NULL,
	"userBId" text NOT NULL,
	"createdAt" timestamp (3) DEFAULT now() NOT NULL,
	"updatedAt" timestamp (3) DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "MessageReaction" (
	"id" text PRIMARY KEY NOT NULL,
	"messageId" text NOT NULL,
	"userId" text NOT NULL,
	"emoji" text NOT NULL,
	"createdAt" timestamp (3) DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "Message" (
	"id" text PRIMARY KEY NOT NULL,
	"content" text NOT NULL,
	"authorId" text NOT NULL,
	"channelId" text NOT NULL,
	"threadId" text,
	"replyToId" text,
	"authorColor" text,
	"attachments" text DEFAULT '[]' NOT NULL,
	"mentions" text DEFAULT '' NOT NULL,
	"edited" boolean DEFAULT false NOT NULL,
	"pinned" boolean DEFAULT false NOT NULL,
	"deletedAt" timestamp (3),
	"createdAt" timestamp (3) DEFAULT now() NOT NULL,
	"updatedAt" timestamp (3) DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "MutedMember" (
	"id" text PRIMARY KEY NOT NULL,
	"userId" text NOT NULL,
	"serverId" text NOT NULL,
	"mutedById" text NOT NULL,
	"reason" text DEFAULT 'Spam automático' NOT NULL,
	"expiresAt" timestamp (3) NOT NULL,
	"createdAt" timestamp (3) DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "PushSubscription" (
	"id" text PRIMARY KEY NOT NULL,
	"userId" text NOT NULL,
	"endpoint" text NOT NULL,
	"p256dh" text NOT NULL,
	"auth" text NOT NULL,
	"userAgent" text,
	"createdAt" timestamp (3) DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "RefreshToken" (
	"id" text PRIMARY KEY NOT NULL,
	"token" text NOT NULL,
	"userId" text NOT NULL,
	"expiresAt" timestamp (3) NOT NULL,
	"createdAt" timestamp (3) DEFAULT now() NOT NULL,
	"revokedAt" timestamp (3),
	CONSTRAINT "RefreshToken_token_unique" UNIQUE("token")
);
--> statement-breakpoint
CREATE TABLE "ServerMember" (
	"id" text PRIMARY KEY NOT NULL,
	"userId" text NOT NULL,
	"serverId" text NOT NULL,
	"role" "Role" DEFAULT 'MEMBER' NOT NULL,
	"nameColor" text,
	"joinedAt" timestamp (3) DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "Server" (
	"id" text PRIMARY KEY NOT NULL,
	"name" text NOT NULL,
	"iconUrl" text,
	"inviteCode" text NOT NULL,
	"ownerId" text NOT NULL,
	"isGroup" boolean DEFAULT false NOT NULL,
	"messageRetentionDays" integer,
	"createdAt" timestamp (3) DEFAULT now() NOT NULL,
	"updatedAt" timestamp (3) DEFAULT now() NOT NULL,
	CONSTRAINT "Server_inviteCode_unique" UNIQUE("inviteCode")
);
--> statement-breakpoint
CREATE TABLE "Thread" (
	"id" text PRIMARY KEY NOT NULL,
	"channelId" text NOT NULL,
	"parentMessageId" text NOT NULL,
	"name" text NOT NULL,
	"createdById" text NOT NULL,
	"createdAt" timestamp (3) DEFAULT now() NOT NULL,
	"updatedAt" timestamp (3) DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "User" (
	"id" text PRIMARY KEY NOT NULL,
	"email" text NOT NULL,
	"username" text NOT NULL,
	"displayName" text NOT NULL,
	"avatarUrl" text,
	"bio" text,
	"googleId" text,
	"passwordHash" text,
	"isBot" boolean DEFAULT false NOT NULL,
	"bannerUrl" text,
	"bannerColor" text,
	"profileTheme" text,
	"status" "UserStatus" DEFAULT 'ONLINE' NOT NULL,
	"createdAt" timestamp (3) DEFAULT now() NOT NULL,
	"updatedAt" timestamp (3) DEFAULT now() NOT NULL,
	CONSTRAINT "User_email_unique" UNIQUE("email"),
	CONSTRAINT "User_username_unique" UNIQUE("username"),
	CONSTRAINT "User_googleId_unique" UNIQUE("googleId")
);
--> statement-breakpoint
ALTER TABLE "Channel" ADD CONSTRAINT "Channel_serverId_Server_id_fk" FOREIGN KEY ("serverId") REFERENCES "public"."Server"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "DirectMessage" ADD CONSTRAINT "DirectMessage_senderId_User_id_fk" FOREIGN KEY ("senderId") REFERENCES "public"."User"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "DirectMessage" ADD CONSTRAINT "DirectMessage_receiverId_User_id_fk" FOREIGN KEY ("receiverId") REFERENCES "public"."User"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "DirectMessage" ADD CONSTRAINT "DirectMessage_conversationId_DMConversation_id_fk" FOREIGN KEY ("conversationId") REFERENCES "public"."DMConversation"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "DMConversation" ADD CONSTRAINT "DMConversation_userAId_User_id_fk" FOREIGN KEY ("userAId") REFERENCES "public"."User"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "DMConversation" ADD CONSTRAINT "DMConversation_userBId_User_id_fk" FOREIGN KEY ("userBId") REFERENCES "public"."User"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "MessageReaction" ADD CONSTRAINT "MessageReaction_messageId_Message_id_fk" FOREIGN KEY ("messageId") REFERENCES "public"."Message"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "MessageReaction" ADD CONSTRAINT "MessageReaction_userId_User_id_fk" FOREIGN KEY ("userId") REFERENCES "public"."User"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "Message" ADD CONSTRAINT "Message_authorId_User_id_fk" FOREIGN KEY ("authorId") REFERENCES "public"."User"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "Message" ADD CONSTRAINT "Message_channelId_Channel_id_fk" FOREIGN KEY ("channelId") REFERENCES "public"."Channel"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "MutedMember" ADD CONSTRAINT "MutedMember_userId_User_id_fk" FOREIGN KEY ("userId") REFERENCES "public"."User"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "PushSubscription" ADD CONSTRAINT "PushSubscription_userId_User_id_fk" FOREIGN KEY ("userId") REFERENCES "public"."User"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "RefreshToken" ADD CONSTRAINT "RefreshToken_userId_User_id_fk" FOREIGN KEY ("userId") REFERENCES "public"."User"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "ServerMember" ADD CONSTRAINT "ServerMember_userId_User_id_fk" FOREIGN KEY ("userId") REFERENCES "public"."User"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "ServerMember" ADD CONSTRAINT "ServerMember_serverId_Server_id_fk" FOREIGN KEY ("serverId") REFERENCES "public"."Server"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "Server" ADD CONSTRAINT "Server_ownerId_User_id_fk" FOREIGN KEY ("ownerId") REFERENCES "public"."User"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "Thread" ADD CONSTRAINT "Thread_channelId_Channel_id_fk" FOREIGN KEY ("channelId") REFERENCES "public"."Channel"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "Thread" ADD CONSTRAINT "Thread_createdById_User_id_fk" FOREIGN KEY ("createdById") REFERENCES "public"."User"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "Channel_serverId_idx" ON "Channel" USING btree ("serverId");--> statement-breakpoint
CREATE INDEX "DirectMessage_conversationId_createdAt_idx" ON "DirectMessage" USING btree ("conversationId","createdAt" DESC NULLS LAST);--> statement-breakpoint
CREATE INDEX "DirectMessage_senderId_idx" ON "DirectMessage" USING btree ("senderId");--> statement-breakpoint
CREATE UNIQUE INDEX "DMConversation_userAId_userBId_key" ON "DMConversation" USING btree ("userAId","userBId");--> statement-breakpoint
CREATE INDEX "DMConversation_userAId_idx" ON "DMConversation" USING btree ("userAId");--> statement-breakpoint
CREATE INDEX "DMConversation_userBId_idx" ON "DMConversation" USING btree ("userBId");--> statement-breakpoint
CREATE UNIQUE INDEX "MessageReaction_messageId_userId_emoji_key" ON "MessageReaction" USING btree ("messageId","userId","emoji");--> statement-breakpoint
CREATE INDEX "MessageReaction_messageId_idx" ON "MessageReaction" USING btree ("messageId");--> statement-breakpoint
CREATE INDEX "Message_channelId_createdAt_idx" ON "Message" USING btree ("channelId","createdAt" DESC NULLS LAST);--> statement-breakpoint
CREATE INDEX "Message_authorId_idx" ON "Message" USING btree ("authorId");--> statement-breakpoint
CREATE INDEX "Message_channelId_pinned_idx" ON "Message" USING btree ("channelId","pinned");--> statement-breakpoint
CREATE INDEX "Message_replyToId_idx" ON "Message" USING btree ("replyToId");--> statement-breakpoint
CREATE INDEX "Message_threadId_idx" ON "Message" USING btree ("threadId");--> statement-breakpoint
CREATE UNIQUE INDEX "MutedMember_userId_serverId_key" ON "MutedMember" USING btree ("userId","serverId");--> statement-breakpoint
CREATE INDEX "MutedMember_serverId_idx" ON "MutedMember" USING btree ("serverId");--> statement-breakpoint
CREATE INDEX "MutedMember_expiresAt_idx" ON "MutedMember" USING btree ("expiresAt");--> statement-breakpoint
CREATE UNIQUE INDEX "PushSubscription_endpoint_key" ON "PushSubscription" USING btree ("endpoint");--> statement-breakpoint
CREATE INDEX "PushSubscription_userId_idx" ON "PushSubscription" USING btree ("userId");--> statement-breakpoint
CREATE INDEX "RefreshToken_userId_idx" ON "RefreshToken" USING btree ("userId");--> statement-breakpoint
CREATE UNIQUE INDEX "ServerMember_userId_serverId_key" ON "ServerMember" USING btree ("userId","serverId");--> statement-breakpoint
CREATE INDEX "ServerMember_serverId_idx" ON "ServerMember" USING btree ("serverId");--> statement-breakpoint
CREATE INDEX "ServerMember_userId_idx" ON "ServerMember" USING btree ("userId");--> statement-breakpoint
CREATE INDEX "Thread_channelId_idx" ON "Thread" USING btree ("channelId");--> statement-breakpoint
CREATE INDEX "Thread_parentMessageId_idx" ON "Thread" USING btree ("parentMessageId");