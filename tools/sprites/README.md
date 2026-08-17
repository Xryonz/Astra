# Sprites do gato (pet do Astra)

Arte **original**, desenhada no estilo chibi de `assets/images/gatins.jpg` — que serve
de **referência**, não de fonte. Aqueles sprites são do *Super Cat Tales 2*
(Neutronized) e não entram no pacote: o Astra é publicado em release pública, e
distribuir arte de jogo comercial não é uma opção.

## Como a arte é feita

Cada quadro é uma **grade de texto** em `GatoSprites.java`, um caractere por pixel:

```
'.' transparente   'K' contorno   'B' pelo   'D' sombra
'W' peito/focinho  'P' rosa       'E' olho
```

Mudar a cor do gato é mudar uma linha do mapa de paleta. Mudar a silhueta é editar
o desenho ASCII, que se lê como o próprio sprite. É por isso que a arte mora em
código e não num `.aseprite`: dá pra revisar num diff.

## Gerar

```
cd tools/sprites
javac -d /tmp/sprites GatoSprites.java
java -cp /tmp/sprites Gerar <saida.png>
```

A saída vai pra `mobile-native/desktopApp/src/main/resources/pet/`.

## Regra que não se quebra no app

Pixel art **nunca** é escalada com suavização. No Compose isso é
`FilterQuality.None`, e de preferência em múltiplo inteiro (2x, 3x) — um sprite de
24px desenhado a 34dp numa tela a 150% vira borrão cinza com o filtro padrão.
