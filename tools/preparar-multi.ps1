# Prepara (e abre) uma SEGUNDA copia do Astra, pra usar como se fosse outra pessoa.
#
# SEM ACENTO NENHUM NESTE ARQUIVO, DE PROPOSITO: o PowerShell 5.1 le .ps1 sem BOM
# como ANSI, e ai todo "a" com til vira dois caracteres e o parser quebra no meio
# de uma string. Ja aconteceu. Texto com acento so via Write-Host de fora, nunca
# dentro do script.
#
# Pra que serve: testar call, transmissao de tela e "o outro ve na hora?" sem
# precisar de um segundo PC nem incomodar um amigo. As duas janelas rodam o app
# EMPACOTADO de verdade (mesma voz, mesmo webrtc, mesmo tudo) - nao e o modo dev.
#
# Como funciona: a copia ganha -Dastra.multi=2 no Astra.cfg. Essa flag faz duas
# coisas no app, e as duas sao necessarias:
#   1. a sessao vai pra uma pasta propria (%APPDATA%\Astra-teste2), senao as duas
#      janelas dividiriam o mesmo login e voce veria a MESMA conta duas vezes;
#   2. desliga a trava de instancia unica, que existe justamente pra impedir duas
#      janelas - sem isso a segunda abre e fecha na hora, sem erro nenhum.
#
# A COPIA NAO SE ATUALIZA SOZINHA, e nem deve: ela se atualizava, extraia na pasta
# da instalacao PRINCIPAL e reiniciava apontando pra la, onde a trava de instancia
# unica a matava calada ("reinicia e nao liga de novo"). Quem atualiza a copia e
# este script, refazendo o espelho a partir da versao instalada. Por isso ele
# roda A CADA ABERTURA em vez de uma vez so: versao nova no principal, versao nova
# na copia, sem ninguem precisar lembrar.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File tools\preparar-multi.ps1
#   -Abrir              sincroniza e ja abre a copia (e o que o atalho faz)
#   -Origem "C:\..."    forca uma versao especifica em vez da mais nova

param(
  [string]$Origem = "",
  [string]$Destino = "C:\Astra\multi",
  [switch]$Abrir
)

$ErrorActionPreference = "Stop"

if (-not $Origem) {
  $base = "C:\Astra\versions"
  if (-not (Test-Path $base)) { throw "Nao achei $base. Instale uma versao primeiro." }
  # Ordena por versao de verdade (0.1.38 > 0.1.9); ordem de texto erraria isso.
  $Origem = (Get-ChildItem $base -Directory |
    Sort-Object { try { [version]$_.Name } catch { [version]"0.0.0" } } |
    Select-Object -Last 1).FullName
}
if (-not (Test-Path "$Origem\Astra.exe")) { throw "Nao achei Astra.exe em $Origem" }

# /MIR apaga o que sobra no destino. Se alguem apontar -Destino pra uma pasta que
# nao e a copia, isso viraria um estrago silencioso.
if ((Test-Path $Destino) -and -not (Test-Path "$Destino\Astra.exe")) {
  if ((Get-ChildItem $Destino -Force | Measure-Object).Count -gt 0) {
    throw "$Destino nao esta vazia e nao parece uma copia do Astra. Nao vou espelhar por cima."
  }
}

Write-Host "origem : $Origem"
Write-Host "destino: $Destino"

# Se a copia estiver aberta, o arquivo fica travado e o espelho falha no meio,
# deixando uma pasta pela metade que parece instalada mas nao abre.
$abertos = Get-Process -Name "Astra" -ErrorAction SilentlyContinue |
  Where-Object { $_.Path -like "$Destino*" }
if ($abertos) {
  Write-Host "fechando a copia que estava aberta..."
  $abertos | Stop-Process -Force
  Start-Sleep -Milliseconds 500
}

# robocopy /MIR em vez de apagar tudo e copiar de novo: sao ~300MB, e entre duas
# versoes muda uma fracao disso. A primeira vez leva os mesmos segundos de antes;
# as seguintes copiam so o que mudou. E o que torna viavel rodar isto A CADA
# abertura, que e o ponto todo.
Write-Host "sincronizando..."
robocopy $Origem $Destino /MIR /NFL /NDL /NJH /NJS /NP /R:1 /W:1 | Out-Null
# Robocopy usa 0-7 pra sucesso (1 = copiou algo, 2 = apagou algo, 3 = os dois...).
# So 8 pra cima e falha de verdade.
if ($LASTEXITCODE -ge 8) { throw "robocopy falhou (codigo $LASTEXITCODE)." }

$cfg = Join-Path $Destino "app\Astra.cfg"
if (-not (Test-Path $cfg)) { throw "Nao achei o Astra.cfg em $cfg" }

# O /MIR acabou de restaurar o cfg ORIGINAL por cima do nosso. E o certo: assim a
# marcacao e refeita do zero a cada sincronia, em vez de empilhar remendo sobre
# remendo de versoes anteriores.
$linhas = Get-Content $cfg
if ($linhas -contains "[JavaOptions]") {
  $saida = foreach ($l in $linhas) {
    $l
    if ($l -eq "[JavaOptions]") { "java-options=-Dastra.multi=2" }
  }
} else {
  $saida = $linhas + @("", "[JavaOptions]", "java-options=-Dastra.multi=2")
}

# SEM BOM, OBRIGATORIO. "Set-Content -Encoding utf8" no PowerShell 5.1 grava os
# tres bytes EF BB BF na frente do arquivo. O lancador do jpackage le o Astra.cfg
# linha a linha procurando a secao literal "[Application]" - com o BOM colado a
# primeira linha vira "<BOM>[Application]", a secao nunca casa, o app.classpath e
# o app.mainclass nunca sao lidos e o resultado e "falhou em rodar o JVM".
# Um arquivo VISUALMENTE identico ao que funciona, quebrado por tres bytes
# invisiveis. Por isso escrevemos os bytes na mao.
$semBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($cfg, ($saida -join "`r`n") + "`r`n", $semBom)

# Da 0.1.43 em diante o proprio app desliga o updater quando ve -Dastra.multi, e
# a versao fica HONESTA na tela Sobre - que e o que interessa quando as duas
# janelas existem justamente pra comparar o mesmo build. Antes disso o unico jeito
# era fingir uma versao altissima (nada publicado e mais novo que 99.0.0, entao a
# copia se acha atualizada e nunca baixa nada). O remendo fica, mas so pra binario
# velho o bastante pra precisar dele.
$texto = [System.IO.File]::ReadAllText($cfg)
$versao = ([regex]::Match($texto, 'astra\.version=([0-9]+\.[0-9]+\.[0-9]+)')).Groups[1].Value
$precisaFingir = $true
if ($versao) {
  try { $precisaFingir = ([version]$versao -lt [version]"0.1.43") } catch { $precisaFingir = $true }
}
if ($precisaFingir) {
  $texto = $texto -replace "java-options=-Dastra\.version=[^\r\n]*", "java-options=-Dastra.version=99.0.0"
  [System.IO.File]::WriteAllText($cfg, $texto, $semBom)
  Write-Host "updater desligado na copia (versao $versao e antiga demais; fingindo 99.0.0)."
} else {
  Write-Host "copia na versao $versao (o proprio app ja desliga o updater no modo multi)."
}

# Conferencia: se sobrou BOM, o app nao abre e a mensagem de erro nao explica nada.
$primeiros = [System.IO.File]::ReadAllBytes($cfg)[0..2]
if ($primeiros[0] -eq 0xEF -and $primeiros[1] -eq 0xBB -and $primeiros[2] -eq 0xBF) {
  throw "O Astra.cfg saiu com BOM. O app nao vai abrir. (nao deveria acontecer)"
}

# Atalho na area de trabalho, criado uma vez. Ele chama ESTE script com -Abrir,
# entao abrir a segunda conta ja sincroniza antes - a copia nunca fica pra tras
# de uma versao sem ninguem perceber.
$atalho = Join-Path ([Environment]::GetFolderPath("Desktop")) "Astra (2a conta).lnk"
if (-not (Test-Path $atalho)) {
  $ws = New-Object -ComObject WScript.Shell
  $lnk = $ws.CreateShortcut($atalho)
  $lnk.TargetPath = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
  $lnk.Arguments = "-ExecutionPolicy Bypass -File `"$PSCommandPath`" -Abrir"
  $lnk.WorkingDirectory = Split-Path $PSCommandPath
  $lnk.Description = "Sincroniza a segunda copia do Astra com a versao instalada e abre"
  if (Test-Path "C:\Astra\astra.ico") { $lnk.IconLocation = "C:\Astra\astra.ico" }
  $lnk.Save()
  Write-Host "atalho criado: $atalho"
}

if ($Abrir) {
  Start-Process "$Destino\Astra.exe"
  Write-Host "abrindo a copia..."
} else {
  Write-Host ""
  Write-Host "PRONTO. Abra as duas:"
  Write-Host "  1) $Origem\Astra.exe   (sua conta)"
  Write-Host "  2) o atalho 'Astra (2a conta)' na area de trabalho"
  Write-Host ""
  Write-Host "A segunda abre na tela de login. Entre com a OUTRA conta."
  Write-Host "Lembrete: o app minimiza pra bandeja. Pra fechar de vez, use Sair na bandeja."
}
