# Push nativo (FCM) — o que falta você fazer (5 min)

O código do app e o backend **já estão prontos**. Você já setou o
`FIREBASE_SERVICE_ACCOUNT` na Railway — ou seja, **já tem um projeto Firebase**.
Falta só dar ao app o "crachá" desse projeto: o `google-services.json`.

## Passo a passo

1. Abre <https://console.firebase.google.com> → seleciona o **mesmo projeto** de
   onde você tirou o service account da Railway.

2. **Adicionar os apps Android** (engrenagem → *Configurações do projeto* → aba
   *Geral* → *Seus apps* → *Adicionar app* → Android). Registra **os dois**
   pacotes (o debug tem sufixo próprio!):
   - `app.astra.mobile.debug`  ← o APK que você instala hoje
   - `app.astra.mobile`        ← o release, já deixa pronto
   - (Pode pular o SHA-1 — não é necessário pra FCM.)

3. Ainda em *Seus apps*, clica em **baixar google-services.json** (um arquivo só
   já contém os dois apps).

4. Cola o arquivo em: `mobile-native/app/google-services.json`
   (já está no `.gitignore` — **não** vai pro repo, é normal o git ignorar).

5. Recompila e instala o APK. Pronto — o plugin liga sozinho quando o json existe.

## Testar

1. Abre o app (a Home pede a permissão de notificação na primeira vez — aceita).
2. Configurações → **Notificações** → **Enviar push de teste**.
3. Fecha o app (swipe) e pede pra alguém te mandar uma DM → deve chegar push.

## Como funciona (pra referência)

- **App fechado/background**: o backend manda `notification` + canal
  (mentions/dms/general) → o **sistema** exibe sozinho.
- **App aberto**: cai no `AstraMessagingService.onMessageReceived` → o app exibe.
- **Token**: registrado no backend (`POST /api/push/fcm-token`) ao entrar na Home
  (via `PushRegistrar`) e re-registrado quando o FCM rotaciona (`onNewToken`).
- **Sem o json**: tudo é no-op — o app compila e roda normal, só sem push.

## Se não chegar push

- Confere se o projeto do json é o MESMO do `FIREBASE_SERVICE_ACCOUNT` da Railway
  (logs da Railway devem mostrar `[FCM] configurado` no boot).
- Confere se o pacote registrado é `app.astra.mobile.debug` (o APK debug).
- Notificações do app permitidas no Android (Config. → Apps → Astra → Notificações).
- Horário silencioso desativado nas prefs (ele suprime push de propósito).
