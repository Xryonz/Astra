param(
  [int]$Segundos = 30,
  [int]$Top = 18
)

$ErrorActionPreference = 'Continue'

$proc = Get-Process -Name 'Astra' -ErrorAction SilentlyContinue |
  Sort-Object -Property WorkingSet64 -Descending | Select-Object -First 1
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
  $tmpErr = Join-Path $env:TEMP 'astra-jstack.err'
  $dump = & cmd /c "`"$jstack`" $($proc.Id) 2>`"$tmpErr`""
  $erro = if (Test-Path $tmpErr) { "$(Get-Content $tmpErr -Raw)".Trim() } else { '' }
  foreach ($linha in $dump) {
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

function Snapshot {
  $r = @{}
  $p = Get-Process -Id $proc.Id -ErrorAction SilentlyContinue
  if (-not $p) { return $r }
  foreach ($t in $p.Threads) {
    try { $r[[int]$t.Id] = $t.TotalProcessorTime.TotalSeconds } catch {}
  }
  return $r
}

function CpuFfmpeg {
  $f = Get-Process -Name 'ffmpeg' -ErrorAction SilentlyContinue
  if (-not $f) { return $null }
  return ($f | Measure-Object -Property CPU -Sum).Sum
}

$a  = Snapshot
$ff0 = CpuFfmpeg
$t0 = Get-Date
Start-Sleep -Seconds $Segundos
$b  = Snapshot
$ff1 = CpuFfmpeg
$dt = ((Get-Date) - $t0).TotalSeconds

$linhas = @()
foreach ($id in $b.Keys) {
  $antes = if ($a.ContainsKey($id)) { $a[$id] } else { 0 }
  $delta = $b[$id] - $antes
  if ($delta -le 0.01) { continue }
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

if ($null -ne $ff0 -and $null -ne $ff1) {
  $ffN = [math]::Round(($ff1 - $ff0) / $dt, 3)
  Write-Host ("  ffmpeg.exe (captura, processo a parte): {0:N3} nucleos" -f $ffN) -ForegroundColor Cyan
  Write-Host ("  SOMA (Astra + ffmpeg): {0:N2} nucleos ({1:N1}% da maquina)" -f ($total + $ffN), (($total + $ffN)/$cores*100)) -ForegroundColor Cyan
} else {
  Write-Host "  (ffmpeg.exe nao esta rodando -- voce nao esta transmitindo, ou a captura caiu pro GDI)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "COMO LER:" -ForegroundColor DarkGray
Write-Host "  ffmpeg-cap     = ler os quadros da captura e copiar pro WebRTC" -ForegroundColor DarkGray
Write-Host "  ffmpeg-preview = a previa local (converter + reduzir)" -ForegroundColor DarkGray
Write-Host "  AWT-EventQueue = a interface" -ForegroundColor DarkGray
Write-Host "  (nativa #N)    = threads do WebRTC/Skia -- encoder, rede, renderizacao" -ForegroundColor DarkGray
