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
- [ ] BUG (adiado): durante screenshare, os cards de participantes na call bugam (aparece "1 risco/linha" por usuario). So na web. Achado testando o M6c nativo (que recebe ok). Revisitar depois do nativo.

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
- [x] M5 servers/canais VALIDADO: M5a lista de servidores + canais (GET /api/servers ja traz channels aninhados + _count; criar servidor por nome cria #geral junto; voz desabilitado=M6) · M5b chat de canal (GET/POST /api/channels/:id/messages + join_channel/new_message/message_deleted, dedupe por id, reusa o motor do DM). Enter envia / Shift+Enter quebra linha nos dois chats.
  - Web: liberado "Adicionar membro" por @username pra servidor (nao so grupo) — invite/:username nao exige amizade; opt-out de estranhos = config futura.
- [x] M6 LiveKit Android — CORE NATIVO COMPLETO (voz, video de camera, screenshare). So a fluidez 60fps do screenshare falta confirmar em device fisico (emulador limita). Fatiado, cada slice validado com 2 contas:
  - [x] M6a so audio VALIDADO no device (falar/ouvir + sobrevive background): tocar canal voz -> POST /api/voice/token -> LiveKit.create + room.connect -> setMicrophoneEnabled. Audio remoto toca sozinho (SDK). CallService foreground type=microphone mantem a call em background. Permissao RECORD_AUDIO pedida antes de entrar (FGS exige). io.livekit:livekit-android 2.26.0 + desugaring (java.time em minSdk 24) + jitpack repo. Rota call/{channelId}. events via io.livekit.android.events.collect. Confirma: LIVEKIT_* setado na Railway (token conectou).
  - [x] M6b painel de participantes VALIDADO no device: lista quem esta na sala com avatar+nome real (resolvido via GET /api/servers/:id/members; identity do LiveKit = userId), anel ambar em quem fala (isSpeaking via eventos), icone mic mudo por pessoa, botoes Mutar/Silenciar(deafen)/Sair. Deafen = setVolume 0.0 nas RemoteAudioTrack + corta o proprio mic (estilo Discord). VoiceManager passou a expor participants[] e refaz snapshot em qualquer RoomEvent. serverId passa na rota call/ pra resolver nomes.
  - [x] M6c video de camera VALIDADO no device (publish + receive entre nativo e web): permissao CAMERA (runtime, opcional ao entrar), toggleCamera -> setCameraEnabled. Painel virou grid 2 col de tiles (aspect 1:1): mostra a camera (VideoTrackView) se ligada, senao cai pro avatar; anel ambar em quem fala, nome+mic mudo no rodape. CallParticipant ganhou cameraEnabled+videoTrack (getTrackPublication(CAMERA)). Render via io.livekit:livekit-android-compose-components:2.4.0 (VideoTrackView overload de VideoTrack cru + passedRoom, sem RoomScope). Camera so com a tela aberta (FGS camera fica pro futuro).
  - [~] M6d screenshare (fatiado):
    - [x] M6d.1 RECEBER tela VALIDADO no device (web compartilha -> nativo ve): snapshot pega tambem source SCREEN_SHARE; quem compartilha ganha um tile extra no grid (span 2 col, aspect 16:9, ScaleType.FitInside, label "(tela)").
    - [x] M6d.2a TRANSMITIR tela VALIDADO no device (nativo compartilha -> web ve): botao Tela -> dialogo MediaProjection (createScreenCaptureIntent, StartActivityForResult) -> setScreenShareEnabled(true, ScreenCaptureParams(resultData, notificationId, notification)). O LiveKit roda o PROPRIO foreground service (ScreenCaptureService type=mediaProjection, ja no manifesto da lib — confirmado no merge final). ControlBar virou 2 linhas (Mutar/Surdo + Camera/Tela) + Sair. Encoding ainda no default.
    - [x] M6d.2b tuning 60fps "gamer" — config aplicada + transmite OK (nativo->web). RoomOptions.screenShareTrackPublishDefaults = VideoTrackPublishDefaults(VideoEncoding(8Mbps, 60fps), simulcast=false) no LiveKit.create. VideoEncoding e (maxBitrate, maxFps). FLUIDEZ pendente de DEVICE FISICO: testado no emulador engasga (captura+encode na CPU virtual nao sustenta fps alto — limitacao do emulador, nao do codigo).
      - Levantado p/ ajustar SE engasgar no device real: (1) so setei maxFps de PUBLISH, nao o de CAPTURA -> screenShareTrackCaptureDefaults com maxFps alto; (2) trocar codec do screenshare pra H264 (encoder HW do celular sustenta 60fps melhor que VP8 software). Nao aplicado agora pra nao tunar as cegas no emulador.
- [~] M7 Design editorial-cosmico (paridade visual com o web; user pediu "copiar tudo do Capacitor, otimizar com Compose, nao se conter nas animacoes"). Accent = prata-estelar #d4d8e0 (NAO mais ambar; web migrou Umbra->Astra cosmic). Fatiado:
  - [~] F1 Fundacao ESCRITO+APK OK (falta validar): tokens cosmicos (6 camadas bg void->active, accent prata, 3 niveis texto, semanticos) em Color.kt + AstraColors holder via LocalAstraColors. 4 fontes .ttf em res/font (DM Serif Display, DM Sans VARIABLE c/ FontVariation, DM Mono, Great Vibes) + tipografia editorial no Material3 (serif titulos, sans corpo) -> app inteiro ja herda. Theme mapeia tudo no darkColorScheme.
  - [x] F2 Atmosfera + primitivas ESCRITO+APK OK: StarField.kt (1 Canvas: 70 stars drift global 90s + 14 twinkle seamless via sin(angulo) + 3 meteoros raros 24s) + CosmicBackground wrapper. Editorial.kt (Reveal stagger fade+lift, MarginaliaLabel mono, RomanNumeral serif italic, HairlineRule). Motion.kt (easings EaseSpring/OutSoft/Snappy do web). Splash ja usa CosmicBackground + wordmark Great Vibes 76sp c/ glow.
  - [x] Telas TODAS redesenhadas (1a passada; falta validar no device + ajustes): Splash, Login (hero), Home (hub: cards editoriais I/II + socket chip), DM list / Server list / Channel list (EditorialTopBar compartilhado + AstraAvatar/EmptyState/CosmicSpinner em CommonUi), chat DM+canal (motor Chat.kt compartilhado: MessageBubble com slide-in direcional + overshoot + starlight sweep gated, ChatInputBar pill + botao circular, ChatMessageList; matou a duplicacao DM/Channel), Call (tiles cosmicos, anel prata em quem fala, CallToggle pill com estado por preenchimento). Componentes em ui/components: StarField, CosmicBackground, Editorial (Reveal/Marginalia/Roman/Hairline), EditorialTopBar, CommonUi, Chat. Logica das telas intacta.
- [~] M8 Chat rico (paridade de interacoes; user pediu "replique tudo do Capacitor, acabe tudo primeiro"). Backend de DM e mais magro que o de canal -> DM so recebe o que o server suporta (paridade com o web). Fatiado:
  - [x] M8a editar/apagar: canal edita (PATCH, banner editando, label "editado", socket message_edited) + apaga (DELETE); DM so apaga (sem PATCH no backend). Menu via combinedClickable+DropdownMenu; helpers EditingBanner/DeleteMessageDialog no Chat.kt; canEdit gateia.
  - [x] M8b reacoes (SO canal): long-press -> 6 emojis rapidos; chips FlowRow sob a bolha (borda prata = eu reagi); POST .../react toggle + socket reaction_update; mine via uid em users[]. DM nao tem reacao no backend.
  - [x] M8c responder (canal+DM): menu Responder + ReplyBanner + quote do pai no topo da bolha; replyToId no POST; replyTo ja vinha do GET/socket.
  - [x] M8d digitando (canal+DM): emite typing_start na 1a tecla / stop apos 3s ou no envio; escuta user_typing/dm_user_typing, agrega por userId (stop nao traz username) c/ expiry 6s; TypingIndicator acima do composer.
  - [x] M8f fixar (SO canal): menu Fixar/Desafixar + indicador 📌 + acao 📌 abre dialog de fixadas (GET /pinned); POST/DELETE .../pin + socket message_pinned.
  - [ ] M8e nao-lidas: BLOQUEADO por dado — GET /api/servers (ChannelDto) NAO traz lastMessageAt, entao o dot de canal nao da pra computar no cold-start via REST. Opcoes a decidir com o user: (a) so socket channel_activity (unread ao vivo, some ao reabrir o app); (b) adicionar lastMessageAt no GET /api/servers (toca backend compartilhado); (c) endpoint dedicado de unread. DM tem dado (lastMessage + /reads/dm) mas faltaria createdAt do lastMessage no DTO.
  - TODOS os slices compilam + APK OK; falta validar no device + decidir M8e.
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
- 2026-06-18: M5b chat de canal escrito+compila — GET/POST /api/channels/:id/messages, realtime via SocketManager (new_message/message_deleted, join/leave_channel, re-join no connect), dedupe por id, bolhas mine/other. ChannelChatScreen duplica a UI do DM (extrair pra ui/chat compartilhado fica como cleanup futuro).
- 2026-06-18: M5 100% VALIDADO no device (chat de canal bidirecional com 2 contas). Enter envia / Shift+Enter quebra linha (fix nos 2 chats). Web: "Adicionar membro" por @username liberado pra servidor normal (skill kotlin-specialist instalada local, gitignored).
- 2026-06-18: M6a (voz so audio) ESCRITO + APK assembleDebug OK. LiveKit Android 2.26.0 (WebRTC nativo empacotado: liblkjingle_peerconnection_so.so), core library desugaring ligado, jitpack no settings. VoiceManager (Room unica, join/leave/toggleMic, state), CallService (foreground type=microphone), CallScreen pede RECORD_AUDIO antes de entrar. Canal de voz virou clicavel -> rota call/. Mesmo backend /api/voice/token do web.
- 2026-06-18: M6a 100% VALIDADO no device — voz nativa funcionando (falar/ouvir) e sobrevivendo ao background. LIVEKIT_* confirmado setado na Railway.
- 2026-06-18: M6b painel de participantes VALIDADO no device (2 contas): avatar+nome real (via /members), anel ambar em quem fala, icone mic mudo, deafen funcionando.
- 2026-06-18: M6c video de camera VALIDADO no device (publish+receive nativo<->web). Grid 2col de tiles (camera ou avatar), permissao CAMERA opcional. Achado bug SO-NA-WEB: cards bugam durante screenshare (adiado, ver T1). Foco segue 100% no nativo (depois do M6 -> app desktop nativo). Proximo: M6d (screenshare via MediaProjection).
- 2026-06-18: M6d.1 RECEBER tela VALIDADO no device (web compartilha -> nativo ve tile da tela).
- 2026-06-18: M6d.2a TRANSMITIR tela ESCRITO + APK OK. MediaProjection (createScreenCaptureIntent) -> setScreenShareEnabled(ScreenCaptureParams). LiveKit roda o proprio FGS (ScreenCaptureService type=mediaProjection da lib; confirmado MEDIA_PROJECTION+service no manifesto merged). VALIDADO no device (nativo compartilha -> web ve). Falta so M6d.2b (60fps) pra fechar o M6 -> app desktop nativo.
- 2026-06-18: M6d.2b 60fps config aplicada + transmite OK (nativo->web). Testado no EMULADOR: engasga (captura+encode de tela na CPU virtual nao sustenta fps alto = limitacao do emulador, NAO do codigo). Decidido nao tunar as cegas; deixei levantados os ajustes p/ device real (capture maxFps + codec H264 HW). => M6 = CORE NATIVO COMPLETO (voz, camera, screenshare). So a fluidez 60fps do screenshare fica pendente de 1 teste em celular fisico. Proximo grande marco: app desktop nativo (Compose Multiplatform reaproveita o Kotlin).
