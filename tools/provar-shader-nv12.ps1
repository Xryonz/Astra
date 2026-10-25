$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
$fonte = Join-Path $raiz "mobile-native\desktopApp\src\main\kotlin\app\astra\desktop\ui\TelaCompartilhada.kt"
$provas = Join-Path $PSScriptRoot "provas-do-shader"
$trabalho = Join-Path $env:TEMP "astra-prova-shader"

if (-not (Test-Path $fonte)) { throw "nao achei $fonte" }
New-Item -ItemType Directory -Force -Path $trabalho | Out-Null

$linhas = Get-Content $fonte
$dentro = $false
$sksl = foreach ($l in $linhas) {
  if (-not $dentro) { if ($l -match '^private const val SKSL_NV12 = """') { $dentro = $true }; continue }
  if ($l -match '^"""') { break }
  $l
}
if (-not $sksl) { throw "nao achei o bloco SKSL_NV12 em TelaCompartilhada.kt" }
$arquivoSksl = Join-Path $trabalho "nv12.sksl"
[System.IO.File]::WriteAllText($arquivoSksl, ($sksl -join "`n"))
"SkSL extraido: $(($sksl | Measure-Object -Line).Lines) linhas"

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
