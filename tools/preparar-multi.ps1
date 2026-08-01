# Prepara uma SEGUNDA cópia do Astra pra abrir como se fosse outra pessoa.
#
# Pra que serve: testar call, transmissão de tela e "o outro vê na hora?" sem
# precisar de um segundo PC nem incomodar um amigo. As duas janelas rodam o app
# EMPACOTADO de verdade (mesma voz, mesmo webrtc, mesmo tudo) — não é o modo dev.
#
# Como funciona: a cópia ganha -Dastra.multi=2 no Astra.cfg. Essa flag faz duas
# coisas no app, e as duas são necessárias:
#   1. a sessão vai pra uma pasta própria (%APPDATA%\Astra-teste2), senão as duas
#      janelas dividiriam o mesmo login e você veria a MESMA conta duas vezes;
#   2. desliga a trava de instância única, que existe justamente pra impedir
#      duas janelas — sem isso a segunda abre e fecha na hora, sem erro nenhum.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File tools\preparar-multi.ps1
#   (opcional) -Origem "C:\Astra\versions\0.1.37"

param(
  [string]$Origem = "",
  [string]$Destino = "C:\Astra\multi"
)

$ErrorActionPreference = "Stop"

if (-not $Origem) {
  $base = "C:\Astra\versions"
  if (-not (Test-Path $base)) { throw "Não achei $base — instale uma versão primeiro." }
  # Ordena por versão de verdade (1.10 > 1.9); ordem de texto erraria isso.
  $Origem = (Get-ChildItem $base -Directory |
    Sort-Object { try { [version]$_.Name } catch { [version]"0.0.0" } } |
    Select-Object -Last 1).FullName
}
if (-not (Test-Path "$Origem\Astra.exe")) { throw "Não achei Astra.exe em $Origem" }

Write-Host "origem : $Origem"
Write-Host "destino: $Destino"

# Se a cópia estiver aberta, o arquivo fica travado e o copy falha no meio,
# deixando uma pasta pela metade que parece instalada mas não abre.
$abertos = Get-Process -Name "Astra" -ErrorAction SilentlyContinue |
  Where-Object { $_.Path -like "$Destino*" }
if ($abertos) {
  Write-Host "fechando a cópia que estava aberta..."
  $abertos | Stop-Process -Force
  Start-Sleep -Milliseconds 500
}

if (Test-Path $Destino) { Remove-Item $Destino -Recurse -Force }
New-Item -ItemType Directory -Path $Destino -Force | Out-Null
Copy-Item "$Origem\*" $Destino -Recurse -Force

$cfg = Join-Path $Destino "app\Astra.cfg"
if (-not (Test-Path $cfg)) { throw "Não achei o Astra.cfg em $cfg" }

$linhas = Get-Content $cfg
if ($linhas -match "astra\.multi") {
  Write-Host "cfg já tinha a flag — nada a fazer."
} else {
  # A flag entra na seção [JavaOptions]. Se ela não existir no cfg, cria no fim.
  if ($linhas -contains "[JavaOptions]") {
    $saida = foreach ($l in $linhas) {
      $l
      if ($l -eq "[JavaOptions]") { "java-options=-Dastra.multi=2" }
    }
  } else {
    $saida = $linhas + @("", "[JavaOptions]", "java-options=-Dastra.multi=2")
  }
  Set-Content -Path $cfg -Value $saida -Encoding utf8
  Write-Host "flag -Dastra.multi=2 adicionada."
}

Write-Host ""
Write-Host "PRONTO. Abra as duas:"
Write-Host "  1) $Origem\Astra.exe        (sua conta)"
Write-Host "  2) $Destino\Astra.exe       (a outra conta)"
Write-Host ""
Write-Host "A segunda abre na tela de login — entre com a OUTRA conta."
Write-Host "Lembrete: o app minimiza pra bandeja. Pra fechar de verdade, use Sair na bandeja."
