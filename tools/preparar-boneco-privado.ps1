# Uso:  .\preparar-boneco-privado.ps1 -Zip "...\Free Base by Cozy Fae.zip"
#
# PREPARA A ARTE QUE NAO ENTRA NO REPOSITORIO.
#
# A licenca do pack da CozyFae permite uso comercial e permite editar, mas proibe
# REDISTRIBUIR os arquivos -- modificados ou nao -- e pede que ninguem consiga
# extrai-los do produto. O Astra e um repositorio PUBLICO: commitar os PNGs seria
# redistribuicao literal, um `git clone` de distancia.
#
# Entao a arte mora fora do Git, e este script e o que fica no lugar dela: quem tiver
# a licenca reconstroi a pasta em um comando; quem nao tiver continua compilando, e o
# app cai no acervo embutido (o template do Eris Esra, licenca compativel com repo
# publico). Ver `Boneco.kt`.
#
# O caminho vem POR PARAMETRO e nao escrito aqui: literal com acento num .ps1 sem BOM
# e lido como ANSI pelo PowerShell 5.1, e vira outra pasta.
param([Parameter(Mandatory=$true)][string]$Zip)

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$saida = $PSScriptRoot + "\..\mobile-native\desktopApp\src\main\resources\boneco-privado"
$tmp = Join-Path $env:TEMP ("boneco-cozy-" + [guid]::NewGuid().ToString("N"))

[System.IO.Compression.ZipFile]::ExtractToDirectory($Zip, $tmp)
New-Item -ItemType Directory -Force -Path $saida | Out-Null

# SO A LINHA DA FRENTE. As folhas vem 64x96 = 2 quadros x 3 direcoes (frente, lado,
# costas) em celulas de 32x32. O cartao de perfil mostra alguem parado olhando pra
# voce, entao a linha 0 e tudo que se usa -- e recortar deixa isso explicito no
# arquivo em vez de escondido num indice do codigo.
function Frente($de, $para) {
  $b = New-Object System.Drawing.Bitmap $de
  $o = New-Object System.Drawing.Bitmap 64, 32
  $g = [System.Drawing.Graphics]::FromImage($o)
  $g.DrawImage($b, (New-Object System.Drawing.Rectangle 0,0,64,32), 0, 0, 64, 32, [System.Drawing.GraphicsUnit]::Pixel)
  $g.Dispose()
  $o.Save((Join-Path $saida $para), [System.Drawing.Imaging.ImageFormat]::Png)
  $o.Dispose(); $b.Dispose()
  "  $para"
}

# Um arquivo por CAMADA, e um so por camada: a cor sai da troca de paleta, entao as
# variantes de cor que vem no pack (cabelo marrom/ruivo/amarelo, blusa em cinco
# cores) seriam o mesmo desenho repetido. Escolhe-se a variante mais neutra de cada
# uma, que e a que menos distorce ao ser repintada.
Frente "$tmp\idle\base\free_base_03_idle.png"            "base.png"
Frente "$tmp\idle\eyes\free_eyes_blue_idle.png"          "olhos.png"
Frente "$tmp\idle\hair\free_hair_short_brown_idle.png"   "cabelo-curto.png"
Frente "$tmp\idle\hair\free_hair_mediu_brown__idle.png"  "cabelo-medio.png"
Frente "$tmp\idle\top\free_base_top_white_idle.png"      "blusa.png"
Frente "$tmp\idle\bottom\free_pants_blue_idle.png"       "calca.png"

# A licenca e o credito viajam junto da arte, como manda a regra da casa -- e neste
# caso a propria licenca exige ("include this license in any project files").
Copy-Item "$tmp\LICENSE.txt" (Join-Path $saida "LICENSE-cozyfae.txt") -Force
Copy-Item "$tmp\CREDIT.txt"  (Join-Path $saida "CREDIT-cozyfae.txt")  -Force

Remove-Item -Recurse -Force $tmp
"pronto em $saida  (ignorado pelo Git de proposito)"
