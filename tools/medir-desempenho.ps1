# Mede CPU e RAM do Astra por FASE, pra saber onde o custo realmente mora.
#
# Por que assim: o encoder de video roda DENTRO do Astra.exe (nativo do webrtc) e a
# captura roda no ffmpeg.exe (processo separado). Entao comparando as fases da pra
# separar as tres coisas sem profiler nenhum:
#
#   parado      -> UI/aurora
#   em call     -> UI + audio
#   transmitindo-> UI + audio + ENCODER (Astra.exe)  +  CAPTURA (ffmpeg.exe)
#
#   encoder ~= (transmitindo - em call) do Astra.exe
#   captura  =  ffmpeg.exe
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File tools\medir-desempenho.ps1
# Ele pergunta a fase, mede 30s, e no fim imprime a tabela + salva um CSV.

$ErrorActionPreference = 'Stop'
$cores = (Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors
$csv   = Join-Path $PSScriptRoot 'astra-medicao.csv'
$linhas = @()

Write-Host ""
Write-Host "=== Medidor de desempenho do Astra ===" -ForegroundColor Cyan
Write-Host "$cores nucleos logicos. Cada fase mede 30 segundos." -ForegroundColor DarkGray
Write-Host ""

function Medir([string]$fase, [int]$segundos = 30) {
  # CPU total do processo em segundos; a diferenca no intervalo vira % da maquina.
  function Snapshot {
    $r = @{}
    foreach ($n in @('Astra', 'ffmpeg')) {
      $ps = Get-Process -Name $n -ErrorAction SilentlyContinue
      if ($ps) {
        $r[$n] = @{
          cpu = ($ps | Measure-Object -Property CPU -Sum).Sum
          ram = ($ps | Measure-Object -Property WorkingSet64 -Sum).Sum
        }
      } else {
        $r[$n] = @{ cpu = 0; ram = 0 }
      }
    }
    return $r
  }

  $a = Snapshot
  $t0 = Get-Date
  Start-Sleep -Seconds $segundos
  $b = Snapshot
  $dt = ((Get-Date) - $t0).TotalSeconds

  foreach ($n in @('Astra', 'ffmpeg')) {
    $dcpu = [double]$b[$n].cpu - [double]$a[$n].cpu
    $pct  = if ($dt -gt 0) { [math]::Round(($dcpu / $dt / $cores) * 100, 1) } else { 0 }
    $ram  = [math]::Round($b[$n].ram / 1MB, 0)
    Write-Host ("  {0,-8} CPU {1,5}%   RAM {2,5} MB" -f $n, $pct, $ram)
    $script:linhas += [pscustomobject]@{ fase = $fase; processo = $n; cpu_pct = $pct; ram_mb = $ram }
  }
}

$fases = @(
  @{ nome = 'parado';       instrucao = 'Deixe o Astra ABERTO e sem fazer nada (nao mexa o mouse nele).' },
  @{ nome = 'em_call';      instrucao = 'ENTRE numa call de voz, mas NAO transmita a tela.' },
  @{ nome = 'transmitindo'; instrucao = 'Agora LIGUE a transmissao de tela (de preferencia um video/jogo em movimento).' }
)

foreach ($f in $fases) {
  Write-Host ""
  Write-Host ("--- FASE: {0} ---" -f $f.nome) -ForegroundColor Yellow
  Write-Host ("  {0}" -f $f.instrucao)
  Read-Host "  Quando estiver pronto, aperte ENTER pra medir 30s"
  Medir $f.nome
}

$linhas | Export-Csv -Path $csv -NoTypeInformation -Encoding utf8

# Conta final: quanto e encoder, quanto e captura.
$astraCall  = ($linhas | Where-Object { $_.fase -eq 'em_call'      -and $_.processo -eq 'Astra'  }).cpu_pct
$astraTx    = ($linhas | Where-Object { $_.fase -eq 'transmitindo' -and $_.processo -eq 'Astra'  }).cpu_pct
$ffTx       = ($linhas | Where-Object { $_.fase -eq 'transmitindo' -and $_.processo -eq 'ffmpeg' }).cpu_pct

Write-Host ""
Write-Host "=== RESUMO ===" -ForegroundColor Cyan
Write-Host ("  Encoder (Astra transmitindo - em call): {0}%" -f [math]::Round($astraTx - $astraCall, 1))
Write-Host ("  Captura (ffmpeg.exe):                   {0}%" -f $ffTx)
Write-Host ("  Total transmitindo:                     {0}%" -f [math]::Round($astraTx + $ffTx, 1))
Write-Host ""
Write-Host ("CSV salvo em: {0}" -f $csv) -ForegroundColor DarkGray
Write-Host "Manda esse resumo (ou o CSV) pro Claude." -ForegroundColor DarkGray
