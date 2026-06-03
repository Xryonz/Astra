# Umbra — Plano de Repaginada (Overhaul)

**Data:** 2026-06-02
**Escopo:** Frontend + API + Infra
**Sequenciamento:** Segurança → Performance → Manutenção
**Nível de cleanup:** Reestruturar (mantém todas as features)
**Status:** Aguardando aprovação do user

---

## 1. Resumo executivo

A base do projeto é **mais madura do que aparenta**. A maioria das práticas
de segurança e performance está implementada. O problema central não é
qualidade de código — é **organização e cleanup pós-features sprint-1df4641**
(que adicionou pronouns, fonts, status emoji, banner border, guestbook, etc).

Os bugs que enfrentamos no ProfileCard nas últimas horas são **sintomáticos**:
- Animações que não funcionam silenciosamente (motion v12 sem `duration` na transition)
- CSS vars referenciadas mas não definidas (`--popover` vs `--color-popover`)
- Schema drift (migrations manuais sem auto-run em dev start)
- Orphan processes (sem cleanup script no predev)

A direção certa: **reforçar a estrutura sem reescrever do zero**.

---

## 2. O que já está bom (não mexer)

### Segurança
- ✅ JWT com blacklist via Redis (revocation funcional)
- ✅ Bcrypt rounds 12 + comparação timing-safe contra hash fake
- ✅ Rate limiting por endpoint (auth 10/15min, messages 20/10s, global 200/min)
- ✅ Helmet com CSP real (tight em prod, loose em dev pra Vite HMR)
- ✅ CSRF via Origin/Referer + SameSite cookie
- ✅ Sanitize em params/query (body fica intacto — React escapa na render)
- ✅ Trust proxy = 1 (não aceita X-Forwarded-For arbitrário)
- ✅ Upload: mime whitelist sem SVG, max 25MB, filenames crypto-random
- ✅ Voice token (LiveKit) com permission check + TTL 6h
- ✅ Socket.IO com auth obrigatório no handshake
- ✅ Env validation Zod fail-fast no boot

### Performance
- ✅ DB indexes em todos hot paths (mensagens, DMs, friendships, notifications)
- ✅ MessageList com virtualization (@tanstack/react-virtual)
- ✅ Bundle chunking via vite manualChunks (livekit, motion, radix, sentry isolados)
- ✅ React Query com staleTime 60s default, gcTime 10min
- ✅ Lazy imports: AppPage, ProfilePage, SettingsPage, CommandPalette, etc.

### Maintenance
- ✅ Testes: jwt, validate, errors, permissions, botMemory (5 specs)
- ✅ Apenas 4 TODOs/FIXMEs em todo o codebase (clean)
- ✅ shadcn configurado (`components.json` presente)
- ✅ Logger estruturado + Sentry + req context
- ✅ Workers separados (retention, reminders)

---

## 3. Achados (action items)

### 🔴 SEGURANÇA (low priority — superfície pequena)

1. **Multi-port dev CORS** — `CLIENT_URL` aceita só uma URL. Quando Vite pula 5173→5174→5175 (porta ocupada), CORS bloqueia tudo silenciosamente. Pegou a gente no bug do ProfileCard.
   - Fix: aceitar array em dev (`localhost:5173|5174|5175`) ou wildcard de porta localhost.

2. **8MB JSON limit em /api/profile** — defensável (banners base64), mas alto.
   - Fix: reduzir pra 4MB ou exigir upload multipart pra banners > 1MB.

### 🟡 PERFORMANCE (highest impact)

3. **Bundle: chunks > 600KB**
   - `wasm-BnjxR4X6.js` (622KB) + `cpp-79dVKc51.js` (637KB) — Shiki grammars
   - `vendor-livekit` (503KB) — OK, lazy
   - `FullEmojiPicker` (506KB) — OK, lazy
   - Fix: investigar se shiki pode ser substituído por server-render highlight, OU lazy-load por linguagem.

4. **Motion v12 sem duration explícito** — variant `transition: { staggerChildren }` sem duration trava parent em opacity inicial. Foi o bug do ProfileCard.
   - Fix: lint rule custom OU helper `staggerVariant({duration, stagger, delay})` que força duration.

5. **ProfileCard `refetchOnMount: 'always'`** — re-fetch a cada abertura. Em 99% dos casos dados de 10s atrás são frescos.
   - Fix: trocar pra `staleTime: 30_000` + remover always.

6. **React Query: queries sem dedup explícito** — não vi `useQuery` consolidado pra perfis em batches (cada hover/click dispara request separado).
   - Fix: usar `useQueries` ou batch endpoint pra membros visíveis.

### 🟠 MANUTENÇÃO (estruturação)

7. **MessageItem.tsx: 1244 linhas** — God component. Inclui ProfileCard, ActionToolbar, ReactionChips, BioMarkdown, etc.
   - Fix: extrair pra `chat/Message/` (subdir com Message.tsx, Reactions.tsx, Actions.tsx, Attachments.tsx).

8. **Sidebar.tsx: 1003 linhas** — Tudo: server strip, channel list, footer, contextos, dialogs.
   - Fix: extrair Server strip + Channel list em componentes separados.

9. **CLAUDE.md fora do repo** — já em .gitignore. Mas significa: outras pessoas (ou Claude em outra sessão) começam sem contexto. Solução: `CONTRIBUTING.md` checado-in com pointers genéricos.

10. **Predev script ausente** — orphans + EADDRINUSE foi recorrente.
    - Fix: `npm run predev` que mata processos em :3001/:5173, roda migration, valida env.

11. **`tailwindcss-animate` em package.json mas inutilizada** — Tailwind v4 não carrega plugins v3 sem `@plugin`. Sobra na deps.
    - Fix: remover do package.json + verificar nenhuma ref residual.

12. **Sheet animations: substituí v3 por v4-native** (commit `4a46c5e`) — mas outras 7 UI primitives (dialog, popover, dropdown, etc.) ainda têm o mesmo padrão quebrado.
    - Fix: mesmo padrão (.dialog-content-* etc.) ou loaderr o plugin via `@plugin "tailwindcss-animate"` (precisa instalar primeiro).

---

## 4. Plano de execução (fases)

Cada fase é commitável independente. User aprova antes da próxima.

### **Fase 1 — Fix imediato do ProfileCard** (1 turno)
- ⚠️ **Pré-requisito**: confirmar se meu último fix (opacity inline) já resolveu OU se precisa nuke + rebuild.
- Se rebuild: ProfileCard novo seguindo shadcn (Sheet do shadcn já existe) + variants com duration explícito + zero CSS var indefinida.

### **Fase 2 — Predev script + dev hardening** (1 turno)
- `scripts/predev.mjs`: kill orphans, verify ports, run migration, validate env.
- Wire em `package.json` scripts: `predev: node scripts/predev.mjs`.
- Atualizar `CLAUDE.md` (e adicionar `CONTRIBUTING.md` versionado).

### **Fase 3 — UI primitives fix (Tailwind v4 animate)** (1-2 turnos)
- Decidir: instalar tw-animate-css OU substituir todas as 7 primitivas como fiz no Sheet.
- Recomendação: instalar `@plugin "tailwindcss-animate"` no CSS (1 linha, restaura todas as classes v3).

### **Fase 4 — Component refactor** (3-4 turnos)
- Extrair MessageItem → `chat/Message/`.
- Extrair Sidebar → `layout/Sidebar/{ServerStrip, ChannelList, UserFooter}.tsx`.
- Cada extração com commit separado.

### **Fase 5 — Performance otimizations** (2 turnos)
- Trocar `refetchOnMount: 'always'` em ProfileCard.
- Batch endpoint pra perfis (members + hover).
- Shiki: lazy por linguagem usada (estimar -400KB do bundle inicial).

### **Fase 6 — Security polish** (1 turno)
- CORS multi-port em dev.
- JSON limit revisado.
- Remover tailwindcss-animate da deps se substituído.

### **Fase 7 — Testes mínimos** (2 turnos)
- Webapp-testing skill (Playwright): E2E pro click avatar → profile abre.
- Coverage de regressão pros 5 bugs silenciosos encontrados.

---

## 5. Estimativa

| Fase | Turnos | Risco |
|---|---|---|
| 1 | 1 | Baixo |
| 2 | 1 | Baixo |
| 3 | 1-2 | Médio (mexe em todos os primitivos UI) |
| 4 | 3-4 | Médio (refactor grande, sem mudança de comportamento) |
| 5 | 2 | Baixo |
| 6 | 1 | Baixo |
| 7 | 2 | Baixo |
| **Total** | **11-13 turnos** | Médio |

---

## 6. Decisões pendentes (user precisa aprovar)

1. **Fase 1 — rebuild OU manter fix atual?**
2. **Fase 3 — `@plugin "tailwindcss-animate"` (rápido) OU refazer todos primitives manualmente (mais control)?**
3. **Ordem das fases — começar pela Fase 1 imediato OU pelo predev script (Fase 2) que evita futuros bugs?**
4. **Pular alguma fase?** (ex: testes E2E podem ser deixados pra depois se urgência é UI)

---

**Próximo passo:** aprovar/rejeitar cada decisão acima. Eu executo Fase 1 só com green-light.
