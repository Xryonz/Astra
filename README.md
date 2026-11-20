# Astra

O Astra é uma plataforma de conversa em tempo real, com estética editorial e escura.
Reúne **constelações** (comunidades), **órbitas** (canais de texto e de voz) e
**sussurros** (conversas privadas), com chamadas de voz, transmissão de tela, cargos,
reações, enquetes, busca, notificações, uma bot com inteligência artificial, níveis e
missões.

| Aplicativo | Situação |
| --- | --- |
| **Computador** (Windows) | fase atual do projeto |
| **Android** | só versões de teste |
| **Web** | arquivado na etiqueta `arquivo/web` |

---

## Tecnologias

### Aplicativo para computador

- **Kotlin** e **Compose Multiplatform**: a linguagem e a interface, desenhada pela
  placa de vídeo (Skia sobre Direct3D)
- **Java 25**: o ambiente em que o app roda, empacotado junto, sem nada para instalar à parte
- **Koin**: organização das dependências internas
- **Retrofit**, **OkHttp** e **kotlinx.serialization**: a conversa com o servidor
- **Coroutines** e **Flow**: tarefas simultâneas e estado que se atualiza sozinho
- **Socket.IO**: mensagens, presença e avisos ao vivo
- **Coil**: imagens e GIFs
- **Compottie**: animações Lottie
- **RikkaUI** e **Lucide**: componentes de interface e ícones
- **JNA**: integração com o Windows (barra de tarefas, foco da janela, início com o
  sistema e sessão protegida pelo próprio Windows)
- **Nucleus**: a identidade do Astra no menu Iniciar
- **MP3SPI** e **VorbisSPI**: os sons em MP3 e OGG da soundboard
- **SkSL**: os shaders do fundo de aurora

### Voz e transmissão de tela

- **LiveKit**: as salas de voz e de tela, pelo LiveKit Cloud
- **Rust**, com o **LiveKit SDK para Rust**, **Tokio** e **windows-rs**: o motor de voz e
  tela, que roda num processo à parte do app
- **libwebrtc**, o WebRTC do Google: o transporte da voz e da tela, compilado dentro do motor
- **Opus**: o codec de voz
- **AEC3**: o cancelamento de eco do libwebrtc, o mesmo do Chromium
- **Media Foundation**: a compressão de vídeo H.264 pela placa de vídeo
- **DXGI Desktop Duplication** e **Windows.Graphics.Capture**: a captura da tela inteira e
  de uma janela

### Android

- **Kotlin**, **Jetpack Compose** e **Material 3**
- **Hilt**, **Room**, **DataStore**, **Navigation** e **Lifecycle**
- **Retrofit**, **OkHttp**, **kotlinx.serialization**, **Socket.IO** e **Coil**
- **Media3**: tocar e comprimir vídeos
- **LiveKit Android**: voz e tela
- **Firebase Cloud Messaging**: notificações
- **Baseline Profiles** e **Macrobenchmark**: abertura mais rápida
- **LeakCanary**: caça a vazamentos de memória nas versões de desenvolvimento

### Servidor

- **Node.js** e **TypeScript**, com **Express**
- **PostgreSQL**, com **Drizzle ORM**
- **Redis**: presença e cache
- **Socket.IO**: o tempo real, com adaptador Redis
- **Zod**: validação dos dados, com os tipos num pacote compartilhado
- **Passport** (entrar com Google), **JWT** e **bcrypt**: contas e sessões
- **Multer**, **Sharp** e **BlurHash**: envio e tratamento de imagens
- **AWS SDK (S3)**: os arquivos, guardados no Supabase Storage
- **LiveKit Server SDK**: o acesso às salas de voz
- **Firebase Admin**: o envio das notificações do Android
- **Helmet**, **CORS** e **express-rate-limit**: a proteção da API
- **Sentry** e **prom-client**: erros e métricas

### Serviços

| Serviço | Para quê |
| --- | --- |
| Render | hospeda o servidor |
| Supabase | banco PostgreSQL e armazenamento de arquivos |
| Upstash | Redis |
| LiveKit Cloud | voz e transmissão de tela |
| Groq e Google Gemini | a IA da bot e a tradução de mensagens |
| GIPHY | busca de GIFs |
| Brevo | e-mail |
| Firebase | notificações do Android |
| Google | entrar com a conta Google |
| Sentry | registro de erros |
| GitHub | código, integração contínua, distribuição das versões e Dependabot |

### Ferramentas

- **Gradle**, **npm workspaces** e **Cargo**: a construção de cada projeto
- **GitHub Actions**: integração contínua e publicação
- **Biome**, **detekt** e **Clippy**: análise estática do TypeScript, do Kotlin e do Rust
- **Vitest** e os testes nativos do Rust
- **Python**: as provas de abertura do pacote
- **Java**: as ferramentas de assinatura Ed25519 e as provas do shader
- **Node.js** e **PowerShell**: scripts de conferência e de medição
- **winresource**: nome, versão e ícone no executável do motor de voz
- **cargo-about**: o texto das licenças das bibliotecas do motor, gerado a cada versão

---

## Como o Astra é feito

### Fluxo de trabalho

- O trabalho do dia acontece num ramo de desenvolvimento. O ramo principal publica para
  todos, então só avança por pull request, com a integração contínua verde e aprovação.
- Cada envio passa pela integração contínua: tipos, estilo, testes e auditoria de
  dependências no servidor; compilação e análise estática do app para computador;
  compilação do Android; testes e análise estática do motor de voz; conferência das
  licenças e dos acentos do texto visível.
- Commits pequenos e frequentes, com mensagens longas que explicam o porquê.
- **O código não tem comentários, de propósito.** O nome diz o que o código faz. O porquê
  de cada decisão (as medições, as armadilhas, os caminhos que não deram certo) fica no
  histórico de commits, amarrado à mudança que o produziu.
- Peças pequenas e nomeadas, em vez de arquivos que concentram tudo.

### Medir antes de mexer

- Mudança de desempenho só entra medida: a versão antiga e a nova se alternam por várias
  rodadas numa cópia de teste isolada, e a mediana decide.
- Antes de cada fase grande vem uma pesquisa a fundo. A troca do motor de voz de Go para
  Rust, por exemplo, passa por portões com critérios numéricos antes de chegar a todos.
- Todo mundo recebe o mesmo app: o Astra não mede a máquina de quem usa nem escolhe a
  qualidade sozinho. Quem quer leveza escolhe um perfil (Leve, Equilibrado ou Completo).

### Publicação

- Cada versão para computador é assinada (Ed25519), e o app só instala o que confere com
  a assinatura e com a lista de arquivos.
- A atualização baixa só o que mudou.
- Antes de publicar, o pacote é aberto treze vezes, em treze condições difíceis: máquina
  fraca, sem placa de vídeo, sem internet, preferências corrompidas, uma segunda cópia
  aberta, entre outras. Se uma falhar, nada é publicado.
- O Android publica só versões de teste, que nunca aparecem como a versão atual.

### Design

- Estética editorial e escura ("obsidiana"), sem tema claro, por escolha.
- **Cartão dentro de cartão:** o conteúdo se separa por superfícies aninhadas, não por
  linhas de borda a borda.
- **Hierarquia por elevação, não por cor:** quanto mais clara a superfície, mais
  importante. A cor de destaque entra por último e em pouca área.
- **Movimento com orçamento:** a animação marca o que importa (mensagem nova, alguém
  entrando na call), e o resto fica quieto.
- Texto em tom adulto e editorial.

### Acessibilidade

É regra de aceite para tela nova, não item de lista de pendências:

- todo botão só com ícone tem nome para o leitor de tela;
- o foco do teclado é sempre visível;
- todo alvo de clique tem pelo menos 24dp;
- toda animação respeita "reduzir movimento" (WCAG 2.3.3, nível AAA);
- o contraste mira o nível AA, porque passar disso ofusca e cansa em sessões longas à
  noite, que é o uso real.

### Licenças e segurança

- Nada sob GPL ou AGPL entra no produto, e as licenças das dependências do motor em Rust
  são conferidas a cada mudança.
- Todo recurso de terceiros entra com o texto da licença ao lado e uma linha nos
  créditos, no mesmo commit.
- Segredos ficam fora do repositório. A chave que assina as versões vive só no cofre do
  GitHub.
- Dependências auditadas a cada envio, limite de pedidos na API e proteção contra acesso
  a endereços internos na prévia de links.

---

## Créditos

Nem tudo que dá cara ao Astra foi feito aqui. O que veio de fora está nomeado abaixo,
com o autor e a origem.

**Regra da casa:** todo recurso de terceiros entra junto do texto da licença dele *e* de
uma linha nesta seção, no mesmo commit. Várias dessas licenças pedem atribuição, e mesmo
as que não pedem merecem: a arte é de quem a desenhou.

### Arte

| O quê | Autor | Origem |
| --- | --- | --- |
| Gato do companheiro, "Simples" | **Elthen** (ELV Games) | [2D Pixel Art Cat Sprites](https://elthen.itch.io/2d-pixel-art-cat-sprites) |
| Gato do companheiro, "Travesso" | **Jump Button** ([@Jump_Button](https://twitter.com/Jump_Button)) | Cat Player, com crédito exigido em uso comercial |
| Companheiro "Sátiro" | *autor a confirmar* | pacote sem arquivo de licença; uso liberado pelo autor ao dono do Astra |

A licença dos companheiros viaja junto da arte, em
`mobile-native/desktopApp/src/main/resources/pet/`.

### Animações

Feitas por animadores e publicadas no [LottieFiles](https://lottiefiles.com) sob a
Lottie Simple License, que permite uso comercial e não exige crédito; o crédito aqui é a
regra da casa. Os arquivos ficam sem alteração em
`mobile-native/desktopApp/src/main/resources/animacoes/`, com o texto da licença ao
lado, e tocam pela biblioteca [Compottie](https://github.com/alexzhirkevich/compottie)
(MIT), que aplica a cor do tema na hora.

| O quê | Autor | Origem |
| --- | --- | --- |
| Brilho da estrela de enviar | **Cristina Loreto V H** (variação de "stars points") | [star shine](https://lottiefiles.com/animations/star-shine-eA7UrOTpO9) |
| Constelação que se forma (coluna e Descobrir) | **Abdul Latif** | [Constellation icon animation](https://lottiefiles.com/animations/constellation-icon-animation-qjz6quYgLl) |
| Eclipse sobre a sua foto ao escolher "não perturbe" | **Anh Nguyen Duc** | [eclipse loading](https://lottiefiles.com/animations/eclipse-loading-Jqdd4oWTcm) |
| Lua de sangue sobre a sua foto ao escolher "ausente" | **can kankaya**, edição de "phase of the moon", de **Moyu** | [moon-phases-edited](https://lottiefiles.com/animations/moon-phases-edited-KkHBa9ixJ2) |

### Tipografia

| Fonte | Onde | Autor | Licença |
| --- | --- | --- | --- |
| Cormorant | computador | Christian Thalmann ([Catharsis Fonts](https://github.com/CatharsisFonts/Cormorant)) | SIL Open Font License 1.1 |
| Cinzel (letreiro "Astra") | computador | Natanael Gama ([NDISCOVER](https://github.com/NDISCOVER/Cinzel)) | SIL Open Font License 1.1 |
| DM Sans · DM Mono | computador e Android | Colophon Foundry / Jonny Pinhorn | SIL Open Font License 1.1 |
| DM Serif Display | Android | Colophon Foundry / Frank Grießhammer, a partir da Source Serif, da Adobe | SIL Open Font License 1.1 |
| Great Vibes | computador e Android | Robert Leuschke | SIL Open Font License 1.1 |

O texto de cada licença viaja junto das fontes: em
`mobile-native/desktopApp/src/main/resources/font/` no computador e em
`mobile-native/app/src/main/assets/licencas/` no Android. **Não é formalidade:** a SIL
OFL exige que a licença acompanhe a fonte sempre que ela é redistribuída, e os pacotes
do Astra são públicos.

### Ícones

[Lucide](https://lucide.dev), sob a ISC License.

A lua do estado "ausente" é o ícone "eclipse" (sólido) da
[Myna UI Icons](https://github.com/praveenjuge/mynaui-icons), de **Praveen Juge**, sob a
MIT License. O arquivo fica sem alteração em
`mobile-native/desktopApp/src/main/resources/icones/`, com o texto da licença ao lado.

### Motor de voz e tela

O motor em Rust (`astra-voz.exe`) leva dentro as bibliotecas abaixo. Ao lado dele, o
pacote traz o texto completo de cada licença: `LICENCAS-DO-MOTOR-DE-VOZ.txt`, com as
bibliotecas em Rust, gerado a cada versão pelo cargo-about, e `LICENCAS-DO-LIBWEBRTC.md`,
com o libwebrtc e os componentes que ele reúne.

| Biblioteca | Para quê | Licença |
| --- | --- | --- |
| [LiveKit Rust SDK](https://github.com/livekit/rust-sdks) | entrar na sala e publicar a voz e a tela | Apache 2.0 |
| [libwebrtc](https://webrtc.org), compilado pela LiveKit | o WebRTC: transporte, cancelamento de eco e codecs | BSD 3-Clause |
| [FFmpeg](https://ffmpeg.org), dentro do libwebrtc | decodificar o vídeo das transmissões | LGPL 2.1 |
| [Opus](https://opus-codec.org), dentro do libwebrtc | o codec de voz | BSD 3-Clause |
| [OpenH264](https://www.openh264.org), [libvpx](https://chromium.googlesource.com/webm/libvpx), [libaom](https://aomedia.googlesource.com/aom) e [dav1d](https://code.videolan.org/videolan/dav1d), dentro do libwebrtc | os codecs de vídeo H.264, VP8, VP9 e AV1 | BSD |
| [BoringSSL](https://boringssl.googlesource.com/boringssl), dentro do libwebrtc | a criptografia das chamadas | Apache 2.0 |
| [Tokio](https://tokio.rs) | as tarefas simultâneas do motor | MIT |
| [windows-rs](https://github.com/microsoft/windows-rs) | a captura de tela, a compressão de vídeo e o resto do Windows | MIT (o projeto oferece MIT ou Apache 2.0; aqui vale a MIT) |
| [serde](https://serde.rs) | a conversa com o app | MIT (o projeto oferece MIT ou Apache 2.0; aqui vale a MIT) |

O FFmpeg, sob a LGPL 2.1, vai ligado de forma estática ao motor. Para cumprir a licença,
cada versão publicada leva junto o código-fonte do FFmpeg usado, o libwebrtc exatamente
como entrou no motor e as instruções para refazer o motor com outra versão do FFmpeg.

### Código adaptado

| Origem | Para quê | Licença |
| --- | --- | --- |
| [moq-video](https://github.com/moq-dev/moq/tree/main/rs/moq-video), de **Luke Curley** | trechos do codificador H.264 por Media Foundation e do conversor de cores do Direct3D 11, adaptados no motor em Rust (`sidecar-voz-rs/src/tela/`) | MIT (o projeto oferece MIT ou Apache 2.0; aqui vale a MIT) |

O texto da licença fica ao lado do código adaptado, em
`sidecar-voz-rs/src/tela/LICENCA-moq-video.txt`.

### Dados

O catálogo que traduz o nome do executável no título do jogo
(`mobile-native/desktopApp/src/main/resources/jogos.tsv`) é destilado da lista de
aplicações detectáveis que o **Discord** publica em
`discord.com/api/v9/applications/detectable`: um endereço público, mas não documentado e
sem licença declarada. Os títulos são marcas dos respectivos donos e aparecem só para
dizer o que a pessoa está jogando.

A arte dos jogos pertence às desenvolvedoras, não ao Discord, que apenas a hospeda. O
servidor do Astra busca cada ícone uma vez, guarda no armazenamento próprio e serve dali
em diante: nenhuma máquina de usuário fala com o Discord, e nenhum ícone viaja no
instalador. Só existem os dos jogos que alguém aqui de fato abriu.

### Bibliotecas nativas

| Biblioteca | Para quê | Licença |
| --- | --- | --- |
| [MP3SPI](https://github.com/pdudits/soundlibs) + [JLayer](http://www.javazoom.net/javalayer/javalayer.html) | leitura de MP3 nos sons da soundboard | LGPL 2.1+ |
| [VorbisSPI](https://github.com/pdudits/soundlibs) + [JOrbis](https://www.jcraft.com/jorbis/) | leitura de OGG nos sons da soundboard | LGPL 2.1+ |
| [Tritonus](https://www.tritonus.org) (`tritonus-share`) | base comum dos dois leitores acima | LGPL 2.1+ |
| [Nucleus](https://github.com/NucleusFramework/Nucleus) (`nucleus.notification-windows` + `nucleus.core-runtime`) | gravar a identidade do Astra no atalho do menu Iniciar | MIT |
