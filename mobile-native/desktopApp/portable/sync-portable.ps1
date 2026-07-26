# sync-portable.ps1 - atualiza a instalacao PORTATIL do dono (C:\Astra) com o
# build recem-empacotado, pra o atalho sempre abrir a versao mais nova sem ele
# fazer nada na mao. Rodar em TODA release do desktop, depois de:
#   ./gradlew :desktopApp:zipDistributable -Pastra.distDir=C:/astra-dist
#
# O que faz (idempotente): copia o app-image novo pra versions\<v>\, arquiva o zip
# em zips\, e garante launcher + icone + atalhos (pasta + area de trabalho). O
# launch.vbs sempre abre a MAIOR versao, entao adicionar versions\<v> ja basta.
#
# ASCII puro de proposito: PowerShell 5.1 le script sem BOM como ANSI e quebra com
# acento/em-dash. NAO usa Remove-Item sob C:\Astra (o sandbox rejeita).

param(
  [string]$Version = ""
)
$ErrorActionPreference = "Stop"

$repo = Split-Path $PSScriptRoot -Parent            # ...\desktopApp
$dist = "C:\astra-dist"
$root = "C:\Astra"; $versions = Join-Path $root "versions"; $zips = Join-Path $root "zips"

# Versao: do parametro, senao le o astraVersion do build.gradle.kts.
if (-not $Version) {
  $gradle = Get-Content (Join-Path $repo "build.gradle.kts") -Raw
  if ($gradle -match 'val astraVersion\s*=\s*"([^"]+)"') { $Version = $Matches[1] }
}
if (-not $Version) { throw "Nao consegui descobrir a versao (passe -Version)." }
Write-Host "Sincronizando portatil -> versao $Version"

New-Item -ItemType Directory -Force $versions | Out-Null
New-Item -ItemType Directory -Force $zips | Out-Null

# versions\<v> (do app-image recem-gerado). Se ja existe, assume integro e pula.
$img = Join-Path $dist "compose\binaries\main\app\Astra"
$dst = Join-Path $versions $Version
if (Test-Path (Join-Path $dst "Astra.exe")) {
  Write-Host "  versions\$Version ja existe - ok"
} else {
  if (-not (Test-Path (Join-Path $img "Astra.exe"))) { throw "app-image nao encontrado em $img - empacote primeiro (zipDistributable)." }
  Copy-Item -Recurse $img $dst
  Write-Host "  versions\$Version copiado"
}

# zips\ (historico)
$zip = Join-Path $dist "Astra-$Version-win-x64.zip"
if (Test-Path $zip) { Copy-Item $zip $zips -Force; Write-Host "  zip arquivado" }

# launcher + icone (garante presenca)
Copy-Item (Join-Path $PSScriptRoot "launch.vbs") $root -Force
Copy-Item (Join-Path $repo "icons\astra.ico") (Join-Path $root "astra.ico") -Force

# atalhos (pasta + area de trabalho) -> wscript + vbs
$ws = New-Object -ComObject WScript.Shell
foreach ($lp in @((Join-Path $root "Astra.lnk"), (Join-Path ([Environment]::GetFolderPath('Desktop')) "Astra.lnk"))) {
  $lnk = $ws.CreateShortcut($lp)
  $lnk.TargetPath = "$env:SystemRoot\System32\wscript.exe"
  $lnk.Arguments = '"' + (Join-Path $root "launch.vbs") + '"'
  $lnk.WorkingDirectory = $root
  $lnk.IconLocation = (Join-Path $root "astra.ico")
  $lnk.Description = "Astra - sempre a versao mais nova"
  $lnk.Save()
}

$best = Get-ChildItem $versions -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'Astra.exe') } |
  Sort-Object { [version]($_.Name) } | Select-Object -Last 1
Write-Host "OK. versions: $((Get-ChildItem $versions -Directory).Name -join ', ')"
Write-Host "atalho abre: $($best.Name)"
