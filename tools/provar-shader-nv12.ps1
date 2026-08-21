# Uso:  .\provar-shader-nv12.ps1
#
# PROVA O SHADER QUE DESENHA A TELA COMPARTILHADA, sem abrir o Astra.
#
# POR QUE ISTO EXISTE. O SkSL nao e compilado pelo Kotlin: ele e compilado em TEMPO DE
# EXECUCAO, quando o componente aparece. Um erro de sintaxe faz `makeForShader` devolver
# nulo, o componente cai no silencio, e o que se ve e uma area preta sem nenhuma
# mensagem em lugar nenhum. Nada no build pega isso.
#
# E a conversao de cor e pior: ela nao FALHA, ela sai errada. Faixa trocada (estudio
# contra cheia) deixa a imagem lavada; conta de coluna errada no plano de cor mistura as
# cores dos blocos vizinhos. Os dois parecem defeito do decodificador, que e onde
# ninguem vai achar.
#
# O QUE ELE FAZ: extrai o SkSL do proprio `TelaCompartilhada.kt` (para nao existir uma
# copia que envelhece), compila, monta um quadro NV12 sintetico com cores conhecidas,
# desenha pelo mesmo caminho do app e confere os pixels que saem.
#
# Precisa das dependencias ja baixadas pelo Gradle -- rode um build antes se for maquina
# nova. Nao precisa de conta, de rede, nem de call.

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
$fonte = Join-Path $raiz "mobile-native\desktopApp\src\main\kotlin\app\astra\desktop\ui\TelaCompartilhada.kt"
$provas = Join-Path $PSScriptRoot "provas-do-shader"
$trabalho = Join-Path $env:TEMP "astra-prova-shader"

if (-not (Test-Path $fonte)) { throw "nao achei $fonte" }
New-Item -ItemType Directory -Force -Path $trabalho | Out-Null

# 1. O SkSL sai do arquivo Kotlin, entre a abertura e o fechamento do texto cru.
$linhas = Get-Content $fonte
$dentro = $false
$sksl = foreach ($l in $linhas) {
  if (-not $dentro) { if ($l -match '^private const val SKSL_NV12 = """') { $dentro = $true }; continue }
  if ($l -match '^"""') { break }
  $l
}
if (-not $sksl) { throw "nao achei o bloco SKSL_NV12 em TelaCompartilhada.kt" }
$arquivoSksl = Join-Path $trabalho "nv12.sksl"
# SEM MARCA DE ORDEM DE BYTES. `Out-File -Encoding utf8` no PowerShell 5.1 escreve os
# tres bytes da marca no comeco, e o compilador de SkSL responde "error: 1: invalid
# token" -- apontando para a linha 1 de um shader que nao tem nada de errado. O
# WriteAllText do .NET escreve UTF-8 limpo.
[System.IO.File]::WriteAllText($arquivoSksl, ($sksl -join "`n"))
"SkSL extraido: $(($sksl | Measure-Object -Line).Lines) linhas"

# 2. O caminho de classes sai do cache do Gradle. O skiko puxa corrotinas por dentro --
#    sem elas o erro que aparece e "kotlinx/coroutines/GlobalScope", que nao lembra em
#    nada um problema de shader.
$cache = Join-Path $env:USERPROFILE ".gradle\caches\modules-2"
function AchaJar($padrao) {
  Get-ChildItem $cache -Recurse -Filter $padrao -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch "sources" } | Select-Object -First 1 -ExpandProperty FullName
}
$jars = @(
  Get-ChildItem $cache -Recurse -Filter "skiko-awt*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch "sources" } | ForEach-Object { $_.FullName }
)
foreach ($p in @("annotations-2*.jar", "kotlin-stdlib-2*.jar", "kotlinx-coroutines-core-jvm-*.jar")) {
  $j = AchaJar $p
  if ($j) { $jars += $j }
}
if ($jars.Count -lt 3) { throw "faltam dependencias no cache do Gradle -- rode um build primeiro" }
$cp = $jars -join ";"

# 3. Compila e roda as duas provas.
$falhou = $false
foreach ($nome in @("ProvaDoShader", "ProvaDasCores")) {
  $java = Join-Path $provas "$nome.java"
  if (-not (Test-Path $java)) { throw "nao achei $java" }
  & javac -nowarn -cp $cp -d $trabalho $java
  if ($LASTEXITCODE -ne 0) { throw "$nome nao compilou" }
  ""
  "--- $nome ---"
  & java -cp "$cp;$trabalho" $nome $arquivoSksl
  if ($LASTEXITCODE -ne 0) { $falhou = $true }
}

""
if ($falhou) { "REPROVADO"; exit 1 } else { "o shader da tela compartilhada esta correto" }
