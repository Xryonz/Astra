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

O desktop tem ainda uma quarta peça, que não é um cliente: **`sidecar-voz`**, um
processo em Go que cuida da voz e da transmissão de tela. Ele é lançado pelo
aplicativo e morre com ele.

> **O código não tem comentários, e isso é deliberado.** O *porquê* de cada decisão
> — as medições, as armadilhas, os caminhos tentados que não deram certo — está no
> **histórico de commits**, que é onde ele fica pesquisável por `git log -S` e amarrado
> à mudança que o produziu.

---

## Stack

**Backend** (`apps/api`) — 34 grupos de rota
- Express 4 · TypeScript · Drizzle ORM 0.45
- PostgreSQL (Neon) · Redis (Upstash, presença + cache) · Socket.io (realtime)
- LiveKit — voz e tela dos **três** clientes; o desktop entra na mesma sala por um
  processo à parte (ver `sidecar-voz`)
- Armazenamento S3 ou R2 (anexos, avatares, banners, figurinhas)
- IA da bot: Groq (Gemini como alternativa; `IA_PROVIDER` desempata)

**Desktop** (`mobile-native/desktopApp`) — a fase ativa
- Kotlin 2.3 · Compose Multiplatform 1.11 · janela sem moldura (título próprio)
- **Koin** (DI) · Retrofit/OkHttp · kotlinx.serialization · Coroutines/Flow
- Coil3 · socket.io-client · JNA (atalhos globais, foco de janela, identidade na barra
  de tarefas, lista de placas) · RikkaUI · Haze · Lucide
- Aurora em shader SkSL · campo de estrelas em Canvas · auto-update por zip-swap

**Voz e tela do desktop** (`sidecar-voz`) — um processo à parte, em Go
- **pion/webrtc** por baixo e **livekit/server-sdk-go** por cima: o sidecar entra na
  sala do LiveKit como um participante e publica lá, igual aos outros clientes. O SFU
  encaminha — não há malha ponto a ponto nem travessia de NAT por conta própria
- Captura de tela inteira por **DXGI Desktop Duplication** e de **uma janela** por
  **Windows.Graphics.Capture**; compressão H.264 pelo **Media Foundation**, na placa
  quando há uma e em software quando não há. Baixa latência, taxa variável com pico
  limitado e CABAC quando o compressor aceita
- A tela pode subir em **duas qualidades** (a cheia e uma pela metade a 30 fps), para
  que quem está com a rede curta receba a menor em vez de derrubar a de todo mundo.
  Exige placa com aceleração e **começa desligado** — Configurações › Voz
- A banda se ajusta sozinha pelo que o TWCC e os relatórios de recepção contam, e o
  pedido de quadro-chave de quem acabou de chegar é atendido na hora
- Áudio em Opus a 64 kbps em banda cheia, com cancelamento de eco, supressão de ruído e
  ganho do Windows. Pacote perdido é reconstruído a partir da redundância que o próprio
  Opus embute em cada pacote, e o colchão contra engasgo se dimensiona pela rede — em
  rede limpa ele é zero, e só cresce para quem teve engasgo comprovado
- Fala com o app por **entrada e saída padrão** (uma linha de JSON por mensagem) e
  entrega os quadros por um cano TCP separado na volta local
- Processo separado de propósito: interoperar com COM/Media Foundation dentro da JVM
  significaria superfície nativa no processo do aplicativo, e uma falha ali derruba a
  janela inteira. Aqui, o pior caso é a chamada cair — o Astra continua de pé

**Android** (`mobile-native/app`)
- Kotlin 2.3 · Jetpack Compose · Material3 (minSdk 24 · compileSdk 36)
- Hilt (DI/KSP) · Room (cache offline-first) · DataStore · Baseline Profile
- LiveKit Android · FCM (push)

**Web** (`apps/web`, congelado)
- React 19 · Vite 8 · TypeScript · Tailwind v4 · shadcn/ui · motion/react
- Zustand · React Query 5 · React Router 6

**Monorepo:** npm workspaces · `packages/types` (Zod compartilhado). O projeto
nativo é um build Gradle à parte em `mobile-native/`, e `sidecar-voz/` é um módulo
Go independente — o Gradle do desktop o compila e empacota junto do aplicativo.

> **`:shared` não é KMP.** Apesar do nome, ele tem só o source set `main`, é
> Kotlin/JVM puro e carrega apenas a camada de rede (`core/network`) e os DTOs.
> **A UI não é compartilhada** entre Android e desktop — cada um tem as próprias
> telas. Paridade entre eles é reescrita, não reuso; planejar como se fosse reuso
> já custou tempo aqui.

**Hospedagem:** web → Vercel · API → Render (US East) · Postgres → Neon ·
Redis → Upstash · arquivos → bucket S3 · voz e tela dos três clientes → LiveKit Cloud.

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
Volume de entrada e de saída, volume por pessoa no botão-direito do cartão, e um
sinal de três barras no rodapé que mostra ida e volta, tremor e perda da chamada.

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
duas), 19 cores de acento, três níveis de gráficos, modo de reduzir movimento.

Quando algo dá errado, o app deixa laudo em `%LOCALAPPDATA%\Astra`:
`arranque.txt` marca por onde o arranque passou e em quanto tempo — onde a lista
para é onde o app parou; `diagnostico.txt` traz placa, motor de desenho e versão;
`falhas.txt` guarda o que estourou; `voz.txt` recebe o relato do sidecar.

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

# Imagens (rodar de dentro de apps/api)
npm run img:diag              # só LÊ: conta o estado de cada imagem do banco
npm run img:encolher          # SIMULA o backfill da versão de exibição
npm run img:encolher -- --vai # executa de verdade
```

---

## Variáveis opcionais

A API sobe com qualquer uma destas vazia — a funcionalidade fica desligada em
fallback, e não quebra o boot:

- `LIVEKIT_*` — sem isso, voz/vídeo off nos três clientes
- `GROQ_API_KEY` / `GEMINI_API_KEY` — sem nenhuma das duas, a bot fica off
- `S3_*` / `R2_*` — em desenvolvimento, o upload cai pro disco local
  (`storageMode = local`). **Em produção ele é RECUSADO** com 503: o disco do
  servidor é efêmero, e gravar ali produziria URLs que morrem no próximo deploy,
  deixando a imagem quebrada no banco para sempre. O log diz quais variáveis faltam
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

A licença dos companheiros viaja junto da arte, em
`mobile-native/desktopApp/src/main/resources/pet/`. A geometria de recorte de cada
folha está documentada no `enum Bicho`, em `ui/GatoDoAstra.kt`.

### Tipografia

| Fonte | Autor | Licença |
| --- | --- | --- |
| Cormorant | Christian Thalmann ([Catharsis Fonts](https://github.com/CatharsisFonts/Cormorant)) | SIL Open Font License 1.1 |
| DM Sans · DM Mono | Colophon Foundry / Jonny Pinhorn | SIL Open Font License 1.1 |
| Cinzel (letreiro "Astra") | Natanael Gama ([NDISCOVER](https://github.com/NDISCOVER/Cinzel)) | SIL Open Font License 1.1 |
| Great Vibes | Robert Leuschke | SIL Open Font License 1.1 |

O texto de cada licença viaja junto do arquivo da fonte, em
`mobile-native/desktopApp/src/main/resources/font/LICENCA-*.txt`. **Não é
formalidade:** a SIL OFL exige que a licença acompanhe o *Font Software* sempre que
ele for redistribuído, e o Astra é um zip público — a linha nesta tabela credita, mas
quem cumpre a licença é o arquivo ao lado da fonte.

### Ícones

[Lucide](https://lucide.dev) — ISC License.

### Dados

O catálogo que traduz nome de executável em título de jogo
(`mobile-native/desktopApp/src/main/resources/jogos.tsv`) é destilado da lista de
aplicações detectáveis que o **Discord** publica em
`discord.com/api/v9/applications/detectable`.

Ser honesto sobre o que isso é: um endpoint **público mas não documentado**, sem
licença declarada. O que o Astra guarda dele é a parte factual — qual executável
pertence a qual jogo — e nada mais: nem ícone, nem identificador, nem descrição. Os
títulos são marcas dos respectivos donos e aparecem só para dizer o que a pessoa está
jogando.

O instantâneo tem data no cabeçalho do arquivo e se refaz com
`node tools/atualizar-catalogo-de-jogos.mjs`. Se a fonte sair do ar, o Astra continua
funcionando: o catálogo é a primeira tentativa, e a descrição do próprio executável
segue como resposta.

### Bibliotecas nativas

| Biblioteca | Para quê | Licença |
| --- | --- | --- |
| [Opus](https://opus-codec.org) (`opus-0.dll`) | codec de voz das chamadas | BSD 3-Clause |
| [Pion WebRTC](https://github.com/pion/webrtc) | transporte de mídia das chamadas | MIT |
| [LiveKit server-sdk-go](https://github.com/livekit/server-sdk-go) | entrar na sala e publicar voz e tela | Apache 2.0 |
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
- **Refresh token no `localStorage`** do web — o único item de segurança que segue
  aberto, e só lá: o `apps/web` está congelado. Desktop e Android guardam a sessão
  fora do alcance de script de página.
