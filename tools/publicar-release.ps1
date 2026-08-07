# ⚠ NAO E MAIS O CAMINHO NORMAL. Quem publica e o
# `.github/workflows/desktop-release.yml`, disparado pelo push que bumpa o
# `astraVersion`. Este script existe so pra emergencia (Actions fora do ar).
#
# Por que deixou de ser o caminho normal: rodar os dois publicava a MESMA
# release por dois caminhos. Como o script e rapido e o workflow leva ~5min
# empacotando, o script sempre chegava primeiro — e o workflow morria no fim com
# "a release with the same tag name already exists". Resultado: CI vermelho em
# todo push, e um CI que se aprende a ignorar nao avisa nada quando importa.
#
# Se precisar rodar isto, va no Actions e CANCELE a execucao do workflow antes.
#
# ---------------------------------------------------------------------------
# Publica uma release do Astra desktop no GitHub, no formato EXATO que o
# auto-update espera. Existe porque publicar na mao ja falhou de todas as formas
# possiveis: a ultima release publicada (26/jul) tinha a tag desktop-v0.1.8 e o
# asset chamado "0.1.9.zip" — o app montava .../desktop-v0.1.8/Astra-0.1.8-win-x64.zip
# e tomava 404. Resultado: 28 versoes seguidas sem ninguem receber atualizacao, e o
# dono mandando o zip na mao pros amigos.
#
# A convencao (UpdateService.kt):
#   tag  : desktop-v<versao>
#   asset: Astra-<versao>-win-x64.zip
# A versao vem do build.gradle.kts — NAO se digita aqui, justamente pra nao divergir.
#
# USO:
#   $env:GITHUB_TOKEN = "<token com escopo repo>"
#   ./tools/publicar-release.ps1
#
# O token vem por variavel de ambiente (nunca por parametro): parametro fica no
# historico do PowerShell e na lista de processos.

$ErrorActionPreference = 'Stop'
$repo = 'Xryonz/Astra'
$raiz = Split-Path -Parent $PSScriptRoot

# ---- 1. Versao: lida do build.gradle.kts, fonte unica ----
$gradle = Join-Path $raiz 'mobile-native/desktopApp/build.gradle.kts'
$m = Select-String -Path $gradle -Pattern 'val astraVersion = "([^"]+)"'
if (-not $m) { throw "nao achei astraVersion em $gradle" }
$versao = $m.Matches[0].Groups[1].Value
$tag    = "desktop-v$versao"
$asset  = "Astra-$versao-win-x64.zip"
Write-Host "versao : $versao"
Write-Host "tag    : $tag"
Write-Host "asset  : $asset"

# ---- 2. O zip tem que existir E ter o nome da convencao ----
$zip = Join-Path 'C:/Astra/build' $asset
if (-not (Test-Path $zip)) {
  throw @"
zip nao encontrado: $zip
Empacote antes:
  cd mobile-native
  ./gradlew :desktopApp:zipDistributable "-Pastra.distDir=C:/Astra/build"
"@
}
$mb = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host "zip    : $zip ($mb MB)"

# ---- 3. Credencial ----
# Ordem: variavel de ambiente > gh CLI. O gh guarda a credencial no Gerenciador
# de Credenciais do Windows (cifrada pelo perfil), entao no caminho normal nao
# existe token em texto puro em lugar nenhum da maquina.
$token = $env:GITHUB_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
  $gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
  if (-not $gh -and (Test-Path 'C:\Program Files\GitHub CLI\gh.exe')) {
    $gh = 'C:\Program Files\GitHub CLI\gh.exe'   # instalado agora, ainda fora do PATH desta sessao
  }
  if ($gh) { $token = try { & $gh auth token | Select-Object -First 1 } catch { $null } }
}
if ([string]::IsNullOrWhiteSpace($token)) {
  throw 'sem credencial: rode `gh auth login` (ou defina $env:GITHUB_TOKEN com escopo repo)'
}
$headers = @{
  Authorization = "Bearer $token"
  'User-Agent'  = 'Astra-Publicar'
  Accept        = 'application/vnd.github+json'
}

# ---- 4. Ja existe release com esta tag? ----
try {
  $existente = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/tags/$tag" -Headers $headers
  throw "release $tag JA existe (id $($existente.id)). Bumpe astraVersion ou apague a release antes."
} catch {
  if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -ne 404) { throw }
}

# ---- 5. Cria a release ----
$corpo = @{
  tag_name = $tag
  name     = "Astra $versao"
  body     = "Atualizacao automatica. O Astra baixa sozinho na proxima abertura."
  draft    = $false          # rascunho = invisivel pro /releases/latest = updater cego
  prerelease = $false        # pre-release TAMBEM nao entra no /releases/latest
} | ConvertTo-Json
$release = Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers -Body $corpo -ContentType 'application/json'
Write-Host "release criada: $($release.html_url)"

# ---- 6. Sobe o asset com o nome EXATO da convencao ----
$uploadUrl = ($release.upload_url -replace '\{.*\}', '') + "?name=$asset"
$up = @{ Authorization = "Bearer $token"; 'User-Agent' = 'Astra-Publicar'; 'Content-Type' = 'application/zip' }
Invoke-RestMethod -Method Post -Uri $uploadUrl -Headers $up -InFile $zip | Out-Null
Write-Host "asset enviado."

# ---- 6b. SHA-256 ao lado do zip ----
# O app confere isto antes de trocar a pasta da versao. Tem que sair daqui
# tambem: uma release publicada por este caminho sem o hash seria aceita (o
# cliente segue quando o arquivo falta), mas ficaria sem a protecao — e o motivo
# de faltar seria invisivel.
$sha = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
$hashTmp = Join-Path $env:TEMP "$asset.sha256"
"$sha  $asset" | Out-File -FilePath $hashTmp -Encoding ascii -NoNewline
$upTxt = @{ Authorization = "Bearer $token"; 'User-Agent' = 'Astra-Publicar'; 'Content-Type' = 'text/plain' }
$hashUrl = ($release.upload_url -replace '\{.*\}', '') + "?name=$asset.sha256"
Invoke-RestMethod -Method Post -Uri $hashUrl -Headers $upTxt -InFile $hashTmp | Out-Null
Remove-Item $hashTmp -Force
Write-Host "sha256 enviado: $sha"

# ---- 7. Confere pelo MESMO caminho que o app usa ----
# O app nao usa a API: le o 302 de /releases/latest e monta a URL do zip por
# convencao. Entao a verificacao tem que passar por ai, senao "publicou" sem
# garantir que o app acha — que foi exatamente o que aconteceu antes.
# HttpWebRequest e nao Invoke-WebRequest: o IWR mente de dois jeitos diferentes
# aqui. No PS 5.1 nao existe -SkipHttpErrorCheck (e PowerShell 7+) e o objeto de
# resposta do -UseBasicParsing nao tem .Headers.Location — devolve vazio calado,
# que e o pior resultado possivel numa verificacao. Com AllowAutoRedirect = $false
# o 302 volta como resposta normal nas duas versoes.
# O ponteiro /releases/latest demora alguns segundos pra andar depois do upload.
# Sem esta espera o script ja gritou "o app nao vai achar" duas vezes com a
# release perfeita no ar — e alarme falso repetido treina a gente a ignorar o
# alarme, que e o pior estrago possivel numa verificacao.
$tagVista = ''
foreach ($tentativa in 1..5) {
  $req = [System.Net.HttpWebRequest]::Create("https://github.com/$repo/releases/latest")
  $req.AllowAutoRedirect = $false
  $req.UserAgent = 'Astra-Publicar'
  $resp = $req.GetResponse()
  $loc = $resp.Headers['Location']
  $resp.Close()
  $tagVista = ("$loc" -split '/releases/tag/')[-1]
  if ($tagVista -eq $tag) { break }
  if ($tentativa -lt 5) { Start-Sleep -Seconds 4 }
}
# GET de UM byte, nao HEAD. O CDN de assets do GitHub recusa HEAD as vezes
# (404 com o arquivo intacto no ar) e isso ja deu falso negativo aqui: o script
# gritou "o app nao vai achar" com a release perfeita. Alem disso o app baixa com
# GET — verificar por HEAD e testar um caminho que ninguem percorre.
$zipUrl = "https://github.com/$repo/releases/download/$tag/$asset"
$ok = try {
  $g = [System.Net.HttpWebRequest]::Create($zipUrl)
  $g.UserAgent = 'Astra-Publicar'
  $g.AddRange(0, 0)
  $gr = $g.GetResponse()
  $codigo = [int]$gr.StatusCode
  $gr.Close()
  $codigo -eq 206 -or $codigo -eq 200
} catch { $false }

Write-Host ""
Write-Host "--- verificacao pelo caminho do app ---"
Write-Host "latest aponta pra : $tagVista   (esperado: $tag)"
Write-Host "zip acessivel     : $ok"
if ($tagVista -ne $tag -or -not $ok) {
  throw 'PUBLICOU MAS O APP NAO VAI ACHAR. Confira tag e nome do asset no GitHub.'
}
Write-Host "OK — os amigos recebem na proxima vez que abrirem o Astra."
