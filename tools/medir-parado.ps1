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

$PRESETS = @{
  'ceu'        = @{ auroraEnabled='1'; starsEnabled='1'; windowTransparent='1'; uiFps='free'; auroraQuality='high'; reduceMotion='0' }
  'soaurora'   = @{ auroraEnabled='1'; starsEnabled='0'; windowTransparent='1'; uiFps='free'; auroraQuality='high' }
  'soestrelas' = @{ auroraEnabled='0'; starsEnabled='1'; windowTransparent='1'; uiFps='free'; auroraQuality='high' }
  'semceu'     = @{ auroraEnabled='0'; starsEnabled='0'; windowTransparent='1'; uiFps='free'; auroraQuality='high' }
  'opaca'      = @{ auroraEnabled='1'; starsEnabled='1'; windowTransparent='0'; uiFps='free'; auroraQuality='high' }
  'fps60'      = @{ auroraEnabled='1'; starsEnabled='1'; windowTransparent='1'; uiFps='60';   auroraQuality='high' }
  'fps30'      = @{ auroraEnabled='1'; starsEnabled='1'; windowTransparent='1'; uiFps='30';   auroraQuality='high' }
  'auroralow'  = @{ auroraEnabled='1'; starsEnabled='1'; windowTransparent='1'; uiFps='free'; auroraQuality='low' }
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

    if (Test-Path $rede) {
      Write-Host "$nome : DESCARTADA -- houve falha de rede (rede.txt existe)" -ForegroundColor Yellow
      continue
    }

    $h = [Jan]::Maior([uint32]$p.Id)
    if (-not $SemFoco) {
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
