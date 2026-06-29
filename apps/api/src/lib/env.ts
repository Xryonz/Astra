import { z } from 'zod'


const EnvSchema = z.object({

  NODE_ENV:  z.enum(['development', 'production', 'test']).default('development'),
  PORT:      z.coerce.number().default(3001),


  DATABASE_URL: z.string().url('DATABASE_URL deve ser uma URL válida'),


  REDIS_URL: z.string().url('REDIS_URL deve ser uma URL válida').default('redis://localhost:6379'),


  JWT_ACCESS_SECRET:  z.string().min(32, 'JWT_ACCESS_SECRET deve ter ao menos 32 caracteres'),
  JWT_REFRESH_SECRET: z.string().min(32, 'JWT_REFRESH_SECRET deve ter ao menos 32 caracteres'),


  GOOGLE_CLIENT_ID:     z.string().min(1, 'GOOGLE_CLIENT_ID obrigatório'),
  GOOGLE_CLIENT_SECRET: z.string().min(1, 'GOOGLE_CLIENT_SECRET obrigatório'),


  CLIENT_URL: z.string().url('CLIENT_URL deve ser uma URL válida'),
  API_URL:    z.string().url('API_URL deve ser uma URL válida').optional(),


  ANTHROPIC_API_KEY: z.string().min(1).optional(),


  VAPID_PUBLIC_KEY:  z.string().optional(),
  VAPID_PRIVATE_KEY: z.string().optional(),
  VAPID_SUBJECT:     z.string().default('mailto:dev@astra.local'),


  FIREBASE_SERVICE_ACCOUNT: z.string().optional(),


  GIPHY_API_KEY: z.string().optional(),


  SENTRY_DSN:           z.string().url().optional(),
  SENTRY_TRACES_SAMPLE: z.coerce.number().min(0).max(1).default(0.1),
  SENTRY_ENVIRONMENT:   z.string().optional(),

  METRICS_TOKEN:        z.string().optional(),
  LOG_LEVEL:            z.enum(['debug', 'info', 'warn', 'error']).optional(),

  RELEASE:              z.string().optional(),


  LIVEKIT_URL:          z.string().url().optional(),
  LIVEKIT_API_KEY:      z.string().optional(),
  LIVEKIT_API_SECRET:   z.string().optional(),


  R2_ACCOUNT_ID:        z.string().optional(),
  R2_ACCESS_KEY_ID:     z.string().optional(),
  R2_SECRET_ACCESS_KEY: z.string().optional(),
  R2_BUCKET:            z.string().optional(),
  R2_PUBLIC_URL:        z.string().url().optional(),
})

const result = EnvSchema.safeParse(process.env)

if (!result.success) {
  console.error('\n[ENV] ❌ Variáveis de ambiente inválidas:\n')
  result.error.issues.forEach((issue) => {
    console.error(`  • ${issue.path.join('.')}: ${issue.message}`)
  })
  console.error('\nVerifique o arquivo .env e reinicie o servidor.\n')
  process.exit(1)
}

export const env = result.data