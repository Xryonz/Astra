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
import cookieParser from 'cookie-parser'

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
import inviteRouter          from './routes/invites'
import invitePreviewRouter   from './routes/invitePreview'
import { serversRouter, channelsRouter } from './routes/servers'
import { attachRealtime }                from './lib/realtime'
import { iniciarRelogioDeCall }          from './lib/xp'
import { createMessagesRouter }          from './routes/messages'
import { createReactionsRouter }         from './routes/reactions'
import { createPollsRouter }             from './routes/polls'
import { createReadsRouter }             from './routes/reads'
import { createDMRouter }                from './routes/dm'
import searchRouter                      from './routes/search'
import discoverRouter                    from './routes/discover'
import uploadRouter, { UPLOAD_DIR }      from './routes/upload'
import { startRetentionWorker }          from './lib/retentionWorker'
import pushRouter                        from './routes/push'
import { initPush }                      from './lib/push'
import { initFcm }                       from './lib/fcm'
import { initMailer }                    from './lib/mailer'
import gifRouter                         from './routes/gif'
import { rolesRouter }                   from './routes/roles'
import { bansRouter }                    from './routes/bans'
import { serverBadgesRouter, userBadgesRouter } from './routes/badges'
import { healthRouter }                  from './routes/health'
import notificationsRouter               from './routes/notifications'
import botCommandsRouter                 from './routes/botCommands'
import bookmarksRouter                    from './routes/bookmarks'
import remindersRouter                    from './routes/reminders'
import translateRouter                    from './routes/translate'
import friendsRouter                      from './routes/friends'
import blocksRouter                       from './routes/blocks'
import voiceRouter                        from './routes/voice'
import wishesRouter                       from './routes/wishes'
import xpRouter                           from './routes/xp'
import sessionsRouter                     from './routes/sessions'
import emojisRouter                       from './routes/emojis'
import channelNotifPrefsRouter            from './routes/channelNotifPrefs'
import { startReminderWorker }            from './lib/reminders'
import { HttpError }                     from './lib/errors'
import { logger }                        from './lib/logger'
import { ensureCategorySchema }          from './db/ensureSchema'

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
// UMA instancia so alcanca os sockets DELA. `io.emit` num processo nao chega em
// quem esta conectado no outro — e o dia em que a API rodar em 2 processos,
// METADE dos avisos some sem erro nenhum no log. O adapter do Redis resolve isso
// repassando os eventos por pub/sub.
//
// Fica atras de um interruptor (SOCKET_ADAPTER=redis) de proposito, e nao ligado
// sempre, por dois motivos concretos: o plano free do Upstash conta comando e o
// adapter publica um por broadcast, e nem todo Redis gerenciado libera pub/sub.
// Hoje o Render roda 1 instancia — ligar so gastaria cota sem ganhar nada.
// No dia que escalar: uma variavel de ambiente, sem tocar em codigo.
if (process.env.SOCKET_ADAPTER === 'redis') {
  const pub = redis.duplicate()
  const sub = redis.duplicate()
  io.adapter(createAdapter(pub, sub))
  console.log('[Socket] adapter Redis ligado (varias instancias)')
}

setupSocket(io)
// Rotas que emitem por socket sao routers `const` (nao factory) — io por setter.
attachRealtime(io)

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

app.use(cookieParser())

app.use(reqContext)
app.use(httpMetrics)

app.use('/api/profile', express.json({ limit: '8mb' }))
app.use(express.json({ limit: '1mb' }))
app.use(express.urlencoded({ extended: false, limit: '128kb' }))
app.use(sanitizeInputs)

// /health ANTES do rate-limiter: e um GET de uptime (cron-job.org pinger + o
// checkRedis) sem userId — nao deve pagar rate-limit por IP nem gastar comando.
app.use(healthRouter)

app.use(globalLimiter)

app.use('/api/auth',     authRouter)
app.use('/api/profile',  profileRouter)
app.use('/api/invites',  inviteRouter)

app.use('/i',            invitePreviewRouter)
app.use('/api/servers',  serversRouter)
app.use('/api/servers',  channelsRouter)
app.use('/api/channels/:channelId/messages', createMessagesRouter(io))
app.use('/api/channels/:channelId/polls',    createPollsRouter(io))
app.use('/api',                              createReadsRouter(io))
app.use('/api/dm', createDMRouter(io))
app.use('/api/search', searchRouter)
app.use('/api/discover', discoverRouter)
app.use('/api/upload', uploadRouter)
app.use('/api/push', pushRouter)
app.use('/api/gif',  gifRouter)
app.use('/api/servers', rolesRouter)
app.use('/api/servers', bansRouter)
app.use('/api/servers', serverBadgesRouter)
app.use('/api/users',   userBadgesRouter)
app.use('/api',         notificationsRouter)
app.use('/api/bot',     botCommandsRouter)
app.use('/api/bookmarks', bookmarksRouter)
app.use('/api/reminders', remindersRouter)
app.use('/api/translate', translateRouter)
app.use('/api/friends',   friendsRouter)
app.use('/api/blocks',    blocksRouter)
app.use('/api/voice',     voiceRouter)
app.use('/api/wishes',    wishesRouter)
app.use('/api/xp',        xpRouter)
app.use('/api/sessions',  sessionsRouter)
app.use('/api/servers',   emojisRouter)
app.use('/api',           channelNotifPrefsRouter)

// Arquivos que VIAJAM COM O CODIGO — hoje, as fotos das duas personas do bot.
//
// Diferente de /uploads em tudo que importa: /uploads e disco efemero do Render e
// morre no proximo deploy (por isso o R2 existe); isto esta no repositorio e sobe
// junto com o build, entao nunca some e pode ficar muito tempo em cache.
//
// POR QUE ARQUIVO E NAO data-URI embutido: avatarUrl viaja no `author` de CADA
// mensagem. Em base64 a foto do bot seriam ~125KB repetidos por mensagem; aqui e
// uma URL de 24 caracteres, baixada uma vez e guardada no cache de disco do
// cliente. __dirname resolve pro mesmo lugar em dev (src/) e em producao (dist/).
app.use('/static', express.static(resolve(__dirname, '../public'), {
  maxAge: '7d', fallthrough: true,
  setHeaders: (res) => { res.setHeader('X-Content-Type-Options', 'nosniff') },
}))

app.use('/uploads', express.static(UPLOAD_DIR, {
  maxAge: '1d', immutable: true, fallthrough: true,
  setHeaders: (res, filePath) => {
    // So imagem/video/audio abrem inline; qualquer outro tipo BAIXA (nunca
    // renderiza no dominio da API) -> mata hospedagem de HTML/phishing no /uploads.
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
  // NÃO derruba o processo. Uma promise rejeitada sem catch em qualquer canto
  // (ex.: um comando de cache) não deve tirar a API inteira do ar — foi isso que
  // causou a queda do Upstash capado (um refreshPresence fire-and-forget). Loga +
  // reporta e segue; o request que a originou falha isolado, o servidor sobrevive.
  logger.error('UnhandledRejection', String(r), r)
  sentry.captureException(r)
})
process.on('uncaughtException', (e) => {
  // Exceção SÍNCRONA não capturada = o processo pode estar em estado indefinido,
  // então sair (e deixar o Render reiniciar) é o certo. Diferente de uma promise
  // rejeitada, que é localizada e recuperável.
  logger.error('UncaughtException', String(e), e)
  sentry.captureException(e)
  process.exit(1)
})

httpServer.listen(env.PORT, async () => {
  logger.info('Astra API', `http://localhost:${env.PORT} (${env.NODE_ENV})`)
  await ensureCategorySchema()
  await initBot()
  // Diz QUAL cerebro acordou. "Eu pus a chave no painel" e "a chave chegou no
  // processo" sao coisas diferentes, e sem esta linha a unica forma de saber a
  // diferenca era adivinhar.
  logger.info('Bot', IA_LIGADA ? `Pronto — IA: ${IA_PROVEDOR} (${MODELO_CONVERSA}).` : 'Pronto — IA DESLIGADA: nenhuma chave no ambiente (GROQ_API_KEY?). So os comandos funcionam.')
  startRetentionWorker()
  logger.info('Retention', 'Worker iniciado (1h)')
  startReminderWorker(io)
  logger.info('Reminders', 'Worker iniciado (30s)')
  iniciarRelogioDeCall()
  logger.info('Xp', 'Relogio de call iniciado (1min)')
  initPush()
  void initFcm()
  initMailer()
})
