import passport from 'passport'
import { Strategy as GoogleStrategy } from 'passport-google-oauth20'
import { eq } from 'drizzle-orm'
import { db } from '../db'
import { users } from '../db/schema'

const userMinCols = {
  id:           users.id,
  email:        users.email,
  username:     users.username,
  googleId:     users.googleId,
  displayName:  users.displayName,
  avatarUrl:    users.avatarUrl,
}

export interface GoogleAuthFailureInfo {
  code:  'email_not_registered'
  email: string
}

if (process.env.GOOGLE_CLIENT_ID && process.env.GOOGLE_CLIENT_SECRET) {
  passport.use(
    new GoogleStrategy(
      {
        clientID:     process.env.GOOGLE_CLIENT_ID,
        clientSecret: process.env.GOOGLE_CLIENT_SECRET,
        callbackURL:  '/api/auth/google/callback',
      },
      async (_accessToken, _refreshToken, profile, done) => {
        try {
          const email = profile.emails?.[0].value
          if (!email) return done(new Error('E-mail não disponível no perfil Google'))

          const [byGoogleId] = await db.select(userMinCols).from(users)
            .where(eq(users.googleId, profile.id)).limit(1)

          if (byGoogleId) return done(null, byGoogleId)

          const [byEmail] = await db.select(userMinCols).from(users)
            .where(eq(users.email, email)).limit(1)

          if (byEmail) {
            const patch: Partial<typeof users.$inferInsert> = { googleId: profile.id }
            if (!byEmail.avatarUrl   && profile.photos?.[0]?.value) patch.avatarUrl   = profile.photos[0].value
            if (!byEmail.displayName && profile.displayName)        patch.displayName = profile.displayName
            const [linked] = await db.update(users).set(patch)
              .where(eq(users.id, byEmail.id)).returning(userMinCols)
            return done(null, linked)
          }

          const failureInfo: GoogleAuthFailureInfo = {
            code:  'email_not_registered',
            email,
          }
          return done(null, false, failureInfo)
        } catch (error: any) {
          const cause = error?.cause ?? error
          console.error('[Passport/Google] insert/update failed:', {
            message:    cause?.message,
            code:       cause?.code,
            constraint: cause?.constraint,
            detail:     cause?.detail,
            table:      cause?.table,
            column:     cause?.column,
          })
          return done(error as Error)
        }
      }
    )
  )
} else {
  console.warn('[Passport] Google OAuth não configurado — GOOGLE_CLIENT_ID ausente')
}

export default passport
