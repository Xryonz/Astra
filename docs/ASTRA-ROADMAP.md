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
- [x] M3 slice Auth: login (POST api/auth/login) → Clean Arch (dto/api → repo → usecase → VM → Compose), TokenStore persiste sessao, NavHost gate login/home, TokenAuthenticator faz refresh transparente no 401 (RefreshApi em client pelado p/ evitar loop). Compila+KSP/Hilt OK. Login real VALIDADO no emulador (sessao ativa).
- [x] M4 WebSocket+DMs VALIDADO (realtime bidirecional no device): M4a SocketManager [chip Online] · M4b lista de DMs · M4c chat DM (historico + join_dm/new_dm + enviar + dedupe por id + abrir por @username + bolhas mine/other + userId no login)
  - Socket robusto: (1) auto-recupera token expirado no connect_error de auth (refresca via RefreshApi, cooldown 10s, deixa a auto-reconexao da lib usar o token novo); (2) re-join das conversas abertas no EVENT_CONNECT (rooms sao server-side, somem na queda — era o motivo de nao receber new_dm apos cair). activeRooms rastreado em join/leave.
- [ ] M5 servers/canais (fatiado): M5a lista de servidores + canais (GET /api/servers ja traz channels aninhados + _count; criar servidor por nome POST /api/servers cria #geral junto; canais de voz visiveis mas desabilitados=M6; nav Home->Servidores->Canais) [VALIDADO] · M5b chat de canal (GET/POST /api/channels/:id/messages + join_channel/new_message/message_deleted no SocketManager, dedupe por id, reusa o motor do DM) [escrito+compila, validar runtime]
- [ ] M6 LiveKit Android
- AUTH/Google (nativo, futuro): Google so LOGA conta existente, NUNCA cria conta — ja e politica de seguranca do backend (passport.ts: email nao-registrado e bloqueado com code email_not_registered). No nativo, ao adicionar "Entrar com Google", tratar o deep link astra://login?error=google_email_unregistered -> mandar pro registro. Nao ha nada a mudar no backend.
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
- 2026-06-17: mobile-native sync OK no Android Studio (tela Astra roda no emulador) = M1+M2 validados. Wrapper Gradle 8.11.1 copiado do projeto Capacitor. M3 (login nativo) escrito + compila (Navigation Compose escolhido p/ o gate de telas).
- 2026-06-18: M3 100% VALIDADO no emulador — login, credencial errada, persistencia (DataStore sobrevive fechar/reabrir), logout. Politica Google anotada.
- 2026-06-18: M4a SocketManager (socket.io-client 2.1.0, heartbeat 30s, re-auth no reconnect) VALIDADO runtime (chip Online no emulador).
- 2026-06-18: M4b lista de DMs VALIDADO (lista vazia OK pra conta nova). Bug socket offline (token expira no handshake) corrigido: refresh+reconnect automatico, chip volta Online sozinho.
- 2026-06-18: M4c chat DM escrito+compila — abrir conversa por @username (POST /api/dm/open), historico (GET messages), realtime via SocketManager (new_dm/dm_deleted como SharedFlow, join/leave), enviar (POST), dedupe por id, bolhas mine/other (userId persistido no DataStore no login).
- 2026-06-18: M4 100% VALIDADO no device — realtime bidirecional (mandar/receber ao vivo entre nativo e web). Fixados 2 buracos: socket caia por token expirado e nao recebia new_dm apos reconectar (faltava re-join das rooms). M4 inteiro fechado.
- 2026-06-18: M5a servidores + canais VALIDADO (criar servidor, ver #geral, navegar).
- 2026-06-18: M5b chat de canal escrito+compila — GET/POST /api/channels/:id/messages, realtime via SocketManager (new_message/message_deleted, join/leave_channel, re-join no connect), dedupe por id, bolhas mine/other. ChannelChatScreen duplica a UI do DM (extrair pra ui/chat compartilhado fica como cleanup futuro). Falta validar runtime.
