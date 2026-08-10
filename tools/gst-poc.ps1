# PROVA DE CONCEITO: GStreamer publicando tela no LiveKit com encoder de HARDWARE.
#
# ASCII PURO DE PROPOSITO. O Windows PowerShell 5.1 le .ps1 como ANSI quando nao ha
# BOM, entao um travessao vira aspa curva -- e o PowerShell aceita aspa curva como
# delimitador de string. O arquivo inteiro desanda a partir dali. Nada de acento aqui.
#
# Isto NAO toca no Astra. E um teste de fora, feito pra responder as tres perguntas
# que decidem se a migracao vale -- e pra que, se a resposta for "nao", ela custe uma
# tarde e nao um mes:
#
#   1. O GStreamer do Windows traz o plugin `livekitwebrtcsink`? Ele vem do
#      gst-plugins-rs (Rust) e precisa ter sido compilado com a feature `livekit`.
#      Se nao vier pronto, a migracao ganha um passo de compilar Rust por conta.
#   2. Esta maquina tem encoder H264 por HARDWARE visivel pro GStreamer? E qual --
#      NVIDIA (nvh264enc), Intel (qsvh264enc), AMD (amfh264enc) ou o generico D3D11
#      (d3d11h264enc)? Sem isso a migracao nao entrega nada: o ganho INTEIRO e sair
#      do encoder por software.
#   3. O SFU do LiveKit aceita o que esse pipeline manda?
#
# COMO USAR
#   1. GStreamer instalado (runtime + development), MSVC 64-bit, instalacao Completa.
#   2. Diagnostico, sem publicar nada:
#        powershell -ExecutionPolicy Bypass -File tools\gst-poc.ps1
#   3. Publicando de verdade numa sala de teste:
#        powershell -ExecutionPolicy Bypass -File tools\gst-poc.ps1 -Url wss://SEU.livekit.cloud -Token COLE_AQUI
#
# O TOKEN NAO PASSA POR CHAT. Gere um de teste no painel do LiveKit e cole direto no
# seu terminal. Ele e temporario e some quando a janela fechar.

param(
  [string]$Url   = "",
  [string]$Token = "",
  [int]$Segundos = 30
)

# 'Continue' e nao 'Stop' DE PROPOSITO: o gst-inspect escreve "No such element" no
# stderr quando o elemento nao existe, e no PowerShell 5.1 stderr de executavel
# nativo vira ErrorRecord. Com 'Stop', a primeira ausencia -- que e justamente o que
# este script existe pra descobrir -- mataria o diagnostico no meio.
$ErrorActionPreference = 'Continue'

function Achar-Gst {
  $c = Get-Command gst-inspect-1.0 -ErrorAction SilentlyContinue
  if ($c) { return Split-Path $c.Source }
  $cands = @(
    "$env:GSTREAMER_1_0_ROOT_MSVC_X86_64\bin",
    "C:\gstreamer\1.0\msvc_x86_64\bin",
    "C:\Program Files\gstreamer\1.0\msvc_x86_64\bin"
  )
  foreach ($p in $cands) {
    if ($p -and (Test-Path (Join-Path $p 'gst-inspect-1.0.exe'))) { return $p }
  }
  return $null
}

$bin = Achar-Gst
if (-not $bin) {
  Write-Host ""
  Write-Host "GStreamer nao encontrado." -ForegroundColor Red
  Write-Host "Instale runtime E development (MSVC 64-bit), instalacao Completa:" -ForegroundColor DarkGray
  Write-Host "  https://gstreamer.freedesktop.org/download/" -ForegroundColor DarkGray
  Write-Host "Se ja instalou, reabra o terminal (o instalador mexe no PATH)." -ForegroundColor DarkGray
  exit 1
}
$inspect = Join-Path $bin 'gst-inspect-1.0.exe'
$launch  = Join-Path $bin 'gst-launch-1.0.exe'
Write-Host ""
Write-Host "GStreamer em: $bin" -ForegroundColor Cyan
$versao = (& $inspect --version | Select-Object -First 1)
Write-Host "  $versao" -ForegroundColor DarkGray

function Tem-Elemento([string]$nome) {
  # A redirecao vai pelo cmd, nao pelo PowerShell: assim o stderr do gst-inspect
  # nunca entra na maquinaria de erro do PS. So o codigo de saida interessa.
  & cmd /c "`"$inspect`" $nome >nul 2>nul"
  return ($LASTEXITCODE -eq 0)
}

# --- 1. o plugin do LiveKit existe nesta instalacao? --------------------------
Write-Host ""
Write-Host "--- 1. Plugin do LiveKit ---" -ForegroundColor Yellow
$temLiveKit = Tem-Elemento 'livekitwebrtcsink'
if ($temLiveKit) {
  Write-Host "  livekitwebrtcsink: SIM" -ForegroundColor Green
} else {
  Write-Host "  livekitwebrtcsink: NAO" -ForegroundColor Red
  Write-Host "  Vem do gst-plugins-rs compilado com --features livekit." -ForegroundColor DarkGray
  Write-Host "  Sem ele, a migracao ganha um passo: compilar o plugin Rust pra Windows." -ForegroundColor DarkGray
}
if (Tem-Elemento 'webrtcsink') {
  Write-Host "  webrtcsink (generico): SIM" -ForegroundColor DarkGray
} else {
  Write-Host "  webrtcsink (generico): NAO -- o pacote rswebrtc nao veio nesta instalacao" -ForegroundColor DarkGray
}

# --- 2. encoders H264 ---------------------------------------------------------
Write-Host ""
Write-Host "--- 2. Encoders H264 ---" -ForegroundColor Yellow
# Ordem = preferencia. Os tres primeiros sao hardware de fabricante; d3d11h264enc e
# mfh264enc sao a via generica do Windows; os dois ultimos sao SOFTWARE e estao aqui
# so pra registrar o fallback. Se SO eles aparecerem, a migracao nao resolve nada
# nesta maquina, porque o ganho inteiro seria sair do encoder por software.
$nomes = @('nvh264enc','qsvh264enc','amfh264enc','d3d11h264enc','mfh264enc','x264enc','openh264enc')
$desc  = @{
  'nvh264enc'    = 'NVIDIA NVENC (hardware)'
  'qsvh264enc'   = 'Intel Quick Sync (hardware)'
  'amfh264enc'   = 'AMD AMF (hardware)'
  'd3d11h264enc' = 'Direct3D11 (hardware, generico)'
  'mfh264enc'    = 'Media Foundation (hardware, generico)'
  'x264enc'      = 'x264 (SOFTWARE - sem ganho)'
  'openh264enc'  = 'OpenH264 (SOFTWARE - sem ganho)'
}
$hw = @()
foreach ($e in $nomes) {
  if (Tem-Elemento $e) {
    $ehw = ($desc[$e] -notmatch 'SOFTWARE')
    if ($ehw) { $hw += $e; $cor = 'Green' } else { $cor = 'DarkGray' }
    Write-Host ("  {0,-14} {1}" -f $e, $desc[$e]) -ForegroundColor $cor
  }
}
if ($hw.Count -eq 0) {
  Write-Host "  Nenhum encoder de hardware visivel." -ForegroundColor Red
  Write-Host "  Nesta maquina a migracao nao entrega o ganho." -ForegroundColor Red
}

# --- 3. captura de tela -------------------------------------------------------
Write-Host ""
Write-Host "--- 3. Captura de tela ---" -ForegroundColor Yellow
$src = $null
foreach ($s in @('d3d11screencapturesrc','dxgiscreencapsrc','gdiscreencapsrc')) {
  if (Tem-Elemento $s) {
    Write-Host "  $s : SIM" -ForegroundColor Green
    if (-not $src) { $src = $s }
  }
}
if (-not $src) { Write-Host "  nenhuma fonte de captura de tela" -ForegroundColor Red }

# --- veredito -----------------------------------------------------------------
Write-Host ""
Write-Host "=== VEREDITO ===" -ForegroundColor Cyan
$pronto = ($temLiveKit -and ($hw.Count -gt 0) -and $src)
if ($pronto) {
  Write-Host ("  Caminho completo: {0} -> {1} -> livekitwebrtcsink" -f $src, $hw[0]) -ForegroundColor Green
} else {
  Write-Host "  Faltam pecas (veja acima)." -ForegroundColor Yellow
}
Write-Host "  Rode este mesmo script na maquina FRACA: a resposta que decide e a dela." -ForegroundColor DarkGray

if (-not $Url -or -not $Token) {
  Write-Host ""
  Write-Host "Sem -Url/-Token: parei no diagnostico. Pra publicar de verdade:" -ForegroundColor DarkGray
  Write-Host "  tools\gst-poc.ps1 -Url wss://SEU.livekit.cloud -Token COLE_AQUI" -ForegroundColor DarkGray
  exit 0
}
if (-not $pronto) {
  Write-Host ""
  Write-Host "Nao publico com pecas faltando." -ForegroundColor Red
  exit 1
}

# --- publicacao ---------------------------------------------------------------
# 720p30 de proposito: e pra medir CUSTO comparavel com o que o Astra gasta hoje no
# MESMO preset. Comparar 60fps do GStreamer com 30fps do webrtc nao diria nada.
$enc = $hw[0]
Write-Host ""
Write-Host ("Publicando {0}s -- {1} -> {2} -> livekitwebrtcsink" -f $Segundos, $src, $enc) -ForegroundColor Cyan
Write-Host "Olhe o Gerenciador de Tarefas AGORA. O custo do encoder e o numero que importa." -ForegroundColor DarkGray

$args = @(
  $src, '!',
  'videoconvert', '!',
  'videoscale', '!',
  'video/x-raw,width=1280,height=720,framerate=30/1', '!',
  $enc, '!',
  'h264parse', '!',
  'livekitwebrtcsink', "signaller::ws-url=$Url", "signaller::auth-token=$Token"
)

$p = Start-Process -FilePath $launch -ArgumentList $args -NoNewWindow -PassThru
Start-Sleep -Seconds $Segundos
if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }
Write-Host ""
Write-Host "Fim. Se a sala mostrou a tela, os tres pontos estao respondidos." -ForegroundColor Green
