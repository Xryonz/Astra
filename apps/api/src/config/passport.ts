import passport from 'passport'
import { Strategy as GoogleStrategy } from 'passport-google-oauth20'
import { eq } from 'drizzle-orm'
import { db } from '../db'
import { users } from '../db/schema'

const userMinCols = {
  id: users.id,
  email: users.email,
  username: users.username,
  googleId: users.googleId,
  displayName: users.displayName,
  avatarUrl: users.avatarUrl,
  emailVerifiedAt: users.emailVerifiedAt,
}

export interface GoogleAuthFailureInfo {
  code: 'email_not_registered' | 'email_not_confirmed' | 'google_email_unverified'
  email: string
}

type PerfilDoGoogle = {
  id: string
  displayName?: string
  emails?: Array<{ value: string; verified?: boolean }>
  photos?: Array<{ value: string }>
}

export async function contaDoGoogle(perfil: PerfilDoGoogle) {
  const email = perfil.emails?.[0]?.value
  if (!email) throw new Error('E-mail não disponível no perfil Google')

  const [byGoogleId] = await db.select(userMinCols).from(users).where(eq(users.googleId, perfil.id)).limit(1)
  if (byGoogleId) return { conta: byGoogleId }

  const falha = (code: GoogleAuthFailureInfo['code']) => ({ falha: { code, email } satisfies GoogleAuthFailureInfo })
  if (perfil.emails?.[0]?.verified !== true) return falha('google_email_unverified')

  const [byEmail] = await db.select(userMinCols).from(users).where(eq(users.email, email)).limit(1)
  if (!byEmail) return falha('email_not_registered')
  if (!byEmail.emailVerifiedAt) return falha('email_not_confirmed')

  const patch: Partial<typeof users.$inferInsert> = { googleId: perfil.id }
  if (!byEmail.avatarUrl && perfil.photos?.[0]?.value) patch.avatarUrl = perfil.photos[0].value
  if (!byEmail.displayName && perfil.displayName) patch.displayName = perfil.displayName
  const [linked] = await db.update(users).set(patch).where(eq(users.id, byEmail.id)).returning(userMinCols)
  return { conta: linked }
}

if (process.env.GOOGLE_CLIENT_ID && process.env.GOOGLE_CLIENT_SECRET) {
  passport.use(
    new GoogleStrategy(
      {
        clientID: process.env.GOOGLE_CLIENT_ID,
        clientSecret: process.env.GOOGLE_CLIENT_SECRET,
        callbackURL: '/api/auth/google/callback',
      },
      async (_accessToken, _refreshToken, profile, done) => {
        try {
          const resultado = await contaDoGoogle(profile)
          return 'conta' in resultado ? done(null, resultado.conta) : done(null, false, resultado.falha)
        } catch (error: any) {
          const cause = error?.cause ?? error
          console.error('[Passport/Google] insert/update failed:', {
            message: cause?.message,
            code: cause?.code,
            constraint: cause?.constraint,
            detail: cause?.detail,
            table: cause?.table,
            column: cause?.column,
          })
          return done(error as Error)
        }
      },
    ),
  )
} else {
  console.warn('[Passport] Google OAuth não configurado — GOOGLE_CLIENT_ID ausente')
}

export default passport
