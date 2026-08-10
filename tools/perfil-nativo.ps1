# Perfil do lado NATIVO do Astra (async-profiler).
#
# Por que nao o JFR: o JFR so enxerga pilha Java. O encoder H264 do webrtc-java e
# C++ dentro de uma DLL — pro JFR ele aparece como um metodo nativo sem nada dentro,
# e a pergunta "quanto do custo e o encoder?" fica sem resposta. O async-profiler
# anda a pilha nativa junto com a Java, entao da pra VER o encoder em vez de deduzir.
#
# COMO USAR
#   1. Baixe o async-profiler pra Windows x64 e descompacte:
#        https://github.com/async-profiler/async-profiler/releases
#   2. Abra o Astra (o instalado, nao o `gradlew run`).
#   3. ENTRE numa call e LIGUE a transmissao, com video/jogo em movimento.
#   4. Rode:
#        powershell -ExecutionPolicy Bypass -File tools\perfil-nativo.ps1 -Profiler C:\caminho\async-profiler
#   5. Abra o astra-perfil.html que sai no fim. As barras mais largas sao onde a CPU
#      esta. Se o encoder for o custo, ele aparece como um bloco grosso com nome de
#      libwebrtc/openh264 — e ai a migracao pro GStreamer esta provada, nao suposta.

param(
  [Parameter(Mandatory = $true)][string]$Profiler,
  [int]$Segundos = 30,
  [string]$Saida = "astra-perfil.html"
)

$ErrorActionPreference = 'Stop'

$asprof = Join-Path $Profiler 'bin\asprof.exe'
if (-not (Test-Path $asprof)) {
  # Layouts antigos do pacote.
  $alt = Join-Path $Profiler 'asprof.exe'
  if (Test-Path $alt) { $asprof = $alt } else {
    Write-Host "asprof.exe nao encontrado em $Profiler" -ForegroundColor Red
    Write-Host "Baixe em https://github.com/async-profiler/async-profiler/releases" -ForegroundColor DarkGray
    exit 1
  }
}

$proc = Get-Process -Name 'Astra' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $proc) {
  Write-Host "O Astra nao esta aberto." -ForegroundColor Red
  Write-Host "Abra o app INSTALADO (nao o gradlew run: ali o processo se chama java)." -ForegroundColor DarkGray
  exit 1
}

$destino = Join-Path (Get-Location) $Saida
Write-Host ""
Write-Host "Astra em PID $($proc.Id). Medindo $Segundos s." -ForegroundColor Cyan
Write-Host "Mantenha a transmissao LIGADA e com movimento na tela ate acabar." -ForegroundColor DarkGray

# cstack=vm: anda tambem a pilha NATIVA (e o ponto de usar esta ferramenta em vez
# do JFR). event=cpu: onde o processador esta, nao onde a memoria vai.
& $asprof -d $Segundos -e cpu --cstack vm -f $destino $proc.Id

Write-Host ""
if (Test-Path $destino) {
  Write-Host "Perfil salvo: $destino" -ForegroundColor Green
  Write-Host "Abra no navegador e me mande o print das barras mais largas." -ForegroundColor DarkGray
} else {
  Write-Host "O profiler nao gerou saida. Rode o PowerShell como administrador e tente de novo." -ForegroundColor Yellow
}
