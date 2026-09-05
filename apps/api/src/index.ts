import 'dotenv/config'
import './lib/env'

import { initSentry, sentry } from './lib/sentry'
initSentry()

import express    from 'express'
import http       from 'http'
import { resolve } from 'path'
import { Server as SocketServer } from 'socket.io'
import { createAdapter } from '@socket.io/redis-adapter'
import { redis } from './lib/redis'
import cors       from 'cors'
import compression from 'compression'

import { env }            from './lib/env'
import { isAllowedOrigin } from './lib/allowedOrigins'
import { secureHeaders, hidePoweredBy } from './middleware/secureHeaders'
import { sanitizeInputs } from './middleware/sanitize'
import { globalLimiter }  from './middleware/rateLimiter'
import { reqContext }     from './middleware/reqContext'
import { httpMetrics }    from './middleware/httpMetrics'
import { setupSocket }    from './config/socket'
import { initBot }        from './lib/bot'
import { IA_LIGADA, IA_PROVEDOR, MODELO_CONVERSA } from './lib/ia'
import './config/passport'

import authRouter            from './routes/auth'
import profileRouter         from './routes/profile'
import botPersonaRouter      from './routes/botPersona'
import inviteRouter          from './routes/invites'
import invitePreviewRouter   from './routes/invitePreview'
import { serversRouter, channelsRouter } from './routes/servers'
import { attachRealtime }                from './lib/realtime'
import { ligarAvisosDaBot, agendarTrocaDeTurno } from './lib/botAvisos'
import { iniciarRelogioDeCall }          from './lib/xp'
import { eventoDeMissao }                from './lib/missoes'
import { createMessagesRouter }          from './routes/messages'
import { createReactionsRouter }         from './routes/reactions'
import { createPollsRouter }             from './routes/polls'
import { createReadsRouter }             from './routes/reads'
import { createDMRouter }                from './routes/dm'
import { createSoundsRouter }            from './routes/sounds'
import { stickersRouter }                from './routes/stickers'
import searchRouter                      from './routes/search'
import discoverRouter                    from './routes/discover'
import uploadRouter, { UPLOAD_DIR }      from './routes/upload'
import { startRetentionWorker }          from './lib/retentionWorker'
import pushRouter                        from './routes/push'
import { initPush }                      from './lib/push'
import { initFcm }                       from './lib/fcm'
import { initMailer }                    from './lib/mailer'
import gifRouter                         from './routes/gif'
import unfurlRouter                      from './routes/unfurl'
import { rolesRouter }                   from './routes/roles'
import { bansRouter }                    from './routes/bans'
import { serverBadgesRouter, userBadgesRouter } from './routes/badges'
import { healthRouter }                  from './routes/health'
import notificationsRouter               from './routes/notifications'
import botCommandsRouter                 from './routes/botCommands'
import friendsRouter                      from './routes/friends'
import blocksRouter                       from './routes/blocks'
import voiceRouter                        from './routes/voice'
import wishesRouter                       from './routes/wishes'
import translateRouter                    from './routes/translate'
import xpRouter                           from './routes/xp'
import missionsRouter                     from './routes/missions'
import sessionsRouter                     from './routes/sessions'
import emojisRouter                       from './routes/emojis'
import channelNotifPrefsRouter            from './routes/channelNotifPrefs'
import { startReminderWorker }            from './lib/reminders'
import { HttpError }                     from './lib/errors'
import { logger }                        from './lib/logger'
import { ensureCategorySchema }          from './db/ensureSchema'
import { garantirBotEmTodas }            from './lib/botMembership'

const app        = express()

app.set('trust proxy', 1)

const httpServer = http.createServer(app)

const socketAllowedOrigin = (origin: string | undefined, cb: (err: Error | null, ok?: boolean) => void) => {
  if (!origin) return cb(null, true)
  if (isAllowedOrigin(origin)) return cb(null, true)
  cb(new Error('CORS blocked'))
}
const io = new SocketServer(httpServer, {
  cors:              { origin: socketAllowedOrigin, credentials: true },
  perMessageDeflate: false,
  pingTimeout:       20_000,
  pingInterval:      25_000,
})
if (process.env.SOCKET_ADAPTER === 'redis') {
  const pub = redis.duplicate()
  const sub = redis.duplicate()
  io.adapter(createAdapter(pub, sub))
  console.log('[Socket] adapter Redis ligado (varias instancias)')
}

setupSocket(io)
attachRealtime(io)
ligarAvisosDaBot(io)

app.use(hidePoweredBy)
app.use(secureHeaders)

app.use(cors({
  origin: (origin, cb) => {
    if (!origin) return cb(null, true)
    if (isAllowedOrigin(origin)) return cb(null, true)
    cb(new Error('CORS blocked'))
  },
  credentials:     true,
  methods:         ['GET','POST','PATCH','PUT','DELETE','OPTIONS'],
  allowedHeaders:  ['Content-Type','Authorization','X-Request-Id'],
  maxAge:          600,
}))

app.use(compression())

app.use(reqContext)
app.use(httpMetrics)

app.use('/api/profile', express.json({ limit: '16mb' }))
app.use('/api/servers', express.json({ limit: '16mb' }))
app.use('/api/bots', express.json({ limit: '16mb' }))
app.use(express.json({ limit: '1mb' }))
app.use(express.urlencoded({ extended: false, limit: '128kb' }))
app.use(sanitizeInputs)

app.use(healthRouter)

app.use(globalLimiter)

app.use('/api/auth',     authRouter)
app.use('/api/profile',  profileRouter)
app.use('/api/bots',     botPersonaRouter)
app.use('/api/invites',  inviteRouter)

app.use('/i',            invitePreviewRouter)
app.use('/api/servers',  serversRouter)
app.use('/api/servers',  channelsRouter)
app.use('/api/channels/:channelId/messages', createMessagesRouter(io))
app.use('/api/channels/:channelId/polls',    createPollsRouter(io))
app.use('/api',                              createReadsRouter(io))
app.use('/api/dm', createDMRouter(io))
app.use('/api/sounds', createSoundsRouter(io))
app.use('/api/stickers', stickersRouter)
app.use('/api/search', searchRouter)
app.use('/api/discover', discoverRouter)
app.use('/api/upload', uploadRouter)
app.use('/api/push', pushRouter)
app.use('/api/gif',  gifRouter)
app.use('/api',      unfurlRouter)
app.use('/api/servers', rolesRouter)
app.use('/api/servers', bansRouter)
app.use('/api/servers', serverBadgesRouter)
app.use('/api/users',   userBadgesRouter)
app.use('/api',         notificationsRouter)
app.use('/api/bot',     botCommandsRouter)
app.use('/api/translate', translateRouter)
app.use('/api/friends',   friendsRouter)
app.use('/api/blocks',    blocksRouter)
app.use('/api/voice',     voiceRouter)
app.use('/api/wishes',    wishesRouter)
app.use('/api/xp',        xpRouter)
app.use('/api/missions',  missionsRouter)
app.use('/api/sessions',  sessionsRouter)
app.use('/api/servers',   emojisRouter)
app.use('/api',           channelNotifPrefsRouter)

app.use('/static', express.static(resolve(__dirname, '../public'), {
  maxAge: '7d', fallthrough: true,
  setHeaders: (res) => { res.setHeader('X-Content-Type-Options', 'nosniff') },
}))

app.use('/uploads', express.static(UPLOAD_DIR, {
  maxAge: '1d', immutable: true, fallthrough: true,
  setHeaders: (res, filePath) => {
    const inlineOk = /\.(png|jpe?g|gif|webp|avif|mp4|webm|mov|mp3|wav|ogg|weba|m4a|aac)$/i.test(filePath)
    if (!inlineOk) res.setHeader('Content-Disposition', 'attachment')
    res.setHeader('X-Content-Type-Options', 'nosniff')
  },
}))

app.use(
  '/api/channels/:channelId/messages/:messageId/react',
  createReactionsRouter(io)
)

app.use((_req, res) => res.status(404).json({ error: 'Rota não encontrada' }))

app.use((err: any, req: express.Request, res: express.Response, _next: express.NextFunction) => {

  if (err instanceof HttpError) {
    return res.status(err.status).json({
      error: err.message,
      ...(err.code ? { code: err.code } : {}),
      ...(err.meta ? { meta: err.meta } : {}),
    })
  }

  if (err?.type === 'entity.too.large' || err?.status === 413) {
    return res.status(413).json({ error: 'Arquivo muito grande. Tente um menor.' })
  }

  const cause = err?.cause ?? err
  const dbInfo = cause !== err ? {
    code:       cause?.code,
    constraint: cause?.constraint,
    detail:     cause?.detail,
    table:      cause?.table,
    column:     cause?.column,
  } : null
  logger.error('Error', err?.message ?? 'unknown', err, dbInfo ?? '')

  sentry.captureException(err, {
    tags: { route: req.route?.path ?? req.path, method: req.method, reqId: req.reqId ?? '' },
    user: req.userId ? { id: req.userId } : undefined,
  })

  if (env.NODE_ENV === 'production') return res.status(500).json({ error: 'Erro interno', reqId: req.reqId })
  res.status(500).json({
    error: err.message,
    cause: dbInfo ?? undefined,
    stack: err.stack,
    reqId: req.reqId,
  })
})

process.on('unhandledRejection', (r) => {
  logger.error('UnhandledRejection', String(r), r)
  sentry.captureException(r)
})
process.on('uncaughtException', (e) => {
  logger.error('UncaughtException', String(e), e)
  sentry.captureException(e)
  process.exit(1)
})

httpServer.listen(env.PORT, async () => {
  logger.info('Astra API', `http://localhost:${env.PORT} (${env.NODE_ENV})`)
  await ensureCategorySchema()
  await initBot()
  void garantirBotEmTodas()
  agendarTrocaDeTurno()
  logger.info('Bot', IA_LIGADA ? `Pronto — IA: ${IA_PROVEDOR} (${MODELO_CONVERSA}).` : 'Pronto — IA DESLIGADA: nenhuma chave no ambiente (GROQ_API_KEY?). So os comandos funcionam.')
  startRetentionWorker()
  logger.info('Retention', 'Worker iniciado (1h)')
  startReminderWorker(io)
  logger.info('Reminders', 'Worker iniciado (30s)')
  iniciarRelogioDeCall((ids) => {
    for (const id of ids) void eventoDeMissao(id, 'call')
  })
  logger.info('Xp', 'Relogio de call iniciado (1min)')
  initPush()
  void initFcm()
  initMailer()
})
