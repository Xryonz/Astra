# Astra — clientes nativos (Kotlin)

Projeto Gradle à parte (rootProject `AstraMobile`) com os dois clientes nativos do Astra,
os dois sobre a mesma API.

| Módulo | O que é |
| --- | --- |
| `:desktopApp` | o app de Windows, em Compose Multiplatform (JVM) |
| `:app` | o app de Android, em Jetpack Compose |
| `:shared` | rede (interfaces do Retrofit) e DTOs, em Kotlin/JVM puro |
| `:baselineprofile` | o perfil de arranque do Android |

O `:shared` não é KMP e não tem interface: cada app tem as próprias telas, e paridade entre
eles é reescrita, não reuso. A voz e a transmissão de tela do desktop rodam num programa à
parte, o `astra-voz.exe`, escrito em Go em `sidecar-voz/`.

## Pilha

- **Comum:** Kotlin 2.3.21 (KSP 2.3.9) · Gradle 9.4.1 · AGP 9.2.0 · bytecode Java 17 ·
  Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization · Coil 3.5 · socket.io-client 2.1 ·
  Lucide · RikkaUI
- **Desktop:** Compose Multiplatform 1.12.2 · Koin 4.1 · JNA 5.14 (sessão cifrada com DPAPI).
  Compilado e empacotado com JDK 25.
- **Android:** Compose (BOM 2026.06.01) · Hilt 2.60 · Room 2.8 · DataStore · Media3 · Firebase
  Messaging (notificações) · LiveKit Android 2.26 · compileSdk 36, minSdk 24, targetSdk 35.
  Compilado com JDK 21.

## Estrutura

- `desktopApp/src/main/kotlin/app/astra/desktop/`: `ui/` (telas e peças), `shell/` (os
  ViewModels), `voice/` (a conversa com o programa de voz), `net/`, `auth/`, `prefs/`,
  `profile/`, `update/` (atualização automática), `di/` e `xp/`.
- `app/src/main/java/app/astra/mobile/`: `feature/<área>/`, com `data`, `domain` e
  `presentation` em cada área; `core/` (rede, banco, notificações e voz); `session/`; `ui/`
  (tema e peças).

## Como montar

Da raiz do repositório:

```powershell
# desktop, rodando
.\mobile-native\gradlew.bat -p mobile-native :desktopApp:run

# desktop, só compilando
.\mobile-native\gradlew.bat -p mobile-native :desktopApp:compileKotlin

# Android (precisa do SDK do Android)
.\mobile-native\gradlew.bat -p mobile-native :app:assembleRelease "-Pastra.android=1"
```

O módulo Android só entra no projeto com `-Pastra.android=1`. No PowerShell, esse argumento
vai entre aspas: sem elas, ele é partido no ponto.

Os dois apps falam com a API publicada: `AstraShared.BASE_URL` no desktop e
`BuildConfig.BASE_URL` no Android.

## Propriedades do Gradle

- `astraVersion`, em `gradle.properties`: a versão do desktop. Mudá-la na `main` dispara a
  publicação de uma versão nova.
- `-Pastra.aec3=1`: monta o programa de voz com o cancelador de eco interno (48 kHz), como na
  versão publicada. Sem ela, o eco é cancelado pelo Windows, a 16 kHz.
- `-Pastra.canal=estavel`: o canal da versão publicada. O padrão é `dev`.
- `-Pjfr`: liga a gravação de voo do Java no app.
