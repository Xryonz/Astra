# PROVA DE CONCEITO: GStreamer publicando tela no LiveKit com encoder de HARDWARE.
#
# Isto NAO toca no Astra. E um teste de fora, feito pra responder as tres perguntas
# que decidem se a migracao vale — e pra que, se a resposta for "nao", ela custe uma
# tarde e nao um mes:
#
#   1. O GStreamer do Windows traz o plugin `livekitwebrtcsink`? Ele vem do
#      gst-plugins-rs (Rust) e precisa ter sido compilado com a feature `livekit`.
#      Se nao vier pronto, a migracao ganha um passo de compilar Rust por conta.
#   2. Esta maquina tem encoder H264 por HARDWARE visivel pro GStreamer? E qual —
#      NVIDIA (nvh264enc), Intel (qsvh264enc), AMD (amfh264enc) ou o generico D3D11
#      (d3d11h264enc)? Sem isso a migracao nao entrega nada: o ganho INTEIRO e sair
#      do encoder por software.
#   3. O SFU do LiveKit aceita o que esse pipeline manda?
#
# COMO USAR
#   1. Instale o GStreamer (runtime + development), MSVC 64-bit, do site oficial:
#        https://gstreamer.freedesktop.org/download/
#      Marque a instalacao COMPLETA (a tipica deixa plugins de fora).
#   2. Diagnostico, sem publicar nada:
#        powershell -ExecutionPolicy Bypass -File tools\gst-poc.ps1
#   3. Publicando de verdade numa sala de teste:
#        powershell -ExecutionPolicy Bypass -File tools\gst-poc.ps1 -Url wss://SEU.livekit.cloud -Token COLE_AQUI
#
# O TOKEN NAO PASSA POR AQUI NEM POR CHAT. Gere um de teste no painel do LiveKit
# (Settings > Keys > generate token) e cole direto no seu terminal. Ele e temporario
# e some quando voce fechar a janela.

param(
  [string]$Url   = "",
  [string]$Token = "",
  [int]$Segundos = 30
)

$ErrorActionPreference = 'Stop'

function Achar-Gst {
  $c = Get-Command gst-inspect-1.0 -ErrorAction SilentlyContinue
  if ($c) { return Split-Path $c.Source }
  foreach ($p in @(
    "$env:GSTREAMER_1_0_ROOT_MSVC_X86_64\bin",
    "C:\gstreamer\1.0\msvc_x86_64\bin"
  )) { if ($p -and (Test-Path (Join-Path $p 'gst-inspect-1.0.exe'))) { return $p } }
  return $null
}

$bin = Achar-Gst
if (-not $bin) {
  Write-Host ""
  Write-Host "GStreamer nao encontrado." -ForegroundColor Red
  Write-Host "Instale o runtime E o development (MSVC 64-bit) em:" -ForegroundColor DarkGray
  Write-Host "  https://gstreamer.freedesktop.org/download/" -ForegroundColor DarkGray
  Write-Host "Escolha a instalacao COMPLETA — a tipica nao traz os plugins que interessam." -ForegroundColor DarkGray
  exit 1
}
$inspect = Join-Path $bin 'gst-inspect-1.0.exe'
$launch  = Join-Path $bin 'gst-launch-1.0.exe'
Write-Host ""
Write-Host "GStreamer em: $bin" -ForegroundColor Cyan
Write-Host ((& $inspect --version | Select-Object -First 1)) -ForegroundColor DarkGray

function Tem-Elemento([string]$nome) {
  & $inspect $nome *> $null
  return ($LASTEXITCODE -eq 0)
}

# --- Pergunta 1: o plugin do LiveKit existe nesta instalacao? -----------------
Write-Host ""
Write-Host "--- 1. Plugin do LiveKit ---" -ForegroundColor Yellow
$temLiveKit = Tem-Elemento 'livekitwebrtcsink'
if ($temLiveKit) {
  Write-Host "  livekitwebrtcsink: SIM" -ForegroundColor Green
} else {
  Write-Host "  livekitwebrtcsink: NAO" -ForegroundColor Red
  Write-Host "  Vem do gst-plugins-rs compilado com --features livekit. Se nao veio," -ForegroundColor DarkGray
  Write-Host "  a migracao ganha um passo: compilar o plugin Rust pra Windows." -ForegroundColor DarkGray
}
if (Tem-Elemento 'webrtcsink') { Write-Host "  webrtcsink (generico): SIM" -ForegroundColor DarkGray }

# --- Pergunta 2: qual encoder H264 por hardware esta disponivel? --------------
Write-Host ""
Write-Host "--- 2. Encoders H264 ---" -ForegroundColor Yellow
# Ordem = preferencia. Os tres primeiros sao hardware de fabricante; d3d11h264enc e
# a via generica do Windows (usa o que a GPU expuser); os dois ultimos sao SOFTWARE
# e estao aqui so pra registrar o fallback — se so eles aparecerem, a migracao NAO
# resolve o problema desta maquina.
$encoders = [ordered]@{
  'nvh264enc'     = 'NVIDIA NVENC (hardware)'
  'qsvh264enc'    = 'Intel Quick Sync (hardware)'
  'amfh264enc'    = 'AMD AMF (hardware)'
  'd3d11h264enc'  = 'Direct3D11 / Media Foundation (hardware, generico)'
  'mfh264enc'     = 'Media Foundation (hardware, generico)'
  'x264enc'       = 'x264 (SOFTWARE — sem ganho)'
  'openh264enc'   = 'OpenH264 (SOFTWARE — sem ganho)'
}
$hw = @()
foreach ($e in $encoders.Keys) {
  if (Tem-Elemento $e) {
    $ehw = $encoders[$e] -notmatch 'SOFTWARE'
    if ($ehw) { $hw += $e }
    $cor = if ($ehw) { 'Green' } else { 'DarkGray' }
    Write-Host ("  {0,-14} {1}" -f $e, $encoders[$e]) -ForegroundColor $cor
  }
}
if ($hw.Count -eq 0) {
  Write-Host "  Nenhum encoder de hardware. Nesta maquina a migracao nao entrega o ganho." -ForegroundColor Red
}

# --- Captura de tela ----------------------------------------------------------
Write-Host ""
Write-Host "--- 3. Captura de tela ---" -ForegroundColor Yellow
$src = $null
foreach ($s in @('d3d11screencapturesrc','dxgiscreencapsrc','gdiscreencapsrc')) {
  if (Tem-Elemento $s) { Write-Host "  $s : SIM" -ForegroundColor Green; if (-not $src) { $src = $s } }
}
if (-not $src) { Write-Host "  nenhuma fonte de captura de tela" -ForegroundColor Red }

# --- Veredito -----------------------------------------------------------------
Write-Host ""
Write-Host "=== VEREDITO ===" -ForegroundColor Cyan
$pronto = $temLiveKit -and ($hw.Count -gt 0) -and $src
if ($pronto) {
  Write-Host "  Caminho completo disponivel: $src -> $($hw[0]) -> livekitwebrtcsink" -ForegroundColor Green
} else {
  Write-Host "  Faltam pecas (veja acima). Rode este mesmo script na maquina fraca —" -ForegroundColor Yellow
  Write-Host "  a resposta que importa e a DELA, nao a da maquina forte." -ForegroundColor Yellow
}

if (-not $Url -or -not $Token) {
  Write-Host ""
  Write-Host "Sem -Url/-Token: parei no diagnostico. Pra publicar de verdade:" -ForegroundColor DarkGray
  Write-Host "  tools\gst-poc.ps1 -Url wss://SEU.livekit.cloud -Token COLE_AQUI" -ForegroundColor DarkGray
  exit 0
}
if (-not $pronto) { Write-Host ""; Write-Host "Nao publico com pecas faltando." -ForegroundColor Red; exit 1 }

# --- Publicacao ---------------------------------------------------------------
# 30fps e 720p de proposito: e pra medir CUSTO, e o objetivo e comparar com o que o
# Astra gasta hoje no MESMO preset. Comparar 60fps do GStreamer com 30fps do webrtc
# nao diria nada.
$enc = $hw[0]
Write-Host ""
Write-Host "Publicando $Segundos s  ($src -> $enc -> livekitwebrtcsink)" -ForegroundColor Cyan
Write-Host "Olhe o Gerenciador de Tarefas AGORA: o custo do encoder e o numero que importa." -ForegroundColor DarkGray

$pipeline = @(
  "$src", '!',
  'video/x-raw(memory:D3D11Memory),framerate=30/1', '!',
  'videoconvert', '!', 'videoscale', '!',
  'video/x-raw,width=1280,height=720', '!',
  "$enc", '!',
  'h264parse', '!',
  "livekitwebrtcsink", "signaller::ws-url=$Url", "signaller::auth-token=$Token"
)

$p = Start-Process -FilePath $launch -ArgumentList $pipeline -NoNewWindow -PassThru
Start-Sleep -Seconds $Segundos
if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }
Write-Host ""
Write-Host "Fim. Se a sala mostrou a tela, os tres pontos estao respondidos." -ForegroundColor Green
