# Migrar os dados: Railway Postgres → Neon

**Por quê:** o site apontava pro backend antigo (Railway) e o app pro novo (Render+Neon).
Resultado: dois bancos separados. O histórico antigo (contas, constelações, mensagens
de meses) vive no **Railway**; o Neon tem só o que foi criado pelo app desde 2026-07-05.

> ⚠️ **Faça isso LOGO.** O plano grátis da Railway está acabando — quando o banco
> de lá hibernar/morrer, o dump não sai mais.

> 🔴 **Isso SUBSTITUI o banco do Neon.** Tudo que existe hoje no Neon (contas e
> constelações criadas pelo app nos últimos dias) é apagado e vira uma cópia do
> Railway. Não existe "misturar" os dois bancos automaticamente — os IDs conflitam.
> Se tiver algo recente no Neon que você ama, anota/recria depois.

---

## Passo 0 — Instalar as ferramentas do Postgres (uma vez)

No PowerShell (como usuário normal):

```powershell
winget install PostgreSQL.PostgreSQL.17
```

Fecha e abre o PowerShell de novo. Testa: `pg_dump --version` (se não achar, o
caminho é `C:\Program Files\PostgreSQL\17\bin\pg_dump.exe` — usa o caminho completo).

## Passo 1 — Pegar as duas connection strings

- **Railway:** painel → teu projeto → Postgres → aba **Connect** → `DATABASE_URL`
  (a **pública**, que começa com `postgresql://` e tem `proxy.rlwy.net` ou similar —
  NÃO a `.railway.internal`).
- **Neon:** console.neon.tech → teu projeto → **Connection string** (com `sslmode=require`).

> 🔒 Essas URLs têm senha. **Não cola em chat nenhum** — só no teu PowerShell.

## Passo 2 — Dump do Railway (backup local)

```powershell
pg_dump --no-owner --no-privileges -Fc "COLA_AQUI_A_URL_DO_RAILWAY" -f "$HOME\astra-railway.dump"
```

Se terminar sem erro, você tem o backup em `C:\Users\<voce>\astra-railway.dump`.
**Guarda esse arquivo** — é o teu seguro de vida dos dados.

## Passo 3 — Restaurar no Neon (o passo destrutivo)

```powershell
pg_restore --no-owner --no-privileges --clean --if-exists -d "COLA_AQUI_A_URL_DO_NEON" "$HOME\astra-railway.dump"
```

- `--clean --if-exists`: apaga as tabelas do Neon e recria com os dados do Railway.
- Warnings de "does not exist, skipping" são normais. Erro de verdade = para e me chama.

## Passo 4 — Deixar o boot completar o schema

O backend no Render roda `ensureSchema` no boot, que cria colunas/tabelas que o
Railway não tinha (ex: ChannelCategory / Channel.position). Então depois do restore:

Render → teu serviço → **Manual Deploy → Deploy latest commit** (ou só espera o
próximo request acordar o serviço). Isso garante o schema 100%.

## Passo 5 — Conferir

- Abre o app → loga com a tua conta ANTIGA (a do site) → as constelações antigas
  devem estar lá.
- Site (já apontando pro Render) → mesmas constelações.

## Se algo der errado

O Neon tem **branches/restore point-in-time** no painel (Restore) — dá pra voltar
o banco pra antes do restore. E você ainda tem o `astra-railway.dump` local.
