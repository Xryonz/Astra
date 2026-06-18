# Astra — Roadmap (compacto)

Foco: desempenho maximo · engenharia de ponta · custo zero.
Produto que SHIPA agora = hibrido React/Capacitor (grupo de testers).
Native Kotlin = track paralelo de longo prazo (nao bloqueia o ship).
Regra: nunca pular etapas; perguntar a cada passo como progredir.

## T1 — Hibrido React/Capacitor  [SHIPA AGORA]
- [ ] LiveKit 60fps screenshare: maxFramerate 60, maxBitrate↑, simulcast OFF na track de tela, codec H264/VP8 c/ HW accel (ScreenShareCaptureOptions + PublishOptions)
- [ ] Native: camera (@capacitor/camera) — avatar/anexo
- [ ] Native: receber compartilhamento (Android intent filter → manda em DM)
- [ ] Native: localizacao na mensagem (@capacitor/geolocation)
- [ ] Anim #2: reduce-motion (2o toggle da aba Acessibilidade)
- [ ] Anim #5: LazyMotion (corta KB do bundle motion)
- [ ] Anim #6: virtualizar MessageList (@tanstack/react-virtual)
- [ ] Perf refactor: memo + selectors finos + code-split (NAO "so dividir componente")
- [ ] Build: 1 APK dev (debug) + 1 APK prod (release ASSINADO p/ distribuir)

## T2 — Native Kotlin  /mobile-native  [EM ANDAMENTO — decisao: encerrar Capacitor, migrar p/ Kotlin]
- Clean Arch MVVM: data / domain / presentation (Composables + ViewModels)
- Rede: Retrofit + kotlinx.serialization + WebSocket · DI: Hilt · async: Coroutines · img: Coil
- Consome o backend existente. Capacitor fica CONGELADO como shippable ate paridade.
- Desktop futuro: Compose Multiplatform
- Toolchain casado c/ Capacitor: Gradle 8.11.1 · AGP 8.7.2 · compileSdk 35 · JDK 17 · Kotlin 2.0.21
- [x] M1 fundacao (Gradle+catalog, Compose, Hilt, tema Astra dark/amber, app roda vazio)
- [x] M2 rede (Retrofit+OkHttp+AuthInterceptor+TokenStore DataStore, modulos Hilt). BASE_URL = umbra-api-production.up.railway.app (mesma do Vite)
- [ ] M3 slice Auth (login → /api/auth, ViewModel, tela Compose, Authenticator p/ refresh no 401)
- [ ] M4 WebSocket+DMs · M5 servers/canais/msgs · M6 LiveKit Android
- DECISAO: Capacitor CONGELADO ja; camera/share/localizacao/animacoes serao nativos no Kotlin (nao no Capacitor)

## T3 — Dados/Infra  [QUANDO O VOLUME PEDIR]
- Postgres (transacional) · Redis (presenca/sessao) · TimescaleDB (historico msgs, particao temporal por channel_id, created_at)
- Free tier: Render/Zeabur (API) · Supabase (PG/Timescale) · Upstash (Redis)
- Agora: tabela messages "partition-ready"; migrar p/ Timescale so sob volume

## Correcoes de mentor (voce pediu "me corrija")
- "Dividir componente" =/= mais rapido. Perf real = memoizacao + virtualizacao + code-split + menos re-render + bundle menor. Dividir so ajuda se cria fronteira de memo.
- Native Kotlin duplica o app inteiro: otimo p/ aprender, pesado como produto solo. Hibrido shipa; Kotlin e paralelo.
- TimescaleDB e prematuro com poucos usuarios; PG indexado aguenta milhoes de linhas. Projetar partition-ready agora, migrar depois.

## Skills sempre on
caveman (think) · primeval-zen (output) · karpathy (code) · vercel-react · shadcn

## Log de progresso
- 2026-06-17: aba Acessibilidade+vibracao, DMs flat-rows, densidade canais, keystore p/ sessao, fundo void p/ gap teclado. Repo renomeado Umbra→Astra.
