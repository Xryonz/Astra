# Uso:  .\gerar-pecas-do-boneco.ps1 -Template "...\16x32\16x32 Idle-Sheet.png"
#
# O caminho da folha vem POR PARAMETRO, e nao escrito aqui dentro: o PowerShell 5.1
# le um .ps1 sem BOM como ANSI, entao um literal com acento ("Codigos e Loucuras")
# vira outro caminho e o script grava os PNGs numa pasta fantasma. Ja aconteceu.
param([string]$Template)

# Gera os recursos do boneco a partir do template do Eris Esra.
#
# DUAS TECNICAS, e a escolha entre elas nao foi de gosto:
#
#   ROUPA -> derivada da silhueta. O pano preenche o interior do tronco e HERDA a
#   sombra que o corpo ja tem. Alinhamento exato de graca, e a dobra do braco
#   continua escura por baixo da manga.
#
#   CABELO -> desenhado a mao. Derivar da silhueta tambem alinhava, mas o
#   resultado era um capacete: a silhueta dilatada tem topo achatado, e cabelo
#   sem domo nao le como cabelo. Forma vence alinhamento automatico aqui.
#
# Anatomia medida do quadro 0 (e o que ancora tudo):
#   y7..y18 cabeca   |  olhos na y13  |  boca y15  |  bochecha y16
#   y19..y26 tronco  |  y27..y31 pernas

Add-Type -AssemblyName System.Drawing

$fonte = $Template
$saida = $PSScriptRoot + "\..\mobile-native\desktopApp\src\main\resources\boneco"
New-Item -ItemType Directory -Force -Path $saida | Out-Null

$folha = New-Object System.Drawing.Bitmap $fonte

# --- 1. a base: a linha 0 (parado, de frente), 4 quadros de 32x32 ---
$base = New-Object System.Drawing.Bitmap 128, 32
for ($y=0; $y -lt 32; $y++) { for ($x=0; $x -lt 128; $x++) { $base.SetPixel($x, $y, $folha.GetPixel($x, $y)) } }
$base.Save("$saida\base.png", [System.Drawing.Imaging.ImageFormat]::Png)

# --- cores-chave das pecas (a rampa do Kotlin troca estas tres) ---
$CONTORNO = [System.Drawing.Color]::FromArgb(255, 0x2F, 0x25, 0x22)
$CLARO    = [System.Drawing.Color]::FromArgb(255, 0xF2, 0xF2, 0xF2)
$MEDIO    = [System.Drawing.Color]::FromArgb(255, 0xA8, 0xA8, 0xA8)
$SOMBRA   = [System.Drawing.Color]::FromArgb(255, 0x5C, 0x5C, 0x5C)

$lim = @{}
for ($y=0; $y -lt 32; $y++) {
  $min = 99; $max = -1
  for ($x=0; $x -lt 32; $x++) { if ($folha.GetPixel($x,$y).A -gt 8) { if($x -lt $min){$min=$x}; if($x -gt $max){$max=$x} } }
  if ($max -ge 0) { $lim[$y] = @($min, $max) }
}

function Salvar($bmp, $nome) {
  # O contorno nasce sozinho: todo pixel cheio que faz fronteira com o vazio.
  # Desenhar contorno a mao numa forma de 32px e onde o pixel art some.
  $borda = New-Object System.Drawing.Bitmap 32, 32
  for ($y=0; $y -lt 32; $y++) { for ($x=0; $x -lt 32; $x++) {
    $p = $bmp.GetPixel($x,$y)
    if ($p.A -le 8) { continue }
    $fronteira = $false
    foreach ($d in @(@(0,-1), @(0,1), @(-1,0), @(1,0))) {
      $nx = $x + $d[0]; $ny = $y + $d[1]
      if ($nx -lt 0 -or $nx -gt 31 -or $ny -lt 0 -or $ny -gt 31) { $fronteira = $true; continue }
      if ($bmp.GetPixel($nx,$ny).A -le 8) { $fronteira = $true }
    }
    if ($fronteira) { $borda.SetPixel($x, $y, $CONTORNO) } else { $borda.SetPixel($x, $y, $p) }
  } }
  $borda.Save("$saida\$nome.png", [System.Drawing.Imaging.ImageFormat]::Png)
  $borda.Dispose()
  "  $nome.png"
}

# --- 2. cabelos, desenhados. Cada string e uma linha de 32 pixels. ---
# A primeira linha do mapa cai na y6 -- um pixel acima do crânio, que comeca na y7.
$COMECA_EM = 6

# DUAS REGRAS QUE VIERAM DE ERRAR:
#
#  1. O CABELO NAO PODE SER MAIS LARGO QUE O CRANIO NAQUELA LINHA. Transbordar
#     1px de cada lado e depois ganhar contorno por fora resulta em 2px de aba --
#     e aba e boina, nao cabelo. As larguras abaixo sao as do cranio, medidas:
#     y7 x13-18 | y8 x12-19 | y9 x11-20 | y10..13 x10-21 | y14-15 x9-22 | y16 x10-21
#
#  2. NADA COM MENOS DE 3px DE ESPESSURA. O contorno nasce na fronteira, entao uma
#     mecha de 2px vira 100% contorno: um risco preto, que foi exatamente o que
#     transformou o cabelo longo em orelhas de coelho.
$CURTO = @(
  '..............xxxx..............',
  '.............xxxxxx.............',
  '............xxxxxxxx............',
  '...........xxxxxxxxxx...........',
  '..........xxxxxxxxxxxx..........',
  '..........xxxxxxxxxxxx..........',
  '..........xxxxxxx...............'
)
# Franja repartida: ela desce mais de um lado que do outro. Simetria perfeita e o
# que faz o corte parecer capacete moldado, e nao cabelo caindo.
$MEDIO_ = @(
  '..............xxxx..............',
  '.............xxxxxx.............',
  '............xxxxxxxx............',
  '...........xxxxxxxxxx...........',
  '..........xxxxxxxxxxxx..........',
  '..........xxxxxxxxxxxx..........',
  '..........xxxxxxx.....xx........',
  '.........xxx.........xxx........',
  '........xxx...........xxx.......',
  '........xxx...........xxx.......',
  '.........xxx.........xxx........'
)
$LONGO = @(
  '..............xxxx..............',
  '.............xxxxxx.............',
  '............xxxxxxxx............',
  '...........xxxxxxxxxx...........',
  '..........xxxxxxxxxxxx..........',
  '..........xxxxxxxxxxxx..........',
  '..........xxxxxxx.....xx........',
  '.........xxx.........xxx........',
  '........xxx...........xxx.......',
  '........xxx...........xxx.......',
  '........xxx...........xxx.......',
  '........xxx...........xxx.......',
  '........xxx...........xxx.......',
  '.......xxxx...........xxxx......',
  '.......xxxx...........xxxx......',
  '.......xxxx...........xxxx......',
  '........xxx...........xxx.......',
  '.........xx...........xx........'
)
# Luz vem de cima: claro no domo, medio no volume, sombra nas pontas.
function Tom($y) { if ($y -le 9) { $CLARO } elseif ($y -le 16) { $MEDIO } else { $SOMBRA } }

function Cabelo($mapa, $nome) {
  $bmp = New-Object System.Drawing.Bitmap 32, 32
  for ($i=0; $i -lt $mapa.Count; $i++) {
    $y = $COMECA_EM + $i
    for ($x=0; $x -lt 32; $x++) { if ($mapa[$i][$x] -eq 'x') { $bmp.SetPixel($x, $y, (Tom $y)) } }
  }
  Salvar $bmp $nome
  $bmp.Dispose()
}
Cabelo $CURTO  "cabelo-curto"
Cabelo $MEDIO_ "cabelo-medio"
Cabelo $LONGO  "cabelo-longo"

# --- 3. roupas: derivadas do tronco, herdando a sombra do corpo ---
$PELE_CLARA  = "FAF3E8"
$PELE_SOMBRA = "B9B3A1"
function Vestir($bmp, $deY, $ateY, $soTronco) {
  for ($y=$deY; $y -le $ateY; $y++) {
    if (-not $lim.ContainsKey($y)) { continue }
    # A sombra da pele marca onde o braco se separa do tronco. Entre os dois
    # separadores e tronco; fora deles, braco.
    $sep = @()
    for ($x=0; $x -lt 32; $x++) {
      $p = $folha.GetPixel($x,$y)
      if ($p.A -gt 8 -and ("{0:X2}{1:X2}{2:X2}" -f $p.R,$p.G,$p.B) -eq $PELE_SOMBRA) { $sep += $x }
    }
    for ($x=$lim[$y][0]+1; $x -le $lim[$y][1]-1; $x++) {
      if ($soTronco -and $sep.Count -ge 2) {
        if ($x -lt $sep[0] -or $x -gt $sep[$sep.Count-1]) { continue }
      }
      $p = $folha.GetPixel($x,$y)
      if ($p.A -le 8) { continue }
      $k = "{0:X2}{1:X2}{2:X2}" -f $p.R,$p.G,$p.B
      if ($k -eq $PELE_CLARA)      { $bmp.SetPixel($x, $y, $MEDIO) }
      elseif ($k -eq $PELE_SOMBRA) { $bmp.SetPixel($x, $y, $SOMBRA) }
    }
  }
}
# A saia fecha o vao entre as pernas -- e o que separa tunica de camiseta longa.
function Saia($bmp, $deY, $ateY) {
  for ($y=$deY; $y -le $ateY; $y++) {
    for ($x=12; $x -le 19; $x++) { $bmp.SetPixel($x, $y, $(if ($y -le $deY+1) { $MEDIO } else { $SOMBRA })) }
  }
}

$r = New-Object System.Drawing.Bitmap 32,32; Vestir $r 19 26 $true;                 Salvar $r "roupa-camiseta"; $r.Dispose()
$r = New-Object System.Drawing.Bitmap 32,32; Vestir $r 19 26 $false;                Salvar $r "roupa-manga";    $r.Dispose()
$r = New-Object System.Drawing.Bitmap 32,32; Vestir $r 19 26 $true;  Saia $r 27 30; Salvar $r "roupa-tunica";   $r.Dispose()

$base.Dispose(); $folha.Dispose()
"pronto em $saida"
