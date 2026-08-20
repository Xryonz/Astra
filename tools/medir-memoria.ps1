# MEDE A RAM DO ASTRA EM USO REAL.
#
# Uso:
#   .\medir-memoria.ps1                 # mede ate voce apertar Ctrl+C
#   .\medir-memoria.ps1 -Minutos 10     # mede 10 minutos e para sozinho
#
# Abra o Astra, rode isto, e entao USE o app do jeito que doi: entre numa call,
# transmita a tela, role uma conversa longa, deixe aberto um tempo. No fim o script
# imprime os PICOS, que sao o numero que importa -- media esconde justamente o
# momento em que a maquina fraca engasga.
#
# MEDE OS DOIS PROCESSOS. O Astra e a JVM, mas a voz mora num processo separado
# (astra-voz.exe, o sidecar em Go). Quem tem 4 GB de RAM paga a soma dos dois, entao
# olhar so a janela mentiria pra baixo.
#
# O detalhamento por categoria (heap, metaspace, JIT) so aparece se houver um JDK
# instalado com jcmd. Sem ele o script ainda funciona: RSS e o numero que o
# Gerenciador de Tarefas mostra, e ja e o que decide se cabe na maquina.
param([int]$Minutos = 0)

$saida = Join-Path $PSScriptRoot "..\memoria-astra.csv"
$jcmd = (Get-Command jcmd -ErrorAction SilentlyContinue).Source
if (-not $jcmd) {
  $c = Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium","$env:ProgramFiles\Java","$env:USERPROFILE\.gradle\jdks" `
       -Recurse -Filter jcmd.exe -ErrorAction SilentlyContinue | Select-Object -First 1
  $jcmd = $c.FullName
}

function AchaAstra {
  $p = Get-Process -Name Astra -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $p) { $p = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq 'Astra' } | Select-Object -First 1 }
  $p
}

$astra = AchaAstra
if (-not $astra) { Write-Host "Astra nao esta aberto. Abra o app e rode de novo."; exit 1 }
Write-Host "medindo o Astra (PID $($astra.Id))." -ForegroundColor Cyan
if ($jcmd) { Write-Host "jcmd encontrado: heap detalhado incluido." } else { Write-Host "sem jcmd: so RSS (ainda serve)." }
Write-Host "use o app normalmente. Ctrl+C encerra e imprime os picos.`n"

"quando;astra_rss_mb;astra_commit_mb;threads;heap_usado_mb;heap_commit_mb;voz_rss_mb;total_mb" | Set-Content $saida -Encoding UTF8

$picoTotal = 0; $picoAstra = 0; $picoHeap = 0; $amostras = 0
$fim = if ($Minutos -gt 0) { (Get-Date).AddMinutes($Minutos) } else { (Get-Date).AddYears(1) }

try {
  while ((Get-Date) -lt $fim) {
    $astra = AchaAstra
    if (-not $astra) { Write-Host "o Astra fechou."; break }
    $voz = Get-Process -Name "astra-voz" -ErrorAction SilentlyContinue | Measure-Object WorkingSet64 -Sum

    $rss = [math]::Round($astra.WorkingSet64 / 1MB, 1)
    $commit = [math]::Round($astra.PrivateMemorySize64 / 1MB, 1)
    $vozRss = if ($voz.Sum) { [math]::Round($voz.Sum / 1MB, 1) } else { 0 }
    $total = [math]::Round($rss + $vozRss, 1)

    $hUsado = 0; $hCommit = 0
    if ($jcmd) {
      # GC.heap_info nao exige NativeMemoryTracking, entao funciona no app empacotado.
      $txt = & $jcmd $astra.Id GC.heap_info 2>$null | Out-String
      if ($txt -match 'total\s+(\d+)K,\s*used\s+(\d+)K') {
        $hCommit = [math]::Round([int]$Matches[1] / 1024, 1)
        $hUsado  = [math]::Round([int]$Matches[2] / 1024, 1)
      }
    }

    "{0};{1};{2};{3};{4};{5};{6};{7}" -f (Get-Date -Format "HH:mm:ss"), $rss, $commit, $astra.Threads.Count, $hUsado, $hCommit, $vozRss, $total |
      Add-Content $saida -Encoding UTF8

    if ($total -gt $picoTotal) { $picoTotal = $total }
    if ($rss -gt $picoAstra) { $picoAstra = $rss }
    if ($hCommit -gt $picoHeap) { $picoHeap = $hCommit }
    $amostras++

    $cor = if ($total -gt 1024) { "Red" } elseif ($total -gt 700) { "Yellow" } else { "Green" }
    Write-Host ("{0}  janela {1,7} MB   voz {2,6} MB   TOTAL {3,7} MB   heap {4,6} MB" -f `
      (Get-Date -Format "HH:mm:ss"), $rss, $vozRss, $total, $hCommit) -ForegroundColor $cor

    Start-Sleep -Seconds 5
  }
} finally {
  Write-Host "`n--- PICOS em $amostras amostras ---" -ForegroundColor Cyan
  Write-Host ("janela do Astra : {0} MB" -f $picoAstra)
  Write-Host ("heap commitado  : {0} MB" -f $picoHeap)
  Write-Host ("TOTAL (com a voz): {0} MB" -f $picoTotal)
  if ($picoTotal -gt 1024) {
    Write-Host "PASSOU DE 1 GB." -ForegroundColor Red
  } else {
    Write-Host "ficou abaixo de 1 GB." -ForegroundColor Green
  }
  Write-Host "`nplanilha completa: $saida"
  Write-Host "me mande esse arquivo."
}
