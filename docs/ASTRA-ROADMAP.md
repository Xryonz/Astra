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

## T2 — Native Kotlin  /mobile-native  [LONGO PRAZO]
- Clean Arch MVVM: data / domain / presentation (Composables + ViewModels)
- Rede: Retrofit + kotlinx.serialization + WebSocket · DI: Hilt · async: Coroutines · img: Coil
- Consome o backend existente
- Desktop futuro: Compose Multiplatform

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
