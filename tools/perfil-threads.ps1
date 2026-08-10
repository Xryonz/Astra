# ONDE o processador do Astra esta indo, por THREAD.
#
# ASCII puro (ver gst-poc.ps1 sobre aspa curva no PowerShell 5.1).
#
# POR QUE ISTO E NAO UM PROFILER DE VERDADE:
#
# O async-profiler nao tem build pra Windows e o projeto nao pretende fazer -- so
# Linux e macOS. O JFR roda aqui, mas so enxerga pilha JAVA, e metade do custo desta
# aplicacao e nativo (webrtc, skia, ffmpeg). Sobrava ETW (PerfView/WPA), que responde
# por MODULO e exige baixar ferramenta e aprender a ler.
#
# Este script pega o atalho: cada thread do Astra TEM NOME (ffmpeg-cap, ffmpeg-preview,
# AWT-EventQueue, as threads internas do WebRTC), e o Windows sabe quanto processador
# cada uma gastou. Duas leituras separadas por N segundos dao o consumo no intervalo,
# por nome. Nao diz qual FUNCAO custa -- diz qual PARTE do app custa, que e a pergunta
# que esta aberta: e o encoder, a captura, a previa, ou a interface?
#
# USO
#   1. Abra o Astra INSTALADO (nao o gradlew run: la o processo se chama java).
#   2. Entre numa call e LIGUE a transmissao, com video/jogo em MOVIMENTO.
#   3. powershell -ExecutionPolicy Bypass -File tools\perfil-threads.ps1
#   4. Mande o resultado.

param(
  [int]$Segundos = 30,
  [int]$Top = 18
)

$ErrorActionPreference = 'Continue'

$proc = Get-Process -Name 'Astra' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $proc) {
  Write-Host ""
  Write-Host "O Astra nao esta aberto." -ForegroundColor Red
  Write-Host "Abra o app INSTALADO. Pelo gradlew run o processo se chama java, e este" -ForegroundColor DarkGray
  Write-Host "script nao acha (nem deveria: medir o build de desenvolvimento mede outra coisa)." -ForegroundColor DarkGray
  exit 1
}

$cores = (Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors
Write-Host ""
Write-Host ("Astra em PID {0}, {1} nucleos logicos. Medindo {2}s." -f $proc.Id, $cores, $Segundos) -ForegroundColor Cyan
Write-Host "Mantenha a transmissao LIGADA e com movimento na tela ate acabar." -ForegroundColor DarkGray

# --- nomes das threads via jstack -------------------------------------------------
# O jstack imprime, por thread: nome, `nid=0x<hex>` (id nativo) e `cpu=<ms>`. O nid e o
# que liga o nome ao numero que o Windows conhece.
#
# Pode falhar: o runtime empacotado (jlink) nao traz jstack, e o do JDK do sistema
# precisa conseguir se anexar. Se falhar, seguimos sem nome -- o consumo por thread
# continua valendo, so fica anonimo.
function Achar-Jstack {
  $c = Get-Command jstack -ErrorAction SilentlyContinue
  if ($c) { return $c.Source }
  foreach ($p in @($env:JAVA_HOME, "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot")) {
    if ($p) { $j = Join-Path $p 'bin\jstack.exe'; if (Test-Path $j) { return $j } }
  }
  $g = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Filter jstack.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($g) { return $g.FullName }
  return $null
}

$nomes = @{}
$jstack = Achar-Jstack
if ($jstack) {
  # O erro do jstack vem JUNTO (2>&1) e nao pro nada (2>nul). A primeira versao disto
  # engolia a mensagem, e quando o jstack nao anexou o script disse so "sem nomes" --
  # inutil pra saber o que fazer. O motivo real costuma ser: o Astra rodando com
  # privilegio diferente do terminal, ou o JDK do sistema mais NOVO que o runtime
  # empacotado do app (o attach so vai de igual pra igual, ou de menor pra maior).
  $tmpErr = Join-Path $env:TEMP 'astra-jstack.err'
  $dump = & cmd /c "`"$jstack`" $($proc.Id) 2>`"$tmpErr`""
  # -Raw devolve $null em arquivo VAZIO (nao string vazia), e ai o .Trim() explode.
  $erro = if (Test-Path $tmpErr) { "$(Get-Content $tmpErr -Raw)".Trim() } else { '' }
  foreach ($linha in $dump) {
    # DOIS FORMATOS, porque o JDK 21 mudou e me pegou:
    #   ate o 17: "nome" #12 daemon prio=5 ... nid=0x1a2b ...      (HEXADECIMAL)
    #   do 21:    "nome" #12 [6699] daemon prio=5 ... nid=6699 ... (DECIMAL)
    # O regex antigo exigia o "0x" e nao casava NADA no 21 -- o script rodava, dizia
    # "sem nomes" e a saida vinha anonima, que e pior que falhar: parece que funcionou.
    $m = [regex]::Match($linha, '^"(?<nome>[^"]+)".*\snid=(?<nid>0x[0-9a-fA-F]+|\d+)')
    if ($m.Success) {
      $raw = $m.Groups['nid'].Value
      $id  = if ($raw.StartsWith('0x')) { [Convert]::ToInt32($raw.Substring(2), 16) } else { [int]$raw }
      $nomes[$id] = $m.Groups['nome'].Value
    }
  }
}
if ($nomes.Count -gt 0) {
  Write-Host ("  {0} threads Java identificadas por nome" -f $nomes.Count) -ForegroundColor DarkGray
} else {
  Write-Host "  sem nomes -- o consumo sai anonimo, mas sai" -ForegroundColor Yellow
  if ($erro) { Write-Host ("  motivo: {0}" -f ($erro -split "`n")[0]) -ForegroundColor Yellow }
  Write-Host "  tente: abrir este terminal como ADMINISTRADOR (o attach exige mesmo nivel" -ForegroundColor DarkGray
  Write-Host "  de privilegio que o processo alvo)." -ForegroundColor DarkGray
}

# --- duas leituras ----------------------------------------------------------------
function Snapshot {
  $r = @{}
  $p = Get-Process -Id $proc.Id -ErrorAction SilentlyContinue
  if (-not $p) { return $r }
  foreach ($t in $p.Threads) {
    try { $r[[int]$t.Id] = $t.TotalProcessorTime.TotalSeconds } catch {}
  }
  return $r
}

$a  = Snapshot
$t0 = Get-Date
Start-Sleep -Seconds $Segundos
$b  = Snapshot
$dt = ((Get-Date) - $t0).TotalSeconds

$linhas = @()
foreach ($id in $b.Keys) {
  $antes = if ($a.ContainsKey($id)) { $a[$id] } else { 0 }
  $delta = $b[$id] - $antes
  if ($delta -le 0.01) { continue }   # thread parada nao interessa
  $linhas += [pscustomobject]@{
    thread  = if ($nomes.ContainsKey($id)) { $nomes[$id] } else { "(nativa #$id)" }
    nucleos = [math]::Round($delta / $dt, 3)
    pct     = [math]::Round(($delta / $dt / $cores) * 100, 1)
  }
}

$linhas = $linhas | Sort-Object nucleos -Descending
$total  = ($linhas | Measure-Object nucleos -Sum).Sum

Write-Host ""
Write-Host "=== onde o processador foi ===" -ForegroundColor Cyan
$linhas | Select-Object -First $Top | ForEach-Object {
  $cor = if ($_.nucleos -ge 0.20) { 'Yellow' } elseif ($_.nucleos -ge 0.05) { 'Gray' } else { 'DarkGray' }
  Write-Host ("  {0,7:N3} nucleos  {1,5:N1}%   {2}" -f $_.nucleos, $_.pct, $_.thread) -ForegroundColor $cor
}
Write-Host ""
Write-Host ("  TOTAL do Astra: {0:N2} nucleos ({1:N1}% da maquina)" -f $total, ($total/$cores*100)) -ForegroundColor Cyan

$ff = Get-Process -Name 'ffmpeg' -ErrorAction SilentlyContinue
if ($ff) {
  Write-Host "  (o ffmpeg.exe da captura e processo SEPARADO -- rode tools\medir-desempenho.ps1 pra ele)" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "COMO LER:" -ForegroundColor DarkGray
Write-Host "  ffmpeg-cap     = ler os quadros da captura e copiar pro WebRTC" -ForegroundColor DarkGray
Write-Host "  ffmpeg-preview = a previa local (converter + reduzir)" -ForegroundColor DarkGray
Write-Host "  AWT-EventQueue = a interface" -ForegroundColor DarkGray
Write-Host "  (nativa #N)    = threads do WebRTC/Skia -- encoder, rede, renderizacao" -ForegroundColor DarkGray
