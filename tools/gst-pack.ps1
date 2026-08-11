# Monta o PACOTE do GStreamer que o Astra baixa sozinho na primeira transmissao.
#
# ASCII puro (ver o comentario do gst-poc.ps1 sobre aspa curva no PowerShell 5.1).
#
# O QUE ELE FAZ, e por que assim:
#
# A instalacao oficial do GStreamer tem 2 GB, dos quais 1,6 GB sao bibliotecas
# estaticas (.a) que nunca embarcam. Sobram ~303 MB de DLL. Dessas, o nosso caminho
# (captura D3D11 -> encoder de hardware -> webrtcbin) usa uma fracao: o resto
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
  # gstrswebrtc (livekitwebrtcsink) NAO entra, e sozinho ele eram 15,3 dos 62 MB.
  # Ele abre a PROPRIA conexao com o LiveKit e entra na sala como um participante
  # separado -- apareceria um segundo "voce" na chamada. Descartado o sink, o webrtcbin
  # cru faz o mesmo trabalho pela nossa sinalizacao, e o pacote emagrece um quarto.
  'gstwebrtc'            = 'webrtcbin -- o transporte, negociado pela sinalizacao do Astra'
  'gstrtp'               = 'empacotamento RTP'
  'gstrtpmanager'        = 'jitter buffer, sessao RTP'
  # rtpgccbwe: o estimador de banda do WebRTC (Google Congestion Control). Sem ele o
  # webrtcbin manda no bitrate fixo do encoder e nao desce quando a subida aperta --
  # o video congela em vez de perder nitidez. O webrtc-java de hoje ja traz isso de
  # fabrica, entao ficar sem seria REGREDIR justamente pra quem tem internet ruim.
  'gstrsrtp'             = 'rtpgccbwe -- adaptacao de bitrate a banda disponivel'
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

# Elementos que TEM que existir em qualquer maquina Windows. Encoder de fabricante
# (nvh264enc, qsvh264enc, amfh264enc) NAO entra: eles so se registram se a placa
# correspondente existir, e exigir isso aqui reprovaria o pacote na maquina errada.
# Quem cobre esse caso e a checagem 0 da Valida, que nao depende de hardware nenhum.
$criticos = @(
  'd3d11screencapturesrc','d3d11convert','mfh264enc','h264parse','videoconvert',
  # o transporte: webrtcbin publicando no lugar da PeerConnection do webrtc-java
  'webrtcbin','rtph264pay','rtpopuspay','opusenc','appsrc','audioconvert','audioresample',
  'rtpgccbwe'
)

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

  # 0) ALGUM plugin deixou de carregar?
  #
  # ESTA CHECAGEM NASCEU DE UM ERRO QUE PASSOU. O primeiro pacote publicado saiu sem
  # `gstd3d12-1.0-0.dll` (0,7 MB), e o gstnvcodec e o gstqsv dependem dela no 1.28. Os
  # dois plugins morriam calados na carga: quem tem RTX ou Quick Sync caia no
  # mfh264enc sem nunca saber que o encoder bom estava ali.
  #
  # A validacao nao pegou porque perguntava por ELEMENTO, e `gst-inspect mfh264enc`
  # passa numerinho redondo mesmo com o nvcodec quebrado ao lado. Perguntar "algum
  # plugin falhou?" nao depende de hardware, nao precisa de lista pra manter, e pega
  # de uma vez qualquer dependencia que o encolhimento tirar demais.
  #
  # O registro e apagado ANTES: com cache valido o gst-inspect responde pela memoria e
  # nao chega a abrir DLL nenhuma -- passaria sem testar nada.
  # A saida vai pra ARQUIVO, pelo cmd, e nao pra cano redirecionado.
  #
  # A primeira versao disto usava RedirectStandardOutput/Error como o resto do script e
  # TRAVOU o build por 17 minutos. O gst-inspect pelado despeja um catalogo inteiro no
  # stdout; enquanto o PowerShell le esse stdout ate o fim, o processo enche o cano de
  # stderr (4 KB no Windows) com os avisos de plugin quebrado e para de escrever. Os dois
  # ficam esperando um ao outro. Nas checagens 1 e 2 o padrao passa porque a saida cabe
  # no cano -- aqui nao cabe.
  $reg = Join-Path $Destino 'registry.bin'
  Remove-Item $reg -Force -ErrorAction SilentlyContinue
  $errFile = Join-Path $Destino '..\astra-gst-inspect.err'
  $psi = New-Object Diagnostics.ProcessStartInfo
  $psi.FileName  = "$env:SystemRoot\system32\cmd.exe"
  $psi.Arguments = "/c `"`"$inspectPack`" >nul 2>`"$errFile`"`""
  $psi.UseShellExecute = $false
  $psi.CreateNoWindow = $true
  $psi.EnvironmentVariables['PATH'] = $dBin
  $psi.EnvironmentVariables['GST_PLUGIN_PATH'] = $dPlug
  $psi.EnvironmentVariables['GST_PLUGIN_SYSTEM_PATH'] = ''
  $psi.EnvironmentVariables['GST_REGISTRY'] = $reg
  $p0 = [Diagnostics.Process]::Start($psi)
  if (-not $p0.WaitForExit(60000)) { try { $p0.Kill() } catch {}; return $false }
  # -Raw devolve $null em arquivo vazio, e ai o -match explodiria.
  $err0 = if (Test-Path $errFile) { "$(Get-Content $errFile -Raw)" } else { '' }
  if ($err0 -match 'Failed to load plugin') { return $false }

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
# O gst-launch era so pra validar e sai. O gst-inspect FICA, e de proposito.
#
# Ele custa ~50KB e permite ao Astra descobrir se a maquina tem encoder de hardware
# SEM carregar o GStreamer dentro do proprio processo. Um Gst.init() que falha de forma
# nativa nao lanca excecao em Kotlin: derruba o app. Perguntar num processo filho troca
# "o Astra fechou" por "o processo respondeu com erro" -- e a pergunta e justamente a
# que nao da pra responder a distancia hoje, quando o PC problematico e de outra pessoa.
Remove-Item $launchPack -Force -ErrorAction SilentlyContinue
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
