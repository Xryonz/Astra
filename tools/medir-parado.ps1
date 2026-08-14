# QUANTO O ASTRA GASTA PARADO -- com as tres travas que faltaram na primeira vez.
#
# ASCII puro (ver gst-poc.ps1 sobre aspa curva no PowerShell 5.1).
#
# POR QUE ESTE ARQUIVO EXISTE
#
# Numa sessao inteira eu medi o custo do app parado seis vezes e tirei tres conclusoes
# CONTRADITORIAS da mesma maquina: "e a aurora", depois "nao e a aurora", depois "e a
# aurora". Nenhuma medicao estava errada em si -- o que estava errado era o que mais
# variava entre elas sem ninguem controlar:
#
#   1. FOCO. Metade das medidas foi com o app na frente e metade atras, sem conferir.
#      Depois que o segundo plano passou a congelar o movimento, essa e a variavel que
#      MAIS muda o numero: 0,001 nucleo contra 0,35.
#   2. ESTADO DA TELA. Uma das medidas pegou o app preso numa tela de carregamento
#      quebrada, com um indicador animado girando pra sempre. Ela mediu o defeito, nao o
#      app -- e foi ela que produziu a conclusao "nao e a aurora", que eu publiquei.
#   3. UMA AMOSTRA SO. Duas leituras a 0,29 e 0,27 viraram "a transparencia nao custa
#      nada". Com uma amostra cada, e ruido, nao resultado.
#
# Este script tranca as tres: confere quem esta em primeiro plano ANTES e DEPOIS de cada
# amostra (e descarta se mudou no meio), recusa medir se o app registrou falha de rede, e
# tira varias amostras por configuracao mostrando todas -- nunca so a media.
#
# USO
#   powershell -ExecutionPolicy Bypass -File tools\medir-parado.ps1
#   powershell -ExecutionPolicy Bypass -File tools\medir-parado.ps1 -Configs 'ceu,semceu'
#   powershell -ExecutionPolicy Bypass -File tools\medir-parado.ps1 -SemFoco
#
# ATENCAO: mexe no ui.properties do dono e devolve no fim (inclusive se der erro).

param(
  [string]$Configs = 'ceu,soaurora,soestrelas,semceu',
  [int]$Amostras = 3,
  [int]$Segundos = 12,
  [switch]$SemFoco
)

$ErrorActionPreference = 'Continue'
$raiz  = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$prefs = Join-Path $env:APPDATA 'Astra\ui.properties'
$rede  = Join-Path $env:LOCALAPPDATA 'Astra\rede.txt'

Add-Type @"
using System;using System.Runtime.InteropServices;
public class Jan {
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr p);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out R r);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  public delegate bool EnumProc(IntPtr h, IntPtr p);
  [StructLayout(LayoutKind.Sequential)] public struct R { public int L,T,Rr,B; }
  public static uint FgPid() { uint p; GetWindowThreadProcessId(GetForegroundWindow(), out p); return p; }
  public static IntPtr Maior(uint alvo) {
    IntPtr best = IntPtr.Zero; int area = 0;
    EnumWindows((h,p) => { uint pid; GetWindowThreadProcessId(h, out pid);
      if (pid==alvo && IsWindowVisible(h)) { R r; GetWindowRect(h, out r);
        int a=(r.Rr-r.L)*(r.B-r.T); if (a>area) { area=a; best=h; } }
      return true; }, IntPtr.Zero);
    return best; }
}
"@

# Cada configuracao muda UMA coisa em relacao a 'ceu'. Trocar duas de uma vez foi como se
# perde a resposta: quando o numero mexe, nao da pra saber qual das duas mexeu.
$PRESETS = @{
  'ceu'        = @{ auroraEnabled='1'; starsEnabled='1'; windowTransparent='1'; uiFps='free'; auroraQuality='high'; reduceMotion='0' }
  'soaurora'   = @{ auroraEnabled='1'; starsEnabled='0'; windowTransparent='1'; uiFps='free'; auroraQuality='high' }
  'soestrelas' = @{ auroraEnabled='0'; starsEnabled='1'; windowTransparent='1'; uiFps='free'; auroraQuality='high' }
  'semceu'     = @{ auroraEnabled='0'; starsEnabled='0'; windowTransparent='1'; uiFps='free'; auroraQuality='high' }
  'opaca'      = @{ auroraEnabled='1'; starsEnabled='1'; windowTransparent='0'; uiFps='free'; auroraQuality='high' }
  'fps60'      = @{ auroraEnabled='1'; starsEnabled='1'; windowTransparent='1'; uiFps='60';   auroraQuality='high' }
  'fps30'      = @{ auroraEnabled='1'; starsEnabled='1'; windowTransparent='1'; uiFps='30';   auroraQuality='high' }
  'auroralow'  = @{ auroraEnabled='1'; starsEnabled='1'; windowTransparent='1'; uiFps='free'; auroraQuality='low' }
  # Ceu ligado, TODO o resto do movimento desligado. E o unico jeito de separar "o custo e
  # o ceu" de "o custo e o resto que se mexe" -- e foi a confusao entre os dois que
  # produziu tres conclusoes contraditorias na primeira rodada.
  'semmovimento' = @{ auroraEnabled='1'; starsEnabled='1'; windowTransparent='1'; uiFps='free'; auroraQuality='high'; reduceMotion='1' }
}

function Fechar-Astra {
  Get-Process -Name java -ErrorAction SilentlyContinue | ForEach-Object {
    $cl = (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)" -ErrorAction SilentlyContinue).CommandLine
    if ($cl -match 'astra') { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
  }
  Start-Sleep -Seconds 3
}

function Achar-Astra {
  Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
    (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)" -ErrorAction SilentlyContinue).CommandLine -match 'astra'
  } | Sort-Object WorkingSet64 -Descending | Select-Object -First 1
}

function Aplicar-Config($nome) {
  $c = $PRESETS[$nome]
  $linhas = Get-Content $prefs
  foreach ($k in $c.Keys) {
    $v = $c[$k]
    if ($linhas -match "^$k=") { $linhas = $linhas -replace "^$k=.*", "$k=$v" }
    else { $linhas += "$k=$v" }
  }
  $linhas | Set-Content $prefs -Encoding utf8
}

Write-Host ""
Write-Host "=== quanto o Astra gasta PARADO ===" -ForegroundColor Cyan
Write-Host ("estado alvo: " + $(if ($SemFoco) { 'SEM foco (segundo plano)' } else { 'COM foco (na frente)' })) -ForegroundColor DarkGray
Write-Host "$Amostras amostras de ${Segundos}s por configuracao; foco conferido antes e depois de cada uma" -ForegroundColor DarkGray
Write-Host ""

$backup = "$prefs.medicao"
Copy-Item $prefs $backup -Force

try {
  foreach ($nome in ($Configs -split ',')) {
    $nome = $nome.Trim()
    if (-not $PRESETS.ContainsKey($nome)) { Write-Host "config desconhecida: $nome" -ForegroundColor Red; continue }

    Fechar-Astra
    Aplicar-Config $nome
    if (Test-Path $rede) { Remove-Item $rede -Force }

    Push-Location (Join-Path $raiz 'mobile-native')
    Start-Process -FilePath '.\gradlew.bat' -ArgumentList ':desktopApp:run','--console=plain' -WindowStyle Minimized
    Pop-Location
    Start-Sleep -Seconds 72

    $p = Achar-Astra
    if (-not $p) { Write-Host "$nome : o app nao subiu" -ForegroundColor Red; continue }

    # TRAVA 2: falha de rede = a tela pode estar num estado de carregamento com
    # indicador animado, e ai a medida e do defeito, nao do app.
    if (Test-Path $rede) {
      Write-Host "$nome : DESCARTADA -- houve falha de rede (rede.txt existe)" -ForegroundColor Yellow
      continue
    }

    # TRAVA 1: colocar o app no estado pedido, e CONFERIR que ele ficou la.
    $h = [Jan]::Maior([uint32]$p.Id)
    if (-not $SemFoco) {
      # minimizar+restaurar e o unico jeito confiavel de trazer pra frente a partir de um
      # processo sem foco (SetForegroundWindow do Windows recusa).
      [void][Jan]::ShowWindow($h, 6); Start-Sleep -Seconds 2
      [void][Jan]::ShowWindow($h, 9); Start-Sleep -Seconds 4
    }

    $linha = @()
    $descartadas = 0
    for ($i = 1; $i -le $Amostras; $i++) {
      $fgAntes = [Jan]::FgPid()
      $p.Refresh(); $t0 = $p.TotalProcessorTime.TotalMilliseconds
      Start-Sleep -Seconds $Segundos
      $p.Refresh(); $t1 = $p.TotalProcessorTime.TotalMilliseconds
      $fgDepois = [Jan]::FgPid()

      $eraFrente = ($fgAntes -eq $p.Id)
      $queria    = (-not $SemFoco)
      if (($fgAntes -ne $fgDepois) -or ($eraFrente -ne $queria)) {
        $descartadas++
        continue
      }
      $linha += [math]::Round(($t1 - $t0) / $Segundos / 1000, 3)
    }

    if ($linha.Count -eq 0) {
      Write-Host ("{0,-12} : TODAS descartadas (foco nao ficou no estado pedido)" -f $nome) -ForegroundColor Yellow
    } else {
      $ord = $linha | Sort-Object
      $mediana = $ord[[math]::Floor($ord.Count / 2)]
      $texto = ($linha -join '  ')
      $aviso = if ($descartadas -gt 0) { "  ($descartadas descartada(s))" } else { '' }
      Write-Host ("{0,-12} : mediana {1,6} nucleo   amostras: {2}{3}" -f $nome, $mediana, $texto, $aviso) -ForegroundColor Green
    }
  }
}
finally {
  Fechar-Astra
  Move-Item $backup $prefs -Force
  Write-Host ""
  Write-Host "prefs do dono restauradas." -ForegroundColor DarkGray
}
