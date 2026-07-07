# Astra Desktop — Design & Plano

**Data:** 2026-07-07
**Status:** aprovado (decisões travadas com o dono) — pré-scaffold
**Fase anterior:** `mobile-native` (Android nativo, Kotlin/Compose) em paridade com o web.

Cliente desktop do Astra, sobre a **mesma API** (Render + Neon + Upstash). Esqueleto
inspirado no Discord desktop, pele editorial-dark "obsidiana" do Astra. Roda em
computador → animações mais ousadas (sem teto de bateria/térmico).

---

## 1. Decisões travadas

| Tema | Decisão | Observação |
|---|---|---|
| Base | **Compose Multiplatform (KMP)** | Reusa o Kotlin/Compose do `mobile-native`. Production-ready 2026. |
| Código | **Módulo `shared` KMP** | Extrai domínio+dados+DI+UI comum; adiciona alvo desktop. Android é re-testado a cada passo. |
| Voz/vídeo | **WebRTC nativo (webrtc-kmp)** | Caminho ambicioso; **fase isolada no fim** (ver §7 e Riscos). |
| Assinatura visual | **Aurora shader vivo + vidro/blur + hover rico** | Sem parallax star-field (por ora). |
| Janela | **Frameless + barra-título obsidiana custom** | Estilo Discord; controles próprios. |

---

## 2. Stack técnica

### Reusa direto (baixo esforço)
- **UI:** Jetpack Compose → Compose Multiplatform (a maioria dos composables é igual).
- **Imagens:** Coil3 (já é KMP, suporta desktop).
- **Cache:** Room 2.8 → Room KMP roda na JVM (driver SQLite bundled).
- **Rede:** Retrofit + OkHttp rodam na JVM direto — **sem migrar pra Ktor no v1** (migração só valeria se um dia formos iOS/web).
- **kotlinx.serialization / Coroutines / DataStore:** KMP-nativos.

### Precisa trocar / portar
- **DI: Hilt (Android-only) → Koin.** Maior refactor. Koin é KMP, sem codegen — os `@HiltViewModel`/módulos viram módulos Koin. Feito de forma incremental mantendo o Android verde.
- **Shader aurora: AGSL `RuntimeShader` (Android 33+) → SkSL `RuntimeEffect` (Skia).** Linguagens quase idênticas; porta-se o `.agsl` pra `.sksl`. Referência: HypnoticCanvas, KMPLiquidGlass.
- **Voz: LiveKit Android → webrtc-kmp + sinalização LiveKit custom** (§7).

### Específico de plataforma (expect/actual)
| Capacidade | Android (`androidMain`) | Desktop (`desktopMain`) |
|---|---|---|
| Decode/EXIF de imagem | `BitmapFactory`+`ExifInterface` | Skia / `ImageIO` |
| Encode WebP | `Bitmap.compress(WEBP)` | Skia `Image.encodeToData` |
| Escolher imagem | `PickVisualMedia` | `java.awt.FileDialog` |
| Shader | AGSL | SkSL |
| Voz | LiveKit Android | webrtc-kmp |
| Token store | DataStore (comum) | DataStore (comum) |
| Push | FCM | — (sem push no v1; badge/tray depois) |
| Háptica | Vibrator | no-op |
| OAuth | Custom Tabs | browser do sistema + loopback |

### Empacotamento / distribuição
- `org.jetbrains.compose.desktop` → jpackage: **MSI/exe (Win), dmg (mac), deb (Linux)**.
- Auto-update: **Conveyor** (Hydraulic) numa fase pós-v1.

---

## 3. Arquitetura de módulos

```
astra/ (ou mobile-native/ renomeado)
├── shared/                 (KMP library)
│   ├── commonMain/         domínio, dados (repos/DTO/Retrofit), DI Koin, UI comum
│   ├── androidMain/        actuals Android (imagem, voz LiveKit, push, háptica)
│   └── desktopMain/        actuals JVM (Skia, webrtc-kmp, FileDialog, SkSL)
├── androidApp/             app fino (Activity, Manifest, FCM) → consome :shared
└── desktopApp/             app fino (main(), Window frameless, tray) → consome :shared
```

**Regra de ouro da migração:** cada passo termina com **o Android compilando e rodando**.
Nada de big-bang. Ordem: mover código puro (domínio/dados) → trocar Hilt→Koin →
extrair UI comum → só então ligar o alvo desktop.

---

## 4. Estrutura de UI — esqueleto Discord, pele Astra

```
┌───────────────────────────────────────────────────────────────┐
│  ▁ barra-título obsidiana (frameless) · arrasta · _ □ ✕        │  ← custom
├────┬───────────────┬────────────────────────────┬─────────────┤
│    │  # órbitas     │  top bar: #canal · tópico  │  membros    │
│rail│  (canais)      │  busca · fixadas · toggle  │  (online/   │
│ de │                ├────────────────────────────┤   cargos)   │
│cons│                │                            │             │
│tela│                │      lista de mensagens     │  toggleável │
│ções│                │                            │             │
│    ├───────────────┤                            │             │
│    │ 👤 painel user │      compositor            │             │
│    │ status·mic·⚙  │                            │             │
└────┴───────────────┴────────────────────────────┴─────────────┘
```

1. **Barra-título obsidiana** — frameless (`Window(undecorated=true)`), controles próprios, `WindowDraggableArea`.
2. **Rail de constelações** (extrema esquerda) — ícones verticais; pill morph no hover/active (já existe no mobile).
3. **Sidebar de órbitas** + **painel do usuário embaixo** (avatar, status, mic/deafen/config) — canto inferior-esquerdo estilo Discord.
4. **Palco central** — top bar (canal, tópico, busca, fixadas, toggle membros) + lista de mensagens + compositor.
5. **Lista de membros** à direita (toggleável) — online/cargos.
6. **Overlays** — settings fullscreen, popout de perfil (vidro), **Command Palette Ctrl+K** (o web já tem a lógica).

**Vantagens de desktop embutidas:** panes redimensionáveis (arrastar divisórias),
atalhos de teclado, tray icon, multi-janela (pop-out de call — futuro).

---

## 5. Assinatura visual (as ousadias escolhidas)

- **Aurora shader vivo (SkSL):** fundo cósmico animado por trás de tudo, com mais
  camadas/partículas que o mobile. Uniforms: `resolution`, `time`, accent do tema.
  Um `Modifier.drawBehind { ShaderBrush }` no shell raiz.
- **Painéis de vidro/blur (Skia `ImageFilter`):** overlays, popout de perfil e command
  palette em vidro-fosco translúcido obsidiana. `graphicsLayer { renderEffect = blur }`.
- **Hover rico + micro-interações:** glow no hover (mouse real), toolbar de mensagem
  que surge on-hover, tooltips, pills que deformam. `Modifier.pointerHoverIcon` +
  `onPointerEvent(Enter/Exit)`.

Tokens/tema saem do mesmo sistema do mobile (`buildAstraColors`, presets AMOLED etc.) —
o desktop herda a customização de accent/fundo do usuário.

---

## 6. Skills de apoio
- `aldefy/compose-skill@compose-expert` — Compose a fundo
- `travisjneuman/.claude@kotlin-multiplatform` — padrões KMP/expect-actual
- `travisjneuman/.claude@ui-animation` + `pedronauck/skills@motion` — motion

---

## 7. Voz nativa (webrtc-kmp) — fase isolada

**Aviso de mentor:** essa é de longe a parte mais arriscada. A LiveKit **não publica
client SDK pra JVM**, então o caminho nativo é: `webrtc-kmp` (PeerConnection/tracks) +
**reimplementar o protocolo de sinalização da LiveKit** (o que o `livekit-android` faz
por baixo: WebSocket de signaling, SDP/ICE, subscribe/publish de tracks, data channel).
Isso é semanas de trabalho e depende de uma lib de comunidade.

**Por isso é a última fase e NÃO bloqueia o v1.** Se travar, o fallback continua sendo
o embed web (KCEF) só na sala de voz. O resto do app (texto/servidores/DMs/perfil)
entrega inteiro antes disso.

---

## 8. Roadmap faseado

| Fase | Entrega | Verificação |
|---|---|---|
| **D0** | Scaffold: root Gradle multi-módulo, `:shared`+`:androidApp`+`:desktopApp`, alvos KMP, janela desktop "hello" | desktopApp abre janela; androidApp roda igual |
| **D1** | Mover domínio/dados pro `commonMain` (Retrofit/Room/repos) | Android verde |
| **D2** | Hilt → Koin (incremental) | Android verde, DI resolvendo |
| **D3** | Extrair UI comum pro `commonMain`; actuals de imagem/arquivo | Android verde |
| **D4** | Shell desktop: barra-título frameless + rail + sidebar + palco + membros | login → ver constelações/canais |
| **D5** | Aurora SkSL + vidro/blur + hover | assinatura visual no ar |
| **D6** | Command palette Ctrl+K, atalhos, panes redimensionáveis, tray | polia desktop-first |
| **D7** | Empacotar (MSI/dmg/deb) + Conveyor | instalador roda em máquina limpa |
| **D8** | **Voz nativa (webrtc-kmp)** — fase de risco isolada | call 1:1 e em canal |

---

## 9. Riscos
- **Voz nativa (D8):** alto. Lib de comunidade + reimplementar signaling LiveKit. Fallback = KCEF web-embed.
- **Hilt→Koin (D2):** invasivo no app que já funciona. Mitiga: incremental, Android verde a cada commit.
- **Shader AGSL→SkSL:** baixo/médio. Sintaxe quase igual; validar visual no desktop.
- **Peso do binário:** JVM + Skia empacotados via jpackage (~80-120MB). Aceitável pra desktop.
