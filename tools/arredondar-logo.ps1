# Arredonda as 4 pontas da logo do Astra e regera o .ico do executavel.
#
# SEM ACENTO NENHUM: o PowerShell 5.1 le .ps1 sem BOM como ANSI e quebra o parser
# no meio de uma string acentuada. Ja mordeu antes.
#
# Por que um script e nao "editar a imagem uma vez": a logo vira TRES coisas (o
# PNG da janela, o .ico do .exe e o icone da bandeja) e o Windows cacheia icone
# de forma agressiva. Ter o passo escrito deixa refazer com raio diferente sem
# reconstruir nada na mao.
#
# Uso:  powershell -ExecutionPolicy Bypass -File tools\arredondar-logo.ps1
#       (opcional) -RaioPct 20   <- porcentagem do lado; 0 = quadrado, 50 = circulo

param(
  [int]$RaioPct = 20
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$raiz = Split-Path -Parent $PSScriptRoot
$origem = Join-Path $raiz "mobile-native\desktopApp\src\main\resources\astra-icon.png"
$destIco = Join-Path $raiz "mobile-native\desktopApp\icons\astra.ico"

if (-not (Test-Path $origem)) { throw "Nao achei a logo em $origem" }

# --- 1. mascara arredondada -------------------------------------------------
# Desenha a logo dentro de um retangulo de cantos redondos. O modo Antialias faz
# a curva sair lisa; sem ele a borda vira escada bem visivel no tamanho grande.
function Arredonda([System.Drawing.Image]$img, [int]$pct) {
  $w = $img.Width; $h = $img.Height
  $r = [int]([Math]::Min($w, $h) * $pct / 100)
  $bmp = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $g.Clear([System.Drawing.Color]::Transparent)

  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  $d = $r * 2
  $path.AddArc(0, 0, $d, $d, 180, 90)
  $path.AddArc($w - $d - 1, 0, $d, $d, 270, 90)
  $path.AddArc($w - $d - 1, $h - $d - 1, $d, $d, 0, 90)
  $path.AddArc(0, $h - $d - 1, $d, $d, 90, 90)
  $path.CloseFigure()

  $g.SetClip($path)
  $g.DrawImage($img, 0, 0, $w, $h)
  $g.Dispose(); $path.Dispose()
  return $bmp
}

# --- 2. redimensiona pro tamanho de cada entrada do .ico --------------------
function Redimensiona([System.Drawing.Image]$img, [int]$lado) {
  $bmp = New-Object System.Drawing.Bitmap($lado, $lado, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $g.Clear([System.Drawing.Color]::Transparent)
  $g.DrawImage($img, 0, 0, $lado, $lado)
  $g.Dispose()
  return $bmp
}

function BytesPng([System.Drawing.Bitmap]$bmp) {
  $ms = New-Object System.IO.MemoryStream
  $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
  $b = $ms.ToArray()
  $ms.Dispose()
  return $b
}

$orig = [System.Drawing.Image]::FromFile($origem)
$redondo = Arredonda $orig $RaioPct
$orig.Dispose()

# Sobrescreve o PNG (icone da janela e da bandeja saem daqui).
$tmpPng = "$origem.tmp"
$redondo.Save($tmpPng, [System.Drawing.Imaging.ImageFormat]::Png)
Move-Item $tmpPng $origem -Force
Write-Host "PNG arredondado ($RaioPct% de raio): $origem"

# --- 3. monta o .ico --------------------------------------------------------
# O .ico antigo so tinha 16/32/48. A barra de tarefas do Windows 11 usa escalas
# maiores (e o Alt+Tab mais ainda): sem uma entrada grande, o Windows AMPLIA a de
# 48 e a logo aparece borrada. Por isso 16..256 aqui.
# Cada entrada guarda um PNG (aceito desde o Vista) — mais simples e menor que o
# BMP com mascara AND, e sem o risco de errar o stride.
$lados = @(16, 24, 32, 48, 64, 128, 256)
$imagens = @()
foreach ($lado in $lados) {
  $b = Redimensiona $redondo $lado
  $imagens += ,(BytesPng $b)
  $b.Dispose()
}
$redondo.Dispose()

$ms = New-Object System.IO.MemoryStream
$bw = New-Object System.IO.BinaryWriter($ms)
$bw.Write([UInt16]0)                 # reservado
$bw.Write([UInt16]1)                 # tipo 1 = icone
$bw.Write([UInt16]$lados.Count)

# Os dados comecam depois do cabecalho + a tabela de entradas.
$offset = 6 + (16 * $lados.Count)
for ($i = 0; $i -lt $lados.Count; $i++) {
  $lado = $lados[$i]
  # 256 e gravado como 0 no campo de 1 byte (o formato so vai ate 255).
  $campo = if ($lado -ge 256) { 0 } else { $lado }
  $bw.Write([Byte]$campo)            # largura
  $bw.Write([Byte]$campo)            # altura
  $bw.Write([Byte]0)                 # cores da paleta (0 = sem paleta)
  $bw.Write([Byte]0)                 # reservado
  $bw.Write([UInt16]1)               # planos
  $bw.Write([UInt16]32)              # bits por pixel
  $bw.Write([UInt32]$imagens[$i].Length)
  $bw.Write([UInt32]$offset)
  $offset += $imagens[$i].Length
}
# O CAST [byte[]] E OBRIGATORIO. Sem ele o PowerShell entrega um Object[] (ele
# desembrulha o byte[] ao passar pela funcao), o BinaryWriter nao acha a sobrecarga
# Write(byte[]), cai na Write(Boolean) — objeto nao-nulo vira $true — e grava UM
# byte 0x01 no lugar da imagem inteira. Resultado: um .ico de 125 bytes com a
# tabela de entradas certinha e ZERO pixel dentro. O Windows nao reclama: so
# ignora o arquivo e continua mostrando o icone velho do cache, o que faz parecer
# que "o arredondamento nao pegou".
foreach ($img in $imagens) { $bw.Write([byte[]]$img, 0, $img.Length) }
$bw.Flush()
[System.IO.File]::WriteAllBytes($destIco, $ms.ToArray())
$bw.Dispose(); $ms.Dispose()

# Confere o tamanho: cabecalho + tabela dao ~118 bytes, entao qualquer coisa perto
# disso significa que as imagens nao entraram (foi exatamente o que aconteceu).
$tamanho = (Get-Item $destIco).Length
$minimo = 6 + (16 * $lados.Count) + 1024
if ($tamanho -lt $minimo) {
  throw "ICO saiu com $tamanho bytes - as imagens nao foram gravadas."
}

Write-Host ("ICO regerado com {0} tamanhos ({1}) - {2} KB" -f $lados.Count, ($lados -join ", "), [math]::Round($tamanho / 1KB, 1))
Write-Host $destIco
Write-Host ""
Write-Host "O Windows cacheia icone: a barra de tarefas so mostra o novo depois de reempacotar."
