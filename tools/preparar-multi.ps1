# Prepara uma SEGUNDA copia do Astra pra abrir como se fosse outra pessoa.
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
# Uso:
#   powershell -ExecutionPolicy Bypass -File tools\preparar-multi.ps1
#   (opcional) -Origem "C:\Astra\versions\0.1.39"

param(
  [string]$Origem = "",
  [string]$Destino = "C:\Astra\multi"
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

Write-Host "origem : $Origem"
Write-Host "destino: $Destino"

# Se a copia estiver aberta, o arquivo fica travado e o copy falha no meio,
# deixando uma pasta pela metade que parece instalada mas nao abre.
$abertos = Get-Process -Name "Astra" -ErrorAction SilentlyContinue |
  Where-Object { $_.Path -like "$Destino*" }
if ($abertos) {
  Write-Host "fechando a copia que estava aberta..."
  $abertos | Stop-Process -Force
  Start-Sleep -Milliseconds 500
}

if (Test-Path $Destino) { Remove-Item $Destino -Recurse -Force }
New-Item -ItemType Directory -Path $Destino -Force | Out-Null
Write-Host "copiando (leva alguns segundos, sao ~300MB)..."
Copy-Item "$Origem\*" $Destino -Recurse -Force

$cfg = Join-Path $Destino "app\Astra.cfg"
if (-not (Test-Path $cfg)) { throw "Nao achei o Astra.cfg em $cfg" }

$linhas = Get-Content $cfg
if ($linhas -match "astra\.multi") {
  Write-Host "cfg ja tinha a flag, nada a fazer."
} else {
  # A flag entra na secao [JavaOptions]. Se ela nao existir no cfg, cria no fim.
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
  # linha a linha procurando a secao literal "[Application]" — com o BOM colado a
  # primeira linha vira "<BOM>[Application]", a secao nunca casa, o app.classpath e
  # o app.mainclass nunca sao lidos e o resultado e "falhou em rodar o JVM".
  # Um arquivo VISUALMENTE identico ao que funciona, quebrado por tres bytes
  # invisiveis. Por isso escrevemos os bytes na mao.
  $semBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($cfg, ($saida -join "`r`n") + "`r`n", $semBom)
  Write-Host "flag -Dastra.multi=2 adicionada."
}

# --- A COPIA NAO PODE SE ATUALIZAR SOZINHA -----------------------------------
# A partir da 0.1.43 o proprio app desliga o updater quando ve -Dastra.multi. Mas
# a copia e feita de um binario JA PRONTO, que pode ser mais velho que isso — e ai
# ele atualiza, extrai em versions/<v>/ (a pasta da instalacao PRINCIPAL, que as
# duas passariam a disputar) e reinicia apontando pra la. Esse exe nao tem a flag
# multi, entao a trava de instancia unica volta a valer, ele ve o Astra principal
# aberto e sai calado: "reinicia e nao liga de novo".
# Fingir uma versao altissima resolve pra QUALQUER binario: nada publicado e mais
# novo que 99.0.0, entao a copia se acha sempre atualizada e nunca baixa nada.
# Pra testar uma versao nova na copia, rode este script de novo.
$texto = [System.IO.File]::ReadAllText($cfg)
if ($texto -notmatch "astra\.version=99\.0\.0") {
  $texto = $texto -replace "java-options=-Dastra\.version=[^\r\n]*", "java-options=-Dastra.version=99.0.0"
  [System.IO.File]::WriteAllText($cfg, $texto, (New-Object System.Text.UTF8Encoding($false)))
  Write-Host "updater desligado na copia (versao fingida 99.0.0)."
}

# Conferencia: se sobrou BOM, o app nao abre e a mensagem de erro nao explica nada.
$primeiros = [System.IO.File]::ReadAllBytes($cfg)[0..2]
if ($primeiros[0] -eq 0xEF -and $primeiros[1] -eq 0xBB -and $primeiros[2] -eq 0xBF) {
  throw "O Astra.cfg saiu com BOM. O app nao vai abrir. (nao deveria acontecer)"
}

Write-Host ""
Write-Host "PRONTO. Abra as duas:"
Write-Host "  1) $Origem\Astra.exe   (sua conta)"
Write-Host "  2) $Destino\Astra.exe  (a outra conta)"
Write-Host ""
Write-Host "A segunda abre na tela de login. Entre com a OUTRA conta."
Write-Host "Lembrete: o app minimiza pra bandeja. Pra fechar de vez, use Sair na bandeja."
