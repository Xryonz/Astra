# Astra — cliente nativo Android (Kotlin + Jetpack Compose)

Cliente nativo do zero. Substitui o híbrido React/Capacitor a longo prazo
(o Capacitor continua na raiz como app que shipa até este aqui ter paridade).

## Stack
- **Kotlin 2.0.21 (K2)** · **Jetpack Compose** (Material3) · **Coroutines**
- **Hilt** (DI, via KSP) · **Retrofit + kotlinx.serialization + OkHttp** (rede)
- **WebSocket** (chat realtime — OkHttp WebSocket) · **Coil** (imagens) · **DataStore** (storage local)
- Toolchain: Gradle 8.11.1 · AGP 8.7.2 · compileSdk 35 · minSdk 24 · JDK 17

## Arquitetura — Clean Architecture (MVVM)
Fluxo de dependência: `presentation → domain → data`. Domain não conhece ninguém.

```
app/src/main/java/app/astra/mobile/
├─ core/                # infra transversal (network, di, util)
├─ data/                # implementações: DTOs, Retrofit services, repositories impl, mappers
├─ domain/              # model puro + interfaces de repository + use cases (sem Android)
└─ presentation/        # Composables (telas) + ViewModels + navegação + ui/theme
```
- **domain** não importa Android/Retrofit — só Kotlin puro. Define `interface XRepository` e use cases.
- **data** implementa os repositories (Retrofit/DataStore), mapeia DTO ↔ model.
- **presentation** = `@Composable` + `ViewModel` (StateFlow). ViewModel chama use cases.
- **Hilt** liga tudo: `@Module`s em `core/di` provêm Retrofit, OkHttp, repositories.

## Como abrir
1. Android Studio → **Open** → selecione a pasta `mobile-native/` (NÃO a raiz do repo).
2. Deixe o Gradle sincronizar (1ª vez baixa AGP/Compose/Hilt — precisa de internet).
3. Defina o backend: em `app/build.gradle.kts`, troque `BASE_URL` pela URL real da API.
4. Run no emulador/aparelho. Milestone 1 = tela vazia com o tema Astra (dark/âmbar).

> Scaffold gerado sem build local (sem Android Studio no ambiente do Claude).
> A 1ª sync pode pedir 1 ajuste de versão — reporta o erro que eu corrijo.

## Roadmap (ver também /docs/ASTRA-ROADMAP.md)
- [x] M1: fundação (Gradle, Compose, Hilt, tema Astra, app roda vazio)
- [ ] M2: camada de rede (Retrofit + OkHttp + interceptor de auth + DataStore de token)
- [ ] M3: slice vertical de Auth (login → /api/auth, ViewModel, tela Compose)
- [ ] M4: realtime (WebSocket) + lista de DMs
- [ ] M5: servidores/canais/mensagens
- [ ] M6: voz/vídeo (LiveKit Android SDK)
