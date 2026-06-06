import passport from 'passport'
import { Strategy as GoogleStrategy } from 'passport-google-oauth20'
import { eq } from 'drizzle-orm'
import { db } from '../db'
import { users } from '../db/schema'

// Tipos compartilhados pro callback — não exportar via passport.user
// (sessões desligadas) então a info viaja via `info` (3º arg do done()).
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

          // 1. Busca por googleId
          const [byGoogleId] = await db.select().from(users)
            .where(eq(users.googleId, profile.id)).limit(1)

          if (byGoogleId) {
            const [updated] = await db.update(users).set({
              avatarUrl:   profile.photos?.[0].value ?? null,
              displayName: profile.displayName,
            }).where(eq(users.id, byGoogleId.id)).returning()
            return done(null, updated)
          }

          // 2. Busca por email — pode ter conta manual antes
          const [byEmail] = await db.select().from(users)
            .where(eq(users.email, email)).limit(1)

          if (byEmail) {
            const [linked] = await db.update(users).set({
              googleId:  profile.id,
              avatarUrl: profile.photos?.[0].value ?? null,
            }).where(eq(users.id, byEmail.id)).returning()
            return done(null, linked)
          }

          // 3. Email não registrado → BLOQUEIA. Não cria conta automaticamente.
          //    Política de segurança: usuário precisa registrar manualmente,
          //    impedindo que qualquer Google login ocupe usernames livres.
          //    `info` passado pra callback do auth.ts redirecionar com email.
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
