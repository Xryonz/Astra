# Quanto o encoder de HARDWARE economiza de processador contra o de SOFTWARE.
#
# ASCII puro (ver gst-poc.ps1 sobre aspa curva no PowerShell 5.1).
#
# Este e o numero que justifica -- ou mata -- a migracao pro GStreamer. Todo o resto
# do trabalho existe por causa dele, entao ele vem ANTES, nao depois.
#
# O desenho da medicao: MESMO pipeline, MESMA resolucao, MESMO fps, trocando so o
# elemento encoder. Assim a diferenca medida e do encoder e de mais nada -- captura,
# conversao e descarte pesam igual nos dois lados e se cancelam.
#
# `fakesink sync=false` de proposito: sem rede e sem esperar relogio, pra medir o custo
# de COMPRIMIR e nao o de transmitir.
#
# USO
#   powershell -ExecutionPolicy Bypass -File tools\gst-medir-encoder.ps1
#   ...-File tools\gst-medir-encoder.ps1 -Largura 1280 -Altura 720 -Fps 60 -Segundos 20

param(
  [int]$Largura  = 1280,
  [int]$Altura   = 720,
  [int]$Fps      = 60,
  [int]$Segundos = 20
)

$ErrorActionPreference = 'Continue'

$raiz = $null
foreach ($p in @($env:GSTREAMER_1_0_ROOT_MSVC_X86_64, "C:\Program Files\gstreamer\1.0\msvc_x86_64", "C:\gstreamer\1.0\msvc_x86_64")) {
  if ($p -and (Test-Path (Join-Path $p 'bin\gst-launch-1.0.exe'))) { $raiz = $p; break }
}
if (-not $raiz) { Write-Host "GStreamer nao encontrado." -ForegroundColor Red; exit 1 }
$launch  = Join-Path $raiz 'bin\gst-launch-1.0.exe'
$inspect = Join-Path $raiz 'bin\gst-inspect-1.0.exe'
$cores   = (Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors

function Existe([string]$n) { & cmd /c "`"$inspect`" $n >nul 2>nul"; return ($LASTEXITCODE -eq 0) }

# Software primeiro: ele e a LINHA DE BASE. Sem ele medido na mesma maquina, o numero
# do hardware nao significa nada -- "8% de CPU" e otimo ou pessimo dependendo de contra
# o que se compara.
$candidatos = [ordered]@{
  'openh264enc' = 'SOFTWARE (a linha de base -- e o que o Astra usa hoje)'
  'x264enc'     = 'SOFTWARE (referencia)'
  'nvh264enc'   = 'hardware NVIDIA NVENC'
  'qsvh264enc'  = 'hardware Intel Quick Sync'
  'amfh264enc'  = 'hardware AMD AMF'
  'mfh264enc'   = 'hardware Media Foundation'
  'd3d11h264enc'= 'hardware Direct3D 11'
}

Write-Host ""
Write-Host "=== Custo do encoder: hardware x software ===" -ForegroundColor Cyan
Write-Host ("{0}x{1} a {2}fps, {3}s cada, {4} nucleos logicos" -f $Largura, $Altura, $Fps, $Segundos, $cores) -ForegroundColor DarkGray
Write-Host "Deixe algo em MOVIMENTO na tela (video, jogo). Tela parada nao faz o encoder trabalhar." -ForegroundColor DarkGray

$resultados = @()
foreach ($enc in $candidatos.Keys) {
  if (-not (Existe $enc)) { continue }

  $caps = "video/x-raw,width=$Largura,height=$Altura,framerate=$Fps/1"
  $args = @('-q','d3d11screencapturesrc','!','videoconvert','!','videoscale','!',$caps,'!',$enc,'!','h264parse','!','fakesink','sync=false')

  $p = Start-Process -FilePath $launch -ArgumentList $args -NoNewWindow -PassThru
  Start-Sleep -Seconds 3          # deixa negociar formato e aquecer antes de medir
  if ($p.HasExited) {
    Write-Host ("  {0,-14} nao iniciou (elemento existe mas o hardware recusou)" -f $enc) -ForegroundColor DarkGray
    continue
  }

  $p.Refresh()
  $t0 = $p.TotalProcessorTime
  $r0 = Get-Date
  Start-Sleep -Seconds $Segundos
  if ($p.HasExited) {
    Write-Host ("  {0,-14} morreu no meio da medicao" -f $enc) -ForegroundColor DarkGray
    continue
  }
  $p.Refresh()
  $dt   = ((Get-Date) - $r0).TotalSeconds
  $cpu  = ($p.TotalProcessorTime - $t0).TotalSeconds
  $ram  = [math]::Round($p.WorkingSet64/1MB, 0)
  Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue

  # Nucleos e a medida honesta: "%" depende de quantos nucleos a maquina tem, e o
  # ponto todo e comparar maquinas diferentes. 1,25 nucleo e 1,25 nucleo em qualquer PC.
  $nucleos = [math]::Round($cpu / $dt, 2)
  $pct     = [math]::Round(($cpu / $dt / $cores) * 100, 1)
  $eSw     = $candidatos[$enc] -match 'SOFTWARE'
  Write-Host ("  {0,-14} {1,5:N2} nucleos   {2,5:N1}% da maquina   RAM {3,4} MB   {4}" -f $enc, $nucleos, $pct, $ram, $candidatos[$enc]) -ForegroundColor $(if ($eSw) { 'Yellow' } else { 'Green' })
  $resultados += [pscustomobject]@{ enc = $enc; nucleos = $nucleos; sw = $eSw }
}

# A LINHA DE BASE E O openh264, e so ele.
#
# A primeira versao disto pegava o software MAIS CARO da lista e comparava com o
# hardware mais barato. Deu "6,1x mais barato" -- e era mentira, porque o software mais
# caro era o x264, que o Astra nao usa em lugar nenhum. O libwebrtc comprime H264 com
# OpenH264; comparar com outra coisa e escolher o adversario pra ganhar a discussao.
$sw = ($resultados | Where-Object { $_.enc -eq 'openh264enc' } | Select-Object -First 1).nucleos
$hwR = $resultados | Where-Object { -not $_.sw } | Sort-Object nucleos | Select-Object -First 1

Write-Host ""
Write-Host "=== VEREDITO ===" -ForegroundColor Cyan
if ($sw -and $hwR -and $hwR.nucleos -gt 0) {
  Write-Host ("  openh264 (o que o Astra usa) : {0:N2} nucleos" -f $sw)
  Write-Host ("  melhor hardware ({0,-12})  : {1:N2} nucleos" -f $hwR.enc, $hwR.nucleos)
  $ganho = $sw - $hwR.nucleos
  Write-Host ("  ECONOMIA REAL: {0:N2} nucleo(s)  ({1:N1}x)" -f $ganho, ($sw / $hwR.nucleos)) -ForegroundColor $(if ($ganho -ge 0.8) { 'Green' } else { 'Yellow' })
  Write-Host ""
  if ($ganho -lt 0.8) {
    Write-Host "  LEIA COM CUIDADO: menos de um nucleo economizado nao paga uma troca de" -ForegroundColor Yellow
    Write-Host "  motor de video. Antes de migrar, descubra ONDE os outros nucleos estao" -ForegroundColor Yellow
    Write-Host "  indo (tools\perfil-nativo.ps1) -- comprimir talvez nem seja o custo." -ForegroundColor Yellow
  }
  Write-Host "  Estes numeros incluem captura+conversao, que pesam igual nos dois lados;" -ForegroundColor DarkGray
  Write-Host "  a DIFERENCA e do encoder, o valor absoluto nao." -ForegroundColor DarkGray
} else {
  Write-Host "  Faltou um dos dois lados pra comparar." -ForegroundColor Yellow
}
