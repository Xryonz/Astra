# Astra

Plataforma de mensagens em tempo real — editorial-dark, anti-Discord. Constelações
(servidores), órbitas (canais), sussurros (DMs), voz e vídeo, cargos, reações,
enquetes, busca, notificações, XP e missões.

**Três clientes sobre uma API só:**

| Cliente | Onde | Stack | Estado |
|---|---|---|---|
| **Desktop** | `mobile-native/desktopApp` | Kotlin · Compose Multiplatform/JVM | **fase ativa** |
| **Android** | `mobile-native/app` | Kotlin · Jetpack Compose | em produção, atrás do desktop |
| **Web** | `apps/web` | React 19 · Vite | congelado, serve de referência de paridade |

O desktop é onde o trabalho acontece hoje. O web ficou congelado depois de servir
de mapa: o que ele já resolvia virou o alvo de paridade dos clientes nativos.

---

## Stack

**Backend** (`apps/api`) — 35 grupos de rota
- Express 4 · TypeScript · Drizzle ORM 0.45
- PostgreSQL (Neon) · Redis (Upstash, presença + cache) · Socket.io (realtime)
- LiveKit (voz/vídeo) · armazenamento S3 ou R2 (anexos, avatares, banners, figurinhas)
- IA da bot: Groq (Gemini como alternativa; `IA_PROVIDER` desempata)

**Desktop** (`mobile-native/desktopApp`) — a fase ativa
- Kotlin 2.3 · Compose Multiplatform 1.11 · janela sem moldura (título próprio)
- **Koin** (DI) · Retrofit/OkHttp · kotlinx.serialization · Coroutines/Flow
- **webrtc-java** — voz e transmissão de tela nativas, sem LiveKit no desktop
- Coil3 · socket.io-client · JNA (bandeja, atalho, prioridade) · RikkaUI · Haze · Lucide
- Aurora em shader SkSL · campo de estrelas em Canvas · auto-update por zip-swap

**Android** (`mobile-native/app`)
- Kotlin 2.3 · Jetpack Compose · Material3 (minSdk 24 · compileSdk 36)
- Hilt (DI/KSP) · Room (cache offline-first) · DataStore · Baseline Profile
- LiveKit Android · FCM (push)

**Web** (`apps/web`, congelado)
- React 19 · Vite 8 · TypeScript · Tailwind v4 · shadcn/ui · motion/react
- Zustand · React Query 5 · React Router 6

**Monorepo:** npm workspaces · `packages/types` (Zod compartilhado). O projeto
nativo é um build Gradle à parte em `mobile-native/`.

> **`:shared` não é KMP.** Apesar do nome, ele tem só o source set `main`, é
> Kotlin/JVM puro e carrega apenas a camada de rede (`core/network`) e os DTOs.
> **A UI não é compartilhada** entre Android e desktop — cada um tem as próprias
> telas. Paridade entre eles é reescrita, não reuso; planejar como se fosse reuso
> já custou tempo aqui.

**Hospedagem:** web → Vercel · API → Render (US East) · Postgres → Neon ·
Redis → Upstash · voz → LiveKit Cloud · arquivos → bucket S3.

---

## O que o app faz

**Conversa**
Órbitas de texto e de voz, categorias, sussurros (DM), grupos, respostas, edição,
menções com `@` e autocomplete, reações, enquetes, GIFs, figurinhas por
constelação, emojis, anexos com prévia e lightbox, marcadores, busca global,
histórico de destinos, tradução, fixar mensagem e menus de botão-direito em tudo.

**Voz e vídeo**
Sala de voz por órbita com antessala, chamada de voz e vídeo dentro do sussurro
com registro no histórico ("Chamada perdida", "Chamada de 12 min"), transmissão
de tela, painel flutuante da call ao navegar, soundboard por constelação.

**Pessoas**
Amigos com pedidos nos dois sentidos, presença ao vivo (online/ausente/ocupado/
invisível), bloqueio, perfil com avatar, banner, pronomes, bio, recado, cor e
fonte próprias, recorte de imagem embutido, cartão de perfil completo, cargos com
cores/hierarquia/permissões, banimentos, convites e prévia de convite.

**Descoberta e chegada**
Descobrir constelações públicas, entrada por convite, onboarding com checklist de
primeiros passos, constelação nova nasce povoada.

**Sinal**
Notificações com painel, badge, marcar tudo como lido e limpar histórico,
silenciar por canal e por constelação, não-lidos com contagem, push (Android).

**Progressão**
XP por mensagem e por tempo em call, níveis com anel em volta da foto, missões
com aviso, distintivos.

**Casa da máquina (desktop)**
Auto-update por zip-swap com verificação SHA-256, bandeja do sistema, atalho no
menu iniciar, paleta de comandos (`Ctrl+K`), diagnóstico de rede e permissões,
configurações com prévia ao vivo por aba, aparência (aurora, estrelas, ou as
duas), 18 cores de acento, três níveis de gráficos, modo de reduzir movimento.

---

## Acessibilidade

Não é item de backlog aqui — é regra de aceite pra tela nova:

- Todo botão só-ícone tem nome pro leitor de tela.
- Foco de teclado é sempre visível, e o anel só aparece na navegação por teclado
  (o equivalente ao `:focus-visible` da web) — clicar com o mouse não desenha anel.
- Alvo de clique mínimo de 24dp; o padrão do app é 26–34dp.
- Toda animação respeita "reduzir movimento". O app cobre **WCAG 2.3.3 (AAA)**.
- Contraste mira **AA, não AAA**: passar do ponto produz halação, que cansa em
  sessão longa à noite — que é o uso real.
- Windows: o leitor de tela enxerga via Java Access Bridge, então o módulo
  `jdk.accessibility` precisa continuar no empacotamento.

---

## Setup local

Requer Node 20+, PostgreSQL e Redis. Pro desktop, JDK 21.

```bash
# 1. Instalar deps
npm install

# 2. Configurar envs (copia .example, preenche)
cp apps/api/.env.example apps/api/.env
cp apps/web/.env.example apps/web/.env

# 3. Migrar DB
npm run db:migrate

# 4. Dev (front + api juntos)
npm run dev
```

Front em `http://localhost:5173` · API em `http://localhost:3001`.

**Desktop:**

```bash
cd mobile-native
./gradlew :desktopApp:run                  # roda
./gradlew :desktopApp:compileKotlin        # só compila (checagem rápida)
./gradlew :desktopApp:zipDistributable     # zip distribuível
```

**Android:**

```bash
cd mobile-native
./gradlew :app:assembleDebug
```

---

## Deploy

### Web → Vercel

1. New Project → Import repo
2. Root Directory: deixe na raiz (`vercel.json` na raiz cuida)
3. Environment Variables:
   - `VITE_API_URL` = URL pública da API no Render (sem barra final)
   - `VITE_SENTRY_DSN` (opcional)
4. Deploy

`vercel.json` já configura: `npm run build:web` → `apps/web/dist` + SPA rewrites +
cache headers pra assets.

### API → Render (+ Neon + Upstash)

Postgres e Redis são serviços externos, não add-ons do Render.

1. Neon → cria um Postgres, copia a connection string → `DATABASE_URL`
2. Upstash → cria um Redis, copia a URL (TLS `rediss://`) → `REDIS_URL`
3. Render → New Web Service → conecta o repo
   - Build Command: `npm run build:api`
   - Start Command: `npm run start:api`
   - Auto-Deploy: **On** (senão os pushes não sobem sozinhos — isso já custou
     uma caçada a um bug que estava corrigido no código e não no ar)
4. Environment Variables (lista completa em `apps/api/.env.example`):
   - `DATABASE_URL` (Neon) · `REDIS_URL` (Upstash)
   - `JWT_ACCESS_SECRET` + `JWT_REFRESH_SECRET`
     (gere com `node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"`)
   - `GOOGLE_CLIENT_ID` + `GOOGLE_CLIENT_SECRET`
   - `CLIENT_URL` = URL do web na Vercel (sem barra final)
   - `API_URL` = URL pública desta API (sem barra final)

Sem passo de migration: o schema é garantido no boot por `ensureSchema`
(DDL idempotente).

O plano free dorme após ~15min sem tráfego — mantenha vivo com um pinger externo
(ex: cron-job.org) batendo em **`/live`**.

> Aponte o pinger pro `/live`, **não** pro `/health`. O `/health` consulta Postgres
> e Redis a cada chamada: pingado de minuto em minuto ele impede o Neon de
> autossuspender e queima a cota de compute do plano free (erro `53000: exceeded
> the compute time quota`, que derruba o deploy). `/live` só responde uptime —
> segura o Render acordado sem tocar no banco.

### Desktop → GitHub Releases

Publicar é **só subir a versão**. Não há tag pra criar nem zip pra arrastar:

1. `mobile-native/desktopApp/build.gradle.kts` → `astraVersion = "x.y.z"`
2. commit + push na `main`

O workflow `desktop-release.yml` monta o zip, calcula o SHA-256, cria a tag
`desktop-v<versão>` e publica o asset `Astra-<versão>-win-x64.zip`. O app procura
sozinho, confere o hash e troca os arquivos na próxima abertura.

> O app minimiza pra bandeja em vez de fechar. Pra testar um build novo, use
> **Sair** na bandeja e reabra — fechar a janela não encerra o processo.

### Pós-deploy

1. `CLIENT_URL` na API = URL da Vercel
2. `VITE_API_URL` na Vercel = URL do Render
3. Google Console: adicionar `https://<vercel-url>/auth/callback` em
   "Authorized redirect URIs"

---

## Scripts úteis

```bash
npm run dev          # web + api juntos (com predev hook)
npm run dev:fast     # mesmo, sem predev (skip migrate + port check)
npm run build        # build types + api + web
npm run build:api    # só API
npm run build:web    # só web
npm run test:e2e     # playwright smoke + mobile
npm run db:migrate   # migrations Drizzle

# API workspace
npm test -w apps/api          # vitest
npm run db:studio -w apps/api # Drizzle Studio
```

---

## Variáveis opcionais

A API sobe com qualquer uma destas vazia — a funcionalidade fica desligada em
fallback, e não quebra o boot:

- `LIVEKIT_*` — sem isso, voz/vídeo off no web e no Android
- `GROQ_API_KEY` / `GEMINI_API_KEY` — sem nenhuma das duas, a bot fica off
- `S3_*` / `R2_*` — sem isso, o upload cai pro disco local (`storageMode = local`)
- `BREVO_API_KEY` + `MAIL_FROM` — sem isso, e-mail off
- `GIPHY_API_KEY` — sem isso, o seletor de GIF some
- `VAPID_*` — sem isso, push off
- `SENTRY_DSN` — sem isso, sem error tracking
- `METRICS_TOKEN` — sem isso, `/metrics` responde 404 em produção

---

## Design

Editorial-dark "obsidiana", dark-only por escolha. O acento de fábrica é
**branco** (`#D4D8E0`, preset "Obsidiana") — âmbar é uma opção entre 18, não o
padrão. O fundo padrão é liso; aurora e estrelas são escolha em
*Aparência › Fundo*, e dá pra ligar as duas.

Três regras carregam o resto:

1. **Cartão dentro de cartão.** Conteúdo se separa por aninhamento de superfície,
   não por linha divisória. Traço de borda a borda lê como grade de tabela; quando
   um separador é mesmo necessário, ele é curto e centralizado.
2. **Hierarquia por elevação, não por cor.** A rampa `void → base → raised →
   overlay → hover → active` é a ferramenta principal de "isto importa mais". Cor
   entra por último e em pouca área — o acento nunca vira fundo.
3. **Movimento com orçamento.** Movimento é sinal, não enfeite: gasta-se nos
   eventos que importam (mensagem nova, alguém entrando na call, entrada de tela)
   e mantém-se hover e repouso quietos. App que pisca por tudo ensina a ignorar o
   piscar.

Tipografia: Cinzel (letreiro) + Cormorant (títulos) + DM Sans + DM Mono + Great Vibes.
Paleta completa de tokens do web em `apps/web/src/index.css`; a do desktop em
`mobile-native/desktopApp/…/ui/theme/`.

---

## Créditos

Nem tudo que dá cara ao Astra foi feito aqui. O que veio de fora está nomeado
abaixo, com o autor e a origem.

**Regra da casa:** todo asset de terceiro entra junto do texto da licença dele *e*
de uma linha nesta seção, no mesmo commit. Várias dessas licenças pedem atribuição,
e mesmo as que não pedem merecem — a arte é de quem a desenhou.

### Arte

| O quê | Autor | Origem |
| --- | --- | --- |
| Gato do companheiro — "Simples" | **Elthen** (ELV Games) | [2D Pixel Art Cat Sprites](https://elthen.itch.io/2d-pixel-art-cat-sprites) |
| Gato do companheiro — "Travesso" | **Jump Button** ([@Jump_Button](https://twitter.com/Jump_Button)) | Cat Player — crédito exigido em uso comercial |
| Companheiro — "Sátiro" | *autor a confirmar* | pacote sem arquivo de licença; uso liberado pelo autor ao dono do Astra |

A licença viaja junto da arte, em
`mobile-native/desktopApp/src/main/resources/pet/`. A geometria de recorte de cada
folha está documentada no `enum Bicho`, em `ui/GatoDoAstra.kt`.

### Tipografia

| Fonte | Autor | Licença |
| --- | --- | --- |
| Cormorant | Christian Thalmann ([Catharsis Fonts](https://github.com/CatharsisFonts/Cormorant)) | SIL Open Font License 1.1 |
| DM Sans · DM Mono | Colophon Foundry / Jonny Pinhorn | SIL Open Font License 1.1 |
| Cinzel (letreiro "Astra") | Natanael Gama ([NDISCOVER](https://github.com/NDISCOVER/Cinzel)) | SIL Open Font License 1.1 |
| Great Vibes | Robert Leuschke | SIL Open Font License 1.1 |

### Ícones

[Lucide](https://lucide.dev) — ISC License.

### Bibliotecas nativas

| Biblioteca | Para quê | Licença |
| --- | --- | --- |
| [Opus](https://opus-codec.org) (`opus-0.dll`) | codec de voz das chamadas | BSD 3-Clause |
| [Pion WebRTC](https://github.com/pion/webrtc) | transporte das chamadas ponto a ponto | MIT |
| [MP3SPI](https://github.com/pdudits/soundlibs) + [JLayer](http://www.javazoom.net/javalayer/javalayer.html) | leitura de MP3 nos sons da soundboard | LGPL 2.1+ |
| [VorbisSPI](https://github.com/pdudits/soundlibs) + [JOrbis](https://www.jcraft.com/jorbis/) | leitura de OGG nos sons da soundboard | LGPL 2.1+ |
| [Tritonus](https://www.tritonus.org) (`tritonus-share`) | base comum dos dois provedores acima | LGPL 2.1+ |
| [GStreamer](https://gstreamer.freedesktop.org) | codificação de vídeo (baixado sob demanda) | LGPL 2.1+ |

---

## Adiante

- **Paridade do Android** com o desktop — hoje o desktop está na frente.
- **Bot mascote** com persona celeste, anunciando entrada e saída, e respondendo
  também no sussurro.
- **XP com recompensas** — a mecânica já grava; falta o que ela destrava.
- **Segurança do upload e do refresh token** — ver o backlog abaixo.
- **Backlog de segurança** auditado e ainda não corrigido (escalação de cargos,
  refresh token no localStorage, rate limiter fail-open, upload).
