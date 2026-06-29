# Astra — Inventário de paridade Web → Nativo (M10)

Data: 2026-06-29 · Fonte: `apps/web` (201 arquivos) cruzado com `mobile-native`.
Objetivo: mapear TODA feature do web e marcar o que falta no Kotlin, com esforço,
pra portar em fatias priorizadas (nada de port às cegas).

Legenda status: ✅ tem · 🟡 parcial · ❌ falta — Esforço: **P** pequeno · **M** médio · **G** grande.

---

## 1. Auth & entrada
| Feature web | Nativo | Esf | Nota |
|---|---|---|---|
| Login / Register | ✅ | — | paridade |
| OAuth Google | ❌ | M | `GoogleButton`/`oauth.ts`/`OAuthCallbackPage`. Backend só LOGA conta existente (política). Precisa deep link `astra://login`. |
| App lock (biometria) | ❌ | M | `appLock.ts` — bloquear app com digital após X em background. |
| Onboarding cósmico | ❌ | M | `CosmicOnboarding`/`OnboardingPage` — ensina vocabulário (constelação/órbita/estrela). `RequireOnboarded` gateia. Já prometido no backlog. |

## 2. Chat (canal + DM) — maior bloco
| Feature web | Nativo | Esf | Nota |
|---|---|---|---|
| Texto, editar, apagar, responder, reações, digitando, fixar, não-lidas | ✅ | — | M8 fechado |
| **Anexos / imagens na msg** | ❌ | **G** | `MessageAttachments`+`Lightbox`+`imageCompress`+`saveImage`. DTO nativo NÃO tem campo attachment → precisa DTO + render + picker no composer + visor. |
| **GIF picker** | ❌ | M | `GifPicker` (provável Tenor/Giphy). |
| **Emoji picker completo + emojis custom do server** | ❌ | M | `FullEmojiPicker` + `useServerEmojis`. Nativo só tem 6 reações rápidas. |
| **Mensagens de voz** | ❌ | G | `VoiceRecorder`/`RecordingDisplay`/`useAudioRecorder`/`useHoldToRecord`. Gravar+enviar+player. |
| **Enquetes (polls)** | ❌ | M | `PollCard`/`PollComposer`. |
| **Bloco de código + syntax highlight** | ❌ | M | `CodeBlock` + shiki worker. Render markdown rico. |
| **Menções @user** | ❌ | M | `MentionBanner` + autocomplete no composer. |
| **Slash commands / lembretes** | ❌ | M | `slashCommands`/`reminderCommand`. |
| **Histórico de edição** | ❌ | P | `EditHistoryPopover` — ver versões anteriores. |
| **Salvos / bookmarks** | ❌ | M | `BookmarksSheet`/`useBookmarks`. |
| **Traduzir mensagem** | ❌ | P | `useTranslate`. |
| **Painel direito (membros/pins lado a lado)** | 🟡 | M | nativo tem dialog de fixadas; membros só via call. `RightPanel`. |
| **Preferência de notificação por canal** | ❌ | P | `ChannelNotifButton`/`useChannelNotifPref`. |

## 3. Voz / vídeo
| Feature web | Nativo | Esf | Nota |
|---|---|---|---|
| Canal de voz (áudio/câmera/screenshare) | ✅ | — | M6 completo |
| **Chamada 1:1 em DM + modal de chamada recebida** | ❌ | G | `DMCallButton`/`IncomingCallModal`. Nativo só entra em canal de voz de server. |
| **Sons de chamada** | ❌ | P | `callSounds`. |

## 4. Servidores (gestão)
| Feature web | Nativo | Esf | Nota |
|---|---|---|---|
| Criar / editar (ícone, nome) / criar canal | ✅ | — | M5 + ServerEdit |
| Entrar/compartilhar convite | ✅ | — | M9f |
| **Roles / permissões** | ❌ | **G** | `ServerSettingsPage` + `useMyPerms`. Nativo não tem nenhum conceito de permissão. |
| **Deletar servidor** | ❌ | P | `DeleteServerDialog`. Repo nativo não tem `deleteServer`. |
| **Adicionar membro por busca** | 🟡 | P | `AddMemberDialog`. Nativo entra por convite só. |
| **Discover (servidores públicos)** | ❌ | M | `DiscoverPage`. API nativa já tem `isPublic` no updateServer, falta a tela. |
| Categorias de canal | ❌ | — | REMOVIDO de propósito (canais flat). Não reabrir sem pedido. |

## 5. Perfil
| Feature web | Nativo | Esf | Nota |
|---|---|---|---|
| Ver perfil / editar (avatar, banner, nome, bio, pronomes) | ✅ | — | base |
| **Badges de perfil** | ❌ | P | `ProfileBadges`. |
| **Banner gradiente (builder)** | ❌ | M | `ProfileBanner`/`GradientBuilder`. |
| **Cores/gradiente do nome** | ❌ | M | `NameColorsSection`. |
| **Fontes de perfil** | ❌ | P | `profileFonts`. |
| **Bio em markdown** | 🟡 | P | nativo tem bio plana; `bioMarkdown` renderiza rico. |
| Hover card de perfil | ❌ | P | `ProfileHoverCard` (menos relevante no mobile). |

## 6. Configurações (nativo só tem Conta + Perfil)
| Seção web | Nativo | Esf | Nota |
|---|---|---|---|
| Conta / Perfil | ✅ | — | |
| **Aparência** | ❌ | M | `AppearanceSection` (tema/cores). |
| **Customização** | ❌ | M | `CustomizationSection`. |
| **Cores do nome** | ❌ | M | ver §5. |
| **Notificações** | ❌ | M | `NotificationsSection`. |
| **Idioma (i18n)** | ❌ | G | `LanguageSection` + `react-i18next`. App nativo é PT-fixo. |
| **Acessibilidade (reduce-motion)** | ❌ | P | `AccessibilitySection`. Casa com eco-mode (T5). |
| **Sessões ativas** | ❌ | M | `SessionsSection` — listar/revogar dispositivos. |
| **Dados (exportar/limpar)** | ❌ | M | `DataSection`. |
| **Estrela dos desejos (feedback)** | ❌ | P | `WishingStarSection`. |

## 7. Notificações
| Feature web | Nativo | Esf | Nota |
|---|---|---|---|
| **Push** | ❌ | G | `usePushNotifications`/`pushNative`/`registerNativePush`. FCM no Android. |
| **In-app + sino** | 🟡 | M | `NotificationBell`/`MobileNotificationsSheet`/`useInAppNotifications`. Sino nativo é decorativo. |
| **Badge de contagem** | ❌ | P | `badge.ts`. |

## 8. Offline / infra
| Feature web | Nativo | Esf | Nota |
|---|---|---|---|
| **Cache offline + outbox** | ❌ | G | `offlineCache`/`messageCache`/`outbox`. = bloco Room (R1–R4) já planejado. |
| **Pull-to-refresh** | ❌ | P | `usePullToRefresh`. |
| Presença / status | ✅ | — | |
| **Deep link de convite** (`astra://`/https) | ❌ | M | abrir convite tocando no link. |

## 9. Diversos
| Feature web | Nativo | Esf | Nota |
|---|---|---|---|
| Amigos | ✅ | — | M9b |
| **Command palette (Cmd+K)** | ❌ | M | `CommandPalette` — menos crítico no mobile. |
| **Lightbox (visor de imagem)** | ❌ | — | acoplado a anexos (§2). |

---

## Leitura de mentor — por onde começar
Ordenando por **valor pro usuário × esforço**, e respeitando que o app é um chat:

1. **Anexos/imagens na msg (G)** — é o buraco mais sentido num chat hoje. Alto valor.
2. **Push (G)** — sem push, app de chat não avisa. Alto valor, mas depende de FCM/infra.
3. **Emoji completo + GIF (M)** — expressividade, baixo risco.
4. **Roles/permissões (G)** — importante pra servers, mas é peso de backend+UI.
5. **Settings reais (Aparência/Notif/Acessibilidade) (M)** — fecha telas "em breve".

Os blocos **offline/outbox** já estão mapeados como Room (R1–R4) no T6/ordem de execução — não duplicar aqui.

> Regra M10: pegar 1 item, ler o código web correspondente, fatiar, perguntar a cada passo.
