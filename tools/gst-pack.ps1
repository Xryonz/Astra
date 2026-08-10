# Monta o PACOTE do GStreamer que o Astra baixa sozinho na primeira transmissao.
#
# ASCII puro (ver o comentario do gst-poc.ps1 sobre aspa curva no PowerShell 5.1).
#
# O QUE ELE FAZ, e por que assim:
#
# A instalacao oficial do GStreamer tem 2 GB, dos quais 1,6 GB sao bibliotecas
# estaticas (.a) que nunca embarcam. Sobram ~303 MB de DLL. Dessas, o nosso caminho
# (captura D3D11 -> encoder de hardware -> livekitwebrtcsink) usa uma fracao: o resto
# e encoder de HEVC, encoder de AV1, ffmpeg inteiro, renderizador de SVG e a GTK.
#
# Este script COPIA o necessario e depois ENCOLHE por experimento: tira uma DLL
# grande, testa se os elementos ainda carregam, e so devolve o que fez falta. E
# empirico de proposito -- adivinhar dependencia de DLL no Windows e como se sabe que
# faltou alguma: quando quebra na maquina de quem nao tem GStreamer instalado.
#
# A validacao roda com GST_PLUGIN_SYSTEM_PATH VAZIO. Sem isso, a instalacao do
# sistema salvaria o teste e o pacote sairia quebrado justamente pra quem nao tem
# GStreamer -- ou seja, todo mundo menos quem desenvolve.
#
# USO
#   powershell -ExecutionPolicy Bypass -File tools\gst-pack.ps1
#   powershell -ExecutionPolicy Bypass -File tools\gst-pack.ps1 -SemEncolher   (rapido)

param(
  [string]$Raiz    = "",
  [string]$Destino = "",
  [switch]$SemEncolher
)

$ErrorActionPreference = 'Continue'

# --- onde esta o GStreamer ----------------------------------------------------
if (-not $Raiz) {
  foreach ($p in @(
    $env:GSTREAMER_1_0_ROOT_MSVC_X86_64,
    "C:\Program Files\gstreamer\1.0\msvc_x86_64",
    "C:\gstreamer\1.0\msvc_x86_64"
  )) { if ($p -and (Test-Path (Join-Path $p 'bin\gst-inspect-1.0.exe'))) { $Raiz = $p; break } }
}
if (-not $Raiz) { Write-Host "GStreamer nao encontrado." -ForegroundColor Red; exit 1 }
if (-not $Destino) { $Destino = Join-Path ([IO.Path]::GetTempPath()) "astra-gst-pack" }

$inspect = Join-Path $Raiz 'bin\gst-inspect-1.0.exe'
$versao  = ((& $inspect --version | Select-Object -First 1) -split ' ')[-1]
Write-Host ""
Write-Host "GStreamer $versao em $Raiz" -ForegroundColor Cyan
Write-Host "Montando o pacote em $Destino" -ForegroundColor DarkGray

# --- os plugins do caminho ----------------------------------------------------
# Cada um esta aqui por um motivo declarado. Plugin sem motivo nao entra: ele traz
# dependencias junto, e cada MB aqui vira MB no download de quem tem internet ruim.
$plugins = [ordered]@{
  'gstcoreelements'      = 'queue, fakesink, o basico do pipeline'
  'gstapp'               = 'appsrc/appsink -- a ponte com o codigo Kotlin'
  'gstd3d11'             = 'captura de tela por Direct3D11 (o DXGI que o ffmpeg ja usa)'
  'gstnvcodec'           = 'encoder NVIDIA NVENC'
  'gstqsv'               = 'encoder Intel Quick Sync'
  'gstmediafoundation'   = 'encoder generico do Windows (o fallback de hardware)'
  'gstvideoconvertscale' = 'conversao de cor e reducao'
  'gstvideoparsersbad'   = 'h264parse'
  'gstrswebrtc'          = 'webrtcsink e livekitwebrtcsink'
  'gstwebrtc'            = 'a base WebRTC do GStreamer'
  'gstrtp'               = 'empacotamento RTP'
  'gstrtpmanager'        = 'jitter buffer, sessao RTP'
  'gstdtls'              = 'DTLS -- a criptografia obrigatoria do WebRTC'
  'gstsrtp'              = 'SRTP -- a midia cifrada'
  'gstnice'              = 'ICE (libnice) -- travessia de NAT'
  'gstwasapi2'           = 'microfone e som pelo audio do Windows'
  'gstopus'              = 'codec de voz'
  'gstaudioconvert'      = 'conversao de formato de audio'
  'gstaudioresample'     = 'reamostragem de audio'
  'gstaudioparsers'      = 'opusparse'
  'gsttypefindfunctions' = 'deteccao de formato (o GStreamer reclama sem isto)'
  'gstplayback'          = 'decodebin, usado no lado de receber'
  'gstautodetect'        = 'escolha automatica de dispositivo'
}

# --- copia inicial: plugins + TODAS as DLLs do bin -----------------------------
if (Test-Path $Destino) { Remove-Item $Destino -Recurse -Force }
$dBin  = Join-Path $Destino 'bin'
$dPlug = Join-Path $Destino 'lib\gstreamer-1.0'
New-Item -ItemType Directory -Path $dBin  -Force | Out-Null
New-Item -ItemType Directory -Path $dPlug -Force | Out-Null

$faltando = @()
foreach ($p in $plugins.Keys) {
  $f = Join-Path $Raiz "lib\gstreamer-1.0\$p.dll"
  if (Test-Path $f) { Copy-Item $f $dPlug } else { $faltando += $p }
}
if ($faltando.Count -gt 0) {
  Write-Host ("  plugins ausentes nesta instalacao: {0}" -f ($faltando -join ', ')) -ForegroundColor Yellow
}
Copy-Item (Join-Path $Raiz 'bin\*.dll') $dBin

function Peso($p) { [math]::Round((Get-ChildItem $p -Recurse -File | Measure-Object Length -Sum).Sum/1MB, 1) }
Write-Host ("  ponto de partida: {0} MB" -f (Peso $Destino)) -ForegroundColor DarkGray

# --- validacao: os elementos carregam SO com o que esta no pacote? -------------
# Copia o gst-inspect pra dentro do pacote: assim ele roda com as DLLs do pacote, e
# nao com as da instalacao. E o teste que importa.
Copy-Item $inspect $dBin -Force
Copy-Item (Join-Path $Raiz 'bin\gst-launch-1.0.exe') $dBin -Force
$inspectPack = Join-Path $dBin 'gst-inspect-1.0.exe'
$launchPack  = Join-Path $dBin 'gst-launch-1.0.exe'

$criticos = @('d3d11screencapturesrc','mfh264enc','livekitwebrtcsink','h264parse','opusenc','videoconvert')

# NUNCA remover, mesmo que o teste diga que da.
#
# Isto existe porque carregar um elemento NAO e o mesmo que rodar um. O gst-inspect
# so le os metadados do plugin; ele passa sem tocar em criptografia. O OpenSSL so e
# chamado no aperto de mao DTLS, que acontece quando a chamada comeca -- ou seja, o
# teste aprovaria um pacote que quebra exatamente na hora de entrar numa call.
#
# Quando o raciocinio e o experimento discordam sobre uma dependencia que a
# especificacao do WebRTC torna obrigatoria, o raciocinio ganha.
$protegidas = @(
  'libcrypto-3-x64.dll',  # DTLS/SRTP: obrigatorios no WebRTC, usados so no handshake
  'libssl-3-x64.dll'      # idem
)

function Valida {
  $ok = $true
  # 1) os elementos carregam?
  foreach ($e in $criticos) {
    $psi = New-Object Diagnostics.ProcessStartInfo
    $psi.FileName  = $inspectPack
    $psi.Arguments = $e
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError  = $true
    # O ambiente do processo filho aponta SO pro pacote.
    $psi.EnvironmentVariables['PATH'] = $dBin
    $psi.EnvironmentVariables['GST_PLUGIN_PATH'] = $dPlug
    $psi.EnvironmentVariables['GST_PLUGIN_SYSTEM_PATH'] = ''
    $psi.EnvironmentVariables['GST_REGISTRY'] = Join-Path $Destino 'registry.bin'
    $psi.EnvironmentVariables['GST_REGISTRY_UPDATE'] = 'no'
    $proc = [Diagnostics.Process]::Start($psi)
    $proc.StandardOutput.ReadToEnd() | Out-Null
    $proc.StandardError.ReadToEnd()  | Out-Null
    $proc.WaitForExit()
    if ($proc.ExitCode -ne 0) { $ok = $false }
  }
  if (-not $ok) { return $false }

  # 2) um pipeline RODA de verdade? Captura de tela -> encoder -> parser -> descarte.
  # Carregar um elemento passa com metade das dependencias; negociar formato, abrir a
  # GPU e codificar dez quadros nao. E a diferenca entre "o plugin existe" e "a
  # transmissao funciona", que e a unica que interessa pra quem baixou o pacote.
  $psi = New-Object Diagnostics.ProcessStartInfo
  $psi.FileName  = $launchPack
  $psi.Arguments = '-q d3d11screencapturesrc num-buffers=10 ! videoconvert ! mfh264enc ! h264parse ! fakesink'
  $psi.UseShellExecute = $false
  $psi.RedirectStandardOutput = $true
  $psi.RedirectStandardError  = $true
  $psi.EnvironmentVariables['PATH'] = $dBin
  $psi.EnvironmentVariables['GST_PLUGIN_PATH'] = $dPlug
  $psi.EnvironmentVariables['GST_PLUGIN_SYSTEM_PATH'] = ''
  $psi.EnvironmentVariables['GST_REGISTRY'] = Join-Path $Destino 'registry.bin'
  $p2 = [Diagnostics.Process]::Start($psi)
  $p2.StandardOutput.ReadToEnd() | Out-Null
  $p2.StandardError.ReadToEnd()  | Out-Null
  if (-not $p2.WaitForExit(30000)) {
    try { $p2.Kill() } catch {}
    return $false
  }
  return ($p2.ExitCode -eq 0)
}

Write-Host ""
Write-Host "--- validando o ponto de partida ---" -ForegroundColor Yellow
if (-not (Valida)) {
  Write-Host "  Nem com o bin inteiro os elementos carregam." -ForegroundColor Red
  Write-Host "  Provavel: falta um plugin na lista acima. Rode gst-inspect na mao pra ver qual." -ForegroundColor DarkGray
  exit 1
}
Write-Host "  todos os elementos criticos carregam" -ForegroundColor Green

# --- encolhimento por experimento ---------------------------------------------
if (-not $SemEncolher) {
  Write-Host ""
  Write-Host "--- encolhendo (tira, testa, devolve se fez falta) ---" -ForegroundColor Yellow
  # So vale a pena mexer nas grandes: 15 arquivos concentram 126 dos 174 MB do bin.
  $candidatas = Get-ChildItem $dBin -File -Filter *.dll | Sort-Object Length -Descending | Select-Object -First 40
  $lixeira = Join-Path $Destino '..\astra-gst-lixeira'
  if (Test-Path $lixeira) { Remove-Item $lixeira -Recurse -Force }
  New-Item -ItemType Directory -Path $lixeira -Force | Out-Null
  $tiradas = 0
  foreach ($c in $candidatas) {
    if ($protegidas -contains $c.Name) {
      Write-Host ("  FICA  {0,-38} +{1,6:N1} MB  (protegida)" -f $c.Name, ($c.Length/1MB)) -ForegroundColor Cyan
      continue
    }
    $tmp = Join-Path $lixeira $c.Name
    Move-Item $c.FullName $tmp -Force
    if (Valida) {
      Write-Host ("  fora  {0,-38} -{1,6:N1} MB" -f $c.Name, ($c.Length/1MB)) -ForegroundColor DarkGray
      $tiradas++
    } else {
      Move-Item $tmp $c.FullName -Force
      Write-Host ("  FICA  {0,-38} +{1,6:N1} MB" -f $c.Name, ($c.Length/1MB)) -ForegroundColor Green
    }
  }
  Remove-Item $lixeira -Recurse -Force
  Write-Host ("  {0} DLLs removidas" -f $tiradas) -ForegroundColor DarkGray
}

# O gst-inspect era so pra validar; nao vai no pacote.
Remove-Item $inspectPack -Force -ErrorAction SilentlyContinue
Remove-Item $launchPack  -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $Destino 'registry.bin') -Force -ErrorAction SilentlyContinue

# --- zip ----------------------------------------------------------------------
$zip = Join-Path (Split-Path $Destino) ("gstreamer-{0}-win-x64.zip" -f $versao)
if (Test-Path $zip) { Remove-Item $zip -Force }
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::CreateFromDirectory($Destino, $zip)

# Hash ao lado do zip, no formato do `sha256sum` ("<hash>  <nome>"). E a mesma
# convencao que o auto-update do app ja usa (UpdateService.conferirHash), entao o
# GStreamerPack le do mesmo jeito. Sem isso, um zip trocado no caminho viraria DLL
# desconhecida carregada dentro do processo -- o pior lugar possivel pra confiar.
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
"$hash  $(Split-Path $zip -Leaf)" | Out-File "$zip.sha256" -Encoding ascii -NoNewline

Write-Host ""
Write-Host "=== RESULTADO ===" -ForegroundColor Cyan
Write-Host ("  pasta : {0} MB" -f (Peso $Destino))
Write-Host ("  zip   : {0} MB   {1}" -f ([math]::Round((Get-Item $zip).Length/1MB,1)), $zip) -ForegroundColor Green
Write-Host ("  sha256: {0}" -f $hash) -ForegroundColor DarkGray
Write-Host ""
Write-Host "PUBLICAR (o --prerelease NAO e opcional, ver abaixo):" -ForegroundColor Yellow
Write-Host "  gh release create gstreamer-$versao --repo Xryonz/Astra --prerelease ``" -ForegroundColor Gray
Write-Host "    --title 'GStreamer $versao (pacote de runtime do Astra)' ``" -ForegroundColor Gray
Write-Host "    '$zip' '$zip.sha256'" -ForegroundColor Gray
Write-Host ""
Write-Host "POR QUE --prerelease: o auto-update do app le github.com/<repo>/releases/latest," -ForegroundColor Yellow
Write-Host "e o GitHub aponta esse endereco pra release normal mais recente. Publicar o" -ForegroundColor Yellow
Write-Host "pacote como release normal o tornaria 'latest' e o app pararia de enxergar as" -ForegroundColor Yellow
Write-Host "versoes novas do Astra. Pre-release fica FORA desse calculo." -ForegroundColor Yellow
