# Sprites do gato (pet do Astra)

A arte é do pacote **Cat 2D Pixel Art**, de **Mattz Art** (`xzany`) —
<https://xzany.itch.io/cat-2d-pixel-art>. Versão gratuita.

O texto da licença viaja junto da arte, em
`mobile-native/desktopApp/src/main/resources/pet/LICENCA-cat-2d-pixel-art.txt`. Em
resumo: uso comercial liberado dentro de um projeto, modificação liberada, proibido
revender ou redistribuir **como asset avulso**, proibido virar NFT, crédito não
exigido mas apreciado — daí este arquivo.

`assets/images/gatins.jpg` continua no repositório apenas como **referência de
estilo**. Aqueles sprites são do *Super Cat Tales 2* (Neutronized) e não entram no
pacote: distribuir arte de jogo comercial não é uma opção.

## Geometria das folhas — medida, não estimada

Cada `.png` é uma tira horizontal de quadros de **80×64**. Varrendo o alfa dos 42
quadros das quatro folhas usadas, o gato cabe inteiro em `x 7..64`, `y 16..49` — daí
o recorte único de **58×34** aplicado em toda animação. Recorte igual para todas é o
que mantém o alinhamento sem código: cada quadro fica no lugar exato em que foi
desenhado, só sem a margem vazia.

As patas repousam em `y=47` do quadro, ou seja, na **linha 31 do recorte**. Por isso
a âncora do desenho é o pé, e não o centro: com o pé fixo, o pulo sobe de verdade em
vez de o bicho inteiro escorregar para cima.

| Arquivo             | Origem     | Quadros | FPS |
| ------------------- | ---------- | ------- | --- |
| `gato_parado.png`   | `IDLE`     | 8       | 8   |
| `gato_andando.png`  | `WALK`     | 12      | 12  |
| `gato_correndo.png` | `RUN`      | 8       | 14  |
| `gato_pulo.png`     | `JUMP`     | 3       | 9   |

Sobraram sem uso `ATTACK 1`, `HURT` e `RUNNING JUMP` — o pet não briga nem se
machuca. `HURT` ainda tem um quadro de flash todo branco, que num pet leria como
falha de desenho.

O gato do pacote olha para a **esquerda**. Andando para a direita, a folha é
espelhada no eixo do próprio bicho.

## Regra que não se quebra no app

Pixel art **nunca** é escalada com suavização. No Compose isso é
`FilterQuality.None`, e em **múltiplo inteiro de pixel físico**. Em 2,5× metade das
colunas do sprite ocupa 2 pixels e a outra metade 3, e o bicho ganha uma listra que
o artista não desenhou. Por isso `GatoDoAstra` deriva a escala da densidade da tela
(`round(2 × density)`, travado entre 2 e 6) em vez de usar um valor em dp.
