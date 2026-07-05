# Migrar o backend: Railway → Render + Neon + Upstash (custo zero, sem cartão)

Guia pra tirar a API do Astra da Railway (plano grátis acabando) e colocar em 3
serviços grátis. Nenhum pede cartão de crédito. Feito pra seguir sem experiência
de infra.

> **Segurança:** todos os valores (senhas, secrets, URLs com senha) você copia da
> aba **Variables** da Railway e cola direto no painel do serviço novo. **Nunca
> cole secret no chat.** Se um valor tem `secret`, `password`, `key` no nome, é
> pra ficar só entre você e o painel.

## O mapa (o que vai onde)

| Peça | Hoje (Railway) | Vai pra | Grátis? |
|---|---|---|---|
| Servidor Node (API + realtime) | Railway | **Render** (Web Service) | sim, sem cartão |
| Banco Postgres | Railway | **Neon** | sim, sem cartão |
| Redis (presença/cache) | Railway | **Upstash** | sim, sem cartão |

A ordem importa: cria **Neon** e **Upstash** primeiro (eles geram as URLs que a
API precisa), depois o **Render** apontando pra elas.

---

## Passo 1 — Postgres no Neon

1. Entra em **neon.tech** → cria conta (login com GitHub serve).
2. **Create project** → nome `astra`, região **AWS São Paulo** (`sa-east-1`) pra
   ping baixo.
3. Terminada a criação, abre **Connection string** → copia a URL que começa com
   `postgresql://...`. Ela já vem com `?sslmode=require` no fim — deixa assim.
   - Essa URL é o valor de **`DATABASE_URL`** lá no Render (Passo 3).
4. Não precisa criar tabela nenhuma à mão: quando o Render subir, ele roda as
   migrations sozinho (`npm run db:migrate`) e cria todo o schema.

## Passo 2 — Redis no Upstash

1. Entra em **upstash.com** → cria conta (GitHub serve).
2. **Create Database** (Redis) → nome `astra`, região mais perto do Brasil
   disponível (us-east-1 costuma ser a mais próxima no free).
3. Na página do banco, procura a URL de conexão que começa com **`rediss://`**
   (com dois "s" = TLS). Copia.
   - Esse é o valor de **`REDIS_URL`** no Render.
   - O código já usa `ioredis`, que liga o TLS sozinho quando a URL é `rediss://`
     — não precisa mexer em nada.

## Passo 3 — API no Render

1. Entra em **render.com** → conta com GitHub → autoriza o repo **Xryonz/Astra**.
2. **New +** → **Web Service** → escolhe o repo `Astra`.
3. Configura assim:
   - **Root Directory:** deixa **em branco** (o build precisa da raiz do monorepo).
   - **Runtime/Language:** Node.
   - **Build Command:**
     ```
     npm install && npm run build:api
     ```
   - **Start Command:**
     ```
     npm run db:migrate && npm run start:api
     ```
   - **Instance Type:** **Free**.
   - **Health Check Path:** `/health`
4. Em **Environment** → adiciona a variável `NODE_VERSION` = `20` (garante Node 20).
5. Adiciona o resto das variáveis (Passo 4) e clica **Create Web Service**.

O primeiro deploy demora alguns minutos (instala o monorepo, compila, roda
migrations no Neon). Quando o health-check em `/health` passar, tá no ar. A URL
fica tipo `https://astra-api.onrender.com`.

## Passo 4 — Variáveis de ambiente (no Render)

Abre a aba **Variables** da Railway numa aba, e vai copiando **valor por valor**
pro Render. Divididas em obrigatórias (a API não sobe sem) e opcionais (cada uma
liga um recurso).

**Obrigatórias — sem elas a API não inicia:**
| Chave | De onde vem o valor |
|---|---|
| `DATABASE_URL` | URL do Neon (Passo 1) — **não** a da Railway |
| `REDIS_URL` | URL `rediss://` do Upstash (Passo 2) |
| `JWT_ACCESS_SECRET` | copia da Railway (≥32 chars) |
| `JWT_REFRESH_SECRET` | copia da Railway (≥32 chars) |
| `GOOGLE_CLIENT_ID` | copia da Railway |
| `GOOGLE_CLIENT_SECRET` | copia da Railway |
| `CLIENT_URL` | onde o **site** roda (mesma que está na Railway) |

**Opcionais — cada uma liga uma feature (copia da Railway se existir lá):**
| Chave | Liga |
|---|---|
| `ANTHROPIC_API_KEY` | bot IA + traduzir + resumir canal |
| `GIPHY_API_KEY` | picker de GIF |
| `GMAIL_USER` / `GMAIL_APP_PASSWORD` | verificação de e-mail por código |
| `FIREBASE_SERVICE_ACCOUNT` | push no mobile |
| `LIVEKIT_URL` / `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` | voz/vídeo |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` / `VAPID_SUBJECT` | push no site |
| `API_URL` | põe a URL nova do Render (`https://...onrender.com`) |

> **Google login:** depois que a API estiver no ar, vai no **Google Cloud
> Console** → OAuth → **Authorized redirect URIs** e adiciona a URL nova de
> callback (`https://SUA-API.onrender.com/api/auth/google/callback`). Sem isso o
> "Entrar com Google" quebra — o Google recusa redirect pra URL não cadastrada.

## Passo 5 — Manter acordado (opcional, mas recomendado pra realtime)

O Render free **dorme depois de ~15 min sem acesso**. Ao acordar, a primeira
requisição espera ~1 min e o socket cai nesse meio-tempo (o app reconecta
sozinho, mas trava um pouco). Pra evitar:

1. Entra em **cron-job.org** (grátis, sem cartão) → cria conta.
2. Novo cron job → URL `https://SUA-API.onrender.com/health` → a cada **10 min**.

Isso mantém o servidor vivo. O free do Render dá ~750 horas/mês, que cobre um
serviço ligado o mês inteiro (~730h) — então o pinger cabe no limite.

## Passo 6 — Apontar os apps pra API nova

O endereço do backend muda, então os clientes precisam saber:

- **App nativo (Kotlin):** a URL está **fixa no build**. Troca em dois lugares e
  gera um APK novo:
  - `mobile-native/app/build.gradle.kts` → `BASE_URL` (hoje `umbra-api-production.up.railway.app`)
  - `mobile-native/app/src/main/AndroidManifest.xml` → `android:host` do deep link
  - (me avisa que eu faço essa troca + rebuild quando a API nova estiver no ar)
- **Site (web):** troca a variável de API do Vite no host do site (Vercel/etc.) e
  redeploya.

## Limitações conhecidas do free (pra não te pegar de surpresa)

- **Disco é temporário:** no Render free, arquivos gravados em disco **somem a
  cada restart/deploy**. Avatares/ícones do app nativo vão como *data-uri* (ficam
  no banco, sobrevivem), mas **emojis custom e uploads via site viram arquivo** e
  se perderiam. Se for usar emoji custom pra valer, o certo depois é Cloudflare R2
  (as variáveis `R2_*` já existem no código). Por ora, sem bloqueio.
- **Neon suspende com 5 min ocioso** e acorda na próxima query (~0,5s). Com o
  pinger do Passo 5, isso raramente aparece.

## Migrar os dados (opcional)

As contas/mensagens de teste ficam na Railway. Duas opções:

- **Começar limpo (mais simples):** não faz nada — o Neon nasce vazio e o schema é
  criado no primeiro deploy. Você recria as contas de teste.
- **Levar os dados junto:** `pg_dump` da URL antiga (Railway) → `pg_restore` na
  URL nova (Neon). Dá pra fazer, me chama que eu monto os dois comandos.

---

**Quando terminar:** me manda só a **URL nova do Render** (ex.:
`https://astra-api.onrender.com`) — sem nenhum secret — que eu troco o `BASE_URL`
do app nativo, gero o APK e a gente valida o login apontando pro backend novo.
