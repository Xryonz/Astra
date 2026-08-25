# Anotações do Astra

> Este arquivo guarda o **porquê** das decisões do código. Ele existe porque os
> comentários foram removidos do código-fonte: tudo que estava explicado dentro dos
> arquivos passou a morar aqui.

**Como usar.** Cada nota está sob o arquivo de onde saiu e ancorada no *símbolo* que
ela explicava — a função, o tipo, a constante. Ancorar por símbolo e não por número de
linha é deliberado: com os comentários fora do código, toda linha mudou de lugar, e um
"linha 959" viraria mentira no primeiro commit.

**O que está aqui.** Só blocos de quatro linhas ou mais — o corte em que um comentário
deixa de ser rótulo (`// o botão`) e vira explicação. Rótulos de uma linha não
sobreviveram, e não fazem falta: eles diziam o que o código já dizia.

**O que procurar aqui.** Medições (números que custaram uma tarde para obter),
armadilhas (o que parece certo e não é), e caminhos que foram tentados e não deram —
esse último é o que nunca se recupera lendo o código.

---

## Sumário

- [sidecar-voz — voz e tela (Go)](#sidecarvoz-—-voz-e-tela-go) — 381 notas
- [desktopApp — o aplicativo (Kotlin/Compose)](#desktopapp-—-o-aplicativo-kotlincompose) — 703 notas
- [shared — rede e DTOs (Kotlin)](#shared-—-rede-e-dtos-kotlin) — 18 notas
- [app — Android (Kotlin)](#app-—-android-kotlin) — 16 notas
- [mobile-native — build](#mobilenative-—-build) — 2 notas
- [apps/api — servidor (TypeScript)](#appsapi-—-servidor-typescript) — 95 notas
- [packages — tipos compartilhados](#packages-—-tipos-compartilhados) — 3 notas

---

## sidecar-voz — voz e tela (Go)

### `sidecar-voz/aparelhos_test.go`

**`func TestListarAparelhos(t *testing.T)`**

Enumeracao de aparelhos contra o Windows de verdade.

Pede variavel de ambiente porque depende da maquina ter placa de som — no CI nao
tem, e um teste que falha por ausencia de hardware ensina a ignorar teste.

ASTRA_TESTE_AUDIO=1 go test -run Aparelhos -v

**`func TestAparelhoInvalidoCaiNoPadrao(t *testing.T)`**

O ID GUARDADO PODE APONTAR PARA UM APARELHO QUE NAO EXISTE MAIS.

E o caso comum, nao a exceção: headset USB tirado da porta, placa desabilitada,
perfil levado para outro computador. Sem a queda para o padrao, a call abriria
muda por causa de um fone desplugado semana passada — e nada na tela diria isso.

Este teste existe porque a queda e um caminho que NUNCA roda no uso normal: so
aparece no dia em que alguem desplugou algo, que e exatamente o dia em que
ninguem quer descobrir que ela nao funciona.

### `sidecar-voz/aparelhos.go`

**— sobre o arquivo inteiro —**

ESCOLHER MICROFONE E SAÍDA.

O motor sempre usou o aparelho de COMUNICAÇÃO padrão do Windows, que é o certo
como ponto de partida: é o que a pessoa já escolheu no sistema para conversar. Mas
"certo como padrão" não é o mesmo que "certo sempre" — quem tem duas placas, ou um
headset que o Windows não elegeu, precisa poder dizer qual quer.

Isto existe porque a escolha SUMIU quando a voz mudou de casa: o motor antigo
listava dispositivos e o novo não listava. Era regressão, não simplificação.

A ENUMERAÇÃO É COM PURO, e o caminho tem quatro paradas: o enumerador devolve uma
coleção, a coleção devolve dispositivos, cada dispositivo devolve o próprio
identificador e uma loja de propriedades, e é a loja que carrega o nome legível.

**`func ListarNumaThreadPropria(sentido int) ([]Aparelho, error)`**

ListarNumaThreadPropria enumera numa thread com apartamento COM só dela.

Existe porque quem pede a lista é o laço que lê a ponte, e esse laço NÃO tem COM
iniciado — só as duas threads de áudio têm, cada uma no próprio apartamento.
Chamar a enumeração de lá devolveria erro de "COM não inicializado", que é o tipo
de falha que só aparece em runtime e confunde.

Uma thread por consulta é barato: isto acontece quando alguém abre as
configurações da call, não a cada quadro de áudio.

**`func abrirDispositivo(enumerador objeto, sentido int, id string) (objeto, error)`**

abrirDispositivo devolve o aparelho pedido, ou o padrão de comunicação quando o
pedido é vazio.

CAIR NO PADRÃO QUANDO O PEDIDO FALHA é deliberado. O identificador guardado nas
preferências aponta para um aparelho que pode ter sido desconectado desde a última
vez — headset USB tirado da porta é o caso comum. Sem esta queda, a call abriria
muda porque a pessoa desplugou um fone semana passada.

### `sidecar-voz/banda_test.go`

**— sobre o arquivo inteiro —**

O CONTROLE DE BANDA, SEM REDE E SEM PLACA.

É conta pura, e é por isso que dá para provar aqui: erro de sinal, histerese frouxa ou
piso furado não aparecem em teste de integração — aparecem numa chamada de verdade,
meia hora depois, como imagem que oscila ou que nunca volta a melhorar.

O QUE MAIS IMPORTA PROVAR é o que NÃO acontece: que um pico isolado de perda não
derruba a banda, e que a subida não é nervosa. O atuador deste controle é REABRIR o
compressor (a banda só é ajustável na abertura — ver `sonda_banda_ao_vivo_test.go`), e
cada reabertura custa um quadro-chave. Controle nervoso produziria mais engasgo do que
a perda que ele existe para corrigir.

### `sidecar-voz/banda.go`

**— sobre o arquivo inteiro —**

QUANTO A REDE ESTÁ AGUENTANDO — decidido pela perda que o outro lado relata.

O QUE ISTO CONSERTA. Até aqui a transmissão mandava o que o preset dizia e não recuava
nunca. Numa conexão que não aguenta 2.500 kbps, o resultado não é imagem pior: é
pacote perdido, retransmissão em cima de retransmissão, e imagem quebrada do outro
lado — com a máquina de quem transmite achando que está tudo bem.

O DADO JÁ CHEGA. Cada par devolve um `ReceiverReport` por segundo, e dentro dele vem a
fração de pacotes perdidos. `ouvirPedidos` já lê esse RTCP para atender pedido de
quadro-chave, e jogava o resto fora.

POR QUE NÃO O GCC. O caminho canônico seria o controle de congestionamento do pion
(`pkg/gcc`), que estima a banda por atraso E por perda e sabe SUBIR sozinho. Ele foi
tentado e não subiu — ver `sonda_banda_test.go` para os quatro fatos medidos, incluindo
o pior deles: ligado do jeito óbvio, ele descarta todos os pacotes e a transmissão
para. Isto aqui cobre a metade que decide se a imagem quebra; a outra metade (usar
banda que sobra) fica para quando o GCC funcionar.

A HISTERESE É FORTE DE PROPÓSITO, e o motivo é o atuador. Mudar a banda exige REABRIR
o compressor — medido em três rotas diferentes, ele só aceita a banda na abertura (ver
`sonda_banda_ao_vivo_test.go`). Reabrir custa um quadro-chave e uns décimos sem
imagem. Um controle nervoso ficaria reabrindo a cada oscilação e produziria mais
engasgo do que a perda que está tentando corrigir.

Daí a assimetria: desce depressa, sobe devagar. Errar para baixo custa nitidez; errar
para cima custa a imagem inteira.

**`segundosParaRecuar = 3`**

Segundos seguidos antes de agir. A assimetria é o coração do controle: três
segundos para recuar (um pico isolado não conta, uma congestão de verdade sim) e
vinte para voltar a subir, porque subir cedo demais recria exatamente o
congestionamento do qual acabamos de sair.

**`type ControleDeBanda struct`**

ControleDeBanda decide, uma vez por segundo, quantos kbps pedir ao compressor.

NÃO É CONCORRENTE. Vive na thread presa do laço de transmissão, junto do compressor —
que é a mesma razão de `PedirQuadroChave` levantar bandeira em vez de mandar no
compressor direto.

**`func (c *ControleDeBanda) Segundo(perda float64) (int, bool)`**

Segundo recebe a pior perda relatada no último segundo e devolve a banda nova, quando
há uma. O booleano é "mudou" — falso é o caso normal, e é o que impede o compressor de
ser reaberto à toa.

A PIOR PERDA E NÃO A MÉDIA, porque numa malha o compressor é UM só para todos os
pares. Quem estiver na conexão pior manda no ritmo de todo mundo — é a limitação
central da malha, e a média esconderia justamente a pessoa que está sofrendo.

**`type PerdaDosPares struct`**

PerdaDosPares junta o que cada par relata e devolve a pior.

CONCORRENTE, ao contrário do controle: quem escreve são as goroutines de RTCP, uma por
par, e quem lê é a thread do laço de transmissão.

**`func (p *PerdaDosPares) Pior() float64`**

Pior devolve a maior perda relatada recentemente.

RELATO VELHO NÃO CONTA. Um par que parou de mandar relatório caiu, mudou de rede ou
está congelado — e a última coisa que ele disse antes de sumir não deve segurar a
banda de todo mundo para sempre. Três segundos é o triplo do intervalo normal.

### `sidecar-voz/buffer_com_test.go`

**`func chamarPelaTabela(b *BufferDeMidia, indice int, args ...uintptr) uintptr`**

Prova o IMediaBuffer PELA VTABLE, e não chamando os métodos Go direto.

Chamar `b.definirTamanho(...)` em Go provaria pouco: o que precisa funcionar é o
caminho que o Windows usa — ler o primeiro campo do objeto, achar a tabela, saltar
para o índice certo. Se o layout do struct estiver errado, ou o callback estiver
na posição trocada, só este teste percebe. Chamada Go direta passaria feliz.

### `sidecar-voz/buffer_com.go`

**— sobre o arquivo inteiro —**

UMA INTERFACE COM IMPLEMENTADA DENTRO DO GO.

Em todo o resto deste projeto o Go é CLIENTE de COM: pega um objeto do Windows e
chama métodos nele. Aqui o sentido se inverte — o cancelador de eco exige que nós
forneçamos um `IMediaBuffer`, e é ELE quem chama os nossos métodos.

COMO UM OBJETO COM É POR DENTRO: um ponteiro para uma tabela de funções (a
"vtable"), e é só isso. Quem recebe o objeto lê o primeiro campo, acha a tabela, e
chama a função no índice que quer. Então basta montar uma tabela dessas com
ponteiros para funções Go — e é o que `syscall.NewCallback` produz.

TRÊS ARMADILHAS, e as três derrubam o processo se ignoradas:

 1. O primeiro campo TEM de ser o ponteiro da tabela, e nada pode vir antes. O
    Windows não sabe nada do nosso struct: ele lê os primeiros oito bytes do
    endereço que demos e salta para lá.
 2. O objeto não pode ser MOVIDO nem coletado enquanto o Windows tem o endereço.
    Daí o `runtime.Pinner`.
 3. Voltar do endereço para o objeto Go exige um registro próprio. O `this` que
    chega no callback é um número; converter número em ponteiro Go e usar é
    exatamente o que o coletor de lixo não garante.

**`var(…`**

O registro que traduz endereço de volta para objeto.

Um mapa e não conversão direta de ponteiro: o endereço que o Windows devolve é um
inteiro, e ressuscitar um ponteiro Go a partir de inteiro é justamente o que as
regras do coletor proíbem. O mapa mantém uma referência viva de verdade.

**`fixadorDaTabela runtime.Pinner`**

O FIXADOR DA TABELA PRECISA VIVER TANTO QUANTO O PINO, e isto já foi um
defeito aqui: começou como variável local dentro da função que monta a tabela.

O `runtime.Pinner` guarda um finalizador que PANICA se ele for coletado ainda
segurando pinos — "found leaking pinned pointer". Ou seja, o Go grita em vez
de deixar o ponteiro solto virar corrupção silenciosa mais tarde. Como a
tabela vive enquanto o processo viver, o fixador dela também tem de viver.

**`func soltarRef(this uintptr) uintptr`**

A CONTAGEM NÃO LIBERA NADA, e isso é deliberado.

Quem cria o buffer somos nós, e quem o destrói é o nosso `Fechar` — o ciclo de
vida é conhecido e curto (uma captura). Deixar o Windows liberar memória Go pela
contagem de referências seria entregar a um estranho a decisão de quando o
coletor pode agir.

### `sidecar-voz/captura_test.go`

**`func TestAbrirELerMicrofone(t *testing.T)`**

CONFERÊNCIA DA LIGAÇÃO COM O WASAPI.

Vale o mesmo que foi dito no teste do Opus, e aqui vale mais: são chamadas COM
escritas à mão, e um índice de vtable errado não dá erro de compilação — chama a
função errada e trava. Este teste é o que separa "compila" de "funciona".

Precisa de microfone de verdade, então só roda quando pedido:

$env:ASTRA_TESTE_MIC="1"; go test -run Captura -v ./...

### `sidecar-voz/captura.go`

**— sobre o arquivo inteiro —**

CAPTURA DO MICROFONE por WASAPI.

O formato pedido é sempre 48 kHz, mono, 16 bits — o que o Opus consome. O
Windows reamostra sozinho quando o aparelho é de outra taxa (bandeira
`converteFormato`), o que evita escrever um reamostrador à mão só para lidar com
as muitas placas que rodam a 44,1 kHz.

O laço é guiado por EVENTO, não por relógio nem por espera ocupada: o Windows
avisa quando há material, e a goroutine dorme no resto do tempo. Um laço de
espera ativa daria a mesma latência gastando um núcleo inteiro — num app que
roda durante horas, isso é bateria de notebook indo embora por nada.

**`func AbrirCaptura(id string) (*Captura, error)`**

AbrirCaptura prepara o microfone. `id` vazio significa o de comunicação padrão do
sistema, que é o certo como ponto de partida; um id escolhe outro.

PRECISA ser chamada da mesma thread que vai ler — COM tem afinidade de thread, e
o `runtime.LockOSThread` do laço de leitura é o que garante isso. Quem chamar
isto de uma goroutine e ler de outra vai ver comportamento aleatório, que é o
pior tipo de defeito.

**`blocoSilencioso = 0x2`**

AUDCLNT_BUFFERFLAGS_SILENT — o Windows diz "este trecho é silêncio e eu nem
escrevi os bytes". Ler o buffer nesse caso é ler lixo: a documentação manda
tratar como zeros. Ignorar esta bandeira produz estalos aleatórios que
parecem defeito de microfone.

**`var ErrSemAudio = errors.New("nada disponível agora")`**

ESTES DOIS NÚMEROS JÁ ESTIVERAM TROCADOS AQUI, e o teste não pegou — em sala
silenciosa e sem falha de escalonador, nenhuma das duas bandeiras acende. O
efeito seria pernicioso: zerar áudio válido quando houvesse engasgo, e reenviar
o bloco anterior quando houvesse silêncio de verdade.

A ordem certa está no cabeçalho `audioclient.h` e vale conferir antes de mexer:
DATA_DISCONTINUITY vem PRIMEIRO (0x1), SILENT vem em SEGUIDO (0x2). A página da
Microsoft lista os nomes sem os valores, o que convida exatamente a este erro.

**`func (c *Captura) Ler(destino []int16) (int, bool, error)`**

Ler entrega o próximo bloco do microfone em `destino`, devolvendo quantas
amostras foram escritas.

Devolve ErrSemAudio quando não há nada — que é o caso comum e não é falha. Quem
chama deve esperar pelo evento antes de tentar de novo, e é assim que a espera
sai de graça.

**`func (c *Captura) Esperar(limiteMs uint32) error`**

Esperar dorme até o Windows avisar que há material, ou até o tempo acabar.

O tempo limite existe para o laço não ficar preso para sempre se o aparelho
sumir (fone desconectado no meio da call é rotina, não exceção).

**`func PrenderNaThread() func()`**

PrenderNaThread trava a goroutine atual numa thread do sistema.

Obrigatório antes de abrir e usar qualquer coisa daqui: COM é preso à thread que
inicializou o apartamento, e o Go move goroutines entre threads quando bem
entende. Sem isto, funciona nos testes e falha em produção — o pior padrão de
falha que existe.

### `sidecar-voz/chamada_de_tela_test.go`

**`func TestATransmissaoAtravessaDePontaAPonta(t *testing.T)`**

A TRANSMISSÃO INTEIRA, DE PONTA A PONTA, DENTRO DE UM PROCESSO.

Este é o teste que faltava. Os outros provam peças — o compressor comprime, o
descompressor descomprime, o cano entrega no formato certo. Este prova o CAMINHO, que
é onde moram os defeitos que nenhuma peça sozinha revela:

tela -> compressor -> faixa -> RTP -> rede -> remontador -> descompressor -> cano

O que só aparece aqui: se o perfil declarado no SDP casa com o que o compressor emite,
se os cinquenta pacotes de um quadro-chave são remontados na ordem, se o
descompressor aceita EXATAMENTE o que este compressor produz, e se o quadro chega do
outro lado com a forma certa. Cada um desses já tinha como estar errado com todas as
peças passando nos testes delas.

Dois pares no mesmo processo, sinalizando por canos — é o arranjo que o teste de voz
já usa, e é o servidor do Astra reduzido ao essencial.

**`var diario struct`**

O DIÁRIO É RECOLHIDO E IMPRESSO NO FIM, e não gritado de dentro das goroutines.

`t.Logf` chamado depois que o teste termina faz o Go PANICAR — e as goroutines
aqui continuam vivas durante o desligamento das conexões, que é justamente quando
mais eventos saem. Custou uma medição inteira: seis execuções contadas como falha
quando o que falhava era o arnês, e o caminho de mídia estava correto.

### `sidecar-voz/chamada_test.go`

**`func TestChamadaCompletaEntreDoisPares(t *testing.T)`**

UMA CHAMADA INTEIRA DENTRO DE UM PROCESSO.

Este é o teste que importa. Os outros provam peças; este prova o CAMINHO: dois
pares de verdade fazendo o aperto de mão pela mesma sinalização que o Astra usa,
voz codificada em Opus atravessando o transporte do Pion, e chegando decodificada
no misturador do outro lado.

Não precisa de microfone nem de alto-falante — entra um tom sintetizado no lugar
do microfone e confere-se o que sai da mistura. Por isso roda em qualquer
máquina, inclusive sem placa de som.

### `sidecar-voz/com.go`

**— sobre o arquivo inteiro —**

COM À MÃO, SEM CGO.

O áudio do Windows (WASAPI) e o cancelamento de eco (Voice Capture DSP) só
existem como objetos COM. A saída usual seria cgo; aqui não, pelo mesmo motivo do
Opus — manter o build como `go build` puro.

COMO COM FUNCIONA, para quem for mexer nisto depois: um objeto COM é um ponteiro
para um ponteiro para uma tabela de funções (a "vtable"). Chamar o método N é ler
o ponteiro N dessa tabela e pular para ele, passando o próprio objeto como
primeiro argumento — é `this` explícito, exatamente como C++ faz por baixo.

TRÊS REGRAS QUE NÃO SE QUEBRAM AQUI, e as três já custaram caro em outros
projetos:

 1. O ÍNDICE É CONTADO A PARTIR DE IUnknown. Todo objeto COM começa com
    QueryInterface(0), AddRef(1) e Release(2). O primeiro método próprio de
    qualquer interface é o índice 3. Errar o índice não dá erro: chama a função
    errada, com os argumentos errados, e trava.
 2. COM TEM AFINIDADE DE THREAD. Quem inicializa o apartamento tem que ser a
    mesma thread que usa os objetos — daí `runtime.LockOSThread()` em toda
    goroutine que toca nisto. Sem isso o Go troca a goroutine de thread no meio e
    o comportamento vira aleatório.
 3. TODO OBJETO OBTIDO PRECISA DE Release. Não há coletor de lixo do outro lado
    da fronteira.

**`type objeto uintptr`**

objeto é um ponteiro para uma interface COM. Fica sem tipo de propósito: o que
dá segurança aqui não é o tipo em Go, é o índice certo na tabela — e um tipo
próprio por interface daria a falsa impressão de que o compilador está
conferindo alguma coisa.

**`func (o objeto) metodo(indice int) uintptr`**

metodo lê o endereço do método `indice` na vtable.

O `go vet` marca esta linha como "possível mau uso de unsafe.Pointer", e a
marcação é correta EM GERAL: converter uintptr em ponteiro é perigoso porque o
coletor de lixo do Go não enxerga uintptr e pode mover o que ele apontava.

Aqui é seguro, e o motivo é específico: este endereço é de memória NATIVA,
alocada pelo COM, fora do heap do Go. Não há nada para o coletor mover. É o
mesmo motivo pelo qual todo interop com COM em Go tem esta linha — não existe
forma de expressá-la que o vet aceite sem mentir sobre o que ela faz.

O 64 é um teto folgado: nenhuma interface usada aqui passa de 15 métodos.

**`func (o objeto) consultar(iid *windows.GUID) (objeto, error)`**

consultar é o QueryInterface: pede OUTRA interface do mesmo objeto.

Um objeto COM costuma implementar várias, e cada uma tem a própria tabela de
funções. O cancelador de eco é o caso vivo disto no projeto: a configuração dele
entra por `IPropertyStore` e o áudio sai por `IMediaObject`, no mesmo objeto.

Quem recebe a interface nova precisa soltá-la separado — cada uma carrega a
própria contagem de referências.
SUCESSO COM PONTEIRO NULO É FALHA, e precisa ser dito aqui. Um `QueryInterface` que
devolve código de sucesso sem escrever a interface acontece — driver com defeito,
objeto em estado ruim, e o caso comum neste projeto: um `S_FALSE` (código 1, bit alto
apagado) que `hr` deixa passar por não ser negativo.

Sem esta guarda, quem chamou segue com um zero e o primeiro método vira leitura de
endereço nulo — que em Go não é erro devolvido, é QUEDA DO PROCESSO INTEIRO. Numa
chamada de voz isso derruba todo mundo da sala por causa de um monitor que não
duplicou. Já custou uma queda aqui, em `AbrirTela`.

**`func hr(codigo uintptr, oQueFazia string) error`**

hr transforma um HRESULT em erro.

HRESULT é um inteiro de 32 bits em que o bit mais alto ligado significa falha.
O texto vem do próprio Windows, que costuma explicar melhor que qualquer tabela
que eu escrevesse aqui — e alguns erros de áudio (dispositivo em uso, formato
recusado) têm mensagem boa.

**`type propvariant struct`**

propvariant é o PROPVARIANT do Windows, e o tamanho aqui não é chute.

A união interna cabe em 16 bytes no x64 e o cabeçalho ocupa 8, então 24 bytes é o
que a estrutura mede. Declarar menos faz o `GetValue` escrever depois do fim do
que reservamos — corrupção de pilha que aparece longe da causa, do pior tipo
possível de caçar.

Só interessa o caso de texto (`VT_LPWSTR`), em que os 8 bytes a partir do offset
8 são o ponteiro para a string larga.

**`converteFormato   = 0x80000000`**

AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM + SRC_DEFAULT_QUALITY: deixa o Windows
reamostrar para o formato que pedimos. Sem isto, seria preciso aceitar a taxa
nativa do aparelho (44,1 kHz em muita placa) e reamostrar na mão para os 48
kHz que o Opus quer — trabalho que o sistema já faz bem.

**`func abrirCOM() error`**

abrirCOM prepara o apartamento COM da thread atual.

Multithreaded (MTA) porque o áudio roda em goroutine própria, sem bomba de
mensagens de janela — apartamento de thread única exigiria uma, e não temos.

RPC_E_CHANGED_MODE significa que a thread já está num apartamento de outro tipo.
Isso não é falha para nós: os objetos continuam utilizáveis.

**`type formatoDeOnda struct`**

formatoDeOnda é o WAVEFORMATEX do Windows.

O CAMPO `Extra` NÃO É ENFEITE: quando ele é diferente de zero, a estrutura de
verdade é maior (WAVEFORMATEXTENSIBLE) e tem 22 bytes a mais logo depois. Ler ou
copiar só estes campos, nesse caso, perde informação — é por isso que o formato
devolvido pelo Windows é sempre tratado por ponteiro, e nunca copiado.

**`const tamanhoDoFormatoDeOnda = 18`**

O TAMANHO DE VERDADE DO WAVEFORMATEX SÃO 18 BYTES, e `unsafe.Sizeof` diz 20.

O Go arredonda o struct para múltiplo do próprio alinhamento (4, por causa dos
campos de 32 bits); o `WAVEFORMATEX` do C é empacotado e termina no `cbSize`, sem
sobra. Todos os CAMPOS caem na posição certa nos dois — só o rabo difere.

Isso passou despercebido por muito tempo porque o WASAPI lê o formato por posição
e nunca olha o tamanho declarado. O cancelador de eco OLHA: passar 20 fez o
`SetOutputType` recusar com "exceção não esperada", que não diz nada sobre
tamanho de struct e mandaria qualquer um procurar no lugar errado.

Constante, e não `unsafe.Sizeof`, justamente para não voltar a mentir.

### `sidecar-voz/compressor_test.go`

**`func precisaDeVideo(t *testing.T)`**

SONDA DO COMPRESSOR DE VÍDEO.

A pergunta que decide a arquitetura da transmissão é uma só: existe nesta máquina um
compressor de H.264 que aceite a textura ONDE A CAPTURA A DEIXA, na placa? Se sim, o
caminho custa 0,07 núcleo. Se não, o quadro precisa descer e volta a custar 0,84 —
doze vezes mais, medido.

Perguntar antes de montar o cano é a lição do cancelador de eco, onde supor a
resposta custou uma tarde.

**`func TestAcharCompressorDeH264(t *testing.T)`**

A PROVA DE QUE OS ÍNDICES DO IMFAttributes ESTÃO CERTOS.

São 30 métodos herdados antes de `GetCount` e 33 antes de `ActivateObject` — uma
contagem que ninguém confere de olho. Mas o NOME do compressor sai do índice 13, e
nome legível ("NVIDIA H.264 Encoder MFT", "Intel® Quick Sync Video H.264 Encoder
MFT") só sai se a tabela inteira estiver certa: qualquer erro de índice devolveria
lixo, string vazia, ou travaria.

**`func TestFormatosQueOCompressorAceita(t *testing.T)`**

A PERGUNTA QUE PODE APAGAR UMA PILHA INTEIRA DE CODIGO.

A captura entrega BGRA. O livro-texto diz que H.264 quer NV12, e converter na placa
exigiria o ID3D11VideoProcessor -- mais umas cinco interfaces COM, so pra trocar o
arranjo dos canais. Mas "o livro-texto diz" nao e resposta: quem responde e o
compressor DESTA maquina. Se ele aceitar BGRA ou ARGB, o passo nao existe.

### `sidecar-voz/compressor.go`

**— sobre o arquivo inteiro —**

O COMPRESSOR DE VÍDEO — Media Foundation, e o quadro continua na placa.

A captura (`tela.go`) entrega `ID3D11Texture2D`. O Pion não comprime nada: ele
recebe H.264 pronto e empacota em RTP. Entre os dois falta exatamente uma coisa, e é
o que este arquivo procura: um compressor que aceite a textura ONDE ELA JÁ ESTÁ.

Media Foundation e não uma biblioteca de fora porque ele já vem no Windows — zero
byte a mais no pacote, que foi metade do motivo de o ffmpeg (137 MB) ter saído.

POR ORA ISTO SÓ PROCURA E RELATA. É de propósito, e é a lição do cancelador de eco:
lá, montar tudo antes de provar as peças custou caro, e o que resolveu foi uma sonda
que perguntava ao objeto em vez de supor. Aqui a pergunta que decide a arquitetura
inteira é uma só — "existe nesta máquina um compressor que fala D3D11?" — e ela tem
resposta antes de qualquer cano ser montado.

O QUE A SONDA RESPONDEU NA MÁQUINA DO DONO (placa híbrida Intel + NVIDIA):

NVIDIA H.264 Encoder MFT                    nem liga: "falha catastrófica"
Intel Quick Sync Video H.264 Encoder MFT    fala D3D11
Intel Quick Sync Video H.264 Encoder MFT    fala D3D11
Microsoft AVC DX12 Encoder                  fala D3D11
H264 Encoder MFT (software)                 não fala, como esperado

O caminho barato EXISTE aqui. E o compressor da NVIDIA não ligar não é problema a
resolver: em máquina híbrida a duplicação de tela vem do adaptador que DESENHA o
monitor, que costuma ser o integrado, e textura de um adaptador não serve no outro.
Compressor tem de casar com a placa que produziu o quadro — usar a NVIDIA aqui
obrigaria a copiar o quadro de uma placa para a outra, que é o vaivém que este
arquivo inteiro existe para evitar. A escolha certa é o compressor do MESMO
adaptador da captura, e nesta máquina ele está ali, funcionando.

E A SEGUNDA PERGUNTA APAGOU UMA PILHA INTEIRA DE CÓDIGO. O plano previa converter
BGRA→NV12 na placa antes de comprimir, porque é o que o livro-texto manda: H.264
usa NV12 por dentro. Isso custaria o `ID3D11VideoProcessor` e umas cinco interfaces
COM só para trocar o arranjo dos canais. Perguntado, o compressor da Intel
respondeu que aceita:

{3231564E-3961-42AE-BA67-FF47CCC13EED}   NV12 próprio da Intel
NV12
ARGB32

ARGB32 é, byte a byte, o que a Desktop Duplication entrega (B8G8R8A8). O quadro vai
da captura ao compressor sem NENHUM passo no meio, e a conversão acontece dentro do
próprio compressor, na placa, de graça. O de software não aceita RGB (IYUV, YV12,
NV12, YUY2) — mas ele é o caminho de emergência, e lá um passo a mais não decide nada.

**`formatoNV12 = guid(0x3231564E, 0x0000, 0x0010,`**

MFVideoFormat_NV12 — 'NV12'. É o formato que o H.264 usa por dentro, e o que
TODO compressor aceita. Serve aqui só para a busca: um compressor que aceita
NV12 é um compressor de H.264 de verdade.

A ENTRADA REAL NÃO É ESTA — ver `FormatosQueAceita`. O de hardware desta
máquina aceita ARGB32, que é exatamente o que a captura entrega, e isso apaga um
passo inteiro do caminho.

**`chaveDoEspacamento = guid(0xC16EB52B, 0x73A1, 0x476F,`**

MF_MT_MAX_KEYFRAME_SPACING {C16EB52B-73A1-476F-8D62-839D6A020652}

De quantos em quantos QUADROS sai um quadro-chave. Medido nesta máquina sem pedir
nada: o Quick Sync espaça em 5 segundos, e cinco segundos é o tempo que quem entra
na sala fica olhando para o nada — ver `chaveDoEspacamento` em `configurarSaida`.

**`chavePerfil = guid(0xAD76A80B, 0x2D5C, 0x4E0B,`**

MF_MT_MPEG2_PROFILE {AD76A80B-2D5C-4E0B-B375-64E520137036}

O nome diz MPEG-2 por herança; para H.264 ele carrega os valores de
`eAVEncH264VProfile`. Pedir o perfil não é preciosismo — ver `perfilDoSPS` e o
comentário em `configurarSaida`.

**`chaveDestrancar = guid(0xE5666D6B, 0x3422, 0x4EB6,`**

MF_TRANSFORM_ASYNC_UNLOCK {E5666D6B-3422-4EB6-A421-DA7DB1F8E207}

Compressor de hardware costuma ser assíncrono, e assíncrono nasce TRANCADO: sem
destrancar, quase tudo responde erro. Não é proteção contra nós — é para o
código antigo, escrito antes de MFTs assíncronos existirem, não tropeçar num.

**`chaveFalaD3D11 = guid(0x206B4FC8, 0xFCF9, 0x4C51,`**

MF_SA_D3D11_AWARE {206B4FC8-FCF9-4C51-AFE3-9764369E33A0}

É A PERGUNTA QUE DECIDE TUDO. Verdadeiro = o compressor aceita textura em
memória de vídeo e o quadro nunca desce. Falso = ele quer o quadro na memória
principal, e aí voltaríamos aos 0,84 núcleo que a migração existe para evitar.

**`const(…`**

Índices de vtable do IMFAttributes, na ordem de declaração do mfobjects.idl. O
IMFActivate herda dela inteira e acrescenta os três do fim.

Trinta e três métodos herdados antes do primeiro próprio é o tipo de contagem que
não se confere de olho: por isso a sonda lê o NOME do compressor. Nome legível
saindo do índice 13 prova a tabela toda de uma vez — lixo sairia de qualquer erro.

**`)`**

GetInputAvailableType
GetOutputAvailableType
SetInputType
SetOutputType
ProcessMessage
ProcessInput
ProcessOutput

**`const progressivo = 2`**

versaoDoMF é o MF_VERSION do Windows 7 pra frente: versão do SDK na parte alta,
versão da API na baixa.
MFVideoInterlace_Progressive: a tela nao e entrelacada, e dizer isso explicitamente
evita o compressor supor campos que nao existem.

**`type CompressorDisponivel struct`**

CompressorDisponivel é um compressor que o Windows oferece, ainda desligado.

Só o nome, e de propósito: tudo o mais que interessa (se fala D3D11, que tamanhos
aceita, se comprime rápido) só se sabe DEPOIS de ligar. Guardar aqui um campo que
parece resposta mas foi lido do lugar errado é exatamente o defeito que esta sonda
já cometeu uma vez.

**`func ProcurarCompressores() ([]CompressorDisponivel, error)`**

ProcurarCompressores lista os compressores de H.264 desta máquina.

Pede NV12 na entrada e H.264 na saída, que é exatamente o que a transmissão vai
usar. Filtrar aqui, e não depois, evita achar um compressor que existe mas não serve.

**`var lista *objeto`**

`IMFActivate***` no cabeçalho, e é fácil errar a contagem de estrelas: como
`objeto` JÁ é o ponteiro da interface, o vetor é `*objeto` e o que se passa é o
endereço dele. Uma estrela a mais aqui compila, roda, e trava na primeira
chamada de método — porque o que chega ao COM é o endereço de um ponteiro em
vez do objeto.

**`func (c CompressorDisponivel) FalaD3D11() (bool, error)`**

FalaD3D11 responde a pergunta que decide o caminho: este compressor aceita a
textura onde a captura a deixa?

PRECISA LIGAR O COMPRESSOR PARA PERGUNTAR, e essa foi a primeira coisa que a sonda
desmentiu. O `MF_SA_D3D11_AWARE` parece um atributo do ativador — o ativador é um
saco de atributos, tem o nome, tem a categoria — mas não é: ele mora na loja de
atributos do TRANSFORMADOR, que só existe depois de ligado. Perguntado ao ativador,
ele responde "não tenho essa chave", e "não tenho" é indistinguível de "falso" se
quem pergunta não souber a diferença. O relatório dizia que nenhuma placa desta
máquina fala D3D11, o que era falso e teria condenado a arquitetura inteira por
engano.

**`func textoDoAtributo(a objeto, chave *windows.GUID) string`**

textoDoAtributo lê uma string alocada pelo Media Foundation.

A memória vem do alocador do COM e é nossa para liberar. Esquecer isso vaza a cada
enumeração — pouco por vez, e nunca visível num teste curto, que é o pior formato de
vazamento que existe.

**`func configurarSaida(t objeto, largura, altura, fps, kbps int) error`**

configurarSaida descreve o H.264 que queremos: tamanho, taxa e banda.

A ORDEM IMPORTA E É CONTRA-INTUITIVA: no H.264 a SAÍDA vem primeiro. Enquanto ela
não estiver definida, o compressor não revela sequer que formatos aceita na entrada
— a lista volta vazia, sem erro. Faz sentido depois de pensado (o que ele aceita
depende do perfil que vai produzir), mas custa uma tarde a quem espera a ordem
natural de "entra, sai".

**`definirNumero(tipo, &chavePerfil, perfilBaseline)`**

PEDIR O PERFIL, e pedir BASELINE.

Sem esta linha cada compressor emite o padrão DELE, e o padrão não é o mesmo em
máquinas diferentes: o Quick Sync desta aqui emite High (`64001f`), medido pelo
próprio fluxo em `TestEmissorTransmiteDeVerdade`. A faixa de vídeo, porém,
declara Baseline restrito no SDP — porque é o único dialeto que atravessa
navegador, celular e biblioteca sem negociação falhar.

Declaração e realidade divergindo é o defeito que não aparece deste lado: quem
manda continua mandando, e quem recebe é que não decodifica. Pedir aqui faz as
duas concordarem.

E BASELINE AINDA AJUDA A MÁQUINA FRACA, que é o alvo: sem CABAC e sem transformada
de 8x8, ele custa menos para decodificar do que High. Paga-se em compressão —
texto pequeno numa tela compartilhada é o pior caso disso —, e é um preço que se
paga de novo se algum dia o outro lado for sempre um Astra.

**`definirNumero(tipo, &chaveDoEspacamento, uint32(fps*2))`**

QUADRO-CHAVE A CADA DOIS SEGUNDOS, e o número é o tempo de espera de quem chega
depois.

Um decodificador de H.264 não abre imagem nenhuma antes de um quadro-chave: os
outros só descrevem a DIFERENÇA em relação ao anterior. Quem entra na sala com a
transmissão já em curso — que é o caso normal — fica olhando para o vazio até o
próximo. Sem pedir nada, o Quick Sync desta máquina espaça em CINCO SEGUNDOS
(medido em `TestOCompressorDaQuadroChaveComRegularidade`), e cinco segundos de
tela preta é tempo de a pessoa concluir que está quebrado.

O preço é banda: quadro-chave é caro, e dobrar a frequência engorda o fluxo em uns
10%. Numa malha ponto a ponto essa conta é paga uma vez POR PESSOA, então não vale
baixar mais do que isso — dois segundos é o ponto em que a espera deixa de parecer
defeito sem que a banda comece a doer.

EM QUADROS E NÃO EM SEGUNDOS, que é como a chave é declarada.

E ELA NÃO É HONRADA POR TODO COMPRESSOR — está anotado porque a linha parece
funcionar e não funciona. Medido nesta máquina: o Quick Sync continua espaçando em
cinco segundos com ou sem esta chave (ver `TestOCompressorDaQuadroChaveComRegularidade`).
Fica porque não custa nada e os compressores que a honram melhoram; baixar de
verdade num que a ignora exige `ICodecAPI`, que é outra pilha de COM.

**`func (c CompressorDisponivel) FormatosQueAceita() ([]string, error)`**

FormatosQueAceita lista os formatos de pixel que este compressor aceita na entrada.

A PERGUNTA POR TRÁS: a captura entrega BGRA e o livro-texto diz que H.264 quer NV12,
o que exigiria um passo de conversão na placa — mais uma pilha de COM
(ID3D11VideoProcessor, umas cinco interfaces) só para trocar o arranjo dos canais.
Mas "o livro-texto diz" não é resposta: quem responde é o compressor desta máquina.
Se ele aceitar BGRA, o passo inteiro deixa de existir.

**`func destrancarSeAssincrono(t objeto) error`**

destrancarSeAssincrono libera um compressor de hardware para uso.

Chamado sempre, e sem conferir antes se é assíncrono: em compressor síncrono a
chave simplesmente não significa nada, e conferir custaria uma leitura para evitar
uma escrita inofensiva.

**`func nomeDoFormato(g windows.GUID) string`**

nomeDoFormato traduz um GUID de formato de mídia para algo legível.

A família inteira segue o mesmo molde: {XXXXXXXX-0000-0010-8000-00AA00389B71}, em
que os quatro primeiros bytes são um código de quatro letras ('NV12', 'H264') ou um
número pequeno herdado do Direct3D antigo (21 = ARGB32, 22 = RGB32). Decodificar
assim, em vez de manter uma tabela de nomes, faz aparecer no relatório até o formato
que eu não previ — que é o ponto de uma sonda.

**`case err != nil:`**

NÃO LIGOU é uma resposta, e diferente de "não fala". Placa ocupada por
outro programa e driver a meio caminho de uma atualização dão isto, e o
remédio é outro — dizer "não fala D3D11" mandaria a pessoa trocar de
placa por causa de um jogo aberto.

### `sidecar-voz/cpu.go`

**— sobre o arquivo inteiro —**

QUANTO DE PROCESSADOR ISTO CUSTA — a pergunta da máquina fraca.

Tempo de relógio não responde. O cano gasta 4,4ms por quadro, mas quase tudo é
ESPERAR a placa terminar, e esperar não queima processador: a thread fica parada num
evento do Windows. Uma máquina fraca não sofre com espera, sofre com trabalho.

Daí esta medida. É a mesma régua da decisão que originou a migração inteira, e que
está guardada no histórico do projeto:

quadro fica na GPU do começo ao fim   0,07 núcleos
quadro desce pra CPU + encoder de HW  0,68
quadro desce pra CPU + software       0,84

Sem reproduzir esse número no cano novo, "está rápido" é fé. Com ele, dá para dizer
a quem tem um computador de escritório se a transmissão vai roubar o processador do
resto do app — que é o que decide se a call fica utilizável ou não.

**`func TempoDeProcessador() time.Duration`**

TempoDeProcessador devolve quanto processador este processo já consumiu, somando o
tempo em código nosso e o tempo dentro do núcleo do Windows.

Os dois juntos, e não só o de usuário, porque a maior parte do trabalho aqui
acontece do outro lado de uma chamada de sistema — driver de vídeo, Media Foundation,
cópia de memória. Contar só o tempo de usuário diria que a transmissão é gratuita.

### `sidecar-voz/descompressor_test.go`

**`func TestVoltaCompletaDaTela(t *testing.T)`**

A VOLTA COMPLETA: a tela vira H.264 e o H.264 vira quadro de novo.

É o teste que separa "o decodificador liga" de "o decodificador DECODIFICA". Ele
alimenta o descompressor com a saída do compressor de verdade — mesmo perfil, mesmo
tamanho, mesmos quadros-chave —, que é exatamente o que vai chegar pela rede quando
a outra ponta existir. Um fluxo sintético não provaria nada disso.

O QUE ELE CONFERE, e por que cada coisa:

  - que sai quadro, e não só "não deu erro";
  - que a FORMA bate com a que foi comprimida — largura, altura, e o tamanho em bytes
    que NV12 exige. Quadro com passo errado não dá erro: dá imagem enviesada, e essa
    é a falha que se descobre tarde;
  - que o quadro tem CONTEÚDO. Um decodificador mal amarrado entrega buffer do
    tamanho certo cheio de zero, e "não deu erro, veio do tamanho certo" passaria.

**`if quadrosDecodificados > 0`**

O CUSTO DE DECODIFICAR É O QUE A MÁQUINA FRACA PAGA PARA ASSISTIR, e é uma conta
diferente da de transmitir: quem assiste não captura nem comprime, mas paga um
descompressor POR PESSOA que estiver transmitindo. O número por quadro é o que
permite dizer quantos cabem antes de a taxa cair.

### `sidecar-voz/descompressor.go`

**— sobre o arquivo inteiro —**

O DECODIFICADOR DE VÍDEO — o caminho de volta da transmissão.

A fatia 1 fez a tela SAIR da máquina: captura, comprime, escreve na faixa. Isto é a
outra ponta — o H.264 que chega de outra pessoa virando quadro para desenhar.

MESMO SUBSISTEMA DO COMPRESSOR, e por isso este arquivo é curto: Media Foundation já
está ligado, `objeto` já sabe chamar vtable, e os índices do `IMFTransform` já foram
conferidos um a um. O que muda é a direção — H.264 entra, quadro sai.

SAI EM NV12, E NÃO EM BGRA, e a escolha é de largura de banda entre processos.

O decodificador do Windows não oferece RGB: ele entrega NV12, YV12, IYUV, YUY2 — a
família YUV, que é o que o H.264 guarda por dentro. Converter para BGRA aqui custaria
ou um passo a mais na placa ou um laço por pixel na CPU, e a CPU é justamente o que
falta na máquina que este projeto quer atender.

E converter aqui SAIRIA CARO DUAS VEZES: o quadro ainda precisa atravessar a fronteira
entre este processo e a JVM, e NV12 é 1,5 byte por pixel contra 4 do BGRA. Em 720p são
1,3 MB contra 3,5 MB por quadro — a 30 por segundo, 40 MB/s contra 105 MB/s. Deixar em
NV12 até o fim e converter no shader (o Astra já desenha com SkSL) é mais barato em
CPU, em banda e em código.

ORDEM DOS TIPOS, e ela é o contrário do redimensionador: aqui a ENTRADA vem primeiro e
a saída depois, porque a lista de saídas disponíveis só existe depois de o
decodificador saber o que vai receber. É a mesma assimetria que o `redimensionador.go`
documenta entre ele e o compressor — três peças vizinhas do mesmo subsistema, com
ordens diferentes.

A FORMA REAL VEM DO FLUXO, e não do que pedimos. O tamanho declarado na abertura é um
palpite: quem manda é o SPS que chega dentro do H.264. O decodificador avisa isso
devolvendo `mudouAFormaDaSaida` na primeira saída, e é nessa hora que se lê o tamanho
de verdade. Ignorar esse recado é receber quadro com o passo errado — imagem
enviesada, o defeito clássico de quem confiou no palpite.

**`type Quadro struct`**

Quadro é um quadro decodificado, em NV12.

TRÊS COISAS E NÃO SÓ OS BYTES, porque nenhuma delas se deduz das outras: o passo pode
ser MAIOR que a largura (o decodificador alinha as linhas do jeito que a placa gosta),
e sem ele o desenho sai enviesado.

**`func AbrirDescompressor(largura, altura int) (*Descompressor, error)`**

AbrirDescompressor liga o decodificador de H.264 desta máquina.

`largura`/`altura` são um PALPITE — o tamanho que se espera receber. Serve para o
decodificador dimensionar o primeiro buffer; o tamanho verdadeiro chega no fluxo e
substitui este. Zero é aceito e vira 1280x720.

PRECISA RODAR NUMA THREAD PRESA com COM e Media Foundation abertos, igual ao
compressor.

**`uintptr(mftHardware|mftSincrono|mftOrdenaEFiltra),`**

SÓ SÍNCRONOS, e é a diferença mais importante entre este e o compressor.

Descompressor assíncrono é comandado por recados, e o laço de recados do
compressor existe porque lá o ganho vale: comprimir é a conta cara e a placa
precisa de fila. Aqui o quadro chega da REDE, um de cada vez, no ritmo de
quem manda — não há fila para encher, e um caminho assíncrono só
acrescentaria uma máquina de estados que pode travar.

**`func (d *Descompressor) definirSaida() error`**

definirSaida ESCOLHE ENTRE AS QUE ELE OFERECE em vez de montar uma do zero.

Montar do zero é o que o compressor faz, e lá funciona porque nós é que ditamos a
saída. Aqui é o contrário: o decodificador tem opinião sobre o arranjo interno do
quadro (alinhamento de linha, tamanho do buffer) e uma descrição feita à mão que não
bata em qualquer detalhe é recusada com "tipo inválido" — mensagem que não diz QUAL
detalhe. Pedir a lista dele e escolher a NV12 evita a adivinhação inteira.

**`func (d *Descompressor) lerAForma(tipo objeto)`**

lerAForma pega largura, altura e passo do tipo que o decodificador aceitou.

O PASSO PODE SER MAIOR QUE A LARGURA, e é o detalhe que estraga a imagem em silêncio:
o decodificador alinha as linhas ao que a placa gosta (múltiplos de 16, 64, 256), e
desenhar assumindo passo igual à largura produz aquela imagem enviesada em diagonal.
Quando o atributo não está lá, o passo É a largura — que é o caso do decodificador de
software.

**`func (d *Descompressor) medirASaida() error`**

medirASaida descobre se o decodificador traz a própria amostra ou se nós alocamos.

Os dois casos são reais e a diferença não é de gosto: o de hardware costuma trazer (a
memória é da placa), o de software espera receber. Alocar quando ele traz faz a
amostra ser ignorada em silêncio; não alocar quando ele espera devolve erro na
primeira saída.

**`func (d *Descompressor) Decodificar(h264 []byte, quando time.Duration, receber func(Quadro)) error`**

Decodificar entrega um pedaço de H.264 e chama `receber` para cada quadro que sair.

UM PEDAÇO PODE RENDER ZERO OU VÁRIOS QUADROS. Zero é o caso normal no começo — o
decodificador precisa da sequência de parâmetros e de um quadro-chave antes de abrir
imagem nenhuma. Vários acontece quando chega um pedaço com mais de um quadro dentro.

O quadro entregue vale SÓ ATÉ A CHAMADA SEGUINTE, mesma regra do compressor: quem
quiser guardar, copia. É o que evita alocar um megabyte e meio trinta vezes por
segundo.

**`if err := d.definirSaida(); err != nil`**

AQUI CHEGA A FORMA DE VERDADE, vinda do SPS do fluxo. É o momento em que o
palpite da abertura é substituído pelo que a outra pessoa está mandando de
fato — e é obrigatório reatender, porque o buffer que reservamos foi
dimensionado pelo palpite.

**`func parDoAtributo(a objeto, chave *windows.GUID) (int, int, bool)`**

parDoAtributo lê um atributo de 64 bits que carrega DOIS inteiros de 32.

É como o Media Foundation guarda tamanho de quadro e proporção: a parte alta é o
primeiro número. Ler como um só devolve um número gigante que não significa nada — e
não dá erro.

### `sidecar-voz/eco_sonda_test.go`

**`func TestSondaDoEco(t *testing.T)`**

SONDA DO CANCELADOR DE ECO — pergunta ao próprio objeto quais propriedades ele
tem, em vez de confiar em constante copiada de algum lugar.

As chaves MFPKEY_WMAAECMA_* não estão na documentação pública da Microsoft com os
valores, só com os nomes. Copiar de um fórum é como se ganha um GUID errado que
falha EM SILÊNCIO: a propriedade simplesmente não é reconhecida, o cancelador roda
com a configuração padrão, e ninguém entende por que o eco continua.

O objeto sabe. `IPropertyStore` enumera as próprias chaves e devolve os valores.

O QUE ESTA SONDA JÁ ESTABELECEU (Windows 11, agosto de 2026):

conjunto = {6F52C567-0360-4BD2-9617-CCBF1421C939}, 28 propriedades

pid=2  I4   = 0     -> SYSTEM_MODE      (0 = SINGLE_CHANNEL_AEC)
pid=3  BOOL = true  -> DMO_SOURCE_MODE  (o modo fonte é o padrão, e é o que
                                         queremos: o DSP puxa do aparelho
                                         sozinho e não precisa ser alimentado)
pid=4  I4   = -1    -> DEVICE_INDEXES   (-1 = aparelhos padrão)
pid=5  BOOL = false -> FEATURE_MODE     (liga o ajuste fino das outras)

A identificação NÃO é chute: cada uma casa com o tipo E com o valor padrão que a
Microsoft documenta por nome, e as quatro caem em PIDs consecutivos na ordem em
que a documentação as apresenta. Rodar esta sonda de novo é o jeito de conferir
isso noutra máquina antes de culpar o código.

ASTRA_SONDA_ECO=1 go test -run SondaDoEco -v

**`for i := uint32(0); i < quantas; i++`**

LER O VALOR DE CADA UMA, e não só o nome da chave.

A enumeração dá os identificadores mas não os nomes. O tipo e o valor PADRÃO
de cada propriedade estão documentados por nome na Microsoft, então ler os
valores é o que permite casar um com o outro — é a diferença entre saber e
achar que sabe.

### `sidecar-voz/eco_test.go`

**`func TestVtableDoCancelador(t *testing.T)`**

A VALIDAÇÃO DA VTABLE VEM ANTES DE TUDO.

Índice errado numa tabela de funções não devolve erro: salta para a função
vizinha, com os argumentos errados, e derruba o processo — num lugar que não tem
nada a ver com a causa. É o defeito mais caro de caçar que existe nesta camada.

`GetStreamCount` é o teste perfeito da BASE da tabela: não recebe nada além dos
dois destinos, e a resposta é conhecida. Se o índice 3 estiver certo, todos os
outros contados a partir dele também estão — vêm da mesma lista de declaração.

A RESPOSTA CERTA É ZERO ENTRADAS E UMA SAÍDA, e essa foi uma lição: a primeira
versão deste teste exigia uma e uma, e falhou. Não por causa da tabela — por causa
da expectativa. No modo FONTE o cancelador não tem entrada nenhuma, porque ele
mesmo puxa o áudio dos aparelhos; quem tem uma entrada é o modo filtro.

Ou seja, o número que parecia errado era a confirmação de que o modo fonte é
mesmo o padrão do objeto, como a documentação diz.

**`func TestFormatosQueOCanceladorAceita(t *testing.T)`**

PERGUNTA AO CANCELADOR QUAIS FORMATOS ELE ACEITA.

`SetOutputType` recusando com "excecao nao esperada" nao diz o que esta errado —
pode ser taxa, canais, profundidade, ou o struct inteiro. Adivinhar qual seria
tentar combinacoes as cegas.

`GetOutputType` enumera. O objeto responde exatamente o que aceita, e ai nao ha o
que adivinhar.

**`modoFonte := os.Getenv("ASTRA_ECO_FILTRO") == ""`**

O MODO VEM DA VARIAVEL DE AMBIENTE para dar pra comparar os dois sem editar
codigo. O limite de taxa pode ser do modo FONTE e nao do cancelador, e a
diferenca decide se da pra usar isto sem estragar a voz:

ASTRA_ECO_FILTRO=1  ->  modo filtro (nos alimentamos os dois fluxos)
sem a variavel      ->  modo fonte  (ele puxa dos aparelhos sozinho)

**`func TestTaxasQueOCanceladorAceitaDeVerdade(t *testing.T)`**

ENUMERAR E UMA DICA; SETOUTPUTTYPE E A VERDADE.

Muitos DMOs listam so o formato PREFERIDO em GetOutputType e aceitam outros
perfeitamente. Concluir "so aceita 8 kHz" a partir da lista seria decidir a
arquitetura da voz do Astra em cima de uma inferencia.

O SetOutputType tem uma bandeira de TESTE que pergunta sem comprometer nada. Este
teste percorre as taxas que interessam e diz, uma por uma, quais passam.

**`func TestMontarOCancelador(t *testing.T)`**

Monta o cancelador inteiro, do jeito que o motor monta, e conferе que ele ACEITA a
configuracao e o formato.

Este e o teste que separa "compila" de "funciona": cada passo aqui e um HRESULT
que o Windows pode recusar, e a recusa e sempre por um motivo que so aparece
tentando — modo incompativel com o formato, propriedade na ordem errada, aparelho
que nao aceita.

**`porQuadro := c.Taxa() * MilissegundosPorQuadro / 1000`**

ESTE TESTE NAO MEDE PRODUCAO, de proposito.

Producao depende de haver um fluxo de SAIDA ativo — descoberta deste dia, e a
razao de existir `TestCanceladorComSaidaAtiva`. Medir producao aqui, sem abrir
alto-falante, daria um resultado que depende de qual teste rodou antes: se
outro deixou uma saida aberta, este passa; sozinho, falha. Teste que muda de
resultado conforme a ordem e pior que teste nenhum, porque ensina a ignorar
falha.

O que se prova aqui: cada passo do MONTAGEM foi aceito pelo Windows — a
configuracao, o formato, a alocacao de recursos. E que pedir audio devolve
ErrSemAudio, e nao erro de verdade.

**`func TestDiagnosticoDoCancelador(t *testing.T)`**

DIAGNOSTICO CRU do ProcessOutput: imprime o HRESULT e as bandeiras a cada volta.

"Montou e nao produz" tem varias causas possiveis e nenhuma delas aparece no
caminho normal, que so distingue "veio audio" de "nao veio". Aqui olhamos o que o
Windows realmente responde.

ASTRA_TESTE_AUDIO=1 go test -run DiagnosticoDoCancelador -v

**`func TestCanceladorComSaidaAtiva(t *testing.T)`**

O CANCELADOR PRECISA DE UM ALTO-FALANTE ATIVO?

Hipotese: em modo fonte ele cancela eco comparando o microfone com o que ESTA
SAINDO na saida. Sem nenhum fluxo de saida aberto, ele pode ficar esperando a
referencia e nunca produzir — que e exatamente o sintoma (S_FALSE constante).

No app de verdade o laco de saida escreve silencio o tempo todo, entao o fluxo
existe sempre. Este teste reproduz essa condicao.

### `sidecar-voz/eco.go`

**— sobre o arquivo inteiro —**

CANCELAMENTO DE ECO pelo Voice Capture DSP do Windows.

O PROBLEMA: quem usa caixas de som em vez de fone joga o áudio dos outros de volta
no próprio microfone. Todo mundo se ouve com atraso, e a call fica insuportável sem
que ninguém consiga apontar o culpado — cada um acha que o problema é do outro.

A SOLUÇÃO NÃO É NOSSA, e essa é a melhor parte. O Windows traz um cancelador de
eco pronto, o mesmo que os programas de chamada do sistema usam. Escrever um do
zero seria meses de processamento de sinal para chegar pior.

MODO FONTE, e é o que torna isto viável.

O cancelador tem dois modos. No modo FILTRO, nós capturamos o microfone, nós
capturamos o que sai no alto-falante, e alimentamos os dois nele. No modo FONTE,
ele mesmo abre os dois aparelhos e nós só pedimos o resultado limpo.

O modo fonte poupa metade do trabalho: some a necessidade de capturar o retorno do
alto-falante (captura em laço, que tem armadilhas próprias) e some a sincronia
entre os dois fluxos, que é onde canceladores de eco costumam morrer. Em troca,
ele escolhe os aparelhos por ÍNDICE em vez de identificador — ver `indicesDe`.

ESTRUTURA: `CapturaComEco` tem a mesma forma que `Captura`, então o motor consome
as duas pela interface `FonteDeAudio` e não sabe qual está usando. Trocar uma pela
outra (ou cair de uma para a outra quando o cancelador não abre) não toca em uma
linha do laço de áudio.

**`type FonteDeAudio interface`**

FonteDeAudio é de onde o motor tira a voz de quem está falando aqui.

A interface existe para o laço de captura não precisar saber se há cancelamento de
eco no caminho. É a diferença entre "trocar a fonte é uma linha" e "trocar a fonte
é mexer no laço de áudio", e a segunda é onde se introduz defeito.

**`Taxa() int`**

Taxa é a amostragem que ESTA fonte entrega, e é por isso que ela existe: a
captura crua entrega 48 kHz e o cancelador de eco entrega 16 kHz. Deixar isso
numa constante global obrigaria o laço a saber qual fonte está usando — que é
exatamente o que a interface existe para evitar.

**`propModoDeAjuste = chaveDePropriedade{conjunto: conjuntoDoCancelador, id: 5}`**

AS TRÊS QUE FAZEM A SUPRESSÃO DE RUÍDO E O GANHO AUTOMÁTICO EXISTIREM.

Estabelecidas pela mesma sonda das de cima (`TestSondaDoEco`), e a identificação
se sustenta em duas coincidências independentes por chave: o TIPO e o VALOR
PADRÃO batem com o que a Microsoft documenta por nome, e elas caem em PIDs
consecutivos na ordem em que a documentação as apresenta.

pid=5  BOOL = false -> FEATURE_MODE  (o portão)
pid=8  I4   = 1     -> FEATR_NS      (I4 e não BOOL: tem modos, não liga/desliga)
pid=9  BOOL = false -> FEATR_AGC

O PORTÃO É O QUE IMPORTA ENTENDER. Com FEATURE_MODE em falso — que é o padrão —
o cancelador roda a configuração de fábrica dele e IGNORA as duas outras. Era
exatamente esse o estado do Astra: a supressão de ruído estava ligada sempre
(padrão 1) e o ganho automático estava desligado sempre (padrão falso), desse
jeito e sem relação nenhuma com os interruptores da tela.

**`type AjustesDaVoz struct`**

AjustesDaVoz é o que a pessoa escolheu em Configurações › Voz.

UM TIPO E NÃO TRÊS PARÂMETROS porque os três viajam juntos do começo ao fim (ponte
→ motor → abertura da fonte), e três booleanos em sequência numa assinatura são um
convite a trocar dois de lugar — erro que compila e só aparece no ouvido de alguém.

**`const modoSoCancelarEco = 0`**

Valor de SYSTEM_MODE. Só um nos interessa.

SINGLE_CHANNEL_AEC é cancelamento de eco puro, sem processamento de arranjo de
microfones. Os modos com arranjo exigem que a máquina TENHA um arranjo descrito, e
falham ou pioram o som em máquina comum — que é a maioria.

**`const taxaDoCancelador = 16000`**

A TAXA DO CANCELADOR É 16 kHz, E ISSO NÃO CUSTA QUALIDADE NENHUMA.

Parece uma queda feia vindo dos 48 kHz da captura crua, e seria — se o Opus
estivesse aproveitando os 48. Ele não está: o codificador é configurado em
`OPUS_BANDWIDTH_WIDEBAND`, que é banda de áudio de 8 kHz, e 8 kHz de banda pedem
exatamente 16 kHz de amostragem. Tudo acima disso já era descartado dentro do
codificador, antes de virar pacote.

Ou seja: o cancelamento de eco sai de graça em qualidade, e ainda economiza — o
microfone e o Opus passam a trabalhar com um terço das amostras.

O NÚMERO NÃO É ESCOLHA NOSSA, é o que o objeto aceita. Perguntamos a ele, com a
bandeira de teste do `SetOutputType`, e a resposta foi: 8000, 11025, 16000 e 22050
passam; 32000, 44100 e 48000 são recusados. 16000 é o melhor que serve à banda que
já usamos. Ver `TestTaxasQueOCanceladorAceitaDeVerdade`.

**`const(…`**

Índices de vtable do IMediaObject (mediaobj.h), na ordem de declaração.

ÍNDICE ERRADO AQUI NÃO DÁ ERRO: salta para a função vizinha, com os argumentos
errados, e derruba o processo. Por isso `TestVtableDoCancelador` confere a base
chamando GetStreamCount e exigindo a resposta certa (1 entrada, 1 saída) ANTES de
qualquer coisa depender destes números.

**`func AbrirEntradaDeVoz(idAparelho string, aj AjustesDaVoz) (FonteDeAudio, error)`**

AbrirEntradaDeVoz é a porta única para o motor: devolve a melhor fonte de áudio
disponível.

A QUEDA PARA A CAPTURA CRUA É PARTE DO DESENHO, e não remendo. O cancelador
depende de o Windows ter o componente registrado e de os aparelhos cooperarem;
numa máquina onde ele não abre, a escolha certa é call com eco e não call nenhuma.
O motivo vai para o registro, para a queda não ser silenciosa.
A SUPRESSÃO DE RUÍDO E O GANHO CAEM JUNTO COM O ECO, e isso não é escolha nossa: no
Windows os três moram no MESMO objeto. A captura crua é o microfone sem tratamento
nenhum — não existe "só supressão de ruído" para oferecer. Quem desliga o eco está
desligando os três, e a tela precisa dizer isso em vez de deixar dois interruptores
acesos sobre um caminho que não passa por eles.

**`func AbrirCapturaComEco(idAparelho string, aj AjustesDaVoz) (*CapturaComEco, error)`**

AbrirCapturaComEco monta o cancelador e o deixa pronto para entregar áudio limpo.

PRECISA ser chamada da mesma thread que vai ler — COM tem afinidade de thread,
igual à captura crua.

**`if err := escreverPropBool(loja, propModoDeAjuste, true); err != nil`**

O PORTÃO PRIMEIRO, E SEMPRE ABERTO. Com ele fechado o cancelador ignora as duas
linhas seguintes e roda a configuração de fábrica — que é o estado em que os
interruptores da tela não mandavam em nada. Abrir sempre custa nada e garante
que o que está na tela é o que está no ar, inclusive quando as escolhas por
acaso coincidem com o padrão.

As demais propriedades do bloco (tamanho de quadro, comprimento do eco) ficam no
padrão de propósito: a sonda leu 0 e 256, que são exatamente os valores que a
Microsoft documenta, então não escrevê-las é escolher o padrão e não esquecê-lo.

**`func (c *CapturaComEco) Esperar(limiteMs uint32) error`**

Esperar dorme um pouco antes da próxima tentativa.

NÃO HÁ AVISO POR EVENTO no modo fonte: o cancelador não entrega um `HANDLE` para
esperar, então a única opção é perguntar de novo. Cinco milissegundos é metade do
bloco que ele produz — perde-se pouca latência e não se queima processador num
laço de pergunta contínua.

**`func indicesDe(idAparelho string) (entrada, saida int)`**

indicesDe traduz o identificador de aparelho que o app usa para o índice que o
cancelador quer.

SÃO DUAS NUMERAÇÕES DIFERENTES, e essa é a costura mais frágil do modo fonte: o
resto do projeto identifica aparelho pelo id do Windows (estável, único), e o
cancelador só aceita a posição na enumeração. Posição muda quando se pluga um fone.

Quando não dá para traduzir, -1 manda usar o padrão do sistema — que é o
comportamento certo, e o mesmo da captura crua.

### `sidecar-voz/emissao_test.go`

**`type coletor struct{ para chan Evento }`**

coletor é o destino dos eventos no teste: o `Escritor` de verdade, escrevendo num
io.Writer que decodifica cada linha de volta para Evento.

NUNCA BLOQUEIA, e isso é o que importa: o `Escritor` é chamado de dentro do laço da
transmissão, então um canal cheio travaria o laço e `Desligar` esperaria para sempre
— um teste que pendura em vez de falhar.

**`func TestPerfilSaiDoSPS(t *testing.T)`**

O PERFIL LIDO DO PRÓPRIO FLUXO.

Vale um teste porque o erro aqui é MUDO: um parser errado devolve "não achei", o
emissor não reporta perfil nenhum, e ninguém percebe até o outro lado não conseguir
decodificar — em outra fatia, noutro dia, com outra suspeita.

Os dois códigos de início convivem no mesmo fluxo do Media Foundation (quatro bytes
antes da sequência de parâmetros, três antes das fatias), então os dois entram aqui.

**`func TestEmissorTransmiteDeVerdade(t *testing.T)`**

A TRANSMISSÃO DE VERDADE, do monitor até a faixa.

Não confere pixel: confere que o caminho INTEIRO fecha — captura, compressor,
juntar os pedaços num quadro, e o pion aceitar a amostra. É o que separa "compila"
de "sai byte pela rede", que era exatamente o que faltava neste projeto.

Sem conexão nenhuma na faixa de propósito: um `TrackLocalStaticSample` solto engole
a amostra em silêncio, e é justamente esse o caso de quem começa a compartilhar
antes de o primeiro convidado chegar.

**`if perfil[:2] != "42"`**

CONFERE O PERFIL E O NÍVEL, E NÃO OS TRÊS BYTES.

O byte do meio são as bandeiras de restrição, e ele NÃO precisa bater. A faixa
declara `42e01f` (Baseline restrito: bandeiras 0, 1 e 2) e o compressor emite
`42401f` (só a 1). São o mesmo perfil e o mesmo nível — Baseline 3.1 —, e um
fluxo Baseline de compressor de placa não usa as três coisas que a restrição
exclui (ordem de fatia arbitrária, grupos de macroblocos, fatias redundantes).
Qualquer decodificador que aceita `42e01f` decodifica isto.

O que NÃO pode divergir é o primeiro byte: com High (0x64) no fluxo, um
decodificador que confiou na declaração não abre a imagem. Foi exatamente o que
este teste pegou antes de existir alguém do outro lado para reclamar.

### `sidecar-voz/emissao.go`

**— sobre o arquivo inteiro —**

A TRANSMISSÃO DE TELA — o laço que leva o quadro capturado até a rede.

O QUE JÁ EXISTIA E O QUE FALTAVA. A captura (`tela.go`, DXGI Desktop Duplication) e
o compressor (`transmissao.go`, H.264 do Media Foundation) estavam prontos e
testados havia tempo, mas só eram exercitados por `MedirTransmissao` — um BANCO DE
PROVAS, que captura, comprime, conta e joga fora. Os bytes nunca saíam da máquina,
porque não havia faixa de vídeo em conexão nenhuma.

Este arquivo é a peça que faltava, e é pequena de propósito: quase tudo aqui é o
laço de `MedirTransmissao` com um destino em vez de um contador.

UMA FAIXA PARA A SALA INTEIRA, pela mesma razão do microfone (ver `NovoPar`): um
`TrackLocalStaticSample` guarda uma ligação por conexão em que foi adicionado, e uma
escrita nele reaproveita o MESMO quadro comprimido para todas. Numa malha isso é a
diferença entre comprimir uma vez e comprimir N vezes — e o compressor é, de longe,
a coisa mais cara que este processo faz.

A FAIXA NASCE COM A CONEXÃO, e não quando a transmissão começa. Em WebRTC, incluir
uma faixa depois obriga a renegociar o SDP com todo mundo da sala; declarar desde o
início custa uma linha de mídia parada e apagada, e "começar a transmitir" vira
simplesmente começar a escrever nela.

**`var CapacidadeH264 = webrtc.RTPCodecCapability`**

CapacidadeH264 é como a faixa de vídeo se anuncia no SDP.

`42e01f` é o dialeto que todo mundo entende: perfil Baseline (0x42) com as três
restrições ligadas (0xe0) no nível 3.1 (0x1f). Não é o melhor H.264 possível — é o
único que atravessa navegador, celular e biblioteca sem negociação falhar.

`packetization-mode=1` permite fatiar um NAL grande em vários pacotes (FU-A). Sem
ele, um quadro-chave de 720p simplesmente não caberia num pacote e a imagem nunca
abriria.

`level-asymmetry-allowed=1` diz que os dois lados não precisam do mesmo nível. É o
que permite declarar 3.1 aqui e mandar 720p60 (que pede 3.2) sem o outro lado
recusar de saída.

O QUE ISTO AINDA NÃO GARANTE, e está anotado porque só dói do lado que decodifica: o
compressor do Windows não recebe ordem de perfil (ver `configurarSaida`), então ele
emite o padrão DELE. Se emitir Main ou High, esta declaração estará mentindo. Por
isso o emissor LÊ o perfil de dentro do primeiro SPS que sai e o reporta — ver
`perfilDoSPS`. Medir em vez de supor, e conferir antes de existir alguém para
reclamar.

**`const(…`**

A JANELA DO AQUECIMENTO, em quadros.

Oito descartados porque a partida do compressor não representa o regime: não há quadro
de referência ainda, o primeiro é obrigatoriamente chave (dezenas de vezes maior que
os outros), e o de software leva algumas voltas para encher a fila interna. Medir a
partida e concluir que a máquina é fraca condenaria a taxa por causa dos piores
quadros que ela jamais produzirá.

Dezesseis medidos porque é amostra suficiente para o custo parar de oscilar e ainda
assim fechar rápido: vinte e quatro quadros são 0,4s a 60/s e 1,6s a 15/s.

**`const sinalDeVida = 2 * time.Second`**

DE QUANTO EM QUANTO TEMPO A TELA PARADA DÁ SINAL DE VIDA.

O número sai de um empate entre duas coisas que puxam para lados opostos:

curto demais  ->  banda gasta à toa. O quadro reenviado é um quadro-chave, e neste
                  compressor ele mede ~99 KB (medido numa sonda temporária, que não
                  ficou no repositório — ver `abreImagemSozinho`). A dois segundos
                  isso são ~0,4 Mbps com a tela imóvel.
longo demais  ->  quem para de transmitir demora a sumir do palco alheio, porque o
                  outro lado só pode declarar o fim depois de esperar mais que isto.

Dois segundos gastam 16% do preset de 2,5 Mbps — e gastam isso justamente no momento em
que os outros 84% não estão sendo usados, porque nada está mudando na tela. Do outro
lado, `silencioQueEncerra` espera duas vezes e meia isto antes de dar a tela por
encerrada, de modo que dois sinais podem se perder na rede sem apagar a imagem de
ninguém.

**`func (e *Emissor) PerdaRelatada(par string, fracao float64) { e.perdas.Relatar(par, fracao) }`**

PerdaRelatada guarda o que um par acabou de dizer sobre o que não chegou.

CHAMADA DE OUTRA GOROUTINE, a que lê o RTCP daquele par — mesma regra de
`PedirQuadroChave`. Segura chamar com a transmissão desligada: o número fica guardado
e envelhece sozinho.

**`func (e *Emissor) PedirQuadroChave() { e.querChave.Store(true) }`**

PedirQuadroChave atende ao "perdi a imagem" de quem assiste.

CHAMADA DE OUTRA GOROUTINE — a que lê os recados de cada conexão —, então ela só
levanta a bandeira; quem manda no compressor é o laço, que é dono da thread presa
onde o objeto do Media Foundation vive. Tocar no compressor daqui seria usá-lo de
outra thread, que em COM não dá erro: dá comportamento indefinido.

Segura chamar com a transmissão desligada: a bandeira fica levantada e a próxima
transmissão começa com um quadro-chave, que é justamente o que se quer.

**`func (e *Emissor) Desligar()`**

Desligar para o laço e ESPERA ele morrer.

A espera não é zelo: o laço segura um dispositivo D3D11, uma duplicação de tela e um
compressor do Media Foundation. Voltar antes de ele soltar tudo deixaria o próximo
`Ligar` disputando a duplicação com o anterior — e a Desktop Duplication é EXCLUSIVA
por processo, então o segundo simplesmente falharia.

**`func (e *Emissor) laco(ctx context.Context, aj AjustesDaTela) error`**

laco é o caminho inteiro: captura, comprime, escreve na faixa.

PRESO NUMA THREAD SÓ, do começo ao fim. COM tem afinidade de thread, e o dispositivo
D3D11, a duplicação e o compressor foram todos criados aqui — usá-los de outra
thread não dá erro claro, dá comportamento indefinido.

**`controle := NovoControleDeBanda(aj.Kbps)`**

REABRIR É O ATUADOR DE DUAS COISAS, e por isso este laço existe.

a MÁQUINA não sustenta a taxa pedida  ->  reabre com menos quadros por segundo
a REDE não sustenta a banda pedida    ->  reabre com menos kbps

Reabrir em vez de ajustar ao vivo não é preguiça: o compressor só aceita a taxa e
a banda na ABERTURA. Foram três rotas tentadas para mudar a banda com ele aberto,
e as três falharam — duas aceitas e ignoradas, uma derrubando o compressor. O
registro está em `sonda_banda_ao_vivo_test.go`.

O CONTROLE VIVE AQUI FORA, e essa é a linha que faz a coisa funcionar: ele guarda
o TETO do preset e os contadores de histerese. Criado lá dentro, cada reabertura o
zeraria — o teto viraria a banda já reduzida, e a imagem nunca voltaria a melhorar
depois que a rede sarasse.

**`comoSubiu := fmt.Sprintf("%dx%d @%d", c.saidaL, c.saidaA, c.fps)`**

A CONFIRMAÇÃO SAI ANTES DO PRIMEIRO QUADRO, e com o que de fato foi montado —
não com o que foi pedido. O compressor pode ter caído para software, e o tamanho
pode ter sido arredondado para par. Anunciar o pedido em vez do obtido é como se
esconde uma queda de qualidade de quem está pagando por ela.

E A QUEDA PARA SOFTWARE VAI POR EXTENSO, porque o nome do compressor não conta
isso a quem não é do ramo: "H264 Encoder MFT" e "Intel® Quick Sync Video H.264
Encoder MFT" são a diferença entre a máquina estar acelerada e não estar, e nada
nos dois nomes diz qual é qual. Sem esta frase, quem cair para software vê 30
quadros por segundo e conclui que o Astra escolheu 30 por conta própria.

**`duracao := time.Second / time.Duration(c.fps)`**

UMA CHAMADA DE VOLTA = UM QUADRO = UMA AMOSTRA. Escrito assim depois de o
contrário ter sido MEDIDO custando metade da taxa.

O primeiro desenho juntava tudo que saísse de uma chamada de `Comprimir` num
buffer só, na crença de que a chamada de volta viesse uma vez por PEDAÇO de
quadro. Não vem: `sair` já junta os buffers de uma amostra do compressor
(`ConvertToContiguousBuffer`) e chama de volta UMA vez por amostra — e uma amostra
é um quadro codificado inteiro.

O estrago aparecia porque o compressor da placa é ASSÍNCRONO. Ele responde por
recados, então uma volta do laço às vezes drena DOIS quadros já prontos. Juntando
os dois numa escrita só, o relógio do RTP andava a duração de UM — e a medição
mostrava exatamente isso: 29 quadros capturados por segundo virando 14 amostras.
Metade da taxa, com o tempo andando errado, e nada errado na rede para explicar.

Escrever de dentro da chamada de volta é seguro: `WriteSample` empacota e
despacha na hora, então o fatiamento emprestado não sobrevive à chamada — que é
exatamente a regra que ele exige.

**`var falhaAoEntregar error`**

A ENTREGA VIVE FORA DO LAÇO porque o quadro pronto pode chegar em DUAS ocasiões:
junto de uma compressão, e na colheita avulsa de quando a tela não mudou. Duas
cópias desta função divergiriam, e a que divergisse seria a do caminho raro — o
que se percebe só quando alguém para de mexer no mouse.

**`if err := e.faixa.WriteSample(media.Sample{Data: quadroPronto, Duration: duracao}); err != nil && falhaAoEntre`**

ESCREVER SEM NINGUÉM CONECTADO NÃO É ERRO. Um `TrackLocalStaticSample` sem
ligação nenhuma engole a amostra em silêncio, e é o que queremos: transmitir
sozinho numa sala é o caso de quem começou a compartilhar antes de o primeiro
convidado chegar.

A FALHA É GUARDADA e não devolvida daqui: esta é uma chamada de volta que o
compressor faz de dentro do laço dele, e abandoná-la no meio deixaria a fila
de saída por drenar — o jeito conhecido de travar os dois lados.

**`if err := tela.Remontar(aj.Monitor); err != nil`**

TROCA DE RESOLUÇÃO, JOGO EM TELA CHEIA, BLOQUEIO DE SESSÃO. A
duplicação morre nessas horas e remontar é o comportamento certo —
derrubar a transmissão faria a pessoa reapertar o botão toda vez que
alguém apertasse Ctrl+Alt+Del do outro lado da casa.

**`semMudanca++`**

Nada mudou na tela dentro do prazo. Não é falha: é uma tela parada, que
é o caso comum de quem compartilha um documento.

MAS AINDA PRECISA COLHER. O compressor deixou de ser drenado até o fim a
cada quadro (ver o comentário em `Comprimir`), então há sempre um quadro
ou dois maturando dentro dele. Sem esta chamada, parar de mexer na tela
congelaria a imagem de quem assiste UM QUADRO ANTES do que deveria — e
justamente no instante em que a pessoa parou de mexer para alguém ler o
que está ali.

**`if len(abridor) > 0`**

A TELA PARADA AINDA PRECISA DAR SINAL DE VIDA — e este bloco conserta dois
defeitos de uma vez, os dois medidos em `telaparada_test.go`.

1. QUEM CHEGA COM A TELA PARADA NUNCA VIA IMAGEM. O pedido de quadro-chave
   era atendido lá embaixo, DEPOIS do `continue` que este bloco substitui:
   com a tela parada, `querChave` ficava pendurado e ninguém o levantava.
   Quem entrasse numa sala onde já se compartilhava uma tela parada lia
   "abrindo a tela de fulano…" até alguém mexer o mouse.

2. QUEM PARAVA DE TRANSMITIR FICAVA CONGELADO NO PALCO ALHEIO. Não existe
   pacote de "acabou" em RTP; a faixa só para de trafegar. Quem assiste não
   tinha como distinguir "parou" de "está parada", porque os dois eram
   silêncio. Agora "está parada" faz barulho, e só o silêncio de verdade
   quer dizer que acabou (ver `recepcao.go`).

POR QUE REENVIAR EM VEZ DE CAPTURAR DE NOVO: com nada mudando o DXGI não
entrega quadro nenhum — `QuadroAtual` não ajuda, foi medido. A alternativa
seria guardar uma cópia da textura na placa (CreateTexture2D + CopyResource)
e recomprimi-la; reenviar bytes que já saíram custa zero de CPU e ~99 KB de
memória, e o resultado no fio é idêntico.

REENVIAR É SEGURO PORQUE O QUADRO É AUTOCONTIDO. Um IDR com SPS e PPS junto
não descreve diferença nenhuma em relação ao anterior: ele descreve a imagem
inteira. Aplicá-lo duas vezes dá duas vezes a mesma imagem. Com um quadro de
diferença (P) isso seria errado, e é por isso que só o abridor é guardado.

**`if e.querChave.Swap(false)`**

O PEDIDO É ATENDIDO ANTES DE ENTREGAR O QUADRO, e a ordem é o que faz ele
valer: a ordem vale para o PRÓXIMO quadro que entrar no compressor, então
levantá-la depois de entregar atenderia o quadro seguinte — um a mais de
espera, que é justamente o que o pedido existe para cortar.

**`if medir`**

O AQUECIMENTO: a máquina sustenta a taxa que prometemos?

A medição acontece com a transmissão JÁ NO AR — estes quadros são reais e
saem para quem assiste. Não há tela de espera, não há atraso para começar; o
que existe é uma pergunta feita ao vigésimo quadro em vez de a um banco de
provas que teria de abrir um segundo compressor.

OS PRIMEIROS QUADROS SÃO OS MAIS CAROS e por isso são descartados: o
compressor ainda não tem quadro de referência, o primeiro é sempre chave, e o
de software leva algumas voltas para encher a fila interna. Medir esses e
concluir que a máquina é fraca condenaria a taxa por causa da partida.

**`e.saida.Manda(Evento`**

TIPO PRÓPRIO, E NÃO "ritmo" — a diferença não é cosmética.

Este aviso é o ÚNICO lugar onde a pessoa fica sabendo por que a
transmissão dela não está na taxa que ela escolheu, e ele acontece
UMA VEZ. Enquanto saía como "ritmo", o relatório do segundo seguinte
o sobrescrevia: a explicação aparecia por um segundo e sumia, e o
que ficava era um número baixo sem causa. Com tipo próprio o Astra
pode guardá-lo ao lado do relatório em vez de no lugar dele.

**`if desde := time.Since(relatorio); desde >= time.Second`**

UM RELATÓRIO POR SEGUNDO. É o que dá à pessoa uma prova de que a transmissão
está viva enquanto ainda não existe imagem para ver — e o que permite
perceber que a máquina não está dando conta antes de alguém reclamar.
O RELATÓRIO CONTA ONDE OS QUADROS FICAM, e não só quantos saíram.

"14 fps" sozinho não diz se a máquina não dá conta, se a tela estava parada, ou
se o compressor está segurando quadro — três coisas com o mesmo número e
remédios opostos. Do lado que recebe, contar as etapas foi o que apontou o
defeito do remontador em vez de mandar caçar no decodificador; aqui vale a
mesma regra.

**`if !medir`**

A REDE ESTÁ AGUENTANDO? O controle decide uma vez por segundo e quase
sempre responde "continua igual" — a histerese dele existe justamente
porque agir custa uma reabertura. Ver `banda.go`.

SÓ VALE ENQUANTO NÃO ESTAMOS MEDINDO A MÁQUINA: durante o aquecimento a
taxa ainda pode mudar, e reabrir por banda no meio disso jogaria fora a
medição pela metade e recomeçaria a conta.

**`func perfilDoSPS(fluxo []byte) (string, bool)`**

perfilDoSPS lê o perfil e o nível de dentro do próprio fluxo.

POR QUE LER O BITSTREAM em vez de perguntar ao compressor: perguntar exigiria a
chave `MF_MT_MPEG2_PROFILE`, mais um GUID copiado de algum lugar — e GUID errado
falha em SILÊNCIO no Media Foundation, devolvendo zero como se fosse resposta. Os
três bytes que interessam estão em toda sequência de parâmetros, logo depois do
cabeçalho do NAL, e ali não há o que interpretar errado.

Devolve o mesmo formato do `profile-level-id` do SDP, para dar para comparar com o
que a faixa declara sem converter nada na cabeça.

**`func percorrerNal(fluxo []byte, cada func(tipo byte, inicio int) bool)`**

percorrerNal visita cada unidade do fluxo, entregando o tipo e onde ela começa.
Devolver `false` para a função para a varredura.

Código de início de três ou quatro bytes; os dois convivem no mesmo fluxo, e é por isso
que isto não é um `bytes.Split`. Os cinco bits de baixo do primeiro byte dizem o tipo.

**`func abreImagemSozinho(fluxo []byte) bool`**

abreImagemSozinho diz se esta amostra basta para o outro lado montar imagem do zero.

A DEFINIÇÃO É PRECISA E NÃO É "É UM QUADRO-CHAVE". Um IDR sozinho não abre nada: ele
descreve a imagem, mas as dimensões, o perfil e as tabelas de referência estão no SPS
(7) e no PPS (8). Guardar um IDR pelado para reenviar depois entregaria a quem chega um
quadro que o descompressor recusa — e o sintoma seria "abrindo a tela…" para sempre,
que é exatamente o defeito que se está consertando.

MEDIDO neste compressor, numa sonda temporária que não ficou no repositório — o número
abaixo é o que ela deixou, e esta função é onde ele volta a ser testável se o
compressor mudar: o quadro-chave sai `IDR SEI SPS PPS AUD`
com ~99 KB, e os normais saem `P SEI PPS AUD` com ~9 KB. Ou seja, os três vêm juntos —
mas isso é conclusão de medição, não de documentação, e esta função é onde a suposição
fica testável se algum dia o compressor for outro.

### `sidecar-voz/entrega_test.go`

**`func TestOCanoDeQuadrosEntregaOQuePrometeu(t *testing.T)`**

O CONTRATO DO CANO DE QUADROS, escrito de um lado e lido do outro.

Este teste vale mais do que parece: o formato daqui é implementado DUAS vezes, aqui
em Go e lá em Kotlin, e nenhum compilador confere que os dois concordam. Um campo
trocado de lugar não dá erro em lugar nenhum — dá imagem embaralhada, que manda quem
investiga procurar no decodificador.

O teste faz o papel do Astra: escuta, recebe o segredo, lê um quadro e confere cada
campo do cabeçalho contra o que foi mandado.

**`func TestMandarNuncaEspera(t *testing.T)`**

MANDAR NUNCA PODE BLOQUEAR, e é a propriedade que mantém a call de pé.

`Mandar` é chamada de dentro do laço que lê os pacotes RTP. Se ela esperar — porque
ninguém está consumindo, porque o Astra travou, porque a fila encheu —, esse laço
para de consumir a rede, e conexão que não é consumida entope. O sintoma não seria
"vídeo travado": seria memória subindo até o processo morrer.

Aqui NINGUÉM aceita a conexão de propósito. Mesmo assim as cem chamadas têm de
voltar na hora, descartando o que não coube.

**`func TestSemEnderecoNaoHaCano(t *testing.T)`**

SEM ENDEREÇO NO AMBIENTE, NÃO HÁ CANO — e isso não é erro.

É o caso de rodar este binário à mão para diagnosticar, e o de uma versão do Astra
mais velha que a do processo. Nos dois, a voz tem de continuar funcionando inteira; o
que se perde é só a imagem.

### `sidecar-voz/entrega.go`

**— sobre o arquivo inteiro —**

A ENTREGA DOS QUADROS — como a imagem atravessa do processo de voz para o Astra.

O PROBLEMA: a ponte que já existe (stdin/stdout, JSON por linha) carrega comandos e
avisos, coisas de dezenas de bytes. Um quadro de 720p em NV12 são 1,4 MB, e a 30 por
segundo isso são 40 MB/s. Passar isso por JSON exigiria base64 — mais um terço de
tamanho — e faria a mesma fila que carrega "mudo" e "quem está falando" competir com
dezenas de megabytes por segundo. O aviso de fala chegaria atrasado por causa da
imagem.

A ESCOLHA: um cano à parte, em TCP na volta local.

Foi entre isto e memória compartilhada nomeada, que é o caminho "certo" do livro:
zero cópia, o Go escreve e a JVM lê o mesmo endereço. Ela perde aqui por três razões
concretas, e não por preguiça:

 1. A JVM não abre uma seção nomeada do Windows com biblioteca padrão. Precisaria de
    JNA (que o projeto tem) e de mais uma superfície nativa — no processo cujo motivo
    de existir é NÃO ter superfície nativa na JVM.
 2. Escrever e ler o mesmo endereço de dois processos pede um protocolo de leitura
    consistente (contador antes e depois, para pegar leitura rasgada). É correto e é
    código sutil, do tipo que falha uma vez a cada mil quadros.
 3. O ganho é uma cópia de memória. 40 MB/s de `memcpy` custa fração de por cento de
    um núcleo — abaixo do ruído da medição.

Na volta local o TCP não passa por placa de rede nenhuma; é cópia de memória com
contabilidade do sistema. Em troca vem enquadramento pronto, fluxo garantido em ordem,
e desligamento limpo quando qualquer um dos dois lados morre — as três coisas que a
memória compartilhada obrigaria a escrever à mão.

QUEM ESCUTA É O ASTRA, e este processo é quem liga. Ao contrário do que parece
natural (o dono do dado abre a porta), e a razão é uma corrida: se este processo
abrisse a porta, ele teria de ANUNCIAR o número dela, e o Astra teria de estar
ouvindo o anúncio antes de ele sair. Com o Astra escutando, o endereço vem pronto na
variável de ambiente, antes de este processo existir. Não há o que perder.

E VEM UM SEGREDO JUNTO. A volta local não é privada: qualquer programa da máquina
pode se conectar numa porta de escuta. O que passa por aqui é a TELA DA PESSOA. O
segredo é sorteado pelo Astra a cada execução e viaja pelo ambiente, que só os dois
enxergam.

**`const(…`**

O cabeçalho de cada quadro. Fixo, e o tamanho do id do par vem dentro dele — assim o
outro lado sabe quanto ler sem nunca precisar adivinhar.

0  uint32  marca ('ASTV')
4  uint32  bytes do id do par
8  uint32  largura
12 uint32  altura
16 uint32  passo (bytes por linha; pode ser MAIOR que a largura)
20 uint32  bytes do quadro
24 [..]    id do par, em UTF-8
   [..]    o quadro, em NV12

**`fila:  make(chan *quadroPronto, 2),`**

FILA CURTA DE PROPÓSITO. Vídeo não é áudio: quadro atrasado não tem valor
nenhum, porque já existe um mais novo. Uma fila longa só acrescentaria atraso
entre o que está na tela de quem transmite e o que aparece na de quem assiste
— e depois entregaria os dois de uma vez.

**`func (e *EntregaDeQuadros) Mandar(par string, q Quadro)`**

Mandar entrega um quadro, COPIANDO os bytes.

A cópia é obrigatória: o quadro que o descompressor devolve é o buffer interno dele, e
vale só até o próximo. Guardar a fatia daria um quadro montado com os bytes do
seguinte.

NUNCA BLOQUEIA. É chamada de dentro do laço que lê a rede, e travar ali pararia de
consumir pacotes RTP — que não é "vídeo lento", é conexão entupindo. Com a fila cheia
o quadro é DESCARTADO, e descartar é a decisão certa: quem assiste quer o quadro de
agora, não a fila do que já passou.

**`func (e *EntregaDeQuadros) servir()`**

servir mantém a ligação e escreve o que chega na fila.

UMA GOROUTINE SÓ, e ela é a única que toca a conexão: escrita concorrente em TCP
intercala bytes no meio de um cabeçalho, e o outro lado passa a ler tamanho de quadro
onde havia pixel. Como o laço de rede já entrega pela fila, isso sai de graça.

### `sidecar-voz/espelho.go`

**— sobre o arquivo inteiro —**

O ESPELHO — a tela de quem transmite, de volta para a janela dele.

O QUE FALTAVA, e por que não era um defeito: o cano de quadros sempre carregou só a
tela dos OUTROS, porque quem produz quadro em NV12 é o descompressor, e a própria tela
nunca passa por descompressor nenhum. Quem transmitia via o botão aceso e um relatório
de texto — nada que respondesse "estou mostrando a janela certa?", que é a única
pergunta que a pessoa realmente faz nos primeiros dez segundos.

O CAMINHO BARATO JÁ ESTAVA MONTADO, e é o motivo de este arquivo ser pequeno:

  - O `Redimensionador` (Video Processor MFT) já sabe reduzir E converter dentro da
    placa, e já sabe entregar NV12 legível pela CPU — é assim que a máquina sem
    compressor de placa transmite (ver `comprimirNaMemoria`). Aqui ele é aberto uma
    segunda vez, com outro destino.
  - O protocolo do cano JÁ RESERVA o campo vazio para "eu" (ver `ponte.go`), e o lado
    do Astra já traduz `tamPar == 0` em `par = ""`. Nada muda no formato.
  - O NV12 que sai daqui é o mesmo que o shader do Astra já desenha. Zero código de
    desenho novo.

TRÊS DECISÕES QUE SEGURAM O CUSTO, porque o espelho não pode roubar do compressor —
ele é o enfeite, e o compressor é o produto:

 1. PEQUENO. 320 de largura, não o tamanho da transmissão. A miniatura é desenhada com
    uns 160 pixels de lado na faixa de participantes; mandar 720p para caber ali seria
    mover vinte vezes mais bytes para jogar fora na hora de desenhar.
 2. LENTO, E POR RELÓGIO. Oito por segundo, medidos em tempo e não em "a cada N
    quadros" — assim o espelho custa o mesmo com a transmissão a 60 ou a 15, e a
    máquina fraca não paga mais justamente por ser fraca.
 3. DESISTE EM SILÊNCIO. Qualquer erro aqui apaga o espelho e deixa a transmissão
    seguir. Derrubar uma chamada porque a miniatura falhou seria trocar o produto pelo
    enfeite.

**`const compassoDoEspelho = 125 * time.Millisecond`**

DE QUANTO EM QUANTO TEMPO O ESPELHO ATUALIZA.

Oito por segundo é o ponto em que o movimento ainda se lê como movimento (o olho aceita
bem acima de seis) e o custo já é ruído: a conversão medida é 0,7ms no tamanho cheio, e
aqui a saída tem um vigésimo dos pixels. Oito vezes por segundo isso não chega a 0,1%
de um núcleo.

Não é ajustável de fora de propósito: seria um botão para a pessoa piorar a própria
transmissão em troca de uma miniatura mais fluida, e essa troca nunca vale.

**`func AbrirEspelho(gerente objeto, deL, deA int, entregar func(Quadro)) (*Espelho, error)`**

AbrirEspelho monta o redutor da miniatura na MESMA placa da captura.

`gerente` tem de ser o mesmo que o compressor recebeu: duas peças em placas diferentes
não trocam textura, e a máquina híbrida (Intel + NVIDIA) é onde isso aparece.

Devolve nulo SEM ERRO quando não há para quem entregar — rodar este processo à mão não
tem Astra do outro lado, e não é falha.

**`func (e *Espelho) Talvez(amostra objeto)`**

Talvez entrega um quadro ao espelho SE já for hora. Barata de chamar a 60 por segundo:
o caso comum é olhar o relógio e voltar.

A amostra recebida é a do compressor — a cópia que ele fez da captura, não a textura da
área de trabalho. Isso importa: a de lá tem de ser devolvida ao DXGI depressa, e o
espelho não pode ser mais um a segurá-la.

**`esperado := e.l * e.a * 3 / 2`**

O TAMANHO É CONFERIDO, e esta é a linha que impede o defeito mais chato desta
função. Um buffer contíguo de NV12 tem passo igual à largura, e é sobre isso que o
`Passo` mandado adiante se apoia. Se um dia vier acolchoado, a conta não fecha — e
sem esta conferência o sintoma seria uma imagem ENVIESADA em diagonal, que manda
quem investiga procurar no shader, no cano e no descompressor antes de desconfiar
de uma multiplicação.

### `sidecar-voz/fala.go`

**— sobre o arquivo inteiro —**

QUEM ESTÁ FALANDO — detectado aqui dentro, porque não há mais ninguém para
detectar.

Numa malha não existe servidor no caminho da mídia, e portanto não existe
servidor para dizer "fulano está falando". Essa informação tem que sair de onde o
áudio passa, e o áudio já passa por aqui decodificado — o custo é uma soma de
quadrados sobre 960 amostras, que é ruído perto de decodificar Opus.

O QUE VAI PELA PONTE É A TRANSIÇÃO, NUNCA O NÍVEL.

Mandar o nível a cada quadro seriam 50 mensagens por segundo POR PESSOA — numa
call de seis, 300 linhas de JSON por segundo só para acender e apagar um círculo,
e cada uma acordando o lado Kotlin para recompor a tela. A tela só precisa saber
quando começa e quando para: um punhado de mensagens por minuto.

**`const esperaAntesDeCalar = 400 * time.Millisecond`**

Segura o "falando" um instante depois do nível cair.

Sem isso, a pausa entre duas palavras apagaria o círculo, e um indicador que
pisca a cada sílaba informa menos do que indicador nenhum: o olho aprende a
ignorá-lo.

**`func (d *DetectorDeFala) Alimentar(pcm []int16, agora time.Time) bool`**

Alimentar entrega um quadro e devolve `true` quando o estado MUDOU.

Quadro vazio é silêncio válido, e é assim que o chamador avança o relógio quando
não chegou pacote nenhum — ver o prazo de leitura em `Par.receber`.

**`func nivelDe(pcm []int16) float64`**

nivelDe é a raiz da média dos quadrados, normalizada em 0..1.

Média dos quadrados e NÃO pico, de propósito: o pico dispara com qualquer estalo
de teclado ou batida na mesa. O que separa voz de estalo é energia sustentada, e
é isso que a média mede.

### `sidecar-voz/main.go`

**— sobre o arquivo inteiro —**

astra-voz — o processo de voz do Astra.

POR QUE ISTO É UM PROCESSO À PARTE, e não uma biblioteca dentro do app:

A voz do Astra vivia dentro da JVM, falando com objetos nativos por JNI. Objeto
nativo liberado enquanto outra thread ainda o usa não lança exceção em Kotlin —
derruba o processo inteiro. Na prática: abrir uma call podia fechar o Astra, e
encerrar uma call também. Um app de conversa que morre ao entrar numa chamada
está pior do que um que não tem chamada.

Num processo separado, o pior caso de um defeito de mídia vira "a call caiu e
reconectou". A conversa em texto, os servidores e as janelas abertas continuam
de pé. Essa é a razão principal de existir este binário — vale mesmo que o
código aqui dentro tenha os seus próprios bugs, porque bugs vão existir de
qualquer jeito e o que muda é o tamanho do estrago.

TOPOLOGIA: ponto a ponto (malha). A mídia vai direto de uma pessoa pra outra e
não passa por servidor nenhum. O servidor do Astra só apresenta os dois e
carrega os envelopes do aperto de mão. Isso dá a menor latência possível e custo
zero de servidor de mídia — em troca de a banda de subida crescer com o número
de pessoas na sala, que é o limite conhecido desta escolha.

**`var marcaDeOrdem = []byte{0xEF, 0xBB, 0xBF}`**

A MARCA DE ORDEM DE BYTES (BOM) na frente da primeira linha.

Não é paranoia: aconteceu no primeiro teste desta ponte. Vários escritores no
Windows põem esses três bytes no começo do fluxo, e o `json.Unmarshal` recusa
com "invalid character 'ï'" — mensagem que não lembra nem de longe a causa.
Custa uma comparação por linha evitar uma hora de caçada.

**`faixa, err := webrtc.NewTrackLocalStaticSample(CapacidadeOpus, "audio", "astra-microfone")`**

UMA faixa de microfone para a call inteira — não uma por pessoa.

É a otimização mais importante da malha, e ela precisa nascer aqui em cima
para todos os pares receberem a MESMA. Ver o comentário longo em NovoPar:
o Opus passa a rodar uma vez por quadro em vez de uma vez por companheiro.

O áudio é mono (voz não precisa de estéreo, e estéreo dobraria a banda para
carregar a mesma informação). A declaração no SDP é outra coisa — ver
CapacidadeOpus, que explica por que ela diz dois canais.

**`tela, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "video", "astra-tela")`**

A TELA COMPARTILHADA, e UMA para a sala inteira pela mesma razão do microfone:
uma escrita reaproveita o mesmo quadro comprimido para todas as conexões. Aqui a
economia pesa muito mais que no áudio — comprimir H.264 é a coisa mais cara que
este processo faz, e numa malha de quatro seriam quatro compressores.

**`config: webrtc.Configuration`**

Sem TURN configurado, o padrão é o STUN público do Google: ele resolve a
maioria das redes domésticas. As que ficam de fora são as de NAT
simétrico, e para essas só um TURN resolve — o Astra manda a lista dele
no comando `config`, e aí este padrão é substituído.

**`palco atomic.Pointer[string]`**

QUAL TELA ESTÁ NO PALCO DO ASTRA. Ver `assistindo` logo abaixo para o porquê de
ser um ponteiro e não uma string.

Atômico, e não protegido pelo mutex de baixo, porque quem lê isto é o laço de
rede de cada par — uma leitura por pacote RTP, milhares por segundo. Um mutex
aqui poria o comando raro de troca de palco no caminho de todo pacote de vídeo
da sala.

**`func (a *App) assistindo(id string) bool`**

assistindo responde se vale a pena decodificar a tela desta pessoa.

O PONTEIRO NULO NÃO É "NINGUÉM", É "O ASTRA AINDA NÃO DISSE" — e a diferença é o que
mantém este processo utilizável sozinho. Rodar `astra-voz.exe` à mão, ou os testes de
ponta a ponta, nunca manda `assistir`; se nulo valesse "ninguém", a imagem simplesmente
não abriria e o motivo não estaria em lugar nenhum.

Depois que o Astra fala uma vez, ele manda sempre — inclusive o "ninguém" explícito, que
é uma string vazia e faz esta função responder não para todo mundo.

**`func (a *App) Servir(ctx context.Context, entrada io.Reader) error`**

Servir lê a ponte linha a linha até a entrada fechar.

Um comando por vez, na mesma goroutine, de propósito: os comandos são raros
(entrar, sair, mudo) e serializá-los elimina de graça uma classe inteira de
corrida. O trabalho pesado — mídia, ICE, codificação — acontece nas goroutines
do Pion e do áudio, não aqui.

**`a.emissor.Ligar(AjustesDaTela`**

NÃO DEVOLVE ERRO DE ABERTURA, e é de propósito: montar a captura e o
compressor leva quase um segundo, e segurar a leitura da ponte por esse
tempo pararia a call inteira (é uma goroutine só, um comando por vez, ver
`Servir`). O laço sobe sozinho e conta o que houve pelo evento
EvTransmissao — que é o mesmo caminho por onde ele conta que parou.

**`go func()`**

NÃO ESPERA A RESPOSTA, ao contrário do irmão de cima. Listar aparelhos de
áudio é uma consulta a um registro e volta em milissegundos; amostrar
monitores exige DUPLICAR cada tela, e são uns 100ms por monitor. A ponte é uma
goroutine só, um comando por vez (ver `Servir`) — segurá-la meio segundo
pararia a chamada inteira, incluindo a voz.

A janela de escolha do outro lado já nasce sabendo esperar: ela abre com a
lista vazia e preenche quando o evento chega.

**`responder := func(lista []MonitorDaTela, err error)`**

A RESPOSTA SAI SEMPRE, mesmo quando dá errado — e sai VAZIA em vez de não
sair. A janela de escolha do outro lado espera este evento para parar de
dizer "procurando as telas"; sem ele, uma falha aqui a deixaria procurando
para sempre, que é o beco sem saída pior que o erro. O motivo vai junto,
pelo caminho de erro, para quem for ler o registro.

**`par.pedirQuadroChave = a.emissor.PedirQuadroChave`**

O PEDIDO DE QUADRO-CHAVE ATRAVESSA A MALHA INTEIRA, e cai num emissor só.

Faz sentido justamente porque a faixa de tela é UMA para a sala: quem pede é uma
pessoa, mas o quadro-chave que sai atende todo mundo de uma vez. Numa sala em que
três entram juntas, os três pedidos viram um quadro caro em vez de três.

**`a.motor.DefinirMudo(ligado)`**

O mudo é do MICROFONE, não das conexões: silenciar na fonte significa que
nem sequer sai pacote, em vez de sair silêncio codificado para cada pessoa
da sala. Em malha isso é a diferença entre gastar banda com nada e não
gastar, multiplicada pelo número de pessoas.

**`type Escritor struct`**

Escritor serializa a saída.

Existe porque os eventos nascem em várias goroutines do Pion ao mesmo tempo, e
duas escritas concorrentes na saída padrão intercalariam bytes no meio de uma
linha. O outro lado lê linha a linha: meia linha de JSON é um erro de parse que
só aparece sob carga, que é o pior tipo de bug pra caçar.

### `sidecar-voz/memoria.go`

**— sobre o arquivo inteiro —**

QUANTA MEMÓRIA ISTO SEGURA — e a pergunta é sobre a memória que o Go NÃO vê.

O perfil do Go mede o heap do Go. Quase nada do que este processo segura mora lá: as
texturas são da placa, as amostras são objetos COM, os buffers do Media Foundation são
alocados pelo Windows. Um `IMFSample` que deixa de ser solto não aparece em
`runtime.MemStats` — aparece como a memória do processo subindo sem explicação, que é
exatamente a caçada que já custou caro neste projeto.

POR ISSO A MEDIDA É `PrivateUsage` E NÃO O CONJUNTO DE TRABALHO. O conjunto de
trabalho é quanto está na RAM física AGORA, e o Windows o encolhe sozinho quando quer
memória — ele desce sem nada ter sido liberado, e sobe sem nada ter vazado. O
`PrivateUsage` é o quanto o processo pediu e ainda não devolveu, incluindo o que foi
paginado para o disco. É o número que só sobe quando alguém esquece de soltar.

### `sidecar-voz/mistura_test.go`

**`func TestMisturaSobConcorrencia(t *testing.T)`**

O misturador é a peça mais disputada do processo: uma goroutine por pessoa
entregando voz, mais a goroutine da saída puxando, todas ao mesmo tempo. Estes
testes existem para rodar sob `-race`, que é o que transforma uma corrida
silenciosa em falha visível.

**`var entregadores sync.WaitGroup`**

DOIS GRUPOS DE ESPERA, e não um.

Na primeira versão a goroutine que puxa estava no MESMO grupo das que
entregam. Como ela só para depois do `close(parar)`, e o `close` só acontece
depois do `Wait()`, o `Wait()` esperava por alguém que esperava por ele —
impasse por construção. O teste travou por nove minutos até o tempo estourar.

Separar deixa a ordem óbvia: espera quem entrega terminar, manda a saída
parar, e só então espera por ela.

**`puxador.Add(1)`**

A saída puxa sem parar, como no app — mas CEDENDO a vez a cada volta.

Sem o `Gosched`, este laço vira uma espera ocupada que pega o cadeado, larga
e pega de novo sem intervalo, e as goroutinas que entregam nunca conseguem
entrar. Não é hipótese: a primeira versão deste teste travou por nove
minutos exatamente assim.

No app real isso não acontece porque a saída dorme esperando o aviso do
aparelho (~10ms entre voltas). O `Gosched` aqui reproduz esse respiro sem
precisar de relógio, e mantém o teste rápido.

**`func TestEntregarCopiaOQuadro(t *testing.T)`**

O QUADRO ENTREGUE NÃO PODE SER GUARDADO POR REFERÊNCIA.

Quem entrega reaproveita o próprio buffer no quadro seguinte — é o que o laço de
recepção faz. Se o misturador guardasse a fatia em vez de copiar, a fila inteira
apontaria para a mesma memória e o som viraria o último quadro repetido N vezes.
Este teste falha exatamente nesse caso.

### `sidecar-voz/mistura.go`

**— sobre o arquivo inteiro —**

MISTURA — juntar a voz de todo mundo num fluxo só para o alto-falante.

Numa malha não existe servidor somando as vozes: cada pessoa recebe N-1 fluxos
separados e tem que somá-los em casa. Este arquivo é esse "em casa", e é o lugar
onde mora o custo que cresce com o tamanho da call — a faixa compartilhada
resolveu a CODIFICAÇÃO, não a decodificação.

A soma tem uma armadilha que quase todo mundo pisa: somar duas ondas de 16 bits
estoura os 16 bits. Somar sem tratar isso não produz volume alto, produz um
rangido — o valor dá a volta e vira negativo, e o resultado soa como rádio
quebrado. É por isso que existe o corte lá embaixo.

**`const quadrosDeFolga = 3`**

quadrosDeFolga é quantos quadros de 20ms cada pessoa pode adiantar antes de
começarmos a descartar.

Três quadros (60ms) é um meio-termo escolhido: menos que isso e qualquer
tremida de rede vira buraco audível; muito mais e a conversa fica com atraso
perceptível, que é pior — gente começa a falar por cima uma da outra.

**`type Misturador struct`**

Misturador guarda o que chegou de cada pessoa e entrega a soma.

Um buffer por pessoa, e não uma fila só: cada um chega no seu ritmo, e juntar
tudo numa fila única faria a voz de quem tem rede pior atropelar a de quem tem
rede boa.

**`soma []int32`**

Acumulador reaproveitado entre chamadas de Puxar.

Alocar aqui dentro custava 50 alocações por segundo, e — pior — a alocação
acontecia SEGURANDO O CADEADO, alongando a seção crítica justamente na função
que as goroutinas de recepção disputam. Reaproveitar encurta o trecho travado
e tira o coletor de lixo do caminho do áudio.

Só é tocado sob o cadeado, então não precisa de proteção própria.

**`ultimaEntrega time.Time`**

Quando esta pessoa entregou voz pela última vez.

TEMPO, e não contagem de chamadas — e a diferença é um defeito de verdade que
já esteve aqui. Contar chamadas assume que quem puxa puxa a cada 20ms, e não
é o que acontece: o laço de saída enche TODO o espaço livre de uma vez, então
dispara várias puxadas em rajada. Com contagem, uma rajada de esvaziamento
eliminava da mistura gente que estava só um pouco atrasada, e a voz dessa
pessoa sumia sem motivo aparente.

**`const silencioAteEsquecer = 3 * time.Second`**

Depois de quanto tempo sem receber nada uma pessoa é esquecida.

Ela saiu, caiu, ou está em silêncio profundo com DTX. Nos três casos, largar o
buffer evita segurar memória por uma call inteira — e reaparecer é barato,
porque o primeiro quadro que chegar recria a entrada.

**`func (m *Misturador) Entregar(id string, pcm []int16)`**

Entregar guarda um quadro decodificado de alguém.

Chamado da goroutine que lê a conexão daquela pessoa — uma por par, portanto
várias ao mesmo tempo. Daí o mutex.

**`if len(v.fila) >= quadrosDeFolga`**

DESCARTA O MAIS ANTIGO, não o mais novo, quando a fila enche.

Parece contraintuitivo jogar fora o que chegou primeiro, mas em conversa ao
vivo o áudio velho não tem valor nenhum: ninguém quer ouvir o que foi dito há
200ms com 200ms de atraso. Guardar o novo mantém a conversa no presente, ao
custo de um engasgo curto — que é o que o ouvido perdoa.

**`copy(v.fila, v.fila[1:])`**

`copy` e não `fila[1:]`: re-fatiar avança o início dentro do array de
baixo, e o pedaço abandonado na frente nunca é reaproveitado. Ao longo de
uma call de horas isso é um crescimento lento e silencioso. Deslocar em
cima do mesmo array mantém a memória constante, e são três posições.

**`func cortar(v int32) int16`**

cortar prende o valor dentro dos 16 bits.

Corte simples, e não divisão pelo número de vozes, de propósito: dividir faria o
volume de todo mundo CAIR toda vez que alguém entrasse na call, o que soa como
defeito. O estouro só acontece quando várias pessoas falam alto ao mesmo tempo,
que é raro e curto; baixar o volume de todos o tempo inteiro para evitá-lo seria
pagar sempre por um problema que acontece às vezes.

### `sidecar-voz/monitores_test.go`

**— sobre o arquivo inteiro —**

A PROVA DO SELETOR DE TELA.

Duas coisas que só a máquina responde, e nenhuma delas é adivinhável:

 1. os índices de vtable e o arranjo do `DXGI_OUTPUT_DESC`. Índice errado em COM não
    dá erro — chama outra função. Arranjo errado desloca os campos e devolve números
    que parecem plausíveis. A prova é o NOME sair legível (`\\.\DISPLAY1`) e o tamanho
    bater com a resolução de verdade: as duas coisas juntas só acontecem se a struct
    inteira estiver certa.
 2. a miniatura tem imagem de verdade, e não um retângulo preto. Este é o caso que a
    área de trabalho parada produzia antes de `QuadroAtual` existir.

ASTRA_TESTE_TELA=1 go test -run Monitores -v

### `sidecar-voz/monitores.go`

**— sobre o arquivo inteiro —**

O SELETOR DE TELA — quais monitores existem, e o que está em cada um.

Até aqui a transmissão mandava sempre o monitor 0 e não perguntava. Numa máquina de um
monitor isso está certo por acaso; em duas telas, é metade de chance de compartilhar a
errada — e quem erra descobre pelo "não é essa" de outra pessoa na chamada.

A MINIATURA NÃO É ENFEITE, e é o motivo de este arquivo ser maior que uma listagem. O
Windows chama os monitores de `\\.\DISPLAY1` e `\\.\DISPLAY2`, e esses nomes não dizem
nada: dois monitores do mesmo modelo têm a mesma resolução e nomes que só diferem no
dígito. A única informação que separa um do outro é O QUE ESTÁ NELE. Escolher por lista
de texto é escolher por tentativa e erro, com a tentativa acontecendo ao vivo na frente
de outras pessoas.

LISTAR E AMOSTRAR SÃO PASSOS SEPARADOS, de propósito. A lista sai de `EnumOutputs` e
nunca falha; a miniatura precisa DUPLICAR o monitor, e duplicação é exclusiva por
processo. Se a pessoa já estiver transmitindo o monitor 1 e abrir o seletor para
trocar, a amostra desse monitor falha — e a resposta certa é a lista completa com uma
miniatura faltando, não uma lista vazia.

**`var avisarQueEntendemosDePixel = sync.OnceFunc(func()`**

avisarQueEntendemosDePixel diz ao Windows que este processo fala em pixels de verdade.

SEM ISTO O SELETOR MENTE, e mentia: numa tela 1920x1080 a 125%, o `DXGI_OUTPUT_DESC`
respondia 1536x864 — as coordenadas da área de trabalho vêm ESCALADAS para processos
que não se declaram cientes de DPI. A miniatura mostraria a tela certa com o tamanho
errado escrito embaixo, e o número errado é justamente o que a pessoa usa para
distinguir dois monitores.

É seguro num processo sem janela nenhuma: a ciência de DPI só muda como o Windows
reporta coordenadas e escala janelas, e aqui não há janela para escalar.

Uma vez por processo, e antes de qualquer consulta ao DXGI — depois disso o Windows
ignora a mudança.

**`const LarguraDaMiniatura = 256`**

LarguraDaMiniatura é o tamanho em que cada tela é amostrada.

256 e não 320 por causa do transporte: a resposta viaja como UMA LINHA de JSON pela
saída padrão, com o PNG em base64 dentro. A 256 de largura cada miniatura fica em uns
30 KB codificados; a 320, em 50. Com quatro monitores a diferença é entre 120 KB e
200 KB numa linha só, e a linha é lida de uma vez do outro lado.

**`func ListarMonitores() ([]MonitorDaTela, error)`**

ListarMonitores devolve as telas desta máquina, cada uma com uma amostra do que está
nela quando dá para tirá-la.

PRECISA RODAR NUMA THREAD PRESA com COM aberto, como todo o resto deste subsistema.

**`var textura objeto`**

O PRIMEIRO QUADRO DEPOIS DE DUPLICAR VEM PRETO, e isto custou uma volta: a
miniatura saía com 472 bytes de PNG, que é o tamanho de um retângulo de uma cor
só. A duplicação precisa de um ciclo para engatar — o primeiro `AcquireNextFrame`
devolve uma superfície válida e vazia.

Descartar o primeiro e ficar com o segundo resolve. `QuadroAtual` e não
`ProximoQuadro` porque quem escolhe qual tela compartilhar costuma estar com a
área de trabalho parada, e `ProximoQuadro` responde "nada mudou" justamente aí.

**`const AmostrasPorLado = 3`**

AmostrasPorLado é quantos pontos de origem entram em cada pixel da miniatura.

Três por lado, ou seja nove por pixel. O caminho barato seria pegar UM ponto por
bloco, e ele produz aquele serrilhado de miniatura mal feita — o texto da tela vira
chuvisco e a imagem deixa de ser reconhecível, que é a única coisa que ela precisa
ser. Ler o bloco INTEIRO seria o certo em teoria e custa caro na prática: a memória
mapeada de uma textura é lida devagar (foi ela que custou 6,9ms por quadro na
transmissão), e ler os 8 MB de um 1080p inteiro leva dezenas de milissegundos POR
MONITOR.

Nove pontos por pixel são 590 mil leituras para uma tela de 2 milhões de pixels —
bom o bastante para o olho e barato o bastante para não fazer a janela demorar.

### `sidecar-voz/motor.go`

**— sobre o arquivo inteiro —**

O MOTOR DE ÁUDIO — os dois laços que fazem o som circular.

São duas goroutines, cada uma presa à própria thread do sistema, e a separação
não é enfeite: captura e reprodução são guiadas por relógios DIFERENTES. O
microfone entrega quando o aparelho quer; o alto-falante pede quando está com
fome. Amarrar os dois no mesmo laço faria um esperar pelo outro, e a espera de um
viraria falha de áudio do outro.

Entre eles não há chamada direta, só o misturador — que é o único ponto onde os
dois relógios se encontram, e é por isso que ele carrega o mutex.

**`"errors"`**

`errors.Is` e não `==` nas comparações com ErrSemAudio.

Hoje as duas formas dão o mesmo resultado — o erro nunca é embrulhado no caminho
até aqui. Mas é exatamente a família do defeito que custou caro nesta sessão (ver
`esperaEstourada` em par.go): comparação de erro que passa a mentir no dia em que
alguém acrescenta um `%w` no meio, sem falhar em compilação nem em teste. E o
silêncio seria pior aqui do que lá: "não há áudio agora" é o caso COMUM deste laço,
então confundi-lo com falha de verdade derrubaria a captura a cada bloco vazio.

**`var CapacidadeOpus = webrtc.RTPCodecCapability`**

CapacidadeOpus é como a faixa se anuncia no SDP.

`Channels: 2` mesmo o áudio sendo MONO, e isso não é engano: em WebRTC o Opus é
sempre negociado como `opus/48000/2`, e é assim que o Pion o registra por padrão.
A contagem no SDP é formalidade de negociação — quantos canais o áudio realmente
tem viaja dentro do próprio fluxo Opus, e mono é perfeitamente válido ali.

Declarar `Channels: 1` aqui parece mais honesto e QUEBRA a chamada: a faixa deixa
de casar com o codec registrado, e o outro lado recusa com "codec não suportado".
Foi assim que o teste de chamada completa falhou duas vezes.

**`aparelhoEntrada atomic.Value`**

Qual aparelho usar em cada sentido. Vazio = o de comunicação padrão do
Windows. A GERAÇÃO ao lado é o que avisa o laço de que a escolha mudou: o laço
a compara a cada volta e, quando difere, fecha o aparelho e abre o novo.

Contador em vez de "mudou?" booleano porque duas trocas rápidas seguidas
perderiam a segunda — o laço zeraria a bandeira depois de atender a primeira e
nunca saberia da outra.

**`cancelarEco atomic.Bool`**

Passar o microfone pelo cancelador de eco do Windows. LIGADO por padrão: quem
usa caixas de som em vez de fone devolve o áudio de todo mundo pelo próprio
microfone, e essa pessoa é a última a perceber — quem sofre são os outros.
Deixar desligado por padrão seria escolher o defeito.

**`suprimirRuido atomic.Bool`**

Supressão de ruído e ganho automático. Moram DENTRO do cancelador (é o mesmo
objeto do Windows), então valem só quando ele está no caminho — ver
AbrirEntradaDeVoz. Guardados aqui do mesmo jeito que o eco porque mudam pela
ponte, em plena call, e o laço de captura os relê ao reabrir a fonte.

**`ecoReprovado atomic.Bool`**

O CANCELADOR JÁ FOI TENTADO E NÃO PRODUZIU NESTA MÁQUINA.

Separado da escolha da pessoa de propósito: `cancelarEco` é o que ela quer,
isto é o que a máquina consegue. Sobrescrever a escolha dela faria o interruptor
mentir — ele diria "ligado" para sempre enquanto nada acontece.

**`saidaPronta chan struct{}`**

Fecha quando o alto-falante abre.

O CANCELADOR DE ECO EXIGE UM FLUXO DE SAÍDA ATIVO, e isso não é suposição:
medido. Sem saída aberta ele responde S_FALSE indefinidamente e não entrega uma
amostra sequer; com ela, entrega 98% do tempo real. Faz sentido — é um
cancelador de eco, e sem referência não há o que cancelar contra.

Como as duas goroutines sobem juntas, sem este sinal a captura podia abrir o
cancelador antes de existir saída, e ele nasceria mudo.

**`func (m *Motor) DefinirTratamento(aj AjustesDaVoz)`**

DefinirTratamento troca os três ajustes do microfone em plena call.

Reaproveita a geração da entrada porque o efeito é o mesmo de trocar de aparelho: o
laço fecha a fonte atual e abre outra. Um contador separado só para isto seria
duplicar mecanismo idêntico.

OS TRÊS DE UMA VEZ e não um por chamada: as três são propriedades escritas na
ABERTURA do cancelador, então cada mudança custa uma reabertura — alguns quadros de
silêncio. Aplicar em bloco cobra esse preço UMA vez.

AVALIA OS TRÊS ANTES DE DECIDIR, e a ordem importa: `Swap` já escreveu o valor novo
quando devolve o antigo, então interromper no primeiro que não mudou deixaria os
outros dois por escrever. Foi por isso que os três `Swap` acontecem incondicionalmente
e só a REABERTURA é condicional — reabrir à toa corta o som sem nada em troca.

**`func (m *Motor) DefinirAparelho(sentido int, id string)`**

DefinirAparelho troca o microfone ou o alto-falante EM PLENA CALL.

Não interrompe a chamada: só o laço daquele sentido fecha o aparelho e abre o
outro, o que custa alguns quadros de silêncio. As conexões continuam de pé, e é
por isso que a troca vive aqui e não no nível da sala.

**`func (m *Motor) laçoDeCaptura(ctx context.Context)`**

laçoDeCaptura: microfone -> Opus -> rede.

DOIS LAÇOS, e o de fora existe por causa da troca de aparelho: quando a pessoa
escolhe outro microfone, o de dentro sai, o aparelho é fechado, e o de fora abre o
novo. As conexões nem ficam sabendo.

**`cod, err := NovoCodificador(fonte.Taxa(), CanaisDeVoz)`**

O CODIFICADOR NASCE COM A FONTE, e não antes dela.

Ele era criado uma vez, fora do laço, quando toda fonte entregava 48 kHz.
Com o cancelador de eco isso deixou de valer: ele entrega 16 kHz, e um
codificador aberto na taxa errada não dá erro — produz voz acelerada ou
arrastada, que soa como defeito de rede e manda procurar no lugar errado.

Recriar por troca de aparelho é barato: acontece quando alguém mexe no
seletor, não a cada quadro.

**`if ficouMuda && querEco`**

CANCELADOR QUE NÃO PRODUZ É PIOR QUE CANCELADOR NENHUM: a call fica sem
microfone e a pessoa só descobre quando alguém diz "não te ouço".

Sala em silêncio NÃO cai aqui — o cancelador entrega amostras de silêncio,
não ausência de amostras. Zero amostras por segundos é máquina em que ele
não engatou, e aí a resposta certa é voz com eco em vez de voz nenhuma.

**`func (m *Motor) bombearMicrofone(ctx context.Context, mic FonteDeAudio, cod *Codificador, geracao uint64) bool`**

bombearMicrofone lê desta fonte até o contexto morrer ou a escolha de aparelho
mudar.

Devolve `true` quando a fonte ficou MUDA — nenhuma amostra por tempo demais. Quem
chama usa isso para desistir do cancelador de eco e voltar ao microfone cru.

**`porQuadro := mic.Taxa() * MilissegundosPorQuadro / 1000`**

O QUADRO SAI DA FONTE, e não da constante global.

Vinte milissegundos são vinte milissegundos, mas quantas AMOSTRAS isso são
depende da taxa: 960 na captura crua de 48 kHz, 320 no cancelador de eco de
16 kHz. Usar a constante global aqui mandaria 960 amostras ao Opus quando só
existiam 320 — e o codificador aceitaria, produzindo voz arrastada.

**`const paciencia = 2 * time.Second`**

VIGIA DA FONTE MUDA.

Uma fonte que abre sem erro e nunca entrega nada é o pior estado possível: a
call parece funcionando e a pessoa só descobre quando alguém diz "não te ouço".
Foi exatamente assim que o cancelador de eco se comportou numa máquina sem
fluxo de saída ativo — S_FALSE para sempre, sem um único erro.

Sala em silêncio NÃO dispara isto: microfone entrega amostras de silêncio, não
ausência de amostras.

**`mudo := m.mudo.Load()`**

MUDO CORTA NA FONTE. Não é "codificar silêncio e mandar": é não
mandar nada. Em malha, mandar silêncio codificado para N pessoas é
gastar banda com nada, N vezes.

O acumulador continua sendo consumido para não crescer sem fim
enquanto a pessoa está muda.

**`var paraODetector []int16`**

MUDO É SILÊNCIO PARA O DETECTOR, e não "não medir".

Alimentar com o quadro de verdade acenderia o meu círculo enquanto
estou mudo — eu me veria falando enquanto ninguém me ouve, que é
exatamente o engano que o indicador existe para evitar.

**`func (m *Motor) laçoDeSaida(ctx context.Context)`**

laçoDeSaida: rede -> mistura -> alto-falante.

Mesma estrutura de dois laços da captura, e pelo mesmo motivo: trocar de saída em
plena call fecha só este aparelho.

**`m.avisarSaida.Do(func() { close(m.saidaPronta) })`**

AVISA A CAPTURA que já existe fluxo de saída. O cancelador de eco depende
disso para engatar — sem referência, ele não produz nada. Uma vez só: o
que importa é que a saída já EXISTIU, e as reaberturas por troca de
aparelho são curtas demais para o cancelador notar.

**`bloco := quadro`**

ENSURDECIDO CONTINUA PUXANDO, e joga fora.

Parar de puxar deixaria as filas de todo mundo cheias, e ao voltar a
ouvir a pessoa receberia de uma vez o que ficou represado — um jato de
conversa velha. Puxar e descartar mantém a call andando no presente,
que é onde ela tem de estar quando o ouvido voltar.

**`func (m *Motor) reclamar(oQueFazia string, err error)`**

reclamar manda o erro para o Astra e para o registro.

Falha de áudio NÃO derruba o processo: as conexões continuam de pé, e o Astra
pode decidir avisar a pessoa e tentar de novo. Sair aqui levaria a call inteira
junto por causa de um fone desconectado.

### `sidecar-voz/opus_test.go`

**`func abrirParaTeste(t *testing.T)`**

ESTE TESTE É A REDE DE SEGURANÇA DA LIGAÇÃO SEM CGO.

Sem cgo não existe compilador conferindo assinatura: um tipo errado em `opus.go`
não vira erro de compilação, vira memória corrompida em produção. O que substitui
o compilador é isto — chamar a biblioteca de verdade e conferir que o que volta
faz sentido.

Roda com o caminho da DLL no ambiente:

$env:ASTRA_OPUS_DLL="C:\...\opus-0.dll"; go test ./...

Sem a variável, o teste é PULADO em vez de falhar: numa máquina limpa, ou no
runner de CI, a ausência da DLL não é defeito do código.

### `sidecar-voz/opus.go`

**— sobre o arquivo inteiro —**

OPUS SEM CGO.

O codificador Opus é uma biblioteca em C, e o caminho comum em Go é o cgo
(`hraban/opus`). Aqui não: carregamos a DLL em tempo de execução e chamamos as
funções por `syscall`. O build continua sendo `go build` puro, sem compilador C,
e o release automatizado no Windows não ganha uma dependência que quebra sozinha
meses depois. É o mesmo padrão que o Astra já usa com o ffmpeg e o GStreamer.

O PREÇO, dito em voz alta: sem cgo não há compilador conferindo assinatura. Se um
tipo aqui estiver errado, o resultado não é erro de compilação — é memória
corrompida e travamento. Por isso cada função abaixo carrega, em comentário, a
assinatura em C de onde ela veio (`opus.h`), e os números mágicos vêm com o nome
da constante original (`opus_defines.h`). Conferir isso é o que substitui o
compilador.

Também é Windows-only por construção. É o único sistema que o Astra desktop tem
hoje; se um dia houver Linux, este arquivo ganha um irmão.

**`bandaLarga = 1103 // OPUS_BANDWIDTH_WIDEBAND`**

OPUS_SET_BITRATE_REQUEST
OPUS_SET_MAX_BANDWIDTH_REQUEST
OPUS_SET_COMPLEXITY_REQUEST
OPUS_SET_INBAND_FEC_REQUEST
OPUS_SET_PACKET_LOSS_PERC_REQUEST
OPUS_SET_DTX_REQUEST
OPUS_SET_SIGNAL_REQUEST

**`func AbrirOpus(caminho string) error`**

AbrirOpus carrega a biblioteca. Chamar antes de qualquer outra coisa daqui.

A carga é explícita, e não preguiçosa no primeiro uso, porque falta de DLL tem
que aparecer ao ligar o processo — não no meio de uma chamada, quando a pessoa
já está esperando ouvir alguém.

**`type Codificador struct`**

Codificador transforma PCM em quadros Opus.

NÃO É SEGURO usar de várias goroutines: o estado interno da libopus não é. Só a
goroutine de captura mexe nele, e é assim que tem que continuar.

**`ajustes := []struct`**

OS AJUSTES SÃO A OTIMIZAÇÃO, e é por isso que eles vivem aqui e não numa
função "configurar" que alguém pode esquecer de chamar.

- 24 kbps mono: voz inteligível de sobra. Música pediria 64+, e não é o caso.
- Banda larga (até 8 kHz): voz humana não usa a faixa acima disso, então
  gastar bits ali é desperdício puro.
- Sinal de voz: diz ao codificador o que ele está ouvindo, e ele escolhe
  melhor onde economizar.
- DTX: em silêncio, manda um pacote minúsculo a cada ~400ms em vez de um
  quadro cheio a cada 20ms. É a maior economia de banda por linha escrita.
- FEC embutido: uma cópia de baixa taxa do quadro anterior viaja dentro do
  atual, então perder um pacote não vira um buraco audível. Preferido ao
  NACK porque não custa uma ida e volta — e num buffer de voz, que é curto,
  retransmissão quase sempre chega tarde demais para servir.
- Perda declarada em 10%: é o que diz ao FEC quanta redundância vale a pena.
- Complexidade 5 (de 10): metade do custo de CPU do máximo, com diferença
  inaudível em voz. Numa malha isso multiplica pelo número de pessoas.

**`func (c *Codificador) controlar(pedido, valor int) error`**

controlar chama o `opus_encoder_ctl`, que é variádico em C.

int opus_encoder_ctl(OpusEncoder *st, int request, ...);

Variádico chamado por syscall funciona aqui porque no Windows x64 os argumentos
inteiros de uma função variádica seguem a MESMA ordem de registradores dos
argumentos fixos. Isso vale para os pedidos que recebem um inteiro, que são
todos os usados acima. Pedido que receba ponteiro ou double precisaria de
cuidado próprio — se algum entrar aqui um dia, este comentário é o aviso.

**`func (c *Codificador) Codificar(pcm []int16, saida []byte) (int, error)`**

Codificar transforma um quadro de PCM em bytes Opus, escrevendo em `saida`.
Devolve quantos bytes valem.

opus_int32 opus_encode(OpusEncoder *st, const opus_int16 *pcm, int frame_size,
                       unsigned char *data, opus_int32 max_data_bytes);

`quadros` é a contagem POR CANAL, não o tamanho da fatia — confundir os dois é o
erro clássico desta API e produz áudio acelerado ou lento.

Um retorno de 1 byte NÃO é erro: é o DTX dizendo "silêncio". Esse pacote ainda
deve ser enviado, porque é ele que mantém o outro lado sabendo que a conexão
está viva.

**`func (d *Decodificador) Decodificar(dados []byte, pcm []int16, recuperando bool) (int, error)`**

Decodificar devolve quantos quadros por canal foram escritos em `pcm`.

int opus_decode(OpusDecoder *st, const unsigned char *data, opus_int32 len,
                opus_int16 *pcm, int frame_size, int decode_fec);

`dados` vazio significa PERDA, e é assim que se usa o FEC: o decodificador
reconstrói o que faltou a partir da cópia embutida no pacote seguinte, ou
inventa um trecho plausível. Pular a chamada em vez de avisar a perda produz o
clique que todo mundo reconhece como "falhou a internet".

### `sidecar-voz/orcamento_test.go`

**— sobre o arquivo inteiro —**

AS DUAS REGRAS QUE PROTEGEM A MÁQUINA FRACA, e nenhuma delas precisa de placa para ser
provada: são contas puras. Ficam num arquivo só porque respondem à mesma pergunta —
"o que esta máquina aguenta?" — por dois lados: quantos quadros por segundo, e de que
tamanho.

### `sidecar-voz/palco_test.go`

**— sobre o arquivo inteiro —**

SÓ A TELA QUE ALGUÉM ESTÁ OLHANDO É DECODIFICADA.

São dois testes com ambições bem diferentes, e vale saber qual prova o quê:

TestOPalcoDecideDeQuemEATela   a REGRA, em memória, sem hardware nenhum
TestATelaForaDoPalcoNaoEDecodificada   o CAMINHO, com placa, rede e descompressor

O primeiro roda sempre e é instantâneo. O segundo precisa de monitor e de compressor de
H.264, e é o único que consegue provar a única coisa que de fato importa: que o pacote
CHEGA e mesmo assim não vira imagem.

**`func TestOPalcoDecideDeQuemEATela(t *testing.T)`**

A REGRA, isolada do resto: quem o Astra põe no palco é quem tem a tela decodificada.

O CASO QUE MERECE TESTE É O NULO, e ele é contraintuitivo o bastante para justificar
este arquivo sozinho: "o Astra ainda não disse nada" tem de valer SIM, não não. Se
valesse não, rodar `astra-voz.exe` à mão — ou qualquer teste de ponta a ponta que não
mande `assistir` — simplesmente não abriria imagem, e o motivo não estaria em lugar
nenhum do registro. É o tipo de padrão que só se descobre depois de uma hora.

**`func TestOComandoDeAssistirTrocaOPalco(t *testing.T)`**

O COMANDO QUE VEM PELA PONTE, e não só a regra que ele alimenta.

O que este teste guarda é a CÓPIA dentro de `Executar`. Guardar `&cmd.Par` funcionaria
igualzinho nos dois primeiros casos abaixo e prenderia o `Comando` inteiro na memória
pelo tempo que o palco durar — e o `Comando` carrega o campo onde cabe um SDP. É um
vazamento pequeno, silencioso e que nenhuma asserção de comportamento pegaria; a única
forma de fixá-lo é um teste que diga que a cópia existe.

**`func TestATelaForaDoPalcoNaoEDecodificada(t *testing.T)`**

O CAMINHO INTEIRO, com o pacote chegando de verdade.

Este é o teste que separa "não decodifica" de "não recebe" — e a diferença é tudo. Um
laço que parasse de LER a faixa também não decodificaria nada, e estaria errado do
jeito mais caro possível: pacote não consumido se acumula no buffer do pion até o
processo ficar sem memória, com a pessoa transmitindo do outro lado sem saber.

Por isso a asserção do meio não é um tempo esgotado. É o relatório de segundo dizendo,
com número, quantos pacotes chegaram e foram descartados. Só depois de ter essa prova é
que o teste confere que nenhum quadro atravessou o cano.

**`parA.pedirQuadroChave = emissor.PedirQuadroChave`**

O PEDIDO DE QUADRO-CHAVE PRECISA CHEGAR NO EMISSOR, e ligá-lo é o que o `App` faz de
verdade (ver `abrirPar`). Sem esta linha o arnês mede outro caminho: o PLI sai, cai
no vazio, e a imagem só abre no quadro-chave natural — cinco segundos. Foi
exatamente o que aconteceu na primeira execução deste teste, e o número (4,21s) não
era da fatia, era do arnês.

**`if abriu > 2*time.Second`**

DOIS SEGUNDOS É O TETO, e o número tem origem: o quadro-chave natural deste
compressor sai a cada cinco segundos, e é justamente ele que o pedido de imagem
existe para não esperar. Passar de dois segundos quer dizer que o pedido não
está chegando — a falha silenciosa que faz a troca de palco parecer travamento.

**`antes := total.Load()`**

E CONTINUA CORRENDO. Abrir uma vez e parar é um defeito diferente de não abrir, com
a mesma cara para quem assiste: a imagem congela no primeiro quadro. A folga é
grande de propósito — um terço da taxa transmitida —, porque o que se mede aqui é
"a imagem está viva", não a taxa exata.

### `sidecar-voz/par.go`

**`type Par struct`**

Par é a conexão com UMA pessoa da call.

Numa malha, cada participante tem um destes por companheiro. Eles não sabem uns
dos outros: quem coordena é o App, e essa ignorância é proposital — um par que
cai não pode arrastar os outros junto.

**`mu        sync.Mutex`**

Candidatos que chegaram ANTES da descrição remota.

Isto não é caso raro, é o caso NORMAL do trickle ICE: o outro lado começa a
mandar candidato assim que descobre o primeiro, e isso costuma acontecer
antes de a resposta dele voltar por um servidor que está do outro lado do
país. O Pion recusa candidato sem descrição remota, então guardar e aplicar
depois é obrigatório — sem isso a conexão fecha mais devagar, ou não fecha.

**`func NovoPar(…`**

NovoPar abre a conexão e liga os avisos. `faixa` é o áudio do microfone.

A MESMA `faixa` é passada para todos os pares, de propósito, e essa é a
otimização mais importante desta malha. Um `TrackLocalStaticSample` guarda por
dentro uma ligação por conexão em que foi adicionado, e uma escrita nele
reaproveita o mesmo quadro codificado para todas. Ou seja: o Opus roda UMA vez
por quadro de 20ms, não uma vez por pessoa na sala.

O que isso NÃO faz, e é importante não se enganar: não economiza banda. Cada par
continua recebendo a própria cópia dos pacotes pela rede. O que fica constante é
a CPU de codificação, e é ela que estouraria primeiro numa máquina modesta.

**`if _, err := pc.AddTransceiverFromKind(…`**

SEM MICROFONE AINDA PRECISA DECLARAR QUE OUVE.

Isto não é detalhe: em WebRTC, quem não anuncia nada não negocia nada. Um
par sem faixa produz uma resposta sem áudio nenhum, e o outro lado falha
com "codec não suportado" — foi exatamente assim que o teste de chamada
completa quebrou na primeira vez.

O caso é real e não é raro: entrar na call sem microfone, com o
microfone tomado por outro programa, ou só para escutar. Sem esta linha,
essas pessoas não ouviriam ninguém.

**`if tela != nil`**

A TELA ENTRA AGORA, MESMO SEM NINGUÉM TRANSMITINDO.

Uma faixa incluída depois obriga a renegociar o SDP com todo mundo da sala — e
renegociação em malha é N apertos de mão, no exato instante em que a pessoa
acabou de apertar "transmitir" e a máquina já vai começar a comprimir. Declarada
desde o início, ela custa uma linha de mídia parada, e começar a transmitir vira
apenas começar a escrever nela.

Falhar aqui NÃO derruba a conexão, e é a diferença deste bloco para o do
microfone: sem áudio a call não tem função, mas sem vídeo ela é exatamente o que
o Astra já era até esta versão. Uma call com voz e sem tela compartilhada é bem
melhor que erro na cara de quem só queria conversar.

**`pc.OnICECandidate(func(c *webrtc.ICECandidate)`**

TRICKLE ICE: manda cada candidato assim que aparece, em vez de esperar a
coleta terminar. Numa malha isso pesa muito mais do que numa call de dois —
são N-1 apertos de mão acontecendo ao mesmo tempo, e esperar a coleta
completa de cada um multiplica a espera até a primeira voz sair.

**`if remota.Kind() == webrtc.RTPCodecTypeVideo`**

A faixa PRECISA ser lida sempre: pacote que não é consumido fica se
acumulando no buffer do Pion. Uma conexão sem leitor não é "silenciosa",
é uma conexão que vaza memória enquanto a pessoa fala.

E VÍDEO PRECISA SER LIDO INCLUSIVE QUANDO NÃO HÁ COMO MOSTRAR. Antes de
existir descompressor, esta faixa era ignorada — e ignorada não quer dizer
parada: quem transmitisse encheria o buffer de quem assiste até o processo
ficar sem memória. É por isso que o caminho de vídeo tem leitor próprio, e
não um `if` que às vezes lê.

**`func (p *Par) receber(remota *webrtc.TrackRemote)`**

receber lê a voz desta pessoa, decodifica e entrega ao misturador.

UMA GOROUTINE E UM DECODIFICADOR POR PESSOA, e isso é obrigatório, não escolha
de estilo: o decodificador Opus guarda estado entre quadros (é assim que ele
recupera perda e faz a transição suave), então dois fluxos passando pelo mesmo
decodificador produzem lixo. É também aqui que mora o custo que cresce com o
tamanho da call.

A goroutine morre sozinha quando `ReadRTP` devolve erro, o que acontece quando o
par fecha — daí não precisar de contexto nem de canal de parada.

**`_ = remota.SetReadDeadline(time.Now().Add(200 * time.Millisecond))`**

PRAZO DE LEITURA, e ele existe por um motivo específico.

Quem está mudo não manda pacote NENHUM — o mudo corta na fonte, lá no
motor. Sem prazo, `ReadRTP` simplesmente bloqueia, o detector nunca é
alimentado, e o indicador de fala fica aceso até a pessoa sair da call.
O prazo transforma "não chegou nada" em silêncio explícito, que é o que
isso de fato é.

Mais curto que a espera do detector, senão a espera nunca venceria.

**`func (p *Par) ouvirPedidos(remetente *webrtc.RTPSender)`**

ouvirPedidos escuta os recados que ESTA pessoa manda sobre a tela que mandamos a ela.

O RECADO QUE IMPORTA É "PERDI A IMAGEM" (Picture Loss Indication). Ele é o mecanismo
padrão do WebRTC para recuperar vídeo, e existe porque a alternativa é ruim: sem ele,
quem perde um quadro-chave numa oscilação de rede fica com a imagem congelada até o
PRÓXIMO quadro-chave natural — medido neste compressor, cinco segundos, e ele não
aceita encurtar esse intervalo (ver a sonda do ICodecAPI). Cinco segundos de imagem
parada é tempo de a pessoa concluir que a chamada caiu.

LER OS RECADOS É OBRIGATÓRIO MESMO SEM ATENDER, pelo mesmo motivo de ler a faixa
remota: o que não é consumido se acumula no buffer do pion. Daí esta goroutine existir
mesmo quando `pedirQuadroChave` é nulo.

Ela morre sozinha quando a conexão fecha, que é quando `ReadRTCP` passa a errar.
ouvirPedidos lê o RTCP que volta deste par.

DUAS COISAS CHEGAM POR AQUI, e a segunda é bem mais recente que o nome da função:

PLI / FIR       "perdi a imagem, manda um quadro-chave"
ReceiverReport  "de cada 256 pacotes que você mandou, tantos não chegaram"

O relatório de recepção vem uma vez por segundo, de graça, desde sempre — e era
jogado fora. É ele que diz se a rede está aguentando o que estamos mandando, e é a
única realimentação de rede que a transmissão tem (ver `banda.go` para por que o
controle de congestionamento do pion não entrou no lugar).

**`pior := 0.0`**

A PIOR ENTRE AS FAIXAS deste par, e não a soma: um relatório traz uma
entrada por fluxo (voz e tela), e a voz é minúscula perto do vídeo.
Somar diluiria a perda do que importa dentro do que não importa.

`FractionLost` é ponto fixo de oito bits — a fração é o valor sobre
256, e lê-lo como porcentagem direta daria 25.600% no colapso.

**`func esperaEstourada(err error) bool`**

esperaEstourada diz se o erro é "o prazo de leitura venceu" e não uma falha de verdade.

`errors.Is(err, os.ErrDeadlineExceeded)` NÃO SERVE AQUI, e a lição custou caro porque
falha do jeito mais silencioso possível: o pion devolve `*packetio.netError`, um tipo
dele, que implementa `net.Error` mas NÃO embrulha o erro sentinela da biblioteca
padrão. A comparação compila, roda, e responde "não é prazo" para um erro que é
exatamente um prazo. MEDIDO, e não deduzido — foi o que a sonda imprimiu quando a
transmissão de tela ganhou prazo de leitura:

SONDA: a faixa de A morreu: *packetio.netError i/o timeout

O ESTRAGO É GRANDE PORQUE O RAMO DO PRAZO É O CAMINHO NORMAL, não o de exceção:

voz   quem está MUDO não manda pacote nenhum — o mudo corta na fonte, no motor. O
      prazo de 200ms vencia, a comparação dizia "não é prazo", e a goroutine
      RETORNAVA. Quem ficasse mudo por um quinto de segundo deixava de ser ouvido pelo
      resto da chamada, inclusive depois de desmutar, porque não sobrava ninguém lendo
      a faixa dela. E o pior: a faixa abandonada continua acumulando no buffer do pion.
tela  o mesmo, com a tela parada.

`net.Error.Timeout()` é a pergunta certa. É a interface que os dois implementam, e
`os.ErrDeadlineExceeded` também responde `true` nela — então isto cobre os dois casos
sem precisar saber qual biblioteca produziu o erro.

**`func (p *Par) liberarGuardados()`**

liberarGuardados aplica o que chegou cedo demais.

Erro aqui é registrado e engolido de propósito: um candidato ruim entre vinte
não invalida o aperto de mão, e derrubar a conexão por causa de um seria trocar
uma falha parcial por uma total. O ICE tenta todos os caminhos e usa o que
funcionar.

### `sidecar-voz/ponte.go`

**— sobre o arquivo inteiro —**

A PONTE entre o Astra (Kotlin) e este processo.

O transporte é a entrada e a saída padrão, uma mensagem JSON por linha. Foi
escolhido em cima de socket TCP local por três motivos concretos:

 1. Não abre porta. Um socket local acorda o Firewall do Windows, e o usuário
    recebe um alerta de segurança por causa de um app de conversa querendo
    escutar na rede. Isso assusta, e com razão.
 2. A vida do processo já vem amarrada. Se o Astra morre, a entrada padrão
    fecha, e este processo termina sozinho — sem sidecar órfão comendo
    microfone depois que o app fechou.
 3. Dá pra depurar com o teclado. `astra-voz.exe` e digitar uma linha de JSON
    é um teste completo, sem nenhuma ferramenta.

A saída de ERRO padrão é livre: vai tudo que for log. O Astra captura e guarda
junto do diagnóstico de rede. Só a saída padrão carrega protocolo — misturar as
duas quebraria o leitor de linha do outro lado.

**`type Comando struct`**

Comando é o que o Astra manda pra cá.

Um struct só, com campos opcionais, em vez de um por comando: o JSON chega sem
tipo conhecido, e discriminar antes de decodificar exigiria duas passadas de
parse. O custo é alguns campos vazios por mensagem, o que num canal que carrega
uns poucos KB por chamada não significa nada.

**`Eco   bool json:"eco,omitempty"`**

tratamento: os três ajustes do microfone, JUNTOS num comando só.

Juntos porque moram no mesmo objeto do Windows e mudar qualquer um deles obriga
a reabrir a fonte. Em comandos separados, mexer em dois interruptores seguidos
cortaria o som duas vezes — e quem está ajustando o microfone costuma mexer em
mais de um.

**`CmdAssistir = "assistir"`**

Diz QUAL tela está no palco do Astra agora. `Par` vazio quer dizer "nenhuma" —
que é o caso de sair da sala de voz para uma conversa de texto sem largar a
chamada. Só a tela nomeada aqui é decodificada; as outras chegam e são
descartadas sem custo. Ver `recepcao.go`.

**`EvTransmissao = "transmissao"`**

A transmissão de tela. `V` é "1" no ar e "0" fora dele. Enquanto está no ar,
`Tipo` diz de que notícia se trata e `Msg` a carrega:

Tipo = <nome do compressor>  ->  Msg = "1280x720 @30", o que de fato subiu
Tipo = "perfil"              ->  Msg = "42e01f", lido de dentro do fluxo
Tipo = "ritmo"               ->  Msg = "58 fps · 3,4 Mbps", uma vez por segundo

**`EvTelaDeOutro = "tela"`**

A tela de OUTRA pessoa. `Par` diz de quem, `V` é "1" enquanto a faixa de vídeo
dela existe — e ela existe mesmo quando ninguém está assistindo, que é o que
permite ao Astra oferecer a troca de palco. `Tipo` = "faixa" no aviso de que há
tela, o nome do descompressor quando ele abre, "ritmo" nos relatórios de segundo.

Os QUADROS não vêm por aqui — eles têm cano próprio (ver `entrega.go`). Este
evento é só o aviso de que há tela chegando, que é o que a interface precisa para
abrir o espaço antes do primeiro pixel.

### `sidecar-voz/quadrochave_test.go`

**`func TestOCompressorDaQuadroChaveComRegularidade(t *testing.T)`**

DE QUANTO EM QUANTO TEMPO SAI UM QUADRO-CHAVE?

A pergunta decide se a transmissão FUNCIONA para quem chega depois, e a resposta não
se adivinha: cada compressor tem um padrão diferente, e alguns tratam tela
compartilhada como conteúdo estático e espaçam os quadros-chave por MUITO tempo.

POR QUE IMPORTA. Um decodificador de H.264 não abre imagem nenhuma antes de receber um
quadro-chave: os outros quadros só descrevem a DIFERENÇA em relação ao anterior, e sem
um ponto de partida não há o que diferenciar. Então quem entra na sala depois de a
transmissão ter começado — que é o caso normal — fica olhando para o vazio até o
próximo. Se o próximo demorar trinta segundos, a transmissão está quebrada para essa
pessoa mesmo com tudo funcionando.

Foi exatamente assim que `TestATransmissaoAtravessaDePontaAPonta` falhou uma vez e
passou na seguinte: a primeira execução perdeu o quadro-chave inicial (escrito na
faixa antes de o ICE fechar) e ficou trinta segundos esperando outro.

**`const quadrosNecessarios = 240`**

A CONTA É EM QUADROS, NÃO EM SEGUNDOS DE RELÓGIO — e a diferença já reprovou este
teste por engano.

O compressor conta o intervalo entre quadros-chave em QUADROS COMPRIMIDOS, e quem
alimenta o compressor é a mudança na tela. Numa janela de seis segundos de relógio
com a tela pouco ativa, entram uns cem quadros em vez de cento e oitenta — e o
segundo quadro-chave simplesmente ainda não chegou. MEDIDO nos dois lados de um
diff que não tocava em nada disto: 181 quadros e 2 chaves com a tela ativa, 105 e
1 com ela quieta. O código estava igual; o veredito, não.

Duzentos e quarenta quadros são oito segundos de vídeo a 30/s — folga suficiente
para caber dois quadros-chave em qualquer compressor razoável. O teto de relógio
existe só para o teste não ficar preso quando ninguém está mexendo na máquina.

**`func TestPedirQuadroChaveFuncionaDeVerdade(t *testing.T)`**

PEDIR UM QUADRO-CHAVE FUNCIONA MESMO?

A sonda (`TestSondaDoCodecAPI`) diz que o compressor SUPORTA a ordem. Suportar não é
obedecer: `SetValue` pode devolver sucesso e o compressor seguir o próprio compasso.
Este teste espera o intervalo natural passar de longe, PEDE, e confere que o
quadro-chave veio muito antes do que viria sozinho.

Sem ele, o caminho inteiro de recuperação de imagem (o outro lado pede, este atende)
seria construído sobre uma promessa não conferida.

**`if demora > 1500*time.Millisecond`**

UM SEGUNDO E MEIO, e o número tem folga de propósito.

Meio segundo parecia razoável e reprovou por 0,3 milissegundo numa execução em que
o pedido tinha funcionado perfeitamente. Duas coisas legítimas entram nessa conta e
nenhuma delas é o compressor ignorando a ordem: ele é assíncrono e segura alguns
quadros, e a captura só entrega quadro quando a tela MUDA — tela parada por um
instante empurra tudo para a frente.

O que decide é a comparação com o intervalo natural, que é de CINCO segundos.
Qualquer coisa abaixo de dois só pode ser o pedido; um limite justo demais só
produz reprovação que não significa nada.

**`func temQuadroChave(fluxo []byte) bool`**

temQuadroChave procura um NAL de fatia IDR (tipo 5) no fluxo.

O tipo 5 é o que ancora a imagem: ele se decodifica sozinho, sem depender de nenhum
quadro anterior. Os parâmetros (7 e 8) costumam vir na frente dele, mas são descrição,
não imagem — procurar por eles daria falso positivo em compressor que os repete.

### `sidecar-voz/recepcao.go`

**— sobre o arquivo inteiro —**

RECEBER A TELA DE OUTRA PESSOA — de pacote RTP a quadro pronto para desenhar.

O CAMINHO INTEIRO, e cada etapa existe por um motivo:

pacotes RTP  ->  remontador  ->  H.264 de um quadro  ->  descompressor  ->  NV12

O REMONTADOR NÃO É LUXO. Um quadro-chave de 720p tem uns 60 KB e o caminho da rede
aceita ~1200 bytes por pacote: são cinquenta pacotes para UM quadro. Eles chegam fora
de ordem, um pode se perder, e entregar isso ao descompressor na ordem em que chega
produz lixo. O `samplebuilder` do pion junta pelos números de sequência e só solta
quando o quadro está completo — e descarta o que ficou incompleto, que é o
comportamento certo: meio quadro não é meio de imagem, é imagem quebrada.

UMA THREAD PRESA POR PESSOA, e é exigência de COM. O descompressor é um objeto do
Media Foundation, criado nesta goroutine, e usá-lo de outra não dá erro claro: dá
comportamento indefinido. Como cada pessoa tem o próprio descompressor (é obrigatório
— o decodificador guarda estado entre quadros, igual ao Opus), cada uma tem a própria
thread. Custa uma thread por pessoa transmitindo, que é o preço de assistir.

O CUSTO MEDIDO de decodificar 720p30 é 1,03 ms por quadro — 3,1% de um núcleo (ver
`TestVoltaCompletaDaTela`). Três pessoas transmitindo ao mesmo tempo custam ~9%, o
que cabe folgado até na máquina fraca que o modo econômico atende.

**`const pacotesQueEsperam = 512`**

Quantos pacotes o remontador pode segurar esperando os que faltam.

ESTE NÚMERO JÁ ESTEVE EM 50 E QUEBRAVA A TRANSMISSÃO INTEIRA — de um jeito que parecia
qualquer outra coisa. Vale contar, porque o sintoma não aponta para cá.

O raciocínio errado era: "um quadro-chave ocupa uns cinquenta pacotes, então cinquenta
bastam". Mas o remontador não guarda UM quadro: ele guarda uma JANELA de números de
sequência, e joga fora o mais antigo quando ela estoura. Com a janela do tamanho exato
de um quadro-chave, o primeiro pacote dele já tinha sido despejado quando o último
chegava — e o quadro-chave era o único que NUNCA se completava.

O que se via: os quadros normais (poucos pacotes) passavam todos, quinze por segundo,
e mesmo assim a imagem nunca abria. Sem erro em lugar nenhum, porque nada tinha
falhado — só o quadro que ANCORA a imagem é que não chegava. Um decodificador de
H.264 não abre nada sem ele: os outros quadros descrevem a DIFERENÇA em relação ao
anterior, e sem ponto de partida não há o que diferenciar.

MEDIDO, e não deduzido: com 50, três de seis execuções do teste de ponta a ponta
ficavam trinta segundos sem receber nada; com 512, oito de oito passam em ~3s.

Quinhentos e doze é folgado de propósito: cabe um quadro-chave de 1080p (~150 pacotes)
com espaço para os que chegam enquanto ele se completa. O custo é uma lista de
ponteiros — nada perto de uma transmissão que não abre.

**`const silencioQueEncerra = 5 * time.Second`**

QUANTO SILÊNCIO QUER DIZER "ACABOU".

Não existe pacote de "parei de transmitir" em RTP: a faixa continua declarada no SDP e
simplesmente deixa de trafegar. Sem um prazo, a última imagem de quem parou ficava no
palco de todo mundo parecendo ao vivo, até a pessoa sair da chamada — e quem olha não
tem como saber que está olhando o passado.

O NÚMERO SÓ PODE EXISTIR PORQUE O OUTRO LADO FALA COM A TELA PARADA. Antes de
`sinalDeVida`, "sem pacote" e "sem mudança na tela" eram indistinguíveis, e qualquer
prazo aqui apagaria a tela de quem compartilha um documento e para de mexer — que é o
caso de uso, não a exceção. Os dois números são um par: mexer num sem o outro reintroduz
exatamente esse defeito.

Cinco segundos são duas vezes e meia o sinal de vida: dois sinais seguidos podem se
perder na rede sem que a imagem de ninguém pisque.

**`func (p *Par) receberTela(remota *webrtc.TrackRemote)`**

receberTela lê a faixa de vídeo desta pessoa e entrega os quadros ao Astra.

SÓ DECODIFICA A TELA QUE ESTÁ NO PALCO, e essa é a diferença mais importante entre
este laço e o que ele era. A leitura da rede continua acontecendo sempre — não é
opcional, um `TrackRemote` sem leitor entope o buffer do pion enquanto a pessoa
transmite —, mas o pacote de quem ninguém está olhando morre aqui mesmo, sem passar
pelo remontador nem pelo descompressor.

O QUE ISSO POUPA, em números medidos: 1,03 ms por quadro decodificado (ver
`TestVoltaCompletaDaTela`). Com três pessoas transmitindo e uma no palco, a máquina
deixa de pagar 2 ms a cada 33 ms — 6% de um núcleo que não estava comprando imagem
nenhuma. E o caso que mais pesa nem tem palco: sair da sala de voz para uma conversa
de texto sem largar a chamada desmonta a `VoiceView` inteira, e aí ninguém está
olhando NADA enquanto a call continua.

É a mesma regra que o Discord aplica ("will only relay video to a participant on the
call if they are watching it"), com a diferença de que lá ela vale na origem, porque
há um servidor de mídia no meio. Aqui a malha entrega a todos e o corte é no destino:
economiza CPU, não banda. Cortar banda também exigiria avisar quem transmite, o que é
outra fatia.

A goroutine morre sozinha quando `ReadRTP` devolve erro, o que acontece quando o par
fecha — mesma regra da faixa de voz, e por isso também não precisa de contexto.

**`noAr := true`**

O AVISO DE "HÁ TELA CHEGANDO" É DA FAIXA, NÃO DO DESCOMPRESSOR — e essa separação
virou obrigatória agora.

É este evento que acende o distintivo de "transmitindo" na faixa de participantes,
e é o distintivo que a pessoa clica para pôr aquela tela no palco. Se ele fosse
junto do descompressor, sair do palco apagaria o distintivo, e a tela que ninguém
está vendo viraria uma tela que ninguém CONSEGUE ver — o beco sem saída em que o
próprio remédio tranca a porta.

E ELE VAI E VOLTA. `noAr` acompanha se a tela desta pessoa está de pé agora; ela cai
quando o silêncio passa de `silencioQueEncerra` e volta quando chega pacote de novo.
Sem essa ida e volta, uma transmissão que parasse e recomeçasse ficaria fora do palco
para sempre.

**`pedirImagem := func()`**

"PERDI A IMAGEM, MANDA UM QUADRO-CHAVE."

Sem este pedido, entrar numa sala onde alguém JÁ está transmitindo significa
esperar o próximo quadro-chave natural — medido no compressor do outro lado, até
cinco segundos de vazio, e ele não aceita encurtar esse intervalo. O mesmo vale
depois de qualquer oscilação de rede que engula um quadro-chave, e agora também
toda vez que esta tela sobe ao palco: por definição não há em que ancorar a
imagem, porque os quadros de diferença dos últimos minutos foram descartados.

**`var d *Descompressor`**

O DESCOMPRESSOR NASCE QUANDO ALGUÉM OLHA, e morre quando param de olhar. Abrir um
custa procurar e amarrar um objeto do Windows, e ele segura vários megabytes de
buffer interno enquanto vive — pagar isso por uma tela fora do palco é gastar
memória para produzir pixel que vai direto para o lixo.

**`desistiu := false`**

DESISTIU marca "esta máquina não tem descompressor de H.264".

Sem ela, a falha viraria uma tentativa de abrir por PACOTE — milhares por segundo,
cada uma varrendo o registro de objetos do Windows. Zera quando a tela sai do
palco: se a pessoa insistir, tenta de novo, uma vez.

**`jaTemImagem := false`**

SEPARADA DO CONTADOR DO RELATÓRIO, e a distinção não é cosmética: `quadros` zera a
cada segundo para medir a taxa, então usá-la como "já tenho imagem?" faria o pedido
disparar de novo a cada relatório — uma enxurrada de pedidos com a imagem
perfeitamente no ar, e cada um custando ao outro lado um quadro-chave caro.

**`_ = remota.SetReadDeadline(time.Now().Add(conferirOSilencio))`**

PRAZO DE LEITURA, e ele existe pelo mesmo motivo do prazo da faixa de voz: sem
ele, `ReadRTP` bloqueia para sempre e "acabou" nunca é percebido, porque a
ausência de pacote não gera evento nenhum. O prazo transforma "não chegou nada"
em uma pergunta ao relógio, que é o que isso de fato é.

**`if !jaTemImagem && time.Since(ultimoPedido) >= time.Second`**

INSISTE ENQUANTO NÃO HOUVER IMAGEM, e para de insistir assim que houver.

Chegar pacote e não sair quadro é exatamente o estado de quem entrou no meio
de um grupo de imagens: os quadros de diferença chegam, mas não há em que
aplicá-los. Um pedido por segundo é o suficiente para não desperdiçar o
intervalo e pouco o bastante para não virar enxurrada — cada pedido atendido
custa ao outro lado um quadro caro.

**`if desde := time.Since(relatorio); desde >= time.Second`**

O RELATÓRIO CONTA AS QUATRO ETAPAS, e não só a última.

"0 fps" sozinho não diz nada: pode ser rede que não chega, remontador que
nunca fecha um quadro, descompressor que recusa tudo — ou, agora, ninguém
olhando. São quatro estados diferentes com o mesmo sintoma, e separá-los aqui é
o que transformou uma caçada em uma leitura — foi este contador que apontou o
remontador acima.

**`func (p *Par) queremVer() bool`**

queremVer responde se a tela desta pessoa está no palco do Astra.

NULO É SIM, e não é descuido: é o que mantém o processo utilizável fora do Astra. Ver
`App.assistindo`, que é quem monta este fechamento.

### `sidecar-voz/redimensionador_test.go`

**`var clsidRedimensionador = guid(0x88753B26, 0x5B24, 0x49BD,`**

SONDA DO REDIMENSIONADOR, antes de escrever o redimensionador.

Três perguntas decidem o formato do código, e nenhuma delas tem resposta confiável na
documentação — as três já foram respondidas errado por ela neste mesmo projeto:

 1. ele TRAZ a amostra de saída, ou temos de alocar a textura de destino? A
    diferença é um anel inteiro de texturas a mais.
 2. é comandado por recados, como o compressor de hardware? Muda o laço.
 3. a ordem é saída-antes-de-entrada, como no H.264, ou o contrário?

Perguntar custa este arquivo. Supor custa uma tarde, e já custou.

### `sidecar-voz/redimensionador.go`

**— sobre o arquivo inteiro —**

O REDIMENSIONADOR — reduz o quadro DENTRO da placa, antes de comprimir.

POR QUE ELE EXISTE, já que o conversor de cor não precisou existir. A pergunta foi a
mesma nos dois casos ("o compressor não faz isso sozinho?") e a resposta foi
diferente: em cor ele fazia, em tamanho ele recusa. Medido, com todas as letras:

Intel Quick Sync (x2)   entrada 1080p com saída 720p: "tipo de mídia
                        inválido, inconsistente ou sem suporte"
Microsoft AVC DX12      recusa a própria saída em 1280x720

Entrada e saída do compressor têm de ter o mesmo tamanho. Então alguém precisa
reduzir antes, e esse alguém é o Video Processor MFT — que vem no Windows, roda na
placa, e é OUTRO `IMFTransform`, ou seja, reaproveita tudo que já está montado.

A SONDA (`redimensionador_test.go`) respondeu três coisas que mudaram o formato
deste arquivo, e nenhuma delas era adivinhável:

 1. ELE TRAZ A PRÓPRIA AMOSTRA DE SAÍDA (bandeira 0x100). Isso apaga um anel
    inteiro de texturas de destino que o plano previa — não alocamos nada.
 2. NÃO é comandado por recados. Laço simples: entra quadro, sai quadro.
 3. A ORDEM É ENTRADA E DEPOIS SAÍDA — o CONTRÁRIO do compressor de H.264, onde a
    saída tem de vir primeiro. Duas peças vizinhas, do mesmo subsistema, com ordens
    opostas: é exatamente o tipo de assimetria que custa uma tarde a quem supõe que
    a de um vale para o outro.

**— sobre o arquivo inteiro —**

ELE TAMBÉM CONVERTE, e é isso que abre a transmissão na máquina sem compressor de
placa. Nela o único compressor é o de software, que não fala D3D11 e não aceita RGB —
só a família YUV. Pedir NV12 na saída daqui resolve as duas recusas de uma vez: a
conversão acontece na placa (0,7ms, medido) e a amostra que sai já é legível pela CPU,
que é onde esse compressor vive. Ver `sonda_software_test.go`.

O CAMINHO DE HARDWARE CONTINUA PEDINDO ARGB32, e de propósito: lá o quadro nunca sai
da placa, e converter seria pagar por um passo que o próprio compressor faz de graça.

**`func AbrirRedimensionador(gerente objeto, deL, deA, paraL, paraA int, formatoSaida windows.GUID) (*Redimension`**

AbrirRedimensionador liga o Video Processor MFT e o amarra à mesma placa da captura.

`gerente` é o MESMO gerenciador que o compressor recebeu. Compartilhar não é economia
de linha: as duas peças precisam falar da MESMA placa, senão a textura que uma
produz não serve para a outra — que é o problema que a máquina híbrida já apresenta
entre a Intel e a NVIDIA.

`formatoSaida` é o que o compressor do outro lado aceita: `formatoARGB32` no caminho
de placa, `formatoNV12` no de software.

**`func (r *Redimensionador) Reduzir(entrada objeto) (objeto, error)`**

Reduzir passa um quadro pelo redimensionador e devolve o quadro menor.

A amostra devolvida É DE QUEM CHAMA: precisa ser solta depois de entregue ao
compressor. Ela vem do próprio redimensionador (ele aloca), e por isso este arquivo
não guarda textura nenhuma.

### `sidecar-voz/saida_test.go`

**`func TestTocarTom(t *testing.T)`**

TOCA UM TOM AUDÍVEL. É teste de verdade justamente por isso: som saindo pelo
alto-falante é a única prova de que a ligação COM da saída está certa, e nenhuma
verificação em código substitui ouvir.

$env:ASTRA_TESTE_SOM="1"; go test -run Saida -v ./...

### `sidecar-voz/saida.go`

**— sobre o arquivo inteiro —**

SAÍDA DE SOM por WASAPI — o outro lado da captura.

A diferença de fundo entre os dois: na captura, quem manda no ritmo é o
aparelho, e nós corremos atrás. Aqui, quem tem que estar sempre à frente somos
nós — se o buffer esvaziar antes de escrevermos, o som falha, e falha de saída é
audível na hora. Por isso a saída trabalha com uma folga proposital e escreve
silêncio quando não há nada a dizer, em vez de simplesmente parar.

**`func AbrirSaida(id string) (*Saida, error)`**

AbrirSaida prepara o alto-falante. `id` vazio significa o de comunicação padrão
do sistema; um id escolhe outro.

Mesma exigência da captura: chamar e usar na MESMA thread, presa com
PrenderNaThread.

**`duracao := int64(100 * porMilissegundo)`**

100ms de buffer. Menor que o da captura de propósito: aqui o buffer é
LATÊNCIA que a pessoa ouve, não folga de segurança. Cem milissegundos é o
ponto em que a conversa ainda parece imediata e ainda há margem para uma
pausa do escalonador.

**`func (s *Saida) Escrever(pcm []int16) error`**

Escrever entrega amostras ao alto-falante. `pcm` nulo ou curto demais preenche o
resto com silêncio.

Escreve TUDO que couber, não só um quadro: se o buffer esvaziou porque a máquina
engasgou, encher de uma vez é o que recupera sem falhar de novo no quadro
seguinte.

### `sidecar-voz/semplaca_test.go`

**— sobre o arquivo inteiro —**

A TRANSMISSÃO NA MÁQUINA SEM COMPRESSOR DE PLACA.

Esta é a prova do caminho que `AbrirCompressor` só percorre quando TODOS os
compressores de placa recusaram — o caso da máquina virtual, do notebook antigo e da
área de trabalho remota. Antes dele existir, essas máquinas não transmitiam nada: não
"pior", nada. O botão de compartilhar tela acendia e a imagem nunca aparecia do outro
lado.

POR QUE O TESTE CHAMA `amarrar` DIRETO em vez de `AbrirCompressor`: nesta máquina há
compressor de placa, e ele ganha na primeira passada — o caminho de software nunca
seria exercitado. Chamar a segunda passada pelo nome é o que permite prová-la aqui, e
`amarrar` é a função inteira dela: a única coisa que `AbrirCompressor` acrescenta é o
laço de candidatos e o teto de 720p, que `TestTetoDeSoftware` cobre à parte.

ASTRA_TESTE_TELA=1 go test -run SemPlaca -v

### `sidecar-voz/sonda_banda_ao_vivo_test.go`

**— sobre o arquivo inteiro —**

SONDA: DÁ PARA MUDAR A BANDA COM O COMPRESSOR ABERTO?

A resposta é NÃO, nesta máquina, por três caminhos diferentes — e é por isso que a
adaptação de banda reabre o compressor em vez de fazer a coisa óbvia.

A PERGUNTA IMPORTA porque reabrir custa: um quadro-chave e uns décimos sem imagem,
justamente quando a rede já está sofrendo, que é quando o ajuste é pedido. Mudar ao
vivo seria uma chamada e nenhum engasgo.

AS TRÊS ROTAS, e todas foram MEDIDAS, não deduzidas:

1. ICodecAPI SetValue(AVEncCommonMeanBitRate)
   `TestSondaDoCodecAPI` mostra que os quatro compressores desta máquina aceitam a
   chamada com S_OK — inclusive os dois que declaram `IsModifiable = não`. Este
   teste pesou o fluxo depois: pedido 3000 -> saiu 3015; pedido 600 -> saiu 3014;
   pedido 3000 -> saiu 3015. Reto.

2. SetOutputType com a banda nova, no meio do fluxo
   Aceito e ignorado do mesmo jeito. Saiu 3015 nas três fases.

3. Encerrar o fluxo, repor o tipo, reabrir o fluxo
   Derruba o compressor: "puxar o H.264: Falha catastrófica".

A CONCLUSÃO QUE FICA: o compressor honra o `MF_MT_AVG_BITRATE` que recebe na ABERTURA
— 3000 pedidos viraram 3015 medidos, e a constância do número em tela parada mostra
que ele está em taxa constante e enche o que falta. Depois disso o controle de banda
está travado.

ACEITAR NÃO É OBEDECER, e as duas primeiras rotas são a mesma pegadinha do
`MF_MT_MAX_KEYFRAME_SPACING`, aceito com todas as honras e sem efeito nenhum. É a
quarta declaração do Media Foundation a mentir neste projeto. Perguntar ao objeto se
ele aceita não basta: tem de pesar o que sai.

O teste fica como REGISTRO e como rede de segurança: se um dia um compressor passar a
obedecer de verdade, ele começa a falhar aqui — e aí a adaptação pode largar o
reabrir. Por isso ele reprova quando o fluxo NÃO muda.

ASTRA_TESTE_TELA=1 ASTRA_TESTE_BANDA_AO_VIVO=1 go test -run BandaAoVivo -v

**`rodar := func(quanto time.Duration) float64`**

Roda por um tempo e devolve quantos kbps DE FATO saíram.

Mede pelo relógio e não por quadro: é a banda que está em jogo, e banda é bytes
por segundo. Uma tela mais parada rende menos quadros, e contar por quadro
esconderia isso dentro da média.

### `sidecar-voz/sonda_banda_test.go`

**— sobre o arquivo inteiro —**

SONDA DA ESTIMATIVA DE BANDA — o pion sabe dizer quanto a rede aguenta?

A PERGUNTA POR TRÁS. Hoje a transmissão manda 2.500 kbps porque o preset diz 2.500
kbps. Numa conexão que aguenta 800, o resultado não é imagem pior: é pacote perdido,
enxurrada de retransmissão e imagem quebrada, com nada recuando. O Discord decide
resolução, taxa e qualidade a partir de uma estimativa de banda; o Astra não tem
estimativa nenhuma.

O QUE JÁ EXISTE E O QUE FALTA, conferido no pion 4.2.18 e não suposto:
`RegisterDefaultInterceptors` liga NACK, relatórios RTCP, cabeçalhos de simulcast,
estatísticas e o REMETENTE de TWCC. Ou seja, o outro lado já manda os relatórios de
chegada — e ninguém os consome. Estimador de banda: nenhum.

A peça está em `pion/interceptor/pkg/gcc`, já baixada como dependência transitiva.

A PERGUNTA QUE DECIDE SE VALE A PENA, e que documentação nenhuma responde para o
nosso caso: ele SOBE a estimativa, ou só sabe descer? A diferença é tudo. Um
estimador que só desce protege a conexão ruim mas nunca deixa a boa usar o que tem —
e uma transmissão de tela passa a maior parte do tempo mandando MENOS do que poderia,
porque tela parada não gera quadro. É a "região limitada pela aplicação": sem tráfego
suficiente, o estimador não tem o que medir.

Duas fases, e a segunda é a que responde:

1. oferece 1.000 kbps e observa a estimativa
2. oferece 6.000 kbps e observa de novo

Se a estimativa da fase 2 subir muito acima da fase 1, ele sobe de verdade. Se ficar
grudada no que oferecemos, ele só acompanha — e aí o desenho em produção tem de
sondar a banda por conta própria.

============================================================================
ESTADO: A SONDA NÃO CONSEGUIU LIGAR O GCC. Ela fica no repositório porque o que
ela DESCOBRIU vale mais que o que ela não conseguiu — e porque a próxima pessoa
que tentar precisa começar destes quatro fatos, não do zero.

 1. SEM `ConfigureTWCCHeaderExtensionSender`, O PACER DESCARTA TODOS OS PACOTES.
    `RegisterDefaultInterceptors` registra o REMETENTE de TWCC (quem recebe mídia
    passa a mandar relatório), mas NÃO carimba a extensão nos pacotes que saem. O
    pacer do GCC recusa qualquer pacote sem ela, e recusa num log que só aparece
    se alguém estiver olhando: "failed to write packet: missing transport layer cc
    header extension". Ligar o GCC sem esta linha em produção pararia a
    transmissão INTEIRA, sem erro devolvido a ninguém.

 2. OS DOIS LADOS PRECISAM DA MESMA API. Com o receptor na API padrão, a oferta
    saía com `a=extmap:4 .../transport-wide-cc` e a resposta voltava SEM ela.
    Quem estima é quem manda, mas só consegue se quem recebe souber carimbar a
    chegada.

 3. `ouvirPedidos` É CARGA MAIOR DO QUE O NOME DIZ. Em pion o RTCP só atravessa a
    cadeia de interceptores quando alguém chama `ReadRTCP` no remetente. Aquela
    goroutine, que existe para atender pedido de quadro-chave, é a mesma que vai
    alimentar o controle de congestionamento.

 4. E O QUE TRAVOU: com tudo acima ligado, ZERO pacotes RTP chegam do outro lado.
    O pacer aceita e não entrega. Trinta ReceiverReport voltam (o transporte está
    de pé), nenhum TransportLayerCC, e todas as estatísticas do estimador em zero.
    Ou falta configurar o pacer, ou ele precisa de um empurrão que não achei.

A CONCLUSÃO PRÁTICA: ligar o GCC do pion não é a troca de uma linha que eu estimei.
O caminho barato que entrega a maior parte do valor está descrito em
`ouvirPedidos` — os ReceiverReport JÁ chegam, um por segundo, com fração de perda
dentro, e hoje são jogados fora.
============================================================================

go test -run SondaDaEstimativaDeBanda -v

**`receptor, err := apiDoReceptor(t).NewPeerConnection(webrtc.Configuration{})`**

O RECEPTOR USA A MESMA API, e isso não é preguiça de escrever outra: é o arranjo
REAL. Os dois lados de uma chamada do Astra são o Astra.

E a simetria é OBRIGATÓRIA, o que esta sonda descobriu do jeito difícil. Com o
receptor na API padrão, a oferta saía com `a=extmap:4 .../transport-wide-cc` e a
resposta VOLTAVA SEM ELA — o receptor não aceitava a extensão, não gerava relatório
de chegada nenhum, e o estimador ficava devolvendo o valor inicial para sempre.
Trinta ReceiverReport de volta e zero TransportLayerCC.

Quem estima é quem MANDA, mas só consegue estimar se quem RECEBE souber carimbar
a chegada.

**`var mu sync.Mutex`**

LER O RTCP DE VOLTA É O QUE ALIMENTA O ESTIMADOR, e sem isso ele fica cego.

Em pion o RTCP que chega só atravessa a cadeia de interceptores quando alguém
chama `ReadRTCP` no remetente. Sem esta goroutine os relatórios de chegada do
outro lado ficam parados no soquete: o estimador nunca vê perda nem atraso, e
devolve para sempre o valor inicial — que foi exatamente o que esta sonda mostrou
antes desta linha existir, com TODAS as estatísticas em zero.

A produção já faz isto em `ouvirPedidos`, para atender pedido de quadro-chave. A
sonda descobriu que aquela goroutine é carga muito maior do que o nome sugere: é
ela que vai alimentar o controle de congestionamento também.
CONTA O QUE VOLTA, POR TIPO. "estimativa parada" tem dois diagnósticos opostos —
ele decidiu que aquele é o número certo, ou nunca recebeu nada para decidir. Só
contar os tipos de RTCP separa os dois.

**`var comExtensao, semExtensao int`**

LER O QUE CHEGA É OBRIGATÓRIO. Faixa que ninguém lê enche o buffer de recepção, e
aí a medida passa a ser do buffer e não da rede.
CONFERE NA REDE, e não no SDP. Negociar a extensão e CARIMBAR a extensão são coisas
diferentes, e a segunda é a que o outro lado precisa para carimbar a chegada.

**`for nome, sdp := range map[string]string`**

O SDP DIZ SE A EXTENSÃO FOI NEGOCIADA, e é a única coisa que separa "o receptor
não quis" de "o receptor nem soube que era para querer". Sem `transport-cc` nas
duas descrições, nenhum relatório de chegada é gerado — e o estimador fica
devolvendo o valor inicial para sempre, que foi o que esta sonda viu.

**`e := bwe.GetStats()`**

AS ENTRANHAS JUNTO COM O NÚMERO. "1000 kbps" parado não diz se ele
decidiu que 1000 é o certo ou se nunca recebeu realimentação nenhuma
— dois estados com o mesmo número e diagnósticos opostos. É a mesma
regra que já apontou o remontador e o teto do compressor: contar as
etapas, não só o resultado.

**`func apiComEstimativa(t *testing.T, inicial int) (*webrtc.API, chan cc.BandwidthEstimator)`**

apiComEstimativa monta a API do pion com o controle de congestionamento ligado.

TROCAR `webrtc.NewPeerConnection` POR ISTO É A MUDANÇA INTEIRA em produção — e o
detalhe que não pode ser esquecido é `RegisterDefaultInterceptors`: montar a API na
mão SEM ele apagaria o NACK, os relatórios e o TWCC, que é o que a transmissão já usa
hoje. Ganhar estimativa perdendo retransmissão seria troca ruim.

**`if err := webrtc.ConfigureTWCCHeaderExtensionSender(motor, registro); err != nil`**

A LINHA QUE FALTAVA, e sem ela o GCC descarta TODOS os pacotes.

O padrão do pion registra o REMETENTE de TWCC — quem RECEBE mídia passa a mandar
relatórios de chegada. Isso é o lado de cá do problema. O que faltava é a extensão
no cabeçalho dos pacotes que SAEM, sem a qual o outro lado não tem como numerar o
que chegou.

O pacer do GCC recusa qualquer pacote sem ela — e recusa em silêncio, num log que
só aparece se alguém estiver olhando: "failed to write packet: missing transport
layer cc header extension". Em produção isso seria a transmissão inteira parando
de sair, com a estimativa de banda ligada e nenhum erro devolvido a ninguém.

Foi esta sonda que pegou, na primeira execução.

### `sidecar-voz/sonda_codecapi_test.go`

**`func TestSondaDoCodecAPI(t *testing.T)`**

SONDA DO ICodecAPI — dá para MANDAR o compressor produzir um quadro-chave agora?

A PERGUNTA POR TRÁS: medido, este compressor espaça os quadros-chave em CINCO
SEGUNDOS, e não obedece ao atributo `MF_MT_MAX_KEYFRAME_SPACING` (ver
`TestOCompressorDaQuadroChaveComRegularidade` e a nota em `configurarSaida`). Cinco
segundos é quanto tempo alguém que entra na sala — ou alguém que perdeu o quadro-chave
numa oscilação de rede — fica olhando para o vazio.

O caminho padrão para resolver isso em WebRTC é o outro lado PEDIR ("perdi a imagem,
manda um quadro-chave") e este lado atender. Atender exige uma ordem direta ao
compressor, que no Windows mora no `ICodecAPI` — outra interface, com outra tabela.

ANTES DE MONTAR NADA, PERGUNTAR. É a mesma lição do cancelador de eco e da busca de
compressores: GUID copiado de fórum falha em SILÊNCIO, e o que se ganha é uma
implementação inteira que não faz nada. Aqui os dois GUIDs se autoconferem:

  - se o IID estiver errado, `QueryInterface` devolve E_NOINTERFACE na hora;
  - se o da propriedade estiver errado, `IsSupported` devolve erro em vez de S_OK.

go test -run SondaDoCodecAPI -v

**`{"MeanBitRate", guid(0xF7222374, 0x2144, 0x4815,`**

CODECAPI_AVEncCommonMeanBitRate {F7222374-2144-4815-B550-A37F8E12EE52} — VT_UI4

A CHAVE DA ADAPTAÇÃO DE BANDA. Hoje a transmissão manda o que o preset diz e
não recua: numa conexão que não aguenta, isso não vira imagem pior, vira
pacote perdido e imagem quebrada. Recuar exige mudar a banda AO VIVO — e
reabrir o compressor a cada ajuste custaria um quadro-chave e um engasgo
visível, justamente quando a rede já está sofrendo.

"Modificável" é a resposta que decide: suportado sem ser modificável
significa que ele só aceita o valor na abertura, e aí o caminho barato
morre aqui.

**`mediaDeBanda := guid(0xF7222374, 0x2144, 0x4815,`**

E AGORA A PERGUNTA QUE VALE: ele ACEITA a mudança?

`IsModifiable` é uma DECLARAÇÃO, e neste projeto declaração já mentiu três
vezes — o `MF_TRANSFORM_ASYNC` valia zero em compressores assíncronos, o
`MF_SA_D3D11_AWARE` condenou a arquitetura por engano, e o
`MF_MT_MAX_KEYFRAME_SPACING` é aceito e ignorado. A regra que sobreviveu é
perguntar ao objeto o que ele SABE FAZER, não ler o que ele diz ser.

Aqui a diferença decide a fatia inteira: com mudança ao vivo, recuar a
banda custa uma chamada; sem ela, custa reabrir o compressor e um
quadro-chave — um engasgo visível, justamente quando a rede já sofre.

### `sidecar-voz/sonda_software_test.go`

**— sobre o arquivo inteiro —**

SONDA DO CAMINHO DE SOFTWARE — a máquina sem compressor de placa transmite?

HOJE ELA NÃO TRANSMITE NADA. Não é "transmite pior": `AbrirCompressor` percorre os
candidatos, todos recusam, e a transmissão nem começa. Medido nesta máquina:

NVIDIA H.264 Encoder MFT      nem liga: falha catastrófica
Intel Quick Sync (x2)         fala D3D11        <- é por aqui que passa hoje
Microsoft AVC DX12            fala D3D11
H264 Encoder MFT (software)   NÃO fala D3D11    <- e por isso é recusado

Numa máquina virtual, num notebook velho ou numa área de trabalho remota só existe o
último — e ele é recusado duas vezes: `definirEntrada` só aceita ARGB32 (ele só
aceita YUV) e `medirASaida` exige que ele traga a própria amostra (ele não traz).

O PLANO ÓBVIO É CONVERTER, e a peça já está no caminho: o Video Processor MFT
(`redimensionador.go`) roda na placa e converte formato além de reduzir tamanho. Mas
entre "ele converte para NV12" e "a transmissão funciona" há uma pergunta que decide
a arquitetura inteira e que NÃO se responde lendo documentação:

o compressor de software vive na memória PRINCIPAL. O quadro está na PLACA.
quem faz a travessia, e quanto ela custa?

São três perguntas encadeadas, e esta sonda faz as três em ordem. Cada uma só é feita
se a anterior respondeu sim, e a que falhar aponta o remédio:

1. o Video Processor aceita ARGB32 na entrada e NV12 na saída, ao mesmo tempo
   que reduz o tamanho?
2. o quadro NV12 que ele produz pode ser LIDO pela CPU? (se ele alocar em memória
   de vídeo, ler exige uma textura de leitura montada por nós — mais código)
3. o compressor de software aceita esses bytes e devolve H.264 de verdade?

E depois das três, a pergunta que decide o PRESET: quanto custa por quadro. Um
compressor de software a 720p pode custar mais que o orçamento de 16,67ms de 60/s, e
nesse caso prometer 60 é prometer engasgo.

ASTRA_TESTE_TELA=1 go test -run SondaDoCaminhoDeSoftware -v

**`tela, err := AbrirTela(0)`**

ELA PULA NA SUÍTE INTEIRA E PASSA SOZINHA, e isso é conhecido. Depois de uns trinta
segundos de testes de placa no MESMO processo, o `D3D11CreateDevice` passa a
devolver um dispositivo que não responde `QueryInterface` para DXGI — sucesso sem
interface. É esgotamento do processo de teste, não do aplicativo: em produção existe
UM dispositivo por transmissão, criado e fechado pelo `Emissor`.

Até esta sonda existir, esse mesmo estado DERRUBAVA o processo: o zero devolvido
virava chamada de método em endereço nulo. Hoje vira a mensagem abaixo, graças à
guarda em `consultar`. Rodar sozinha para ver os números:

ASTRA_TESTE_TELA=1 go test -run SondaDoCaminhoDeSoftware -v

**`const quantos = 30`**

---- E A CONTA QUE DECIDE O PRESET --------------------------------------------

Vinte quadros da tela de verdade, do jeito que a transmissão faria: copia,
converte na placa, lê para a memória principal, comprime na CPU. Os dois tempos
separados porque os remédios são opostos — travessia cara pede quadro menor,
compressão cara pede menos quadros por segundo.

**`const quantos = 30`**

CONTA AS RODADAS QUE DE FATO ACONTECERAM, e não as pedidas. A área de trabalho
parada não produz quadro: dividir pelo número pedido quando metade foi pulada
infla o custo por quadro sem nada no relatório dizendo isso. Já custou uma
conclusão errada neste projeto — ver o `sair` que juntava dois quadros num.

**`t.Logf("")`**

---- E A PERGUNTA QUE A CONTA ACIMA LEVANTA -----------------------------------

A leitura é 73% do custo, e ela não é CÓPIA: 1,38 MB de memória para memória
custam meio milissegundo, não sete. Os sete são a CPU PARADA esperando a placa
terminar de converter — destrancar um buffer que embrulha textura força a
sincronização.

É a MESMA FORMA do teto que segurava o caminho de hardware em 45 quadros: uma
espera que transforma LATÊNCIA em VAZÃO por se recusar a seguir sem o resultado.
E o remédio é o mesmo: entregar o quadro de agora, ler o de antes. A placa ganha
uma volta inteira para terminar, e quando a leitura chega o dado já está lá.

Antes de escrever isso em produção, medir se funciona.

**`t.Logf("")`**

---- E A PERGUNTA QUE APAGA CÓDIGO --------------------------------------------

Acima, os bytes do quadro passam pela memória do Go: destranca o buffer, copia
1,38 MB para um `[]byte`, copia de volta para um buffer do Media Foundation, e só
então entrega ao compressor. Duas cópias de um megabyte e meio por quadro, e um
buffer de entrada só nosso para manter.

Mas o compressor de software recebe um `IMFSample` — e a amostra que o Video
Processor devolve JÁ É UMA. Se ele a aceitar direto, some a cópia dupla, some o
buffer de entrada, some a leitura explícita. O compressor destranca por dentro,
e o pipeline continua valendo porque quem escolhe a VOLTA em que ele destranca
continua sendo nós.

Se não aceitar, o caminho de cima é o certo — e aí a cópia é o preço.

**`func TestSondaDosDecodificadores(t *testing.T)`**

SONDA DO OUTRO LADO — a máquina fraca consegue VER a tela dos outros?

Transmitir e receber falham por motivos independentes, e a pergunta do dono é sobre os
dois ("entrar em calls E transmitir"). Esta sonda responde o lado de RECEBER, e a
resposta é boa: o caminho já é livre de placa, por construção.

O QUE ELA MEDIU, e por que o número que ela imprime engana:

Microsoft H264 Video Decoder MFT     fala D3D11: true

Um só decodificador, e ele diz falar D3D11 — o que parece condenar a máquina sem
placa. NÃO CONDENA, por duas razões que só o código responde:

 1. `FalaD3D11` lê o `MF_SA_D3D11_AWARE`, que quer dizer "SEI usar placa", e não
    "EXIJO placa". É o mesmo atributo que este projeto já pegou mentindo antes (ver
    `abrirGeradorSeAssincrono`), e a lição se repete: o atributo descreve capacidade,
    nunca requisito.
 2. `amarrarDescompressor` NUNCA manda `recadoDefinirD3D`. Sem gerenciador entregue, o
    decodificador roda na memória principal e devolve NV12 num buffer que NÓS
    alocamos — que é exatamente o caminho que `medirASaida` já trata.

Ou seja: receber tela já funciona em qualquer máquina, e é por isso que este arquivo
só precisou construir o lado de MANDAR. A sonda fica como registro, para a próxima
pessoa não refazer a mesma pergunta.

go test -run SondaDosDecodificadores -v

**`func ladoDoProcessador(t objeto, indice int, formato windows.GUID, largura, altura int) error`**

ladoDoProcessador amarra um lado do Video Processor num formato e tamanho.

Igual ao `definirLado` de `redimensionador.go`, mas com o formato escolhível — que é
exatamente a mudança que esta sonda existe para justificar.

**`func passarPeloProcessadorMedido(vp, amostra objeto, trazAmostra bool, tamanho uint32)(…`**

passarPeloProcessadorMedido entrega um quadro e devolve os bytes que saíram, JÁ NA
MEMÓRIA PRINCIPAL — que é a pergunta 2 inteira.

SEPARA A CONVERSÃO DA LEITURA, e a separação é o ponto. As duas somam num número só
que não diz o que otimizar, e os remédios são opostos: conversão cara pede quadro
menor; leitura cara é a CPU parada esperando a placa terminar, e isso se recupera com
pipeline — foi exatamente o que destravou os 60fps do caminho de hardware.

### `sidecar-voz/tela_test.go`

**`func precisaDeTela(t *testing.T)`**

SONDA DA CAPTURA DE TELA.

Índice de vtable errado em COM não dá erro: chama outra função, com os argumentos
errados, e trava ou devolve lixo. Conferir a tabela contra a documentação é o
primeiro passo, mas não é prova — a documentação lista os métodos em ordem
alfabética, e a tabela segue a ordem de DECLARAÇÃO. Foi assim que o cancelador de
eco custou caro, e a lição de lá vale aqui: quem responde é a máquina.

Precisa de tela de verdade, então roda só com ASTRA_TESTE_TELA=1.

**`func TestDescricaoDaDuplicacaoBate(t *testing.T)`**

A PROVA DE QUE OS ÍNDICES ESTÃO CERTOS, e não um teste de "abriu sem explodir".

O que a torna forte é o FORMATO. A Microsoft documenta que a imagem da área de
trabalho é SEMPRE DXGI_FORMAT_B8G8R8A8_UNORM, que vale 87. Esse número está num
campo lá no meio da estrutura: para ele sair 87, o `GetDesc` precisa ser mesmo o
índice 7 (ou seja, os sete métodos herdados de IDXGIObject estão contados certo) E
o nosso struct precisa ter o mesmo tamanho e a mesma ordem de campos que o do
Windows. Um erro em qualquer um dos dois produz outro número.

**`func TestQuadrosChegam(t *testing.T)`**

O quadro tem que CHEGAR, e chegar no ritmo do monitor.

Este é o número que decide a transmissão inteira: se a captura não alcança a taxa
do monitor, nenhum ajuste depois disso alcança. Mede em vez de afirmar — a máquina
de quem roda é que responde.

A tela precisa estar MUDANDO. Numa área de trabalho parada a duplicação
legitimamente não entrega quadro nenhum (é o ponto dela), então o teste aceita um
piso baixo e reporta o que viu; quem quiser o número real mexe o mouse por cima de
algo animado enquanto ele roda.

**`func TestSemSoltarNaoVemOutro(t *testing.T)`**

SOLTAR É OBRIGATÓRIO, e o efeito de esquecer é silêncio e não erro.

A API só entrega o próximo quadro depois de devolvido o anterior. Este teste
existe porque essa é a falha mais fácil de introduzir num laço de captura e a mais
difícil de diagnosticar depois: a transmissão simplesmente congela na primeira
imagem, sem nada no registro.

### `sidecar-voz/tela.go`

**— sobre o arquivo inteiro —**

CAPTURA DE TELA SEM TIRAR O QUADRO DA PLACA DE VÍDEO.

Este é o ponto da transmissão inteira, e não o compressor. A medição que decidiu
isto está guardada em `GstScreenEncoder.kt`, do lado Kotlin, e vale repetir aqui
porque é o que justifica todo o COM abaixo:

quadro fica na GPU do começo ao fim   0,07 núcleos
quadro desce pra CPU + encoder de HW  0,68
quadro desce pra CPU + software       0,84

Comprimir 720p60 custa um quarto de núcleo. MOVER o pixel custa dois. O caminho
antigo (ffmpeg num processo à parte) trazia o quadro para a memória principal
quatro vezes: baixava da placa, convertia na CPU, empurrava por um cano a ~83 MB/s,
e o Java copiava de novo antes de comprimir. Trocar só o compressor por um de
hardware rendia 0,16 núcleo; manter o quadro na placa rende 0,77.

Por isso a Desktop Duplication do DXGI: ela entrega uma `ID3D11Texture2D`, ou seja,
o quadro já está onde o compressor vai buscá-lo. Nada desce.

O QUE ESTE ARQUIVO NÃO FAZ: comprimir. Ele para na textura. Quem transforma isso em
H.264 é o Media Foundation, em arquivo separado — e a fronteira entre os dois é uma
textura, não um vetor de bytes, justamente para não desfazer o que está escrito
acima.

**`const(…`**

Índices de vtable, na ORDEM DE DECLARAÇÃO do cabeçalho — que é o que define a
tabela. Conferidos contra a declaração da Microsoft antes de entrar aqui; o teste
`tela_test.go` reconfere na máquina, porque índice errado em COM não dá erro: chama
outra função, com os argumentos errados, e trava.

Todas estas interfaces herdam de IDXGIObject, que já traz sete: os três de IUnknown
mais SetPrivateData, SetPrivateDataInterface, GetPrivateData e GetParent. Por isso o
primeiro método próprio de qualquer uma delas é o 7, e não o 3.

**`_dxgiListarModos1 = 19`**

IDXGIOutput1 — os doze de IDXGIOutput (7..18) e depois os quatro próprios.
DuplicateOutput é o último, no 22. Foi por causa desta contagem que a lista
inteira do IDXGIOutput precisou ser conferida: pular WaitForVBlank, ou
esquecer os três de gama, erra o alvo por uma casa e trava sem mensagem.

**`texturaDescricao = 10`**

IDXGIOutputDuplication
GetDesc
AcquireNextFrame
GetFrameDirtyRects
GetFrameMoveRects
GetFramePointerShape
MapDesktopSurface
UnMapDesktopSurface
ReleaseFrame

**`type infoDoQuadro struct`**

infoDoQuadro é o DXGI_OUTDUPL_FRAME_INFO.

Só dois campos interessam. `UltimaApresentacao` zerado quer dizer que NADA da
imagem mudou — o que chegou foi só movimento de cursor, e o cursor não é o assunto
aqui. Devolver esse quadro ao compressor gastaria banda para transmitir a mesma
imagem de novo.

**`func AbrirTela(indiceDoMonitor int) (*Tela, error)`**

AbrirTela monta o dispositivo de vídeo e a duplicação do monitor `indiceDoMonitor`.

PRECISA RODAR NUMA THREAD PRESA. Vale a mesma regra do áudio: COM tem afinidade de
thread, e o Go troca goroutine de thread quando bem entende. Quem chama isto é
responsável pelo `runtime.LockOSThread`.

**`versaoDoSDKD3D11,`**

adaptador: nulo = o padrão
sem software: sem GPU não há caminho barato
sem driver por software
sem bandeiras
sem lista de níveis: aceita o melhor que a placa der

**`func (t *Tela) montarDuplicacao(indiceDoMonitor int) error`**

montarDuplicacao percorre dispositivo → adaptador → monitor → duplicação.

Está separado de `AbrirTela` porque é exatamente isto que precisa ser refeito quando
o acesso se perde (jogo em tela cheia, troca de resolução, tela de bloqueio). O
dispositivo D3D11 sobrevive a esses eventos; só a duplicação morre.

**`func (t *Tela) Hz() int`**

Hz devolve a taxa de atualização do monitor, arredondada.

É o TETO REAL da captura, e não uma curiosidade: a duplicação entrega no ritmo em
que a tela desenha. Pedir 60 quadros por segundo a um monitor de 60 Hz é pedir tudo
que existe; num de 144 Hz sobra folga; num de 30 Hz não há 60 para dar, e nenhum
ajuste de código inventa quadro que a tela não desenhou.

**`func (t *Tela) ProximoQuadro(limiteMs uint32) (objeto, error)`**

ProximoQuadro espera o próximo quadro e devolve a textura, que continua na placa.

`nil, nil` significa "nada mudou dentro do prazo" e é o caso normal numa tela
parada — quem chama repete a espera em vez de tratar como falha.

A textura devolvida é EMPRESTADA: vale até `SoltarQuadro`, e quem a recebe não pode
guardá-la. É assim que a API funciona, e é o que permite a captura não alocar nada
por quadro.

**`func (t *Tela) QuadroAtual(limiteMs uint32) (objeto, error)`**

QuadroAtual devolve o que está na tela AGORA, mesmo que nada tenha mudado.

A DIFERENÇA PARA `ProximoQuadro` É UMA LINHA E MUDA TUDO PARA QUEM CHAMA. A
transmissão quer só o que mudou: recomprimir uma tela parada gastaria banda para
mandar o que o outro lado já tem. A miniatura do seletor de monitor quer o contrário —
ela precisa da imagem uma vez, e a área de trabalho parada é justamente o caso mais
comum na hora de escolher qual tela compartilhar.

Sem isto, o seletor mostraria retângulo vazio em quem não estivesse mexendo no mouse.

### `sidecar-voz/telaparada_test.go`

**— sobre o arquivo inteiro —**

A TELA PARADA — os dois lados de um par de números que só funciona junto.

O problema apareceu lendo `receberTela` para outra coisa: NÃO EXISTE PACOTE DE "ACABOU"
EM RTP. A faixa continua declarada no SDP e simplesmente deixa de trafegar. Quem parava
de transmitir ficava congelado no palco de todo mundo, com a última imagem parecendo ao
vivo, até sair da chamada. MEDIDO antes do conserto: dez segundos depois de
`Desligar()`, quem assiste ainda tinha a tela no ar.

E O CONSERTO ÓBVIO ERA UMA ARMADILHA. "Sem pacote por N segundos = acabou" apagaria a
tela de quem compartilha um documento e para de mexer — com a tela parada o emissor não
manda pacote nenhum, de propósito. E ler um documento parado é o caso de uso, não a
exceção.

A saída foi fazer a tela parada FALAR (`sinalDeVida`, em `emissao.go`) para que o
silêncio passasse a significar uma coisa só. Os dois números são um par: mexer num sem o
outro reintroduz o defeito. Daí os dois testes deste arquivo existirem em espelho —

TestQuemParaDeTransmitirSaiDoPalcoDeQuemAssiste   parou  -> some, e não antes da hora
TestATransmissaoVivaNaoSomeDoPalco                no ar  -> fica, o tempo todo

O MESMO REMÉDIO CONSERTOU UM DEFEITO PIOR, achado no caminho: o pedido de quadro-chave
era atendido DEPOIS do `continue` do ramo "nada mudou", então com a tela parada ele
ficava pendurado e ninguém o atendia. Quem entrasse numa chamada onde já se compartilha
uma tela parada lia "abrindo a tela de fulano…" até alguém mexer o mouse.

**`if levou < silencioQueEncerra/2`**

O PISO É TÃO IMPORTANTE QUANTO O TETO, e sem ele este teste já passou pelo motivo
errado: sumir DEPRESSA DEMAIS quer dizer que a goroutine de recepção MORREU em vez
de contar o silêncio — e faixa sem leitor entope o buffer do pion, que é o defeito
caro. A primeira versão marcou 600ms e parecia ótima.

**`func TestATransmissaoVivaNaoSomeDoPalco(t *testing.T)`**

O OUTRO LADO DA MOEDA: uma transmissão VIVA não pode sumir do palco.

É o falso positivo que o prazo de leitura introduz, e ele é pior que o defeito que o
prazo conserta: apagar a tela de quem está transmitindo agora mesmo. Roda por mais que
`silencioQueEncerra` de propósito — um teste mais curto que o prazo não prova nada.

### `sidecar-voz/tetodocompressor_test.go`

**`func TestOTetoDoCompressorSemOLaco(t *testing.T)`**

QUAL É O TETO DO COMPRESSOR, sem o laço no caminho?

A PERGUNTA QUE ISTO RESPONDE. Medido, o caminho inteiro custa 6,2 ms por quadro em
720p e 7,0 ms em 1080p — só 12% de diferença para 2,25 vezes menos pixels. Custo que
quase não muda com o tamanho não é custo de COMPRIMIR: é latência de ida e volta.

Se for latência, ela some com pipeline (alimentar o quadro seguinte enquanto a placa
trabalha no atual) e NÃO some reduzindo resolução. Se for trabalho de verdade, é o
contrário. As duas conclusões levam a otimizações opostas, e escolher errado custa a
implementação inteira.

COMO SEPARAR: alimentar o compressor com o MESMO quadro, o mais rápido que ele
aceitar, sem captura no meio. O que sai daqui é a vazão máxima dele. Comparada com os
~160 quadros por segundo que o laço atual alcança, a diferença é o que o pipeline
tem para recuperar.

### `sidecar-voz/transmissao_test.go`

**— sobre o arquivo inteiro —**

SONDA DA TRANSMISSÃO.

Duas perguntas, e as duas só têm resposta honesta na máquina de quem pergunta:

 1. a cópia dentro da placa funciona? (o índice 47 do `ID3D11DeviceContext`)
 2. o caminho inteiro dá 60 quadros por segundo?

A primeira existe porque índice de vtable errado em COM NÃO DÁ ERRO — chama outra
função, com os argumentos errados. `CopyResource` é o 47º da tabela, a contagem mais
funda do projeto, e um erro ali daria transmissão preta ou travamento, nunca uma
mensagem. Conferir de olho contra o cabeçalho não é conferir.

**`func TestCopiarDentroDaPlaca(t *testing.T)`**

A PROVA DE QUE A CÓPIA DENTRO DA PLACA ESTÁ CERTA.

O truque é ter um invariante que só uma cópia de verdade satisfaz: a área de
trabalho do Windows não é preta. Se `CopyResource` estivesse no índice errado, a
textura de destino continuaria zerada e a leitura sairia toda zero.

De quebra, prova `CreateTexture2D` (5), `Map` (14) e `Unmap` (15) na mesma volta:
qualquer um deles errado impede a leitura de chegar ao fim.

**`func TestComoOCompressorEComandado(t *testing.T)`**

ONDE MORA "ESTE COMPRESSOR É ASSÍNCRONO?".

A pergunta parece de detalhe e decide o cano inteiro: um compressor assíncrono
RECUSA quadro que ele não pediu, com "no momento não está aceitando mais entrada" —
erro que soa como fila cheia e é, na verdade, "você falou fora da vez".

A sonda existe porque o `MF_TRANSFORM_ASYNC` lido do transformador voltou ZERO num
compressor que se comporta como assíncrono. Já erramos esse tipo de leitura uma vez
aqui, com o `MF_SA_D3D11_AWARE`, e a lição foi a mesma: "não tenho essa chave" é
indistinguível de "não" para quem não sabe onde a chave mora. Então pergunta-se nos
dois lugares e à própria interface.

**`func TestTransmissaoDaSessenta(t *testing.T)`**

A PERGUNTA DO DONO, respondida com número: dá 60 quadros por segundo?

Mede o caminho inteiro (duplicar, copiar na placa, comprimir, ler o H.264) e conta.
Não é estimativa: é o mesmo trabalho que a transmissão de verdade faz, medido por
dois segundos.

**`t.Logf("compressor: %s (entrada %s, assincrono=%v)", m.Compressor, m.Formato, m.Assincrono)`**

O RETRATO SAI ANTES DO VEREDITO, e de propósito. A medição devolve o que
conseguiu mesmo quando falha no meio, e "qual compressor pegou, em que formato,
por recado ou por chamada" é justamente a informação que separa um defeito do
outro. Falhar sem imprimir isso transforma cada erro numa nova investigação.

**`t.Logf("  comprimir         %8.1fus  (entregar + colher o que estava pronto)",`**

O RÓTULO ANTIGO DESTA LINHA DIZIA "esperar a placa", E ERA MENTIRA — mentira que
escondeu por meses o teto da transmissão. Medido, a placa nunca esperava: o tempo
era NOSSO, parado colhendo a saída do quadro recém-entregue. As duas linhas
seguintes existem para que esse número nunca mais possa se esconder num só.

**`if m.Quadros < 20`**

TELA PARADA NÃO É DEFEITO DO CANO. A duplicação só entrega quando algo muda,
então uma área de trabalho quieta rende pouquíssimos quadros — e o compressor
segura os primeiros antes de fechar o primeiro pedaço. Já vi este teste falhar
por isso, com o código certo, e teste que quebra porque ninguém mexeu no mouse
treina a pessoa a ignorar o vermelho.

**`if m.Folga() < 0`**

ESTA É A ASSERÇÃO QUE VALE, e a taxa de quadros NÃO é.

`ProximoQuadro` espera a tela mudar, então numa area de trabalho parada a taxa
medida e a do Windows, nao a do Astra: o mesmo codigo mediu 79/s numa hora e
44/s noutra sem nada ter mudado no cano. Perseguir aquele numero custou uma
investigacao inteira num defeito que nao existia.

O custo por quadro e da maquina. Se ele cabe no orcamento, os 60 saem sempre que
a tela tiver 60 para dar.

**`func TestTransmissaoReduzida(t *testing.T)`**

A TRANSMISSÃO REDUZIDA — o caminho da sala com três ou mais pessoas.

Este teste nasceu como outra pergunta ("o compressor não reduz sozinho?", a mesma
que apagou o conversor de cor) e a resposta foi NÃO: entrada e saída do H.264 têm de
ter o mesmo tamanho. Então ele virou o teste do caminho com redimensionador.

A pergunta não foi desperdiçada: ela é o que separa "precisa mesmo" de "montei por
via das dúvidas", e está registrada no cabeçalho de `redimensionador.go` para
ninguém refazê-la.

### `sidecar-voz/transmissao.go`

**— sobre o arquivo inteiro —**

A TRANSMISSÃO DE PONTA A PONTA — textura entra, H.264 sai, e o quadro nunca desce.

`tela.go` entrega uma `ID3D11Texture2D`. `compressor.go` já provou que existe nesta
máquina um compressor que aceita a textura onde ela está, e em ARGB32, que é
exatamente o que a duplicação produz. Este arquivo é o cano entre os dois.

A ÚNICA CÓPIA DO CAMINHO, e por que ela PRECISA existir. A textura que a duplicação
entrega vale até `ReleaseFrame` — guardar uma referência não basta, porque o DXGI
reaproveita a superfície por baixo, e o compressor assíncrono enfileira o quadro
para devolver depois. Então o quadro é copiado para uma textura NOSSA antes de o
original ser devolvido.

Isso NÃO desfaz a migração. A cópia é de memória de vídeo para memória de vídeo:
1080p em BGRA são 8 MB contra centenas de GB/s de banda dentro da placa, ou seja,
dezenas de microssegundos. O que custava 0,84 núcleo era ATRAVESSAR o barramento
até a memória principal, e isso continua não acontecendo.

TRÊS TEXTURAS EM RODÍZIO, e não uma. Um compressor assíncrono pode estar segurando
dois ou três quadros ao mesmo tempo; escrever sempre na mesma textura sobrescreveria
um que ele ainda não leu. O defeito daí não é travamento — é imagem rasgada de vez
em quando, que se confunde com problema de rede e some em qualquer teste curto.

O TAMANHO DA SAÍDA sai de `AlvoDeSaida`: 1080p a dois, 720p com três ou mais. É
conta de banda da malha, não gosto — a subida é gasta uma vez por pessoa.

Quando o alvo difere da tela, entra o `Redimensionador` (ver o arquivo dele), e ele
precisou existir: a sonda perguntou se o compressor reduzia sozinho, como já havia
perguntado sobre cor, e desta vez a resposta foi não —

Intel Quick Sync (x2)   entrada 1080p com saída 720p: "tipo de mídia inválido"
Microsoft AVC DX12      recusa a própria saída em 1280x720

O QUE ESTE CANO CUSTA, medido três vezes nesta máquina para não confundir ruído com
resultado — o que já aconteceu duas vezes aqui:

                 custo por quadro        banda        processador
1080p nativo     7,36 / 6,13 / 7,19ms    2679 kbps    0,06 a 0,12 núcleos
720p reduzido    6,78 / 7,64 / 6,74ms    1691 kbps    0,08 a 0,11 núcleos

orçamento a 60 por segundo: 16,67ms

E O TEMPO VAI QUASE TODO NUM LUGAR SÓ:

copiar na placa        7 µs
reduzir              0-150 µs
comprimir           ~7000 µs   <- esperar a placa terminar
ler os NALs          20-60 µs

Duas conclusões, e as duas mudam o que faz sentido otimizar.

PRIMEIRA: o código deste arquivo custa uns 50 a 200 microssegundos por quadro. Todo
o resto é ESPERA, e esperar não queima processador — a thread fica parada num evento
do Windows. Por isso 7ms de relógio por quadro custam só um décimo de núcleo. Não há
o que espremer aqui; o gargalo, quando houver, será da placa.

SEGUNDA: reduzir para 720p NÃO economiza tempo nesta máquina — as duas colunas se
confundem dentro do ruído. Economiza BANDA, que era o motivo de existir. Numa
máquina cuja placa seja o gargalo a conta muda, e é lá que a redução vai pagar duas
vezes.

COMO NÃO MEDIR ISTO, porque eu errei assim duas vezes: a TAXA DE QUADROS não serve.
`ProximoQuadro` espera a tela mudar, então ela mede o Windows e não o Astra — área
parada rende poucos quadros, jogo rende muitos, e o mesmo código deu 79/s numa hora
e 44/s noutra. O custo por quadro e o processador consumido são da máquina.

**`const(…`**

Índices de vtable das interfaces que só este arquivo usa.

As de Media Foundation herdam `IMFAttributes` INTEIRA (30 métodos, terminando no
32), e por isso os métodos próprios começam no 33. É a mesma contagem que já vale
para o `IMFActivate` em `compressor.go`, e ela está provada lá: o nome do compressor
sai legível do índice 13, o que só acontece se a tabela toda estiver certa.

**`bufTrancar        = 3 // Lock`**

IMFSample : IMFAttributes
SetSampleTime
SetSampleDuration
ConvertToContiguousBuffer
AddBuffer

**`buf2DTrancar         = 3 // Lock2D`**

IMFMediaBuffer
Lock
Unlock
GetCurrentLength
SetCurrentLength

**`geradorPegarEvento = 3 // GetEvent`**

IMF2DBuffer
Lock2D
Unlock2D
GetContiguousLength

**`)`**

IMFMediaEventGenerator
GetEvent
IMFMediaEvent : IMFAttributes
GetType

**`const(…`**

Índices do Direct3D 11. O `ID3D11DeviceContext` é a contagem mais funda de todo o
projeto — `CopyResource` é o 47º da tabela —, e por isso ela não entrou aqui de
cabeça: `transmissao_test.go` copia um quadro para uma textura de leitura e confere
que os pixels chegaram. Índice errado em COM não dá erro, chama outra função.

**`)`**

ID3D11DeviceContext — ID3D11DeviceChild traz quatro (3..6) e os próprios
começam no 7. Do 7 ao 46 são estados de pipeline e desenho; a cópia vem
depois deles.
Map
Unmap
CopyResource

**`)`**

MediaEventType. Num compressor assíncrono é ELE quem manda: pede quadro
quando tem espaço e avisa quando tem saída pronta. Alimentar sem ter sido
pedido devolve erro.
METransformNeedInput
METransformHaveOutput

**`)`**

DXGI_FORMAT_B8G8R8A8_UNORM
D3D11_USAGE_DEFAULT
D3D11_BIND_RENDER_TARGET
D3D11_BIND_SHADER_RESOURCE
D3D11_USAGE_STAGING
D3D11_CPU_ACCESS_READ (o WRITE é 0x10000)
D3D11_MAP_READ

**`type quadroDeEntrada struct`**

quadroDeEntrada é uma das texturas do rodízio, já embrulhada para o compressor.

O embrulho é feito UMA vez, na abertura: a textura não muda de endereço, então a
amostra e o buffer que apontam para ela servem para sempre. Por quadro, o único
trabalho é a cópia e a marcação de tempo — nada é alocado.

**`type Compressor struct`**

Compressor é o compressor ligado e amarrado ao dispositivo de vídeo DA CAPTURA.

O mesmo dispositivo, e não um novo: textura de um dispositivo não serve em outro, e
criar o segundo obrigaria a copiar o quadro entre os dois — o vaivém que esta
migração inteira existe para evitar.

**`NaMemoria bool`**

O CAMINHO DE SOFTWARE. Verdadeiro quando nenhum compressor de placa aceitou a
textura e caímos no de software, que vive na memória principal.

Fica exposto porque muda o que se pode PROMETER: o de placa custa 0,9ms por
quadro, o de software custa 5 a 20 dependendo da máquina. Quem liga a transmissão
precisa saber disso para escolher a taxa — ver `TaxaQueCabe`.

**`comandos objeto`**

IMFTransform
IMFMediaEventGenerator, 0 se for síncrono
IMFDXGIDeviceManager
ID3D11DeviceContext, emprestado da captura

**`pendente objeto`**

O QUADRO CONVERTIDO DA VOLTA ANTERIOR, ainda não entregue ao compressor. Só o
caminho de software o usa, e ele é a diferença entre 9,4ms e 5,1ms por quadro.

POR QUE ESPERAR UMA VOLTA: o compressor de software precisa dos bytes na memória
principal, e trazê-los da placa obriga a CPU a esperar a conversão TERMINAR.
Medido, essa espera é 73% do custo do quadro — e não é cópia (1,38 MB copiam em
meio milissegundo), é a CPU parada. Entregando o quadro de agora e só depois
pedindo o de antes, a placa ganha uma volta inteira para terminar e a espera some.

É a MESMA FORMA do teto que segurava o caminho de placa em 45 quadros: uma espera
que transforma LATÊNCIA em VAZÃO por se recusar a seguir sem o resultado.

**`espelho *Espelho`**

A MINIATURA DA PRÓPRIA TELA, para a janela de quem transmite. Nulo quando ninguém
pediu — e a transmissão não muda em nada quando é nulo.

Mora AQUI e não no laço de emissão porque o que ele precisa é da amostra que este
compressor já montou: a cópia da captura, embrulhada em `IMFSample`. Montá-la de
novo lá fora seria repetir o rodízio de texturas inteiro para chegar ao mesmo
objeto — e ainda segurar a textura do DXGI por mais tempo.

**`type Custos struct`**

Custos separa o tempo do cano por ETAPA, somado ao longo da transmissão.

Existe porque "7,43ms por quadro" não diz o que otimizar. As etapas têm naturezas
completamente diferentes e remédios opostos:

Copia       cópia dentro da placa. Deveria ser microssegundos; se não for, a
            placa está saturada por outra coisa.
Reducao     o Video Processor. Só existe quando há redução de escala.
Compressao  entregar o quadro e ESPERAR o compressor de hardware. É trabalho da
            placa, não nosso — não dá para escrever código que o encurte, só
            para pedir menos pixel.
Leitura     puxar os NALs prontos e copiá-los para a memória do Go. Este é
            nosso, e é o único que uma máquina fraca sente na CPU.

Sem essa separação, uma máquina fraca engasgando não diz se o problema é a placa,
o tamanho do quadro ou uma cópia mal feita nossa. Com ela, o remédio é imediato.

**`PedidoDeEntrada time.Duration`**

AS DUAS ESPERAS DO COMPRESSOR ASSÍNCRONO, separadas — e a separação decide uma
otimização inteira.

Ele é comandado por recados, e há dois: "me dá o próximo quadro" e "tenho saída
pronta". Esperar por um ou pelo outro parece a mesma coisa no relógio e não é:

PedidoDeEntrada  — ele está OCUPADO comprimindo. Nada a ganhar do nosso lado;
                   é a placa trabalhando, e o remédio é comprimir menos.
SaidaPronta      — nós é que estamos parados esperando um resultado que poderia
                   ser colhido depois. Aqui um pipeline recupera o tempo inteiro.

Sem separar, os dois somam num número só e a conclusão vira chute — e as duas
conclusões levam a otimizações OPOSTAS.

**`type Ritmo struct`**

Ritmo segura o laço da transmissão no compasso pedido.

POR QUE ELE ESPERA EM VEZ DE DESCARTAR, que foi a primeira tentativa e saiu pior.
Num monitor de 165 Hz a captura entrega muito mais do que os 60 prometidos, e o
instinto é pegar tudo e jogar fora o que sobra. Medido, isso deu 44 quadros por
segundo — PIOR que os 79 de antes de existir ritmo nenhum.

A razão é que o laço é serial: captura, comprime, captura. O quadro descartado já
custou a ida e volta ao DXGI, e descartar triplicou o número dessas idas sem
devolver nada. Jogar trabalho fora depois de tê-lo feito não é economia.

Esperar até a hora resolve os dois lados: a ida ao DXGI só acontece quando vai
render quadro, e a Desktop Duplication sempre entrega o MAIS RECENTE — então
dormir não perde imagem nenhuma, só pula as intermediárias que ninguém veria.

O custo é até um intervalo de atraso (16ms a 60 por segundo) num quadro que acabou
de mudar. Para tela compartilhada isso não se percebe; para o compressor de uma
máquina fraca, o terço de trabalho economizado se percebe muito.

**`func (r *Ritmo) Esperar()`**

Esperar dorme até a próxima casa de tempo.

Quando a volta anterior demorou MAIS que o intervalo — máquina fraca, quadro-chave
pesado —, a casa é reposta a partir de agora em vez de acumular atraso. Sem isso o
relógio ficaria devendo casas e o laço passaria a correr sem dormir, tentando
recuperar um tempo que não volta.

**`func AlvoDeSaida(largura, altura, pessoasNaSala int) (int, int)`**

AlvoDeSaida diz em quanto a transmissão deve sair, dado o tamanho da tela.

A REGRA VEIO DA CONTA DE BANDA, e não de gosto. Em malha ponto a ponto a subida é
gasta uma vez POR PESSOA: 1080p custa ~5 Mbps, então numa sala de quatro seriam 15,
que a maioria das conexões de casa não tem. Com três ou mais, cai para 720p e a
conta volta a caber.

Sozinho com uma pessoa vale a nitidez: compartilhar tela quase sempre é mostrar
texto ou código, e em 720p texto pequeno some.

**`func TaxaQueCabe(custo time.Duration, teto int) int`**

TaxaQueCabe escolhe a maior taxa cujo orçamento comporta o custo medido.

POR QUE ISTO EXISTE. O caminho de placa custa 0,9ms por quadro; o de software custa 4,5
nesta máquina e 2 a 4 vezes mais numa que não tem placa nenhuma. Pedir 60 quadros por
segundo a uma máquina que gasta 25ms em cada um não dá 60: dá 40, com o laço rodando
sem pausa nenhuma e um núcleo inteiro ocupado o tempo todo. É esse núcleo pregado, e
não a taxa, que a pessoa sente como o aplicativo travando.

A CONTA É METADE DO ORÇAMENTO, e a metade não é folga por medo. O custo medido aqui é
só o do compressor; fora dele ainda há a captura, o empacotamento em RTP, a rede, e o
resto do aplicativo desenhando a própria janela na mesma máquina. Deixar metade do
tempo para tudo isso é o que separa "transmite a 30" de "transmite a 30 e a janela
responde".

60/s -> o quadro precisa custar no máximo  8,3ms
30/s ->                                   16,7ms
15/s ->                                   33,3ms

`teto` é o que a pessoa pediu no preset: esta função só ABAIXA. Uma máquina rápida com
preset de 30 continua em 30 — o preset é escolha dela, não um alvo a superar.

**`func tetoDeSoftware(largura, altura int) (int, int)`**

tetoDeSoftware limita a saída do caminho de memória a 720p.

É decisão de orçamento e não de gosto. Neste caminho o quadro atravessa da placa para
a memória principal, e essa travessia é 73% do custo do quadro e escala direto com o
número de pixels — medido em `sonda_software_test.go`. 1080p custaria mais que o dobro
de 720p justamente na máquina que, por definição, é a mais fraca que temos: a que não
tem compressor de placa nenhum.

720p e não menos porque compartilhar tela é quase sempre mostrar texto, e abaixo disso
texto pequeno some — que é exatamente o que se queria mostrar.

**`func AbrirCompressor(tela *Tela, saidaL, saidaA, fps, kbps int) (*Compressor, error)`**

AbrirCompressor escolhe, liga e amarra um compressor ao dispositivo da captura.

`saidaL`/`saidaA` é o tamanho comprimido. Zero em qualquer um deles quer dizer "o
mesmo da tela", que é o caminho sem redução nenhuma.

`fps` zero quer dizer "o do monitor, capado em 60". Quando a pessoa escolheu um
preset (720p30, por exemplo), o número dela precisa chegar aqui: o controle de banda
do compressor DIVIDE a banda pela taxa declarada, então declarar 60 e entregar 30
faz cada quadro sair com metade dos bits que poderia — imagem pior pela mesma banda,
sem nada no código dizendo por quê.

PRECISA RODAR NA MESMA THREAD PRESA da captura — vale a regra de COM de sempre.

**`if fps <= 0`**

A taxa DECLARADA é o teto do que vamos mandar, não o do monitor. Declarar 165
num monitor de 165 Hz faria o controle de banda distribuir a banda por 165
quadros e cada um sair mais pobre — e 60 já é mais do que o olho cobra numa
tela de conversa.

**`recusas := make([]string, 0, 2*len(lista))`**

PERCORRE OS CANDIDATOS EM VEZ DE CONFIAR NO PRIMEIRO. Numa máquina híbrida a
duplicação vem do adaptador que desenha o monitor, quase sempre o integrado, e
o compressor da placa dedicada não serve para a textura dele — na máquina onde
isto foi escrito, o da NVIDIA nem liga. Tentar até um amarrar é mais curto que
descobrir qual placa desenha o monitor.
GUARDA A RECUSA DE CADA UM, e não só a do último. São compressores diferentes
que falham por motivos diferentes — o de software não fala D3D11, o da placa
dedicada nem liga —, e mostrar apenas o último manda quem lê investigar o
candidato errado. Já custou uma volta inteira aqui.

**`memL, memA := tetoDeSoftware(saidaL, saidaA)`**

SEGUNDA PASSADA: O CAMINHO DE SOFTWARE.

Chegar aqui é o caso da máquina virtual, do notebook antigo e da área de trabalho
remota — e até esta linha existir, era o caso de quem simplesmente NÃO TRANSMITIA.
Não "transmitia pior": a função voltava erro e o botão de compartilhar tela não
fazia nada.

TENTAR EM VEZ DE PERGUNTAR, e isso é deliberado. Dava para ler o
`MF_SA_D3D11_AWARE` de cada candidato e mandar os que não falam D3D11 direto para
cá — mas esse atributo já foi pego mentindo neste projeto (ver
`abrirGeradorSeAssincrono`), e uma passada extra que só roda quando a primeira
falhou inteira custa nada e não depende de o atributo estar dizendo a verdade.

O TETO DE 720p É AQUI, e é decisão de orçamento, não de gosto: neste caminho o
quadro atravessa da placa para a memória principal, e essa travessia é 73% do
custo e escala com o número de pixels. 1080p custaria mais que o dobro de 720p
numa máquina que, por definição, é a mais fraca que temos.

**`if err := destrancarSeAssincrono(t); err != nil`**

A ORDEM DAQUI É OBRIGATÓRIA e cada passo tem seu motivo:

 1. destrancar    — compressor de hardware nasce trancado, e trancado recusa
                    quase tudo com erro que não explica nada
 2. dizer o D3D11 — TEM de vir antes dos tipos: é o que faz ele passar a
                    aceitar textura em vez de bytes na memória principal
 3. saída         — no H.264 a saída vem antes; enquanto ela não estiver
                    definida ele não revela sequer o que aceita na entrada
 4. entrada       — só agora a lista existe

**`if err := c.criarGerenciador(tela.dispositivo); err != nil`**

A PLACA VAI PARA O GERENCIADOR NOS DOIS CAMINHOS, mas só o de placa a entrega ao
COMPRESSOR. No de software o gerenciador ainda é obrigatório — é ele que deixa o
Video Processor ler a textura da captura —, e entregá-lo ao compressor de software
é justamente a recusa que trouxe a transmissão até aqui.

**`if naMemoria || saidaL != largura || saidaA != altura`**

NO CAMINHO DE SOFTWARE O REDIMENSIONADOR EXISTE SEMPRE, mesmo quando não há nada
a reduzir: é ele quem converte ARGB32 em NV12. Amarrar essa existência só à
diferença de tamanho faria a transmissão funcionar em 1080p→720p e falhar em
720p→720p, que é o tipo de defeito que parece aleatório de fora.

**`func (c *Compressor) criarGerenciador(dispositivo objeto) error`**

entregarODispositivo é o passo que mantém o quadro na placa.

O compressor não aceita um `ID3D11Device` direto: ele quer um "gerenciador", que é
uma casca do Media Foundation em volta do dispositivo, feita para vários
componentes o dividirem sem brigar pelo acesso. A ficha que o `MFCreateDXGIDeviceManager`
devolve não é enfeite — é ela que autoriza o `ResetDevice` a seguir, e trocá-la por
um zero faz a chamada falhar sem dizer por quê.

**`func (c *Compressor) definirEntrada(formato windows.GUID) error`**

definirEntrada escolhe o formato de pixel e amarra a entrada.

PARTE DO TIPO QUE O PRÓPRIO COMPRESSOR OFERECEU, em vez de montar um do zero. Um
tipo enumerado já vem com tudo que aquele compressor exige e que a documentação não
lista — perfil, arranjo de amostras, coisas que variam por driver. Montar na mão
funciona até o driver que pede um campo a mais, e aí a falha é um erro genérico.

DOIS FORMATOS, UM POR CAMINHO. ARGB32 é o que a captura entrega pronto e o que o
compressor de placa aceita — o quadro vai da tela ao compressor sem passo nenhum no
meio. NV12 é o que TODO compressor de software aceita e o único que eles aceitam:
nenhum deles fala RGB. No caminho de software, quem produz esse NV12 é o Video
Processor (ver `redimensionador.go`), e ele o faz na placa, de graça.

**`func (c *Compressor) abrirGeradorSeAssincrono() error`**

abrirGeradorSeAssincrono descobre se o compressor é comandado por recados.

Compressor assíncrono não é alimentado quando nós queremos: é ELE quem pede o quadro
e avisa quando há saída. Entregar um quadro sem ter sido pedido volta como "no
momento não está aceitando mais entrada" — erro que soa como fila cheia e significa
"você falou fora da vez".

A DECISÃO SAI DE `QueryInterface`, E NÃO DO `MF_TRANSFORM_ASYNC`. O atributo é o
caminho documentado e nesta máquina ele MENTE: vale zero no ativador e zero no
transformador, nos quatro compressores, inclusive nos três de hardware que se
comportam como assíncronos. Já tínhamos levado esse golpe com o `MF_SA_D3D11_AWARE`,
e a lição se repete: chave ausente é indistinguível de chave falsa.

Ter a fila de recados, por outro lado, separa com precisão — medido:

Intel Quick Sync (x2)     tem fila
Microsoft AVC DX12        tem fila
H264 Encoder MFT (soft)   NÃO tem

Que é exatamente a divisão entre hardware e software, ou seja, entre assíncrono e
síncrono. Perguntar ao objeto o que ele SABE FAZER vale mais que ler o que ele diz
ser.

**`func (c *Compressor) medirASaida() error`**

medirASaida descobre se o compressor traz a própria amostra de saída ou espera a
nossa. A divisão é exata e não é de gosto: todo compressor de placa traz (a memória é
dela), nenhum de software traz.

ESTA FUNÇÃO ERA A SEGUNDA RECUSA DO CAMINHO DE SOFTWARE. Ela devolvia erro dizendo que
só o caminho de placa existia — o que era verdade quando foi escrita, e é o motivo de
máquina sem placa não transmitir nada. Alocar aqui é o mesmo que o `Descompressor` já
fazia do outro lado, e por isso o formato é o mesmo.

**`c.soltarSaidaNossa()`**

SOLTA O ANTERIOR ANTES DE RESERVAR OUTRO. Esta função roda uma vez na abertura e
de novo a cada renegociação de formato; sem esta linha, a segunda vez abandonaria
um buffer de saída vivo por transmissão. Vazamento em objeto COM não aparece no
perfil do Go — aparece como a memória do processo subindo sem explicação.

**`if b2d, err := q.buffer.consultar(&iidBuffer2D); err == nil`**

O TAMANHO PRECISA SER DITO À MÃO. Um buffer que embrulha textura nasce com
comprimento zero, porque o Media Foundation não sabe quanto daquela superfície
é conteúdo. Compressor que recebe buffer de comprimento zero não reclama: ele
comprime nada, e a transmissão sai preta.

**`func (c *Compressor) LigarEspelho(mandar func(Quadro))`**

LigarEspelho pede a miniatura da própria tela, entregue em `mandar`.

FALHAR AQUI NÃO DERRUBA NADA, e é o contrato inteiro desta função: sem o Video
Processor a transmissão continua igual, e o que se perde é a miniatura. Devolver erro
para quem chama transformaria "sem prévia" em "sem transmissão", que é a troca errada.

Chamada DEPOIS de `AbrirCompressor` e não de dentro dele porque o compressor é aberto
e reaberto quando a taxa muda (ver `TaxaQueCabe`), e a prévia não precisa saber disso.

**`func (c *Compressor) Comprimir(textura objeto, quando time.Duration, receber func([]byte)) error`**

Comprimir entrega um quadro da captura e devolve o H.264 que ficou pronto.

A textura pode ser devolvida à captura ASSIM QUE ESTA FUNÇÃO RETORNA: a primeira
coisa que ela faz é copiar o quadro para uma textura nossa.

`receber` é chamado uma vez por pedaço pronto, e o fatiamento entregue vale só até a
chamada seguinte — quem quiser guardar copia. Isso é de propósito: a transmissão
entrega ao pion na hora, e alocar por quadro a 60 por segundo é lixo de sobra para o
coletor ter opinião sobre a hora de rodar.

**`c.espelho.Talvez(q.amostra)`**

O ESPELHO OLHA AQUI, e o lugar é escolhido: depois da cópia (então a textura do
DXGI já pode voltar) e ANTES de qualquer caminho se ramificar — assim quem
transmite vê a própria tela igual, com ou sem placa. Ele olha o relógio e volta na
maioria das chamadas; ver `compassoDoEspelho`.

**`if err := c.pedidoDeEntrada(receber); err != nil`**

ASSÍNCRONO — E AQUI ESTAVA O TETO DA TRANSMISSÃO INTEIRA.

O desenho antigo esperava BLOQUEANDO até o compressor pedir o quadro, atendendo
as saídas que aparecessem no caminho, e só voltava depois de alimentar. Parecia
certo e custava caro: medido, 7,5ms por quadro, dos quais

esperando ele pedir entrada      5us
esperando a saída ficar pronta  5179us

A placa NUNCA estava ocupada — ela aceita o próximo quadro em cinco
microssegundos. Os cinco milissegundos eram nós parados colhendo o resultado do
quadro que acabáramos de entregar. Isso é LATÊNCIA do compressor (quadro entra,
quadro sai uns 5ms depois), e o laço a transformava em VAZÃO ao se recusar a
seguir em frente sem o resultado.

Agora: espera o pedido (barato), alimenta, e colhe SEM ESPERAR o que já estiver
pronto. O que não estiver é colhido na volta seguinte. A latência vira atraso de
um quadro em vez de teto de taxa — e é por isso que ela some do orçamento.

**`func (c *Compressor) comprimirNaMemoria(quadro objeto, quando time.Duration, marcarTempo func(objeto), receber`**

comprimirNaMemoria é o caminho da máquina sem compressor de placa.

A ORDEM É O CONTRÁRIO DA INTUIÇÃO, e é ela que faz o caminho caber no orçamento:
entrega o quadro de AGORA ao Video Processor, e comprime o de ANTES.

Por quê: o compressor de software precisa dos bytes na memória principal, e trazê-los
da placa obriga a CPU a esperar a conversão terminar. Medido, essa espera é 73% do
custo do quadro — 6,9ms de 9,4. Não é cópia; 1,38 MB copiam em meio milissegundo. É a
CPU parada. Dando ao quadro uma volta inteira para maturar, a espera cai para quase
nada e o custo total vai a 5,1ms.

A AMOSTRA VAI DIRETO, sem passar pela memória do Go. O compressor de software aceita o
`IMFSample` que o Video Processor devolve (medido — `sonda_software_test.go`), então
não há leitura explícita, não há buffer de entrada nosso, e não há duas cópias de um
megabyte e meio por quadro. Ele destranca por dentro; quem escolhe a VOLTA em que isso
acontece continua sendo este laço.

**`marcarTempo(nova)`**

O tempo é marcado AQUI, na amostra convertida, e não na hora de entregá-la ao
compressor. Ela vai ser comprimida na volta seguinte, e marcá-la lá carimbaria
o quadro com o instante do quadro SEGUINTE — todo o vídeo andaria adiantado
um quadro, sem nada no código dizendo por quê.

**`if c.eventos != 0`**

Os dois protocolos, porque o caminho de memória não garante um compressor
síncrono: nesta máquina o de software não tem fila de recados, mas nada impede um
de placa de cair aqui por recusar a textura e aceitar NV12 na memória. Alimentar
um assíncrono sem crédito volta como "não está aceitando entrada agora".

**`func (c *Compressor) pedidoDeEntrada(receber func([]byte)) error`**

pedidoDeEntrada garante que há UM crédito de entrada, esperando por ele se preciso.

O CRÉDITO É CONTADO, e não presumido. Um compressor com fila interna pede mais de um
quadro antes de receber qualquer um, e jogar fora o pedido excedente faria a volta
seguinte esperar por um recado que JÁ TINHA CHEGADO — travando a transmissão num
impasse em que os dois lados esperam o outro. É o tipo de defeito que só aparece sob
carga, que é o pior lugar para descobri-lo.

**`func (c *Compressor) Drenar(receber func([]byte)) error`**

Drenar colhe o que já estiver pronto SEM ESPERAR por nada.

PRECISA SER CHAMADA TAMBÉM QUANDO NÃO HÁ QUADRO A ENVIAR, e essa é a contrapartida de
não bloquear mais. Com a tela parada a captura não devolve nada, `Comprimir` não é
chamada, e os últimos quadros ficariam presos dentro do compressor — a imagem de quem
assiste congelaria um quadro antes do que deveria, justamente no instante em que a
pessoa parou de mexer para alguém ler o que está na tela.

**`if c.NaMemoria`**

NO CAMINHO DE SOFTWARE HÁ UM QUADRO A MAIS PRESO, o que o pipeline deixou
maturando. Sem esta linha, parar de mexer na tela congelaria a imagem de quem
assiste DOIS quadros antes do que deveria em vez de um — e o segundo é o que o
pipeline acrescentou, ou seja, um defeito que a otimização criaria sozinha.

**`func (c *Compressor) esvaziar(receber func([]byte)) error`**

esvaziar puxa TUDO que estiver pronto, e o "tudo" é o ponto.

Só serve ao caminho síncrono: no assíncrono quem diz que há saída é o recado, e
perguntar por conta própria devolve erro.

ESVAZIAR PELA METADE É O QUE TRAVA A TRANSMISSÃO. Um compressor síncrono recusa o
próximo quadro enquanto tiver saída pendente, e a recusa vem como "no momento não
está aceitando mais entrada" — que soa como problema de ritmo e é, na verdade, fila
não esvaziada. A saída de um quadro pode render mais de um pedaço, então parar no
primeiro deixa resto para sempre.

**`c.bufferSaida.chamar(bufDefinirTamanho, 0)`**

O TAMANHO USADO VOLTA A ZERO A CADA VOLTA. Sem isto o buffer chega ao
compressor já "cheio" da vez anterior e ele recusa por falta de espaço — erro
que só aparece no SEGUNDO quadro, que é o pior lugar para procurar. É a mesma
pegadinha que o `Descompressor` já documenta do outro lado.

**`func (c *Compressor) recadoSeHouver() (uint32, bool, error)`**

recadoSeHouver pega um recado SÓ SE já estiver na fila. O booleano é "veio algum".

É o par da `proximoRecado`, e a existência das duas é a diferença entre esperar a
placa e conviver com ela. Ver o comentário longo em `Comprimir`.

**`func (c *Compressor) proximoRecado() (uint32, error)`**

proximoRecado espera o próximo recado do compressor assíncrono.

Espera BLOQUEANTE de propósito, e não uma consulta em laço. Hoje ela é usada só para
esperar o PEDIDO DE ENTRADA, que medido custa cinco microssegundos — o compressor
quase sempre já está pedindo quando chegamos aqui. Quando de fato espera, é porque ele
está ocupado, e essa é exatamente a hora de não gastar processador perguntando.

**`func (c *Compressor) Fechar()`**

Fechar desmonta tudo.

NÃO DRENA os últimos quadros de propósito. Drenar exige mandar o recado de
esvaziamento e esperar a confirmação, e essa espera pode não voltar se o compressor
já estiver em mau estado — travar o app ao encerrar uma call, para salvar dois
quadros que ninguém vai ver, é troca ruim.

**`var iidCodecAPI = guid(0x901DB4C7, 0x31CE, 0x41A2,`**

IID_ICodecAPI {901DB4C7-31CE-41A2-85DC-8FA0BF41B8DA}

A interface de COMANDO do compressor, separada da de configuração. Ela existe porque
há coisas que não são "como comprimir" e sim "faça isto agora" — e a única que
interessa aqui é o quadro-chave sob demanda.

**`var chaveForcarQuadroChave = guid(0x398C1B98, 0x8353, 0x475A,`**

CODECAPI_AVEncVideoForceKeyFrame {398C1B98-8353-475A-9EF2-8F265D260345}

CONFERIDO PELA SONDA (`TestSondaDoCodecAPI`), e não copiado: o Quick Sync desta
máquina responde "suportado" e "modificável" a esta chave, e responde "não
implementado" ao controle de espaçamento (`CODECAPI_AVEncMPVGOPSize`) — o que explica
por que `MF_MT_MAX_KEYFRAME_SPACING` não muda nada aqui.

A conclusão desenha a arquitetura: NÃO dá para encurtar o intervalo entre quadros-
chave, mas DÁ para pedir um na hora. É exatamente o mecanismo que o WebRTC usa.

**`var chaveBandaMediaDoCodec = guid(0xF7222374, 0x2144, 0x4815,`**

CODECAPI_AVEncCommonMeanBitRate {F7222374-2144-4815-B550-A37F8E12EE52}

A via para mudar a banda SEM REABRIR o compressor. Reabrir custaria um quadro-chave e
um engasgo visível — justamente no instante em que a rede já está sofrendo, que é
quando o ajuste é pedido.

E AQUI O `IsModifiable` MENTIU PELA QUARTA VEZ NESTE PROJETO. A sonda perguntou e
recebeu:

Intel Quick Sync (x2)     modificável: SIM
Microsoft AVC DX12        modificável: não
H264 Encoder MFT (soft)   modificável: não

Depois ela CHAMOU o `SetValue` nos quatro, e os quatro aceitaram. Os antecessores
dessa mentira estão documentados neste arquivo: `MF_TRANSFORM_ASYNC` valia zero em
compressor assíncrono, `MF_SA_D3D11_AWARE` quase condenou a arquitetura por engano, e
`MF_MT_MAX_KEYFRAME_SPACING` é aceito e ignorado. A regra que sobreviveu a todas:
perguntar ao objeto o que ele SABE FAZER vale mais que ler o que ele diz ser.

**`func (c *Compressor) ForcarQuadroChave() bool`**

ForcarQuadroChave manda o compressor produzir um quadro-chave no PRÓXIMO quadro.

POR QUE ISTO PRECISA EXISTIR. Um decodificador de H.264 não abre imagem nenhuma antes
de um quadro-chave — os outros quadros só descrevem a diferença em relação ao
anterior. Quem entra na sala com a transmissão em curso, e quem perde o quadro-chave
numa oscilação de rede, fica olhando para o vazio até o próximo. Medido nesta máquina:
CINCO SEGUNDOS de espera, e o compressor não aceita encurtar esse intervalo.

Devolve falso quando o compressor não expõe a via de comando. Não é erro: é um
compressor que só sabe seguir o próprio compasso, e aí a espera continua sendo o
intervalo dele.

**`func definirGUID(a objeto, chave *windows.GUID, valor windows.GUID)`**

A BANDA NÃO MUDA COM O COMPRESSOR ABERTO — três rotas tentadas, três recusas.

Fica registrado aqui porque é conhecimento que custa caro para redescobrir, e porque
explica por que a adaptação de banda REABRE o compressor (ver `Emissor.transmitir`)
em vez de fazer a coisa óbvia. Medido em `sonda_banda_ao_vivo_test.go`:

1. ICodecAPI SetValue(AVEncCommonMeanBitRate)
   Aceito com S_OK nos QUATRO compressores desta máquina — inclusive nos dois que
   declaram `IsModifiable = não`. E ignorado: pedido 3000 -> saiu 3015; pedido 600
   -> saiu 3014. Reto.

2. SetOutputType com a banda nova, no meio do fluxo
   Aceito e ignorado do mesmo jeito. O compressor honra o `MF_MT_AVG_BITRATE` que
   recebe na ABERTURA (3000 pedidos viraram 3015 medidos) e trava o controle de
   banda quando o fluxo começa.

3. Encerrar o fluxo, repor o tipo, reabrir o fluxo
   Derruba o compressor: "puxar o H.264: Falha catastrófica".

As duas primeiras são a mesma pegadinha do `MF_MT_MAX_KEYFRAME_SPACING`, que também é
aceito com todas as honras e também não muda nada — e é a quarta vez que uma
declaração do Media Foundation mente neste arquivo. A regra que sobreviveu a todas:
não basta perguntar se ele aceita, tem de PESAR o que sai.

**`TempoNoCano time.Duration`**

O TEMPO GASTO DENTRO DO CANO, somado — copiar na placa, reduzir e comprimir.

É ESTE o número que diz se a máquina dá conta, e não a taxa de quadros. A taxa
depende do que está acontecendo NA TELA: `ProximoQuadro` espera a duplicação
avisar que algo mudou, então área de trabalho parada rende poucos quadros por
segundo e um jogo rende muitos. Medir quadros por segundo num desktop quieto
mede o Windows, não o Astra — foi o que me fez perseguir um defeito inexistente,
vendo 79/s numa hora e 44/s noutra sem ter mudado nada que importasse.

O custo POR QUADRO, esse, é da máquina. Se couber no orçamento (16,7ms a 60 por
segundo), o cano não é o gargalo. É a medida que serve para decidir se um
computador fraco aguenta.

**`func MedirTransmissao(monitor int, duracao time.Duration, saidaL, saidaA, kbps int) (MedidaDaTransmissao, erro`**

MedirTransmissao roda o caminho inteiro e conta.

Existe pelo mesmo motivo do `MedirTela`: a pergunta "dá 60 quadros por segundo?" só
tem uma resposta honesta na máquina de quem pergunta. Aqui ela sai com o nome do
compressor que respondeu e a banda que aquilo custaria.

### `sidecar-voz/vazamento_test.go`

**— sobre o arquivo inteiro —**

A TRANSMISSÃO SEGURA MEMÓRIA COM O TEMPO?

A pergunta vale para os dois caminhos e vale mais para o novo. O de placa roda há
meses; o de software (`comprimirNaMemoria`) nasceu agora e aloca coisas que o outro
não alocava — uma amostra de saída nossa, e uma amostra convertida por quadro que
atravessa `pendente` antes de ser solta. Um `soltar` esquecido em qualquer um dos dois
não dá erro, não aparece no perfil do Go, e só se manifesta depois de meia hora de
chamada: a memória sobe até o Windows começar a paginar, e a pessoa relata que "o
Astra vai ficando lento".

ESTE TESTE É O ÚNICO JEITO DE PEGAR ISSO ANTES DA PESSOA. Ler o código já pegou um
vazamento nesta sessão (`medirASaida` reservando outro buffer a cada renegociação de
formato), mas leitura não prova ausência — só a memória do processo, medida ao longo
de milhares de quadros, prova.

A MEDIDA É `UsoPrivado` E NÃO O CONJUNTO DE TRABALHO. Ver `memoria.go`: o conjunto de
trabalho sobe e desce por decisão do Windows, sem nada ter sido alocado nem liberado.

Demora meio minuto por caminho, então fica atrás do próprio portão — a suíte inteira
não deve levar isso a cada volta:

ASTRA_TESTE_TELA=1 ASTRA_TESTE_VAZAMENTO=1 go test -run Vazamento -v -timeout 300s

**`const(…`**

AQUECIMENTO E MEDIÇÃO, separados. Os primeiros segundos de qualquer processo Go sobem
de memória por motivos que não são vazamento: o heap cresce até o tamanho de regime, o
Media Foundation carrega DLLs, o driver de vídeo reserva o que precisa. Contar isso
como vazamento reprovaria um caminho perfeito.

**`func janelaDeMedicao() time.Duration`**

janelaDeMedicao permite alongar a medição de fora.

EXISTE POR UMA PERGUNTA QUE 24 SEGUNDOS NÃO RESPONDEM: vazamento e aquecimento de
pilha se parecem numa janela curta — os dois sobem. O que os separa é a FORMA da
curva. Vazamento é linear para sempre; pilha de driver enche e para. A única maneira
de distinguir é medir por muito mais tempo e ver se a subida continua no mesmo ritmo.

ASTRA_VAZAMENTO_SEGUNDOS=180 go test -run TestTransmissaoNaoVazaMemoria -v -timeout 900s

**`janela := janelaDeMedicao()`**

DUAS METADES, E É A COMPARAÇÃO ENTRE ELAS QUE RESPONDE — não o crescimento total.

ESTA FOI A LIÇÃO CARA DESTE ARQUIVO. A primeira versão dividia o crescimento pelo
número de quadros e reprovava acima de mil bytes por quadro. O caminho de placa
reprovou três vezes seguidas, com 3.253, 3.355 e 3.673 bytes por quadro — parecia
vazamento reproduzível e não era.

O que desmascarou foi alongar a janela. Em 24 segundos o crescimento era +1,3 a
+1,9 MB; em 240 segundos, DEZ VEZES mais tempo, foi +2,0 MB. Vazamento multiplica
com o tempo; aquilo estabilizava. Era a pilha interna do driver de vídeo enchendo
UMA VEZ — custo fixo, que dividido por poucos quadros dá um número por quadro
enorme e dividido por muitos dá um número pequeno. A métrica é que estava errada,
não o código.

Partir a medição ao meio separa os dois sem depender do tamanho da janela: custo de
aquecimento acontece na PRIMEIRA metade e some; vazamento cresce nas duas igual.

**`porSegundo := float64(segunda) / metade`**

O LIMITE VALE SÓ PARA A SEGUNDA METADE, e é por segundo para não depender da
janela escolhida. 150 KB/s é folgado com propósito: medido, a segunda metade fica
perto de zero, e o que este teste existe para pegar é grosso — uma amostra NV12
não solta são 1,4 MB por quadro, ou 42 MB/s a 30 quadros. Duzentas e oitenta vezes
o limite. Apertar mais transformaria o teste em fonte de alarme falso, que é o
jeito conhecido de um teste deixar de ser lido.

**`func abrirParaOCaminho(t *testing.T, tela *Tela, largura, altura int, naMemoria bool) *Compressor`**

abrirParaOCaminho liga o compressor pelo caminho pedido.

O de software é aberto por `amarrar` direto porque nesta máquina existe compressor de
placa, e `AbrirCompressor` sempre o escolheria — a segunda passada nunca rodaria. É a
mesma razão de `semplaca_test.go`.

---

## desktopApp — o aplicativo (Kotlin/Compose)

### `mobile-native/desktopApp/build.gradle.kts`

**`alias(libs.plugins.kotlin.serialization)`**

compiler Compose (ship junto do Kotlin)
Compose Multiplatform (compose.desktop)
OBRIGATORIO, e a falta dele nao aparece no build: `@Serializable` sozinho e so
uma anotacao. Quem escreve o serializador e ESTE plugin, em tempo de
compilacao. Sem ele o codigo compila igual e quebra na primeira linha de JSON,
ja rodando -- foi o que deixou a call presa em "conectando" (o `pronto` do
processo de voz chegava e nao decodificava) e as notificacoes sem remetente
(o payload virava um objeto vazio). Dois sintomas sem nada em comum, uma causa.

**`providers.gradleProperty("astra.dist").orNull?.let`**

jpackage/jlink quebram com caminho non-ASCII no Windows (o repo mora em
".../Codigos e Loucuras/..."), entao o empacote sai do repo e vai pra um
caminho limpo. Tudo do Astra mora em C:/Astra:

  C:/Astra/build/      <- saida do empacote (era a pasta astra-dist solta)
  C:/Astra/versions/   <- as versoes instaladas
  C:/Astra/multi/      <- copia pra abrir como OUTRA pessoa (testar call)

Pra empacotar: ./gradlew :desktopApp:zipDistributable -Pastra.dist
(o valor e opcional; da pra mandar outro caminho com -Pastra.dist=D:/foo)
Sem a flag, nada muda: build normal em build/, dentro do repo.

**`val astraVersion = "0.4.1"`**

Versao unica do desktop: alimenta o packageVersion do jpackage E entra no app
via -Dastra.version -> o auto-update compara com a ultima release do GitHub.
Bumpar aqui (1 lugar) a cada release.

A LINHA 0.1.x MORREU NA 0.1.114. Passamos de cem versoes de patch dentro de um
unico minor, o que fazia o numero perder a funcao: "0.1.113 -> 0.1.114" nao dizia
nada sobre o tamanho da mudanca. Daqui pra frente o minor sobe.

A troca e segura pro auto-update: o isNewer do UpdateService compara campo a campo
como inteiro, entao [0,2,0] > [0,1,114] pelo segundo campo. Comparacao de texto
diria a mesma coisa por acaso, mas e o campo a campo que vale.

**`implementation(libs.mp3spi)`**

A VOZ NAO MORA MAIS NA JVM. Ela vive no sidecar em Go (sidecar-voz/), que fala
WebRTC pelo pion e captura o audio pelo WASAPI direto. Por isso sairam daqui:

  webrtc-java  8,0 MB de nativo do Windows por classifier
  gst-java     bindings do GStreamer

Os dois so eram usados pelo motor antigo, que virou ilha fechada quando a voz
migrou e foi removido inteiro. A captura de tela segue o mesmo caminho: DXGI
Desktop Duplication dentro do Go, sem passar por aqui.

O SIGNALING DO LIVEKIT FOI JUNTO, e demorou a sair. Ficaram para tras 5,4 MB de
Java gerado (src/main/java/livekit + logger) mais os .proto que os geraram e o
runtime protobuf-java que so eles usavam. Nao havia UM import: as unicas
mencoes a LiveKit no codigo Kotlin eram comentarios explicando por que ele nao
existe mais. Codigo morto grande e caro em silencio -- entra no jar, no arquivo
do CDS e no tempo de compilar, e ninguem o ve porque ninguem o chama.
Som da soundboard: MP3 e OGG entram direto pelo JavaSound. Ver ConversorDeSom
-- trocaram um binario de 137,8 MB por ~300 KB de jar.

**`val sidecarFonte = project.file("../../sidecar-voz")`**

O FFMPEG SAIU DO PACOTE, e a conta explica sozinha por quÃª.

Ele pesava 137,8 MB num instalador de 299 MB â€” quase metade do app, baixada por
todo mundo a cada atualizaÃ§Ã£o automÃ¡tica. Entrou para capturar tela; quando a
transmissÃ£o saiu do ar, sobrou com uma Ãºnica funÃ§Ã£o viva: converter o arquivo
que um administrador escolhe ao subir um som de soundboard.

Hoje isso Ã© feito por dois provedores do JavaSound, ~300 KB somados, dentro do
prÃ³prio processo (ver ConversorDeSom).

Quando o vÃ­deo voltar, ele volta em Go â€” nÃ£o por aqui. Se um dia for preciso
ressuscitar esta tarefa, ela estÃ¡ no histÃ³rico do git.
Compila o sidecar de voz (Go) pro appResources. O binario e gerado, nao
versionado â€” quem clona o repo compila junto do empacote.

O SIDECAR NAO PODE FALTAR NO PACOTE: sem ele nao ha voz nenhuma. Por isso esta
task NAO tem `onlyIf { !existe }` â€” ela recompila sempre que o
fonte muda, senao um pacote sairia com a voz da semana passada dentro.

Se o Go nao estiver instalado, falha com mensagem clara em vez de gerar um zip
mutilado que so daria erro na maquina do usuario. Os runners do GitHub para
Windows ja trazem Go.

**`tasks.matching`**

`prepareAppResources` ENTRA NA LISTA, e nÃ£o Ã© detalhe de arrumaÃ§Ã£o.

As duas tarefas acima escrevem dentro de `appResources/windows/`, que Ã©
justamente a pasta que o `prepareAppResources` LÃŠ para montar o pacote. Amarrar
sÃ³ o `createDistributable` deixava a ordem entre elas ao acaso: o Gradle podia
copiar os recursos antes de o Go ter compilado, e o zip sairia sem o componente
de voz â€” um app que instala, abre, e nÃ£o tem call, sem nada no build indicando o
porquÃª.

O Gradle 9 recusa isso na cara em vez de deixar passar ("uses this output without
declaring an explicit dependency"), e foi assim que apareceu.

**`tasks.register<Zip>("zipDistributable")`**

Zipa o app-image (pasta Astra/) pro asset do GitHub Release que o auto-update
baixa. Rodar junto do empacote (mesmo path ASCII do jpackage):
  ./gradlew :desktopApp:zipDistributable -Pastra.distDir=C:/Astra/build
Saida: <buildDir>/Astra-<versao>-win-x64.zip

**`compose.desktop`**

A tarefa `ensaioGst` saiu junto com o EnsaioGst.kt, como o comentario dela mesma
previa. Ela era o banco de testes do transporte por webrtcbin, e o equivalente hoje
e `go test` no sidecar-voz: as sondas de la (eco, aparelhos, tela) provam as pecas
fora de uma call pelo mesmo motivo -- descobrir que uma nao encaixa DENTRO de uma
conversa seria descobrir com a voz de alguem no meio.

**`val gcProfile = providers.gradleProperty("astra.gc").orNull ?: "g1"`**

O skiko (FrameWatcher) chama System.gc() a cada ~40s pra liberar memoria
nativa do Skia. Com G1 isso vira um full GC stop-the-world -> pausa de ms
que ENGASGA a aurora animando 60fps ("corte do nada", achado no JFR). Este
flag transforma o System.gc() explicito num ciclo CONCORRENTE: a memoria
nativa ainda e liberada, mas sem travar as threads de render. Ship pra todos.
--- Fase 1 de desempenho ---
GC: o coletor e a fonte classica de engasgo numa UI 60fps (uma pausa de 30ms
= 2 frames perdidos), mas TAMBEM pesa na memoria. Medimos os dois no app real
(tools/medir-desempenho.ps1), mesma maquina, mesmas 3 fases:

                   ZGC        G1
  parado          582MB      433MB
  em call         609MB      155MB
  transmitindo   2768MB      500MB   <- 5.5x menos

O ZGC mapeia a mesma memoria fisica em varios enderecos e o Windows conta
CADA mapeamento no working set â€” o "vazamento de 2.7GB" era contabilidade
inflada, nÃ£o memoria de verdade. Como o objetivo aqui e custo minimo de RAM,
o padrao e G1. Pra voltar ao ZGC (pausas < 1ms, se um dia o engasgo importar
mais que a memoria): ./gradlew ... -Pastra.gc=zgc

**`val empacotando = gradle.startParameter.taskNames.any { alvo ->`**

AppCDS automatico (JDK 19+): a JVM guarda as classes ja "digeridas" num
arquivo e reusa na proxima abertura -> abre mais rapido e o metaspace fica
menor (memoria compartilhada em vez de recriada). Cria sozinho no 1o run;
se o caminho nÃ£o for gravavel, a JVM so avisa e segue (nÃ£o quebra).
$APPDIR e substituido pelo jpackage pela pasta app/ da instalacao.
So no build EMPACOTADO: `$APPDIR` e substituido pelo jpackage. Rodando pelo
Gradle (:run) o token nÃ£o resolve e a JVM cospe um erro feio de cds (inofensivo,
sai com 0 â€” verificado), entao isto so entra quando se esta EMPACOTANDO.

O GATE ESTAVA ERRADO E NADA DISTO CHEGAVA NO APP PUBLICADO.

Era `astra.distDir`, que significa "jogue a saida do build noutro lugar" â€”
uma gambiarra que existe so porque o repo do dono mora num caminho com
acento e o jpackage nÃ£o engole. O workflow de release NAO passa essa flag
(de proposito: no runner o caminho e limpo). Ou seja, o unico build que
chega em alguem era exatamente o que ficava SEM AppCDS (abertura mais
lenta pra todo mundo) e SEM ErrorFile (o laudo de crash nativo caindo como
hs_err_pid<n>.log na raiz, enquanto o diagnostico mandava procurar
falha-jvm-*.log â€” um arquivo que nunca existiu).

Agora o gate pergunta a coisa certa: "a tarefa pedida e de empacotamento?".
Vale no CI e na maquina do dono, com ou sem a gambiarra do caminho.

**`jvmArgs += "-XX:ErrorFile=\$APPDIR/falha-jvm-%p.log"`**

Crash NATIVO (webrtc/skia derrubando a JVM inteira) nÃ£o passa pelo
CrashLog â€” a JVM morre antes de rodar codigo Java. Neste caso ela
escreve o hs_err aqui, ao lado do app, em vez de num diretorio
aleatorio onde ninguem acha. Junto com falhas.txt, cobre os dois
tipos de "fecha do nada": excecao Java e morte nativa.

**`if (providers.gradleProperty("astra.multi").isPresent)`**

SEGUNDA JANELA pra testar com DUAS contas ao mesmo tempo:
  ./gradlew :desktopApp:run -Pastra.multi
Pula o bloqueio de instancia unica E usa uma pasta de sessao propria
(%APPDATA%\Astra-teste1), entao da pra logar com outra conta e ver ao vivo
o que uma faz aparecer na outra. A maioria dos bugs de tempo real so
aparece com duas pontas â€” com uma conta so, quem cria o canal sempre ve o
canal. NAO vai no pacote: e gateado pela flag, como o -Pjfr.

**`if (providers.gradleProperty("astra.diag").isPresent)`**

Diagnostico de engasgo (NAO vai no pacote normal):
  ./gradlew :desktopApp:run -Pastra.diag
Loga fps e AVISA cada frame que passou de 17ms (= perdeu o vsync de 60fps).
E assim que se acha travamento de verdade em vez de adivinhar.

**`jvmArgs += "-Xmx1g"`**

Teto de HEAP. Sem -Xmx o HotSpot deixa o heap crescer ate 1/4 da RAM FISICA
(num PC de 16GB isso e ~4GB) antes de um GC maior â€” como nÃ£o ha pressao, o GC
fica preguicoso e o RSS so sobe ("em call, de 2 em 2MB a mais, sem parar"). O
churn de getStats do audio (5x/s por participante) + protobuf + UI alimenta
isso. Capar em 768MB forca o heap a ficar enxuto (uso real fica ~150-300MB),
entao o RSS para de escalar. NAO afeta a transmissÃ£o: bitmaps de video sao
memoria NATIVA (fora do heap), presos pelo RasterRecycler, nÃ£o pelo -Xmx.

512m (0.1.35) foi longe demais: teto baixo nÃ£o "economiza" RAM quando o app
realmente precisa dela â€” vira OutOfMemoryError, que mata o processo na hora
e sem aviso. Como MaxHeapFreeRatio devolve as paginas ao Windows depois do
pico, o teto mais alto NAO custa memoria parado; so evita a morte no pico
(call cheia + transmissÃ£o). 1GB e o teto pedido pelo dono.

ATENCAO ao ler o Gerenciador de Tarefas: isto limita o HEAP (objetos Java),
que nÃ£o e o total do processo. Os quadros de video vivem em memoria NATIVA,
fora do heap â€” por isso "3GB transmitindo" NAO e resolvido por este numero.
O que segura aquilo e o lado nativo (ver ScreenCaptureFfmpeg).

**`jvmArgs += "-XX:MinHeapFreeRatio=10"`**

Devolver RAM ao SISTEMA. Por padrao a JVM segura o que ja cresceu: mesmo
depois de coletar, o heap continua reservado e o Gerenciador de Tarefas
segue mostrando o pico. Com estas tres a JVM ENCOLHE o heap e devolve as
paginas ao Windows, entao a memoria CAI depois de uma call/transmissao em
vez de ficar no topo. O periodico so roda quando o app esta ocioso.

**`jvmArgs += "-XX:GCTimeRatio=4"`**

O QUE DE FATO SEGURA O HEAP, e nao e o -Xmx. Medido com um churn de 12s
(aloca muito, retem pouco â€” o perfil de um app de UI), tres repeticoes:

  GCTimeRatio=12 (padrao)   639 MB commitados   referencia
  GCTimeRatio=4             397 MB              -2,5% de vazao
  GCTimeRatio=2             334 MB              -4,3% de vazao
  Xmx768m (com o padrao)    592 MB              quase nada
  Xmx512m (com o padrao)    512 MB

Ou seja: BAIXAR O TETO QUASE NAO AJUDA. Com GCTimeRatio=12 o G1 aceita
gastar ~8% do tempo coletando, e com essa folga ele prefere crescer o heap
a trabalhar â€” commita 639 MB para usar 200. Apertar a razao inverte a
escolha: ele coleta mais e cresce menos. 242 MB a menos por 2,5% de vazao,
e o -Xmx continua sendo o que sempre foi, a valvula contra OutOfMemory.

TESTADO E DESCARTADO no caminho: `-XX:SoftMaxHeapSize`. O G1 do JDK 21
ACEITA o flag sem reclamar e simplesmente o ignora â€” 644 MB com ele, 644 MB
sem. Flag que nao da erro e nao faz nada e a pior especie.

Escolhido 4 e nao 2: `MaxGCPauseMillis=8` continua limitando cada pausa, mas
apertar a razao aumenta a FREQUENCIA delas, e este app anima a 60fps. 4
pega quase toda a economia; 2 cobra o dobro de vazao por 63 MB a mais.

**`jvmArgs += "-XX:MaxMetaspaceSize=256m"`**

Teto do metaspace (classes). Sem limite ele so cresce; o teto evita
crescimento silencioso ao longo de horas. 192m era apertado demais pro que
este app carrega (Compose + Koin + Retrofit + protobuf + webrtc, mais as
classes que o Compose GERA em runtime): estourar o metaspace tambem e um
OutOfMemoryError, ou seja, mais uma forma de "fecha do nada".

**`if (providers.gradleProperty("jfr").isPresent)`**

Profiler RUNTIME (JFR), gated pra nunca vazar pro pacote: rodar
  ./gradlew :desktopApp:run -Pjfr
Usar o app ~2min (aurora, rolar chat, entrar em call, transmitir) e fechar;
gera astra-profile.jfr (dumponexit) na pasta do modulo. Analiso com o
`jfr` CLI (ExecutionSample = CPU; ObjectAllocation = alocacao).

**`if (providers.gradleProperty("astra.nmt").isPresent)`**

ONDE A RAM ESTA, por categoria (NAO vai no pacote):
  .\gradlew :desktopApp:run -Pastra.nmt
  jcmd <pid> VM.native_memory summary

Existe porque "o app usa 900MB" nao e acionavel: heap, metaspace, cache de
codigo, pilhas de thread e buffers diretos sao cinco donos diferentes com
cinco remedios diferentes, e o Gerenciador de Tarefas soma os cinco num
numero so. O NMT separa â€” e separar e o que transforma um numero ruim numa
linha de codigo pra mudar.

Custa ~5% de desempenho e um pouco da propria RAM que mede, entao fica atras
da flag em vez de ligado sempre.

**`modules("jdk.httpserver", "java.management", "jdk.management", "jdk.accessibility")`**

Modulos do JDK que o jlink NAO inclui por padrao mas o app usa em
runtime. jdk.httpserver = com.sun.net.httpserver (loopback do login
Google, GoogleAuthFlow). Sem ele o .exe empacotado joga
NoClassDefFoundError -> "Nao consegui abrir a porta local". No dev
(JDK completo) o modulo existe, por isso so quebrava no pacote.
java.management: sem ele o ManagementFactory nem existe no runtime
enxuto do jlink â€” era o "GC : ?" do diagnostico (a leitura do coletor
falhava calada). Tambem e o que habilita monitoramento/JFR no pacote.
jdk.accessibility: no Windows, o leitor de tela so enxerga o app
atraves da Java Access Bridge, e a ponte mora NESTE modulo. Sem ele
no pacote, todo o trabalho de rotular botao e invisivel â€” nem quem
ligasse o Access Bridge no proprio Windows (jabswitch /enable)
conseguiria usar o Astra por leitor de tela.
jdk.management: o com.sun.management.OperatingSystemMXBean (quanto
processador ESTE processo gastou) mora nele, e nao no java.management.
Sem o modulo, a medicao de custo da transmissao compila e explode so no
app empacotado â€” a mesma pegadinha que ja custou o jdk.httpserver.

**`appResourcesRootDir.set(project.file("appResources"))`**

Recursos por-SO empacotados no app-image. Hoje sao dois:
`astra-voz.exe` (o processo de voz, compilado do Go pelo
compilarSidecarVoz) e `opus-0.dll` (o codec que ele carrega). Em
runtime saem em System.getProperty("compose.application.resources.dir").

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/AtalhosGlobais.kt`

**`object AtalhosGlobais`**

TECLA DE ATALHO QUE FUNCIONA COM O ASTRA NO FUNDO.

Existe por um motivo só: apertar-para-falar e mudo/ensurdecer não valem nada se
exigirem a janela do Astra em foco. Quem está no meio de uma partida não vai
alt-tab pra se calar — ou a tecla chega de fora, ou a funcionalidade é enfeite.

---------------------------------------------------------------------------
O QUE ISTO É, DITO SEM RODEIO: um gancho de teclado de baixo nível
(`WH_KEYBOARD_LL`), que é a mesma peça de Win32 que um keylogger usaria. É o
único jeito de o Windows entregar tecla a um processo sem foco, e é o que
Discord e TeamSpeak fazem. Por isso ele é deliberadamente estreito:

 • só compara `vkCode` contra as teclas que VOCÊ ligou nas configurações;
 • não guarda, não conta e não escreve tecla nenhuma em lugar nenhum;
 • não passa nada pela rede — o Astra não tem para onde mandar isto;
 • SEMPRE chama `CallNextHookEx`, ou seja, NÃO engole a tecla: a mesma tecla
   continua chegando no jogo, no navegador e em quem mais estiver ouvindo;
 • só liga quando existe pelo menos uma tecla ligada (ou durante a captura de
   uma). Sem atalho configurado, este arquivo não instala nada.

Se um dia isto precisar de mais do que "esta tecla desceu/subiu", o pedido está
errado, não o código.
---------------------------------------------------------------------------

DOIS DETALHES DE WIN32 QUE DECIDEM SE FUNCIONA:

1. O gancho vive na THREAD que o instalou, e essa thread precisa de uma bomba
   de mensagens (`GetMessage`) rodando. Sem o laço, o Windows nunca entrega o
   callback e o gancho fica instalado e mudo — que é o formato de "não faz
   nada e não dá erro".

2. O callback tem ORÇAMENTO DE TEMPO (`LowLevelHooksTimeout`, 300ms por
   padrão). Estourar não dá exceção: o Windows desinstala o gancho em silêncio
   e o atalho para de funcionar no meio da sessão. Por isso aqui dentro só
   acontece uma comparação de inteiro, e o trabalho de verdade sai para outra
   thread.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/AtividadeDoSistema.kt`

**`object AtividadeDoSistema`**

"O QUE A PESSOA ESTA USANDO AGORA" — a metade que olha o sistema.

UMA LINHA QUE NAO SE CRUZA: O TITULO DA JANELA NUNCA E LIDO.

Nao e configuracao, e limite. Titulo de janela e texto livre e entrega coisa
que ninguem quis contar: nome de arquivo aberto, aba do navegador, endereco,
termo de busca, nome de quem esta na conversa. E por isso que o Discord so
mostra nome de jogo vindo de lista curada, e por isso que o Windows Timeline
morreu em 2021 — ele passou desse limite.

O que sai daqui e SO o nome do programa, tirado da propria assinatura do
executavel (a mesma coisa que o Windows mostra no Gerenciador de Tarefas).
Navegador vira "Navegando", sem excecao e sem detalhe.

**`private val NAVEGADORES = setOf(…`**

Navegador nao diz o programa, diz o que voce esta LENDO — e e por isso que ele
vira uma palavra so. "Navegando" e o teto do que da pra contar sem contar
demais; o nome do navegador ja seria mais informacao do que a pessoa espera
estar dando, e a aba seria informacao que ela nunca daria.

**`private var ultimoPid = -1L`**

ESTE RECURSO NAO PODE CUSTAR NADA, e o motivo e onde ele roda: a pessoa esta
JOGANDO. Um engasgo de 8ms a cada 5s nao aparece em medicao de media e aparece
como quadro perdido na tela dela. Entao a conta e feita UMA vez por programa,
e depois disso o laco custa duas chamadas de Win32 que respondem em nanosegundos.

Onde estava o custo (e era real):
 - `ProcessHandle.info()` conversa com o sistema pra achar o caminho do exe;
 - ler a assinatura do executavel e LEITURA DE DISCO.
Os dois aconteciam a cada 5s, sempre sobre o mesmo programa, sempre com a
mesma resposta. Um jogo aberto por duas horas fazia isso 1440 vezes pra
descobrir 1440 vezes que ainda era o mesmo jogo.

**`if (pid == ultimoPid) return ultimaResposta`**

MESMO PROCESSO DE ANTES: a resposta ja e conhecida e nada abaixo daqui
precisa rodar. E o caso esmagadoramente comum — ninguem troca de programa
a cada cinco segundos.

Reaproveitar pid tem um risco conhecido: o Windows reusa numero de processo
depois que o antigo morre. O estrago possivel e mostrar o programa errado
por ate 5s, ate a proxima espiada de um pid diferente. Trocar isso por uma
consulta a mais a cada volta seria pagar sempre pra evitar um engano raro e
que se corrige sozinho.

**`private fun nomeBonito(arquivo: File): String?`**

Le FileDescription e, se faltar, ProductName. Nesta ordem porque e a que
acerta mais: "Google Chrome" e FileDescription enquanto o ProductName e
"Google Chrome"; ja em jogo, o FileDescription costuma ser o nome comercial
e o ProductName as vezes vem com o nome da engine.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/auth/AuthRepository.kt`

**`suspend fun loginWithGoogle(): Result<Session> = try`**

Login com Google via loopback (GoogleAuthFlow abre o navegador). Volta um
refresh token; troca-o por um access (o /refresh so devolve tokens, sem user),
salva sessão provisoria pra o interceptor autenticar o /me, e so entao monta a
sessão completa. Qualquer falha depois do save provisorio limpa o disco pra o
app não reabrir numa sessão meia-boca (userId vazio quebraria o shell).

**`fun logout(escopo: CoroutineScope)`**

AVISAR O SERVIDOR FAZ PARTE DE SAIR.

Antes isto só esquecia o token deste lado. O refresh token seguia VÁLIDO no
servidor até expirar sozinho — quem tivesse uma cópia (session.bin de um PC
emprestado, backup, máquina roubada) continuava emitindo access token novo
depois de você ter saído. E a sua sessão seguia listada na aba Sessões,
dizendo que a conta estava aberta num lugar de onde você já tinha saído.

A ORDEM TEM UMA ARMADILHA, e ela custou pensar: o interceptor lê o access
token DO PRÓPRIO `store`. Limpar antes de avisar faz a chamada sair sem
credencial e voltar 401 — o servidor nunca revoga nada. Mas limpar só depois
reabre um bug que já mordeu este app: se o processo morrer no meio, o
session.bin sobrevive e reabrir o Astra entra de novo na conta que saiu.

Então: avisa primeiro, limpa em `finally`, e a espera tem TETO. Três segundos
é o bastante pra um POST e curto o bastante pra ninguém ficar com o disco
sujo por causa de uma rede ruim. Estourou o teto ou deu erro, limpa do mesmo
jeito — o pior caso volta a ser o de antes (token expira sozinho), e o caso
bom passa a revogar na hora.

O socket cai ANTES de tudo: ele é single do Koin, e sem desconectar o
connect() da próxima conta vê connected()==true e o servidor segue te
tratando como a conta anterior.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/auth/GoogleAuthFlow.kt`

**`object GoogleAuthFlow`**

Login com Google no desktop via LOOPBACK (o padrao OAuth pra apps nativos, sem
navegador embutido): sobe um HttpServer em 127.0.0.1:porta-efemera, abre o
navegador do sistema na rota /api/auth/google (passando porta+nonce no `state`),
e espera o backend redirecionar de volta pra 127.0.0.1/callback com o refresh
token na QUERY (o fragment # não chega ao servidor). O nonce casa a volta com
ESTE pedido; a porta so e nossa porque foi aberta antes de abrir o navegador.

**`Thread`**

O /callback captura o token e responde 302 -> /done; o navegador so busca
/done DEPOIS. Parar o server na hora (stop(0)) cortava essa segunda request
-> "conexão recusada" no navegador (mesmo o app logando). Para com folga numa
thread daemon pra o /done ser servido, sem atrasar o login no app.

**`private fun page(msg: String, ok: Boolean): String`**

PALETA DO ASTRA: prata sobre preto. Esta pagina e servida pelo proprio app num
servidor local, entao ela nao le os tokens do Obsidian — os valores estao aqui na
mao, e sao os mesmos: void #06060E, text1 #E4E4EB, text2 #C0C0C6, text3 #8C8C94,
accent de fabrica #D4D8E0.

Era ambar (#c9a96e) no simbolo e VERDE (#6ec98a) no sucesso. Os dois estavam fora
do vocabulario: o accent de fabrica e branco, nao ambar (o ambar e uma opcao entre
18), e verde de "deu certo" e linguagem de formulario web -- o Astra diz o que
aconteceu com texto, sem semaforo. E esta e a primeira coisa que alguem ve depois
de entrar; se ela parece outro produto, e outro produto.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/auth/SessionStore.kt`

**`class SessionStore`**

Sessao persistida em %APPDATA%/Astra (pasta por-usuário do Windows; ~/.astra
no resto). Tokens cifrados em repouso com DPAPI (CryptProtectData amarra o
segredo a conta do Windows); fora do Windows cai no arquivo plano.

Concorrencia: o AuthInterceptor chama load() em TODA request e o
authenticator chama save() no refresh — com cifra, leitura no meio de uma
escrita corrompe o decrypt. Por isso: cache em memoria (disco+DPAPI so uma
vez), lock nas mutacoes e escrita atomica (tmp + move).

**`val slot = Multi.slot`**

Segunda instancia mora numa pasta PROPRIA. Sem isto as duas janelas
dividiriam o mesmo session.bin, ou seja: a mesma conta nas duas — e testar
"eu vejo o que o outro fez" ficaria impossivel, que e justamente o ponto de
abrir a segunda. O numero da conta vira sufixo, entao da pra ter quantos
perfis quiser (Astra-teste2, -teste3, ...).

**`fun deviceId(): String = synchronized(lock)`**

Id estavel por instalacao (não e segredo). Vive nas prefs de UI pra
sobreviver a logout — assim o MESMO PC mantem o id entre logins e o backend
deduplica a sessão (X-Device-Id). Gerado uma vez, sob lock pra não nascer
dois no primeiro boot (varias requests concorrentes chamam load/deviceId).

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/AvisoDeMensagem.kt`

**`data class AvisoNaTela(…`**

O AVISO DE MENSAGEM — a janela que o Astra desenha no canto quando chega mensagem
com o app fechado ou em segundo plano.

POR QUE NÃO O BALÃO DO WINDOWS. Ele saía por `TrayIcon.displayMessage`, e essa API
aceita título, texto e um tipo de ícone. Só isso. Não existe parâmetro de imagem —
não é que a foto de quem mandou ficasse feia, é que ela era IMPOSSÍVEL. E era a
primeira pergunta de quem ouve o aviso: quem me chamou? Reconhecer um rosto é
instantâneo; ler um nome é uma tarefa, ainda que curta.

A janela própria também resolve o segundo defeito de graça: o balão do Windows não
é clicável de forma confiável (o clique vai pro ícone da bandeja, não pro aviso).
Aqui, clicar abre a conversa — que é o que a pessoa quer fazer em quase todo aviso
que ela decide não ignorar.

ELA NÃO PEDE FOCO, E ISSO É A REGRA MAIS IMPORTANTE DESTE ARQUIVO. O menu da
bandeja pede (`window.requestFocus()`), e tem que pedir: é um menu, ele precisa
fechar quando você clica fora. Um aviso é o contrário — ele chega enquanto você
está fazendo outra coisa, e roubar o foco significa engolir a tecla que a pessoa
estava digitando em outro programa. `focusable = false` é o que garante isso, e não
é detalhe de polimento: é a diferença entre um aviso e uma interrupção.

**`object AvisosNaTela`**

Quem segura os avisos vivos. Objeto e não estado de tela porque quem CRIA aviso é o
ShellScreen (que só existe com sessão aberta) e quem DESENHA é o `application`, que
existe sempre — inclusive com a janela principal escondida na bandeja, que é
justamente quando o aviso mais importa.

**`tocarAvisoDeMensagem()`**

O SOM SAI DAQUI, junto do cartão, pelo mesmo motivo que ele saía junto do
balão: quem decide QUANDO avisar é o ShellScreen, e um som com regra própria
acabaria tocando sem nada na tela, ou com o app na frente — barulho sem
referente. Não precisa checar modo transmissão: quem transmite nunca chega
aqui, o caminho discreto desvia antes.

**`AvisosNaTela.vivos.forEachIndexed { indice, aviso ->`**

Cada aviso é uma JANELA. Uma só, alta, com os três dentro, seria menos código —
e teria um buraco: a janela precisaria cobrir a altura da pilha cheia o tempo
todo, e essa área transparente engole clique de quem estiver embaixo. Janelas
separadas ocupam exatamente o retângulo que desenham.

**`val (direita, baixo) = tela ?: return`**

Canto inferior direito, empilhando pra CIMA — a convenção do Windows, e a mesma
do balão que este aviso substitui. Sem informação de tela (ambiente sem monitor,
por exemplo) o aviso simplesmente não aparece: uma janela em coordenada chutada
é pior que aviso nenhum.

**`LaunchedEffect(sobMouse)`**

O RELÓGIO PARA COM O MOUSE EM CIMA. Quem levou o ponteiro até ali está lendo, e
sumir debaixo do olho é a forma mais irritante de um aviso falhar. Sai de novo
assim que o mouse sai — o `LaunchedEffect` reinicia a contagem, o que é a
escolha certa: quem terminou de ler ganha o tempo inteiro para decidir clicar.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/BandejaDoAstra.kt`

**`data class ItemDaBandeja(…`**

A BANDEJA COM MENU DESENHADO PELO ASTRA.

O `Tray` do Compose Desktop é o `SystemTray` do AWT por baixo, e o menu dele é um
`PopupMenu` do Win32: quem pinta é o Windows. Ele não aceita cor, fonte, canto,
ícone nem espaçamento — não existe API pra estilizar, o objeto simplesmente não
tem essas propriedades. Era por isso que o menu da bandeja era o único pedaço do
app que não parecia o app.

Aqui o `TrayIcon` é criado na mão (sem `PopupMenu`), e o clique-direito abre uma
JANELA nossa — sem moldura, transparente, sempre no topo, no ponto do cursor —
com o mesmo vocabulário visual do resto. É o que Discord e Spotify fazem, e pelo
mesmo motivo.

O QUE ISTO CUSTA, dito antes: a bandeja é o caminho por onde o app fica vivo em
segundo plano E por onde os avisos saem. Os dois passam a ser código nosso — o
`sendNotification` do Compose vira `TrayIcon.displayMessage`.

**`menuEm = if (menuEm != null) null else`**

Segundo clique-direito FECHA. É a rede de segurança do menu:
se por algum motivo ele não receber foco (e portanto não
fechar sozinho ao perder), ainda há um jeito óbvio de sair
dele sem escolher nada.

**`DisposableEffect(Unit)`**

FECHAR AO PERDER O FOCO é o comportamento que se espera de um menu, e é
também o único jeito de ele não ficar preso na tela quando a pessoa
desiste. O `alwaysOnTop` garante que ele apareça mesmo se o Windows negar
o primeiro plano; o pedido de foco abaixo é o que faz o fechar funcionar.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/CrashLog.kt`

**`object CrashLog`**

Rede de seguranca do desktop (o :app Android já tinha a dele; aqui não existia).
App de janela do jpackage NAO tem console anexado: qualquer excecao não tratada
mata o processo em silencio — e o "o Astra fecha do nada" fica sem rastro nenhum.
Aqui a excecao vira LINHA NUM ARQUIVO (%LOCALAPPDATA%\Astra\falhas.txt) e um
aviso na tela, entao a proxima vez que fechar sozinho a gente sabe POR QUE.

O arquivo ACUMULA (append): fechamento sozinho costuma ser intermitente, e um
unico registro sobrescrito perderia justo o padrao que interessa.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/DesktopShortcut.kt`

**`object DesktopShortcut`**

Atalho na area de trabalho. A distribuicao do Astra e um app-image (zip
descompactado, sem instalador), entao NAO ha etapa de "instalar" que crie o
atalho — o proprio app garante um Astra.lnk no Desktop no 1o run (se faltar).
APONTA PRO LAUNCHER (launch.vbs), não pro exe de uma versão: a instalacao e
portatil (varias versões em versions\<v>\) e o launch.vbs sempre abre a MAIOR.
Cravar o exe de uma versão travava o atalho nela — quando chegava versão nova, o
atalho seguia abrindo a velha ("não leva pra mais atual"). So Windows; thread
daemon; repara se o atalho existente estiver errado.

**`val ps = buildString`**

A pasta e resolvida PELO POWERSHELL, não por user.home + "Desktop":
com o OneDrive ligado (padrao no Windows 11) a area de trabalho vira
%USERPROFILE%\OneDrive\Desktop e a pasta antiga nem existe — o palpite
falhava calado e o atalho nunca era criado. GetFolderPath('Desktop')
devolve o caminho real, redirecionado ou não.
WScript.Shell (COM) via PowerShell cria o .lnk — sem lib nativa extra.

**`private fun currentExePath(): String? =`**

jpackage seta "jpackage.app-path" com o caminho do launcher (Astra.exe). E a
UNICA fonte confiavel.

O fallback pro comando do processo foi removido: num run de desenvolvimento
(./gradlew :desktopApp:run) ele devolve o java.exe do JDK, que passa num teste
de ".exe" e criava no Desktop um atalho apontando pro java — e, por causa do
antigo "se já existe, desiste", esse atalho quebrado sobrevivia a instalacao
seguinte. Sem app empacotado não ha atalho pra criar; entao não cria nenhum.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/di/AppModule.kt`

**`OkHttpClient.Builder()`**

Cliente PROPRIO, nunca newBuilder() do plain: newBuilder compartilha o
Dispatcher (5 requests/host). O boot dispara 5 chamadas autenticadas;
com token vencido as 5 seguram os slots dentro do authenticator e o
refresh (mesmo host) fica na fila pra sempre — deadlock do
"carregando o ceu…". Dispatcher separado = refresh sempre anda.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/InicioComWindows.kt`

**`object InicioComWindows`**

ABRIR JUNTO COM O WINDOWS.

Faz sentido porque o Astra **já vive na bandeja**: quem fecha no X continua
recebendo aviso de mensagem. Sem isto, esse segundo plano só começa quando a
pessoa lembra de abrir o app — e um mensageiro que só recebe depois de você
lembrar dele é um mensageiro que não recebe.

O REGISTRO É A VERDADE, e não uma preferência nossa. É o mesmo lugar que o
Gerenciador de Tarefas (aba Inicializar) mostra e deixa desligar — se
guardássemos a resposta por fora, o app diria "ligado" para uma coisa que o
Windows já tinha desligado, e a pessoa não teria como saber quem está mentindo.
Ler o registro custa microssegundos e nunca discorda de ninguém.

Chave: HKEY_CURRENT_USER, ou seja, só esta conta do Windows. A de máquina
inteira (HKLM) exigiria elevação e mexeria no login dos outros usuários do PC —
nenhum app de mensagem tem esse direito.

**`fun disponivel(): Boolean = caminhoDoExe() != null`**

Sem app empacotado não há o que registrar: num run de desenvolvimento o
caminho do processo é o java.exe do JDK, e gravar ISSO no arranque do
Windows deixaria pra trás uma entrada quebrada que sobrevive ao próximo
pacote. Mesma lição do DesktopShortcut.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/Main.kt`

**`private fun pastaDaInstalacao(): String =`**

Grava o diagnostico de boot num ARQUIVO. O println sozinho não servia: app de
janela no Windows (jpackage) não tem console anexado, entao a linha ia pro nada —
ninguem conseguia ler. Fica em %LOCALAPPDATA%\Astra\diagnostico.txt (mesma pasta
do cache de imagens). Sobrescreve a cada abertura: e um retrato do boot atual.
Onde o crash nativo cai: a JVM grava o hs_err na pasta de TRABALHO do processo,
que no pacote do jpackage e a pasta do Astra.exe. Sem jpackage (Gradle), e de
onde o build rodou.

**`private fun escolherPlacaDaInterface() = runCatching`**

A placa que desenha a INTERFACE, escolhida em Configuracoes > Desempenho.

TEM QUE SER AQUI, antes de `application {}`. O Skiko le esta propriedade UMA vez, no
instante em que cria a primeira janela, e nunca mais olha -- por isso a tela de
configuracoes avisa que esta metade so vale no proximo arranque. Trocar depois nao
falharia com erro: simplesmente nao teria efeito, que e pior.

O Skiko so aceita tres respostas -- automatico, integrada, dedicada -- e nao um
aparelho especifico. Entao a escolha do dono, que e por PLACA, vira um dos tres. A parte
do video nao passa por aqui e usa o aparelho exato.

**`if (!placa.desenhaATela)`**

PEDIR A PLACA QUE NAO DESENHA O MONITOR DEIXA O APP MAIS LENTO, nao mais rapido.

Num notebook hibrido a dedicada renderiza, mas quem apresenta na tela e a
integrada: cada quadro desenhado na dedicada tem que ser COPIADO de volta pro
adaptador do monitor antes de aparecer. A copia atravessa o PCIe todo frame, e o
ganho de desenhar numa placa mais forte vai embora na conta — o dono ligou a
dedicada e sentiu o app ficar MENOS fluido, que e exatamente o previsto.

Entao a escolha se apaga sozinha em vez de ficar valendo em silencio. Apagar e nao
so ignorar: se ficasse gravada, a tela de configuracoes continuaria mostrando uma
opcao marcada que nao faz nada, e uma opcao que mente e pior que uma que falta.

**`internal object FocoDoSistema`**

FOCO REAL DA JANELA, perguntado ao AWT — e o `window.isFocused` do inicio importa
tanto quanto os eventos.

A primeira tentativa gateava por `LocalWindowInfo.isWindowFocused` e nao economizava
nada. Instrumentado, o motivo apareceu: quando o Astra abre ATRAS de outra janela ele
nunca GANHA foco, entao nunca dispara o evento de perder — e um sinal que so existe por
evento fica preso no valor inicial pra sempre. O `KeyboardFocusManager` tinha o mesmo
defeito por outro caminho: disparava uma vez ao ganhar e nunca mais voltava a nulo.

Terceira tentativa, e a que funciona: perguntar ao WINDOWS quem esta em primeiro plano
e comparar com o nosso processo. Instrumentado, o AWT deste app registra "GANHOU foco"
no arranque e NUNCA mais dispara nada — a janela e frameless e translucida, e nessa
configuracao os eventos de foco do AWT simplesmente nao chegam. Um sinal que so existe
por evento fica preso no valor inicial pra sempre, e foi por isso que as duas versoes
anteriores mediram exatamente a mesma coisa com e sem foco.

Comparar o PROCESSO, e nao a janela, tem um bonus: menu de contexto e popup do Compose
sao janelas separadas mas NOSSAS, entao continuam contando como "o app esta na frente"
e o ceu nao congela a cada clique com o botao direito — que era a objecao original a
gatear por foco.

**`fun cederAFrenteA(pid: Long)`**

CEDE A VEZ DE IR PRA FRENTE a outro processo.

O Windows não deixa um programa qualquer roubar a frente de quem você está
usando — e faz muito bem. A consequência é que um processo que ABRE outro não
passa esse direito adiante automaticamente: o filho nasce atrás de tudo.

Era exatamente isso na atualização: o Astra velho abria o novo e saía, e a
janela nova aparecia ATRÁS do navegador. Da poltrona parecia que o app tinha
sumido ou ficado só na bandeja.

Isto só funciona quando QUEM CEDE está na frente — que é o caso normal (você
acabou de abrir o Astra e ele se atualizou). Se o Astra estava atrás, ninguém
tem direito nenhum pra ceder, e o filho nasce atrás também. Isso é o correto:
app que se atualiza sozinho no fundo não deveria pular na sua frente.

**`appendLine("placa (pedido): ${System.getProperty("skiko.gpu.priority") ?: "auto (o Skiko decide)"}")`**

QUAL PLACA DESENHA A INTERFACE. Sem esta linha, "o Astra esta lagado" numa
maquina de duas placas e uma frase sem resposta possivel: o Skiko sem escolha
explicita cai em "Auto", e Auto pega o adaptador padrao -- que num notebook
hibrido e a INTEGRADA, com a dedicada parada do lado. Adivinhar isso custa uma
sessao; ler custa uma linha.

**`appendLine("(sem falhas.txt = a JVM morreu por fora, em código nativo. O laudo é")`**

O nome e o lugar do arquivo sao os que a JVM usa DE VERDADE — conferidos
num crash real. O texto antigo inventava "falha-jvm-*.log na pasta do
app", e procurar por um arquivo que nao existe com esse nome e pior do
que nao ter dica nenhuma: parece que nao houve registro.

**`appendLine("(nem falhas.txt nem hs_err, e estava numa call? veja gst.txt, aqui do lado.)")`**

Terceiro caso, e o mais traicoeiro dos tres: morte NATIVA SEM laudo nenhum.
Quando o GLib aborta, ele desliga o relatorio de falhas do Windows antes de
morrer, e como o app nao tem console a mensagem se perde. Foi assim que a call
com o motor novo derrubou o Astra tres vezes sem deixar um bilhete. O gst.txt
existe justamente pra esse caso.

**`object Multi`**

QUAL JANELA SOMOS: a principal, ou uma segunda conta aberta pra teste.

Tres lugares precisam saber disto e precisam CONCORDAR — a trava de instancia única,
a pasta da sessão e o atualizador. Quando cada um lia a flag por conta própria, bastava
um deles enxergar diferente pra sair um caso absurdo: duas janelas com a MESMA conta,
ou a segunda se atualizando por cima da instalação principal.

Le a variável de ambiente ASTRA_MULTI **e** a propriedade -Dastra.multi. A variável é
a que importa hoje: ela é o único canal que atravessa o Astra.exe do jpackage sem
editar o Astra.cfg de dentro da instalação — e era editar o cfg que obrigava a manter
uma CÓPIA inteira do app em disco, cópia que vivia atrasada uma versão. A propriedade
fica pro modo dev (`./gradlew :desktopApp:run -Pastra.multi`).

**`val multi: Boolean get() = Multi.ligado`**

Abre um SEGUNDO Astra na mesma maquina, com sessão propria:
  wscript C:\Astra\launch.vbs 2      (o atalho "Astra (2a conta)")
  ./gradlew :desktopApp:run -Pastra.multi

Por que isto existe: a maioria dos bugs que aparecem aqui e do tipo
"funciona pra quem fez a ação, não funciona pro outro" — canal novo que não
aparecia, presenca congelada, status que não propagava, membro que não
surgia. Nenhum deles e azar: e consequencia de so dar pra testar com UMA
conta. Com duas janelas lado a lado, cada um desses aparece em segundos, na
hora de escrever o codigo, em vez de semanas depois pela boca de um amigo.

**`val existe = runCatching`**

Bind falhou — mas isso NAO prova que ha outro Astra. Firewall, porta tomada
por outro programa ou socket preso do boot anterior dao o mesmo IOException,
e antes o app simplesmente SUMIA nesses casos (um "fecha do nada" perfeito:
sem janela, sem erro, sem log). So sai se alguem de fato atender do outro
lado; sem resposta, seguimos como primaria mesmo sem o lock.

**`WindowsAppId.aplicar()`**

Identidade do processo pro Windows. TEM que vir antes de qualquer coisa
grafica: o Windows carimba a identidade quando o icone de bandeja nasce, e
sem ela o Astra e um app anonimo — o aviso sai sem dono e nao aparece em
Configuracoes > Notificacoes pra ninguem ligar ou desligar.

**`var gateDone by remember { mutableStateOf(!updater.installed || nascerEscondido) }`**

Nascendo escondido, o gate NÃO aparece: ele é uma janela alwaysOnTop, e
pular na frente de quem acabou de ligar o computador é exatamente o que
"abrir minimizado" pediu para não acontecer. Não se perde a atualização —
a ronda logo abaixo continua procurando enquanto o app estiver de pé.

**`BandejaComMenu(…`**

A BANDEJA E SEMPRE CRIADA, e isso nao e detalhe de enfeite: no Windows o
aviso do sistema SAI DO ICONE DA BANDEJA. Sem icone nao existe dono pro aviso,
e o sendNotification vai pro vazio, calado.

Antes ela so nascia quando o X minimizava ("com exitOnClose ligado um icone
seria presenca inutil em segundo plano"). O raciocinio parecia certo e estava
errado pelo meio: com exitOnClose ligado nao ha segundo plano nenhum — o app
encerra no X — e o que aquela condicao desligava de fato era a NOTIFICACAO.
Quem tinha "fechar de vez" marcado nunca recebeu um aviso de mensagem, e o
botao "testar notificação" respondia "mandei — olhe o canto da tela" sobre uma
mensagem que nunca teve por onde sair. Era o caso do dono.

O item "Abrir o Astra" so faz sentido quando ha janela escondida pra reabrir;
com exitOnClose a bandeja fica so com o icone e o "Sair".
MENU DESENHADO PELO ASTRA, e não o do Windows. O `Tray` do Compose usa o
`PopupMenu` do AWT, que é um menu Win32: quem pinta é o sistema, e ele não
aceita cor, fonte, canto nem espaçamento. Era o único pedaço do app que
não parecia o app. Ver BandejaDoAstra.kt.

**`if (voltandoDeAtualizacao)`**

A outra metade do conserto da atualizacao (ver UpdateService): o processo
velho cedeu o direito de ir pra frente, e aqui a janela nova o USA. Sem
este pedido o direito cedido nao move nada — ele autoriza, nao levanta.

O respiro existe porque `toFront` so vale depois de a janela existir de
verdade pro sistema; chamado no mesmo instante da composicao, cai no vazio.

**`setSingletonImageLoaderFactory { ctx ->`**

Coil global: data-URIs (avatares no banco) + URLs relativas /uploads.
+ cache em disco (300MB) pra não rebaixar a mesma imagem toda vez —
vive no cache do SO (fora da instalacao, sobrevive a updates). Coil
faz a eviction LRU sozinho ao passar do teto.

**`.memoryCache`**

TETO do cache de imagens EM MEMORIA. Sem isto o Coil usa a regra
dele (~25% da memoria do app) — num heap de 512MB sao ~128MB so de
bitmap decodificado, e era parte do "Astra incha sozinho". 48MB
segura avatares e previas de sobra; o resto vem do cache em DISCO
abaixo (que não custa RAM).

**`EmblemaDaBarra(window, notifUnread)`**

O círculo com o número, colado no ícone da barra de tarefas. Mora
AQUI e não no ShellScreen porque precisa da janela do AWT, e porque
o emblema tem que sobreviver a qualquer troca de tela lá dentro —
ele é do aplicativo, não de uma página.

**`CompositionLocalProvider(LocalContextMenuRepresentation provides AstraTextContextMenu)`**

RikkaUI e CMP (foundation-only): mesmo tema do mobile, tokens obsidiana.
Reconstruido AQUI (não top-level) pra ler os tokens reativos do Obsidian
-> os componentes RikkaUI recolorem junto quando o tema muda.

O menu de botao-direito dos campos de texto entra AQUI, e não dentro de
cada campo: assim vale pro compositor do chat, pra busca, pro login e pra
qualquer campo futuro de uma vez so.

**`CompositionLocalProvider(…`**

ATIVA = VISIVEL, NAO MINIMIZADA **E COM O APP NA FRENTE**.

O foco entrou aqui porque congelar so o ceu nao bastava: medido, com
o ceu ja parado o app ainda gastava 0,28 nucleo em segundo plano. O
motivo e que ALGUEM continuava pedindo quadro, e cada quadro repinta
a aurora inteira. Quem pedia era o resto do enfeite que le este
mesmo sinal — em especial o pulso do marcador de nao-lida, que e um
relogio POR CANAL nao lido, e o dono tem varios.

Quem mais depende disto (conferido antes de mexer): a estrela de
quem fala na call e a PREVIA da propria transmissao. Os dois so
fazem sentido com alguem olhando, e a previa desligada nao muda nada
do que os outros recebem. O video dos outros nao passa por aqui.

**`LocalJanelaNaTela provides (windowVisible && !state.isMinimized),`**

O MESMO SINAL SEM O FOCO, para o que a pessoa esta de fato
OLHANDO — hoje, a tela que alguem compartilha na chamada.

O foco acima e certo para enfeite e errado para video: com o
Astra numa segunda tela enquanto se trabalha na primeira, a
janela nao tem foco e a transmissao esta sendo vista. Cortar a
imagem ali seria o app estragando o que se pediu para mostrar.

**`Box(Modifier.fillMaxSize())`**

O CEU DA JANELA: aurora + estrelas atrás do login E do shell.
Morava dentro do ShellScreen, e o login pintava a propria aurora
num painel de 45% — como o uv do shader e normalizado pelo
tamanho, eram imagens diferentes e a entrada saltava. Aqui em
cima ela não se mexe quando o conteudo troca: entra-se NO app,
não se troca de tela. E fica um shader so, nunca dois.

**`val transmitindo by Transmitindo.ativo.collectAsState()`**

O ENFEITE PARA QUANDO NINGUEM ESTA OLHANDO — e este e o maior custo
parado do app inteiro, medido.

Com a conversa carregada e nada acontecendo:
    ceu ligado, janela visivel ....... 0,35 nucleo
    ceu desligado, janela visivel .... 0,037 nucleo
    minimizado ....................... 0,047 nucleo
Ou seja: a aurora sozinha custa ~0,31 nucleo o tempo todo, inclusive
com o Astra atras do navegador. O perfil (JFR) mostra onde: 90% das
amostras da thread do skiko estao em Direct3DContextHandler.flush,
esperando a GPU — a janela apresenta a 165Hz (a taxa do monitor)
porque ha sempre um frame novo pedido.

"Nao minimizada" nao e o mesmo que "visivel": o Windows nao para de
entregar frames pra janela coberta.

A POLITICA, decidida pelo dono: NA FRENTE sem teto nenhum — o Astra
usa o processador e a placa que precisar pra tudo ficar liso. ATRAS,
o mais perto de zero possivel. O unico recurso com teto e a RAM.

E o gate de foco que sustenta essa conta: sem ele, "sem teto"
significaria 0,42 nucleo o dia inteiro em segundo plano. Ver
`lembrarFocoDoApp` pra saber por que o sinal vem do Windows e nao do
AWT — as duas tentativas em cima do AWT nao economizaram nada.

So aqui dentro: LocalWindowActive continua significando visibilidade
pro resto (video de call nao pode congelar porque a pessoa clicou
noutra janela do segundo monitor).
E O `LocalRenderPrefs` TEM QUE VIR DAQUI, nao do ShellScreen.

Ele e provido la embaixo, dentro do ShellScreen — e o ceu mora AQUI
EM CIMA, acima daquele provedor na arvore. Resultado: a aurora e as
estrelas sempre leram o valor PADRAO (`RenderPrefs()`), ou seja
3 oitavas e teto de fps ZERO. Na pratica, dois ajustes de
Configuracoes › Desempenho nao faziam nada ha tempo:
  - "qualidade da aurora" (baixa/media/alta) — sempre alta;
  - "teto de FPS" (60/30) — sempre livre, ou seja a taxa do monitor.
O dono estava com aurora em "baixa" e o app desenhava em alta.

O ceu subiu pro Main quando o login e o shell passaram a dividir o
mesmo ceu; o provedor ficou pra tras, e como CompositionLocal cai no
default em silencio, nada quebrou visivelmente — so parou de obedecer.
O mesmo vale pro ceu e pro login, que vivem acima do ShellScreen:
fora da frente, "reduzir movimento" ligado. Ver o comentario longo
no provedor equivalente do ShellScreen.

E TRANSMITINDO O CEU SAI DA FRENTE. O dono relatou a transmissao
caindo de 47 pra 35 fps na 0.2.18 — a versao em que o teto de fps do
ceu saiu e ele voltou a rodar na taxa do monitor (165Hz). Neste
notebook a MESMA placa integrada desenha a tela, captura os quadros e
comprime; um shader de tela cheia a 165Hz disputa exatamente com o
compressor. A transmissao e o produto, o fundo e enfeite — enquanto
uma esta no ar, o outro espera.

**`Crossfade(…`**

Entrar no app = os paineis do login se dissolverem e os do
shell aparecerem SOBRE o mesmo ceu, que não se mexe. Por
isso Crossfade e não slide: o ceu ancora as duas telas, e
qualquer deslocamento denunciaria que são telas diferentes.

**`onTestarAviso =`**

Permitir "Avisos" = MANDAR um aviso: e o
unico jeito de o Windows registrar o app.
Pelo caminho de verdade (bandeja do SO) —
um toast desenhado dentro do app não
registraria nada.

**`windowInactive =`**

Toast da bandeja so quando o app não esta na frente.
FOCO conta: antes exigia minimizado/bandeja, entao
com o Astra aberto atras de outra janela — o caso
comum — não vinha aviso nenhum.

**`if (ModoTransmissao.ativo.value) return@ShellScreen`**

Em transmissão, o som não toca: ele
entra no áudio da gravação igual, e
"chegou mensagem agora" é informação
sobre você mesmo sem texto nenhum.

**`tocarAvisoDeMensagem()`**

Som JUNTO do aviso da bandeja, no mesmo
funil: quem decide QUANDO avisar é o
ShellScreen (só com a janela fora de
foco), e o som não pode ter uma regra
própria — tocar sem o aviso na tela, ou
com o app na frente, seria barulho sem
referente.

**`authRepo.logout(escopoDaJanela)`**

O escopo e o da JANELA, e nao o do shell: o
shell sai da composicao no instante em que a
sessao vira nula, e um escopo morto
cancelaria o aviso de logout antes de ele sair.

**`notifUnread = 0`**

O emblema da barra mora na JANELA, que sobrevive
ao logout — sem zerar aqui, o círculo ficaria
grudado no ícone anunciando mensagens de uma
conta que já saiu, e ninguém teria como apagá-lo
a não ser fechando o Astra.

**`}`**

Banner de update (topo): lembrete quando adiado ("depois")
ou achado na checagem manual — conduz o mesmo mini-fluxo.
O aviso saiu do canto inferior direito e virou o ponto na
barra-titulo (ver TitleBar.PontoDeAtualizacao). Manter os
dois seria a mesma coisa dita em dois cantos da tela.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ModoTransmissao.kt`

**`object ModoTransmissao`**

MODO TRANSMISSÃO.

Ligado, o Astra para de colocar coisa sua na tela enquanto ela está sendo vista
por outras pessoas:

  1. o aviso da bandeja perde nome e texto (só o tipo);
  2. o som de aviso não toca — som entra no áudio da transmissão igual;
  3. o e-mail some da aba Conta.

Os três são o mesmo problema: coisas que aparecem POR CIMA do que está sendo
gravado, ou dentro dele, sem você ter mandado aparecer naquele instante.

---------------------------------------------------------------------------
A DETECÇÃO É OPCIONAL E OPT-IN, e por um motivo que vale escrever: para saber
que o OBS está aberto é preciso olhar a LISTA DE PROCESSOS da máquina — uma
leitura mais ampla que a do "o que estou usando", que só olha a janela da
frente. Então ela só acontece se você pedir, e o que se faz com ela é
estritamente comparar nomes de executável com a lista fixa abaixo.

Nada disso sai da máquina: o resultado é um booleano que nem o servidor vê. E
vale a regra de sempre do Astra — TÍTULO DE JANELA NÃO É LIDO, nem aqui.
---------------------------------------------------------------------------

**`private val PROGRAMAS = setOf(…`**

Nome de executável, minúsculo. Lista curta de propósito: cada nome aqui é um
programa que a pessoa abre para transmitir, e não um que só *pode* gravar.
Incluir gravador genérico faria o modo ligar sozinho no meio de um dia
comum, que é o jeito mais rápido de alguém desligar o recurso para sempre.

**`scope.launch`**

Um laço para o estado e outro para a varredura. Separados porque o
primeiro precisa reagir NA HORA ao interruptor (ligar o modo à mão não
pode esperar os 12s da próxima varredura), e o segundo é lento de
propósito.

**`private fun algumProgramaAberto(): Boolean = runCatching`**

ProcessHandle e não JNA: a JVM já expõe a lista, e o que se lê aqui é só o
caminho do executável. Falha (permissão, processo que morreu no meio da
volta) vira `false` — a detecção é uma conveniência, e conveniência não
derruba nada quando não funciona.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/net/AtividadePublicador.kt`

**`private const val ESPIA_MS = 5_000L`**

"O que a pessoa esta usando agora" — a metade que decide o que sai daqui.

DESLIGADO, ELE NEM OLHA. O laco confere a preferencia antes de tocar no Win32:
nao existe caminho em que o app leia o programa em primeiro plano e descarte
depois. "Desligado" tem que significar que a leitura nao aconteceu, senao a
promessa vale s o enquanto ninguem le o codigo.

**`private const val LEITURAS_PRA_VALER = 2`**

SO PUBLICA O QUE FICOU. Um alt-tab rapido pelo Discord, pelo explorador de
arquivos e de volta pro jogo nao e uma mudanca de atividade — e o caminho ate
ela. Sem esta espera, a sua linha piscaria tres vezes em cinco segundos pra
todo mundo que estivesse olhando, e o que ela dissesse seria falso nas tres.

Duas leituras iguais = 10s parado no mesmo programa.

**`private const val RENOVA_MS = 45_000L`**

O servidor guarda com 60s de vida. Reenviar a cada 45s renova antes de expirar,
com folga pra uma rodada perdida — se o envio falhar uma vez, a proxima chega
antes de a linha sumir. Renovar a cada 5s (junto com a espiada) seria doze vezes
mais trafego pra dizer a mesma coisa.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/net/AuthedClient.kt`

**`class DesktopTokenAuthenticator(…`**

401 -> renova com o refresh token e repete a request. SINGLE-FLIGHT (mesma
logica do TokenRefresher do Android): o boot dispara varias chamadas em
paralelo e o refresh e single-use no backend — sem o lock, todas tentavam
rotacionar o MESMO token, so a primeira vencia e as outras matavam a sessão
(bug do "so o nome do usuário carrega").

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/net/DesktopSocket.kt`

**`class DesktopSocket(…`**

Socket.io do desktop — versão enxuta do SocketManager do Android (mesma lib
Java, mesmo protocolo do backend socket.ts). Chat: new_message/new_dm + salas
join_channel/join_dm. Acoes: message_edited/message_deleted/reaction_update/
dm_deleted. Typing: user_typing/dm_user_typing (so chega pra quem esta na
sala). Unread: channel_activity (global via sala pessoal). Presenca depois.

**`private val _activityUpdate = MutableSharedFlow<String>(extraBufferCapacity = 128)`**

"O que a pessoa está usando" mudou. Ao vivo e não por poll: a atividade troca
quando alguém abre outro programa, que é um instante — descobrir dois minutos
depois mostraria o jogo anterior, e informação de presença atrasada é pior que
ausente. `activity` nulo = a pessoa parou de mostrar.

**`private val _missaoConcluida = MutableSharedFlow<String>(extraBufferCapacity = 16)`**

Fechei uma missao. Separado do xp_gain porque sao coisas diferentes na tela: o
XP move o anel em silencio, a missao aparece com nome e recompensa. Quem fecha
as tres do dia recebe quatro eventos seguidos (as tres + o bonus) — a fila do
aviso e que resolve mostrar um de cada vez.

**`private val _reconnected = MutableSharedFlow<Long>(extraBufferCapacity = 8)`**

Reconectou. Enquanto o socket esteve fora, TUDO que aconteceu se perdeu —
evento e dispare-e-esqueça. Quem escuta isto refaz o que precisa estar
certo (mensagens da órbita aberta, lista de canais, membros). Sem isto,
uma queda de 10s deixava a tela mentindo ate o próximo boot.

**`private val recent = ArrayDeque<Pair<Long, String>>()`**

---- Diagnostico (Configuracoes > Diagnostico) ----
Ultimos eventos RECEBIDOS, so nome + hora. Quando alguem diz "não apareceu
pra mim", isto responde na hora se o aviso chegou e o app ignorou, ou se
nunca chegou — que sao problemas em pontas opostas do sistema. Sem isto a
unica saida e adivinhar. NAO guarda conteudo: e diagnostico, não espionagem.

**`private val _sinalRtc = MutableSharedFlow<String>(extraBufferCapacity = 128)`**

SINALIZAÇÃO DA CALL PONTO A PONTO.

Só os envelopes do aperto de mão passam por aqui — oferta, resposta e
candidatos de rede, uns poucos KB por chamada. A voz em si nunca toca o
servidor: vai direto de uma máquina para a outra.

**`@Volatile private var querConectar = false`**

================= CONEXAO =================

POR QUE ISTO E MAIS COMPLICADO QUE UM `IO.socket().connect()`:

O access token vale 15 MINUTOS. O app quase sempre reabre depois disso, ou
seja: o token que esta no disco chega VENCIDO no aperto de mao. O servidor
recusa no middleware (INVALID_TOKEN) e — verificado no bytecode do
socket.io-client 2.1.0 — o cliente Java chama destroy() no socket ANTES de
emitir connect_error. Socket destruido não retenta. Nunca.

Resultado: o socket morria pra sempre no boot enquanto TODO o resto do app
continuava funcionando (o OkHttp renova sozinho no 401). Dai o sintoma
esquisito de "abre, tudo carrega, mas nada chega ao vivo" — e reabrir o app
não resolvia, porque o token do disco continuava vencido.

Pior: a renovacao no EVENT_RECONNECT_ATTEMPT era CODIGO MORTO. O construtor
do Socket copia opts.auth pra um campo proprio (`this.auth = opts.auth`),
entao trocar opts.auth depois nunca chegava no aperto de mao.

Conserto: a retentativa e NOSSA (reconnection = false), e TODA tentativa
comeca garantindo um token valido. Um relogio de 5s cuida de tudo — token
vencido, servidor dormindo (Render free dorme em 15min), rede caida — com
recuo progressivo pra não martelar.

**`private suspend fun tokenValido(): String?`**

Token VALIDO na mão antes de tentar o aperto de mão.
A renovacao passa pelo mesmo caminho do HTTP (uma chamada autenticada barata
toma o 401 e o authenticator rotaciona sob lock). Ter um segundo renovador
aqui seria pior que o bug: o refresh token e de uso unico, os dois brigariam
por ele e a sessão morreria de vez.

**`if (jaConectou) _reconnected.tryEmit(System.currentTimeMillis())`**

presenca viva já no connect (o timer refresca depois)
So a partir da SEGUNDA conexao. Na primeira, as telas acabaram de
carregar sozinhas — avisar aqui so repetiria as mesmas buscas no
pior momento possivel (boot, com o servidor free ainda acordando).

**`s.on("soundboard_play") { args ->`**

Soundboard: toca DIRETO daqui, sem passar pela UI. O som e um efeito da
call, nao um estado de tela — mandar isso subir ate um ViewModel pra
descer de novo so adiaria o audio e criaria uma dependencia entre tocar
som e ter a tela da call composta.

**`private fun ligarRelogio()`**

Um relogio so, duas funcoes:

1. Batida de presenca (a cada 25s). Mantem a chave viva no Redis (TTL 60s).
   Sem ela o usuário aparece OFFLINE pros outros em 1 minuto — era a
   "presenca atrasada". 25s da folga de 2 batidas dentro do TTL.
2. Vigia da conexao (a cada 5s). Caiu? tenta de novo, respeitando o recuo.
   E a rede de seguranca que garante que NENHUM caminho de falha deixa o
   socket morto pra sempre — inclusive os que eu não previ.

**`fun fastSendText(…`**

Envio rapido de texto puro por socket (com ack) em vez de POST HTTP. O
backend insere, faz broadcast do new_message (com o clientNonce) e responde
o ack — a UI mostra a mensagem na hora e reconcilia quando o broadcast volta.
So texto puro em canal: reply e anexo continuam no HTTP (o handler não os le).

**`fun sendBotCommand(channelId: String, serverId: String, content: String): Boolean`**

Chama a bot. NAO e uma mensagem: o backend nao guarda o comando, so responde
— igual a barra do Discord, onde o que voce digitou some e so a resposta
fica. Sem socket, devolve false pra quem chamou avisar em vez de o comando
sumir no vazio.

**`fun registrarDespedida()`**

TCHAU EXPLICITO AO FECHAR O APP.

O backend so marca OFFLINE quando o socket cai, e ele descobre isso de dois
jeitos com custos MUITO diferentes: fechamento limpo (frame de close) e na
hora; queda abrupta e so pelo relogio — pingInterval 25s + pingTimeout 20s,
ou seja, ate ~45 segundos de fantasma online.

Sair do app nunca mandava esse frame: exitApplication/exitProcess derrubam a
JVM e o socket morre junto, sem despedida. Por isso "fechei e continuo online
por vários segundos" — nao era lentidao de rede, era ninguem avisar.

Registrado como shutdown hook pra cobrir TODAS as saidas de uma vez: o X, o
"Sair" da bandeja e o exitProcess do atualizador. Nao cobre kill -9, e nao
tem como cobrir — nesse caso o relogio do servidor volta a ser a rede de
seguranca, que e exatamente pra isso que ele existe.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/net/Insistencia.kt`

**`private val ESPERAS_MS = longArrayOf(…`**

POLITICA UNICA DE REPETICAO do app.

Existia uma copia disto no ChatVm e outra no ShellVm, e as duas erravam a MESMA
conta: tres tentativas com espera de 1,5s e 4s. Isso da 5,5 segundos de janela —
contra um servidor que, no plano free do Render, dorme depois de 15min parado e
acorda em ATE ~50 SEGUNDOS. As tres tentativas queimavam nos primeiros seis
segundos do sono, a tela cravava "não foi possível carregar" e ninguem tentava
de novo. O servidor acordava meio minuto depois, sozinho, sem plateia.

A janela agora cobre o sono inteiro. E a espera cresce rapido de proposito:
insistir de segundo em segundo contra um servidor que ainda esta subindo so
gasta tentativa e enche o log dele.
A janela cobria 72s, o suficiente pro SONO (que acorda em ate ~50s). Nao cobria
o DEPLOY: publicar no Render free tira a instancia do ar por 2 a 5 minutos, e
nesse intervalo as sete tentativas queimavam e a tela cravava erro — inclusive
erro de PARSE, porque durante a troca o roteador devolve pagina de HTML no lugar
do JSON. Agora vai ate ~3 minutos.

O preco assumido: um erro que nao melhora esperando (resposta que o app nao sabe
ler por bug de verdade) demora tres minutos pra aparecer na tela em vez de um.
Vale — quem esta esperando ve "o servidor está acordando" o tempo todo, e a
alternativa e mandar a pessoa recarregar do lado de fora de uma falha temporaria.

**`data class Falha(val motivo: String, val permanente: Boolean)`**

Por que a chamada falhou, do jeito que a tela pode dizer em voz alta.

`permanente` e a parte que faltava: repetir sete vezes um 403 nao muda o 403 —
so faz a pessoa esperar 72 segundos por uma resposta que o servidor ja tinha
dado na primeira. Erro permanente sai do laco na hora, com o motivo real.

**`is IOException -> Falha(…`**

Timeout, DNS, conexao recusada, 502/503 do roteador do Render enquanto a
instancia sobe — tudo isto passa. E o caso que a insistencia existe pra cobrir.

O MOTIVO CRU VAI JUNTO. Sem ele, "sem conexão" cobre timeout, DNS quebrado,
certificado recusado e proxy do trabalho com a mesma frase — e foi por isso
que a primeira investigacao desta tela terminou em palpite. Fica entre
parenteses e em minuscula: informacao pra quem for consertar, nao susto pra
quem so queria conversar.

**`is SerializationException -> Falha(…`**

RESPOSTA ILEGIVEL. Quase sempre nao e bug de contrato: e o roteador do Render
devolvendo uma pagina de HTML enquanto a instancia troca de versao, com status
200. O parser reclama de JSON malformado e a tela dizia
"(JsonDecodingException)" — nome de classe Java na cara de quem so queria
conversar, e ainda por cima acusando o app de um problema que e do servidor.

Nao e permanente: insistir e exatamente o certo aqui, porque em um minuto a
instancia nova responde JSON de verdade.

**`suspend fun <T> insistir(…`**

Insiste ate conseguir, ate esbarrar num erro permanente, ou ate acabar a janela.

`aoTentarDeNovo` recebe o numero da PROXIMA tentativa (2..N) antes de cada
espera: e por ele que a tela troca o vermelho de "falhou" pelo texto honesto de
"o servidor está acordando". Sem isso a espera longa vira tela travada, que e
pior que o erro rapido que ela veio consertar.

**`if (t is SerializationException && ++ilegiveis >= 2)`**

RESPOSTA ILEGIVEL DUAS VEZES NAO E REINICIO, E CONTRATO QUEBRADO.

Insistir aqui foi o que escondeu um defeito real por versoes a fio: o
historico do canal vinha com um campo em formato diferente do que o app
sabia ler, o parser falhava IDENTICO nas nove tentativas, e a tela
passava tres minutos dizendo "o servidor está acordando" com o servidor
no ar respondendo em 200ms. Duas tentativas ainda cobrem o caso legitimo
(o roteador devolvendo HTML durante uma troca de versao); da terceira em
diante, o problema nao vai melhorar sozinho e a pessoa merece saber.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/net/RedeLog.kt`

**`object RedeLog`**

O QUE DE FATO FALHOU, e nao o que a tela teve coragem de dizer.

A tela diz "o servidor está acordando" pra qualquer falha que possa melhorar
esperando: tempo esgotado, 5xx, resposta ilegivel, conexao recusada. Do lado de
quem olha, as quatro sao a mesma frase — e foi assim que uma conversa que nao
carregava passou por "a hospedagem dormiu" durante dias, sem ninguem poder
distinguir de um 500 na consulta ou de um campo novo que o app nao sabe ler.

Aqui fica o motivo cru. So classe e mensagem: nada de cabecalho, nada de token,
nada de corpo de resposta — este arquivo pode ser mandado pra alguem olhar.

**`fun imagemMorreu(url: String)`**

IMAGEM QUE NAO CARREGOU — avatar, banner, icone, anexo.

O app ja sabia disso e guardava so na memoria, para nao repetir a requisicao
condenada (ver `urlsMortas` em Bits.kt). Guardar sem registrar resolve o desperdicio
e esconde a causa: de fora, "a foto nao aparece" e uma tela sem foto, e nada
distingue arquivo que sumiu do servidor de rede que caiu de URL malformada.

A URL VAI INTEIRA, e e o ponto: e ela que diz de onde a imagem deveria ter vindo.
`/uploads/...` significa arquivo no disco da instancia — que no Render nao sobrevive
a um reinicio. Um endereco do bucket significa outra coisa. Sao dois defeitos com o
mesmo sintoma, e o prefixo separa os dois sem precisar de mais nada.

URL de imagem do proprio app nao carrega segredo: nao ha token nela, o nome do
arquivo e aleatorio, e o arquivo e publico por natureza. Este registro continua
podendo ser mandado para alguem olhar.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/net/Servidor.kt`

**`object Servidor`**

A HOSPEDAGEM DORME, e ate agora o app nao contava isso pra ninguem.

O plano gratuito do Render desliga a instancia depois de 15 minutos sem nenhuma
requisicao e religa na proxima -- e religar leva perto de um minuto. Do lado de ca
isso aparecia como o Astra inteiro parado: sem erro, sem barra, sem explicacao. Os
timeouts do OkHttp ja eram folgados o bastante pra sobreviver (60s de connect), entao
o app SEMPRE acabava entrando; so que durante esse minuto a unica leitura possivel pra
quem esta olhando e "travou".

Este objeto nao acelera nada. A espera e exatamente a mesma. Ele so pergunta ao
/health -- que nao precisa de conta -- e deixa a tela dizer o que esta havendo.
Espera explicada e espera; espera calada e defeito.

**`private val cliente by lazy`**

Cliente PROPRIO e curto de proposito. O cliente do app espera 60s no connect, e
esperar 60s pra so entao avisar "estou esperando" derrota o proposito inteiro:
aqui o que importa e a PRIMEIRA falha, nao a ultima. Teto de 8s por tentativa,
sem interceptador nenhum -- esta chamada nao leva token e nao precisa de identidade.

**`private val url = AstraShared.BASE_URL.trimEnd('/') + "/health"`**

ATENCAO ao montar esta URL: em http:// a Cloudflare que fica na frente do Render
responde 301 SOZINHA, na borda, sem encostar na instancia. Um vigia apontado pro
http ve "301, tudo certo" pra sempre enquanto o servidor dorme do outro lado --
foi essa a armadilha que fez um cron externo jurar que estava funcionando. https,
sempre.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/Placas.kt`

**`object Placas`**

AS PLACAS DE VIDEO DA MAQUINA, e qual delas desenha a tela.

POR QUE ISTO PRECISA EXISTIR: notebook com duas placas e o caso comum, e as duas nao
sao intercambiaveis. O quadro de uma captura de tela NASCE no aparelho D3D11 da placa
que desenha o monitor, e um encoder so consegue ler textura da propria placa. Pedir pra
outra comprimir aquele quadro nao da erro -- da SILENCIO: um quadro entra, nenhum sai.
Foi assim que a transmissao deste aplicativo ficou muda por versoes seguidas.

Entao "qual placa desenha a tela" nao e curiosidade: e o que decide quem pode comprimir.

PELO WINDOWS, e nao pelo GStreamer. Daria pra descobrir isto pelos nomes dos elementos
do GStreamer, mas ai a tela de configuracoes so funcionaria depois de baixar o pacote de
video -- e a pergunta "que placas eu tenho?" nao depende de nada disso. O
`EnumDisplayDevices` responde na hora, sem carregar biblioteca nenhuma.

**`private fun ehDedicada(id: String): Boolean = marcaDe(id) != "Intel"`**

Pelo codigo de fabricante do PCI, e nao pelo nome escrito. Nome de placa muda com o
driver e vem traduzido em alguns idiomas; 0x8086 e a Intel desde sempre.

A Intel entra como integrada e as outras como dedicadas. E heuristica -- existe Intel
Arc dedicada e existe AMD integrada em APU -- mas ela so decide a PALAVRA que aparece
ao lado do nome. Quem decide se a placa serve pra transmitir e `desenhaATela`, que e
medido, nao adivinhado.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/prefs/AvisosDaConta.kt`

**`class AvisosDaConta(private val api: NotificationApi)`**

OS AVISOS QUE PERTENCEM À CONTA, não a este computador.

Por que isto existe como objeto único em vez de viver dentro da tela de
configurações: o horário de descanso e o "não perturbe" precisam ser consultados
no instante em que um sussurro chega — e nessa hora a tela de configurações não
está aberta. Guardar num `single` do Koin é o que permite o balão da bandeja
perguntar "posso tocar?" sem depender de nenhuma tela estar composta.

O QUE ESTE ARQUIVO CONSERTA, e é um bug que estava calado: o servidor manda
`silent: true` no evento `notification` quando você está em não-perturbe ou
dentro do descanso, e **o desktop nunca leu esse campo**. Resultado: você punha
o status em "não perturbe" e o balão do Windows pulava do mesmo jeito, às três
da manhã inclusive. O recurso existia inteiro no servidor e não tinha ninguém
escutando do lado de cá.

Só que o `silent` viaja no evento ERRADO pra resolver isso: o balão da bandeja
nasce de `new_dm`/`channel_activity`, não de `notification`. Esperar o
`notification` pra decidir se o balão toca criaria dependência de ordem entre
dois eventos que o servidor emite em caminhos diferentes. Por isso a decisão é
LOCAL: a regra do descanso é reescrita aqui e comparada com o relógio da
máquina.

**`suspend fun salvar(novo: AvisosDaContaDto): Result<Unit>`**

OTIMISTA COM DESFAZER. O interruptor vira no clique porque a API mora no
Render, que dorme: esperar a ida e volta faria o toggle parecer travado por
segundos, e a pessoa clicaria de novo.

Mas o otimismo tem volta: se a chamada falhar, o estado retorna ao anterior.
Deixar ligado o que o servidor recusou é a pior das três opções — a tela
afirmaria uma configuração que não existe, e o erro só apareceria semanas
depois, na forma de "não recebi seu aviso".

No sucesso vale o que o SERVIDOR devolveu, não o que se pediu.

**`fun emDescanso(agora: LocalTime = LocalTime.now()): Boolean`**

ESPELHO EXATO do `isInQuietHours` em apps/api/src/lib/notifications.ts.
Divergir aqui produz o pior tipo de defeito: o servidor cala o push e o
desktop continua tocando, ou o contrário — e nos dois casos a pessoa vê o
app desobedecer uma configuração que ela mesma ligou.

O caso que atravessa a meia-noite (23h → 7h) é o NORMAL, não a exceção: é a
madrugada, que é justamente o que alguém quer calar. Por isso o `s > e` não
é tratado como entrada inválida.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/prefs/DesktopPrefs.kt`

**— sobre o arquivo inteiro —**

O total de RAM FISICA mora no OperatingSystemMXBean do `com.sun.management`, e nao
no do `java.lang.management`. E o mesmo modulo (`jdk.management`) que ja precisa
estar no empacotamento por causa da medicao de custo da transmissao — sem ele isto
compila e explode so no app empacotado.

**`enum class ScreenQuality(…`**

Presets da transmissão de tela (Settings > Voz). SO 720p, por decisao de perf: o
encoder H264 do webrtc-java e por SOFTWARE (sem HW/NVENC) e 1080p não chega nem a
30fps na CPU — entao foco total em 720p, priorizando fluidez. Default = 720p60.
bitrate em bits/s. Aplica ao INICIAR a transmissão. (Chaves antigas de 1080p caem
no default via from() — quem tinha 1080p salvo sobe pro 720p60 suportado.)

**`TINY_540_30("t54030", "540p 30fps — economica", 960, 540, 30, 1_200_000);`**

O degrau pra maquina fraca. Existe porque o custo do H264 por SOFTWARE e
praticamente constante em NUCLEOS: o mesmo encoder que ocupa 8% de um PC forte
ocupa mais da metade de um de quatro nucleos. 720p30 ja e metade do trabalho de
720p60; 540p30 tira mais 44% dos pixels em cima disso.

960x540 e exatamente metade de 1080p em cada eixo, entao a reducao cai em
limite de pixel inteiro, e os dois lados sao pares (o I420 exige, porque o
croma anda de dois em dois).

**`fun padraoDaMaquina(): ScreenQuality = when (Runtime.getRuntime().availableProcessors())`**

O preset de estreia sai da MAQUINA, e nao de um valor fixo.

Todo mundo comecava em 720p60 e so descia depois de sofrer — e quem tem PC
fraco costuma ser exatamente quem nao sabe que existe uma tela de
configuracao pra mexer. A primeira transmissao dele era a ruim.

O corte e por processador logico porque o encoder H264 por SOFTWARE custa
~1,25 nucleo em 720p60, medido. Numa maquina de 4 threads isso e um terco do
computador so pra codificar, com o jogo, o navegador e o proprio Astra
disputando o resto. A escolha continua sendo do dono: isto e so o ponto de
partida de quem nunca escolheu.

**`class DesktopPrefs(private val store: SessionStore)`**

Preferencias LOCAIS do desktop (não vao pro backend): movimento, toasts da
bandeja e agora DESEMPENHO/GRAFICOS. Persistem no ui.properties (mesmo arquivo
da última selecao, que sobrevive a logout). StateFlow pra UI e shell reagirem
na hora que muda.

**`val atividadeVisivel: Boolean = false,`**

Privacidade: mostrar aos outros o programa em primeiro plano.
NASCE DESLIGADO, e isso e a decisao mais importante do recurso. Recurso
que conta o que voce esta fazendo tem que ser um ato seu — ligado por
padrao ele seria uma coisa que aconteceu com voce, mesmo com interruptor
disponivel, porque quase ninguem visita a tela de configuracoes.

**`val auroraEnabled: Boolean = false,`**

DESLIGADAS por padrao (decisao do dono): o Astra abre com fundo liso, e
aurora/estrelas viram uma escolha em Aparencia > Fundo. Fundo animado
como padrao e uma opiniao forte cobrada de quem nunca pediu — e a conta
vem em GPU numa maquina que a gente nao conhece.

**`val uiFps: UiFps = UiFps.FREE,`**

LIVRE, por decisao do dono: com o Astra na frente ele usa o processador e a
placa que precisar; o unico recurso com teto e a RAM. Quem paga a conta do
segundo plano e o gate de foco (Main.kt), nao um teto de fps. O ajuste continua
em Configuracoes > Desempenho pra quem tiver maquina apertada.

**`val placaVideo: String = "",`**

Placa de video preferida, pelo id de PCI (ver `Placas`). Vazio = automatico,
que e o certo pra quase todo mundo: o Astra usa a placa que desenha a tela,
que e a unica que consegue comprimir a captura dela.

A parte do VIDEO vale na proxima transmissao; a parte da INTERFACE so no
proximo arranque, porque o Skiko le essa escolha uma vez, ao criar a janela.

**`val motorNovo: Boolean = false,`**

Motor de video novo (GStreamer publicando direto da placa). DESLIGADO por
padrao ate rodar em call de verdade: ele troca TUDO o que sai (microfone
inclusive), e um defeito aqui nao aparece como tela preta -- aparece como
ninguem te ouvindo. Ligado, ainda cai sozinho pro caminho de sempre se faltar
o pacote ou o encoder de hardware.

**`val avisoDiscreto: Boolean = false,`**

Processamento do microfone (aplica ao ENTRAR na próxima sala de voz).
Aviso SEM conteudo: nem quem escreveu, nem o que escreveu. Existe pra quem
transmite a tela -- o aviso da bandeja aparece POR CIMA de tudo, inclusive
do que esta sendo gravado.

**`val emojiRecentes: List<String> = emptyList(),`**

Emojis usados por ultimo, do mais recente pro mais antigo. Local e nao no
backend de proposito: e preferencia de MAQUINA (o teclado que voce usa
aqui), nao de conta — e sincronizar isso custaria uma escrita no servidor
a cada emoji clicado.

**`private fun aferirAMaquina()`**

O ASTRA SE AJUSTA À MÁQUINA NA PRIMEIRA ABERTURA.

Não existe "computador fraco" no abstrato: existem memória e núcleos, e são
esses dois que decidem se um app que anima vai caber. Quem tem uma máquina
apertada não vai procurar Configurações › Desempenho — vai achar que o Astra é
pesado e desistir. Então ele começa econômico e DIZ que fez isso.

Roda UMA vez por instalação, e nunca por cima de escolha feita: se a chave
`performanceMode` já existe, alguém já decidiu e a decisão é dela.

O que fica gravado é o MOTIVO, não uma bandeira: é ele que deixa o aviso dizer
"3,9 GB de memória" em vez de "achamos melhor assim". Aviso que mostra a medida
se defende sozinho; aviso que só afirma vira desconfiança.

**`private fun motivoParaEconomizar(): String?`**

5 GB e não 4 porque uma máquina de 8 GB reporta ~7,9 e uma de 4 reporta ~3,9:
o corte precisa cair no vão entre as duas. E o Windows sozinho já leva ~2 GB,
então em 4 GB o que sobra é dividido com o navegador, o jogo e o resto — o
Astra não pode ser quem fecha essa conta.

**`private fun migrarCeu()`**

AJUSTE QUE NUNCA VALEU NAO E ESCOLHA — e por isso este remendo de uma vez so.

O `LocalRenderPrefs` era provido dentro do ShellScreen, e o ceu (aurora + estrelas)
mora ACIMA dele na arvore desde que o login e o shell passaram a dividir o mesmo
fundo. CompositionLocal que nao acha provedor cai no default em silencio: nada
quebrou visivelmente, os dois ajustes so pararam de obedecer. Por tempo indefinido
a aurora desenhou em ALTA e sem teto de fps, qualquer que fosse o que estivesse
marcado na tela de Desempenho.

Consertado o provedor, o valor gravado passaria a valer DE REPENTE — e o dono
veria o fundo mudar de aparencia sozinho, por uma escolha que ele fez uma vez, sem
efeito nenhum, e que portanto nunca foi testada por ele. Entao os dois voltam pro
padrao bom uma unica vez, marcado por `ceuMigrado`. A partir daqui a tela manda.

Os dois voltam pro padrao: aurora ALTA (a que o dono sempre viu, porque era o
default que vinha valendo) e fps LIVRE — na frente o Astra nao tem teto de
processador nem de placa, so de RAM. O que segura o custo em segundo plano e o
gate de foco no Main.kt, e nao um teto que valeria tambem com o app na frente.
A marca e VERSIONADA porque esta migracao ja rodou uma vez com outro alvo: a 0.2.17
pos os fps em 60, e a 0.2.18 mudou a politica pra "sem teto na frente, gate de foco
atras". Marca booleana teria deixado quem instalou a 0.2.17 preso nos 60 pra sempre.

**`private fun read() = Prefs(…`**

Ausente = default (toasts ligados; reduceMotion/perfMode desligados; aurora
e estrelas DESLIGADAS; qualidade media; fps livre; janela translucida).

Repare na polaridade de aurora/estrelas: e `== "1"`, e nao `!= "0"`. A
diferenca importa pra quem ja usa o Astra — so quem LIGOU de proposito tem
"1" gravado e continua com o ceu; quem nunca abriu as configs passa a ver o
fundo liso. E o que o dono pediu, e sem apagar escolha de ninguem.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/prefs/TemaDaConta.kt`

**`class TemaDaConta(…`**

O TEMA SEGUE A PESSOA, e não a máquina.

O site já guardava `{ accent, bg }` em /api/profile/preferences desde sempre; o
desktop nunca leu nem escreveu. Resultado: escolher Nebulosa no site e abrir o
Astra dava Obsidiana, e máquina nova nascia no padrão de fábrica mesmo com a
conta tendo um tema há meses.

A CONTA MANDA AO ENTRAR (escolha do dono). Trocar de tema aqui sobe pra conta;
entrar adota o que a conta tem. É o mesmo contrato do site, então os dois
convergem sozinhos em vez de brigar.

O QUE **NÃO** VIAJA, e é decisão e não esquecimento: tamanho de fonte,
densidade, GPU, teto de FPS, voz. Essas dependem do monitor e da máquina — o
que é confortável num notebook de 13" é grande demais numa TV, e sincronizar
pioraria as duas pontas.

**`prefs.state`**

A ORDEM AQUI É O QUE FAZ A COISA FUNCIONAR. Só se observa mudança
DEPOIS de adotar: um coletor ligado antes veria o tema local de
partida, empurraria ele pra conta e apagaria a escolha feita no site —
exatamente o contrário do que "a conta manda" quer dizer.

**`runCatching { api.salvarPreferencias(PreferenciasRequest(nova)) }`**

Falha de rede é silêncio de propósito: isto é conveniência, não um
salvamento que a pessoa pediu. O tema JÁ está aplicado e guardado
localmente — um erro na tela por causa da cópia remota seria alarme
sobre algo que não quebrou nada.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/profile/AvatarPicker.kt`

**`object AvatarPicker`**

Avatar do Astra NAO usa o endpoint de upload: vive como DATA-URI na coluna
avatarUrl (mesmo padrao do mobile e do web; o Coil do desktop já resolve
data-uri, ver Main.kt). O backend recusa acima de 10MB, entao reduzimos pra
AVATAR_DIM antes de codificar. GIF pequeno passa CRU pra não matar a animação.

**`private const val AVATAR_DIM = 1024`**

Mesmos numeros do ImageCrop, e pelo mesmo motivo: o dobro do que a tela pede,
pra cobrir monitor a 150%/200% sem AMPLIAR na hora de desenhar. Este caminho
e o do GIF animado (que nao passa pelo recorte), entao ele tambem precisa
chegar na resolucao certa — deixar so o recorte subir faria a foto parada
sair nitida e a animada sair borrada.

**`data class Imagem(val dataUri: String, val largura: Int, val altura: Int)`**

A imagem pronta pra virar avatar/banner, COM as medidas dela.

As medidas existem porque quem envia um banner precisa saber a proporcao pra
calcular o zoom que enche a faixa. Elas ja estavam em maos aqui dentro e eram
jogadas fora; recuperar depois exigiria decodificar o data-uri de novo.
largura/altura = 0 quando o gif passa cru (nao decodificamos).

**`fun zoomQueCobre(largura: Int, altura: Int, aspectoDaFaixa: Float): Int`**

Zoom que faz a imagem COBRIR uma faixa de proporcao `aspectoDaFaixa`, em
porcento, pronto pro bannerScale.

Ele existe porque o banner desenha com ContentScale.Fit: "caber inteira" numa
faixa 3,5:1 quer dizer encolher ate a ALTURA caber, e uma foto 16:9 chega
ocupando pouco mais da metade da largura, com tarja preta dos dois lados. Era
isso o "banner fica pequeno". O fator e a razao entre cobrir e caber.

Capado em ZOOM_MAX_BANNER: uma imagem muito alta (um print de celular em pe)
pediria 600% pra cobrir, e a 600% ninguem reconhece o que esta vendo.

**`private fun fit(src: BufferedImage, dim: Int): BufferedImage`**

REDUZ EM ETAPAS, metade por vez. Era isto que deixava a foto "pixelada".

Uma foto de 4000px caindo direto pra 512 e uma reducao de 8x, e a
interpolacao bilinear le so 2x2 pixeis vizinhos: 98% da informacao nunca e
olhada, e o que sobra vira serrilhado e granulado. Nao e culpa do tamanho
final — 512 e de sobra pro maior avatar do app —, e do salto.

Cortar pela metade de cada vez faz cada etapa ser uma reducao de 2x, onde
2x2 pixeis sao EXATAMENTE a vizinhanca certa. E o "media de area" feito na
mao, e a diferenca aparece na hora numa foto com textura.

**`private fun escreverJpeg(img: BufferedImage, out: ByteArrayOutputStream)`**

JPEG com qualidade EXPLICITA. O ImageIO.write(…, "jpg", …) usa o padrao do
JDK, que e 0.75 — visivel como bloco em volta de contorno e em area de cor
chapada. 0.95 e o mesmo numero que o recorte (ImageCrop) usa; ficar nos dois
em 0.95 e o que faz a foto sair igual pelos dois caminhos. Alto de proposito:
isto e um INTERMEDIARIO, o servidor re-encoda em WebP por cima.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/shell/ChatVm.kt`

**`val clientNonce: String? = null,`**

--- Envio otimista (so canal, texto puro) ---
Nonce local que casa a bolha temporaria com o new_message que volta do
servidor. pending = ainda não confirmada (bolha esmaecida). failed = o
servidor recusou ou não respondeu (mostra "tentar de novo").

**`fun expirada(poll: PollDto): Boolean`**

Enquete com prazo que ja passou. O backend recusa o voto de qualquer jeito; isto
existe pra a UI nao oferecer um clique que ela sabe que vai falhar.
Data ilegivel = NAO expirada: derrubar a enquete por causa de um formato de data
seria pior que deixar o servidor recusar.

**`private fun load(forcar: Boolean = false)`**

CARGA EM ANDAMENTO NAO E CANCELADA POR OUTRA CARGA. Isto e um conserto de
uma armadilha que a propria janela longa de repeticao criou:

`listenLive()` chama load() a cada reconexao do socket. Com a janela antiga
(5,5s) as duas coisas quase nunca se cruzavam. Com 72 segundos, cruzam o
tempo todo — e como a API dorme no plano free, a queda do socket e a carga
lenta acontecem exatamente juntas. Cada reconexao matava a carga em voo e
recomecava do zero: um laco que nunca termina, e a conversa fica vazia pra
sempre com o servidor de pe.

Quem cancela de verdade e so o clique em "tentar de novo" (o usuario pediu
do zero) e o dispose (a conversa fechou).

**`insistir(…`**

A politica de repeticao mora em net/Insistencia.kt — a janela aqui
era de 5,5s contra um servidor que acorda em ate 50s (ver la).
A partir da segunda tentativa a tela para de dizer "carregando" e
passa a dizer que esta esperando o servidor: a espera pode chegar a
um minuto, e um minuto de silencio parece travamento.

**`val f = (t as? FalhaDeRede)?.falha`**

O MOTIVO REAL, não "não foi possível": 403 e "você não tem
acesso", 404 e "não existe mais", e nenhum dos dois melhora
com "tentar novamente". Dizer qual e o caso e a diferenca
entre a pessoa saber o que fazer e ficar clicando.

**`if (!result.ok)`**

"Sem ack" = o servidor não tem este handler (backend mais velho que o
app) ou a resposta se perdeu. Em vez de acusar falha na cara do usuário,
refaz pelo HTTP, que todo backend entende. So neste caso: erro de
verdade (silenciado, spam, sem acesso) continua virando falha visivel.

**`private fun sendError(t: Throwable, fallback: String): String`**

Motivo REAL da falha. O backend responde { error, code, secondsLeft } — e
trocar tudo isso por um texto fixo foi o que transformou "estou silenciado
ha 4 minutos" num misterio: o anti-spam devolve 429 MUTED e a UI so dizia
"Mensagem não enviada". Sem corpo legivel, cai no código HTTP.

**`fun sendSticker(fig: ServerStickerDto)`**

FIGURINHA: e uma mensagem comum com um anexo marcado `sticker = true`, nao
um tipo novo de mensagem. Assim ela herda de graca resposta, reacao,
exclusao e notificacao — um caminho proprio teria que reimplementar tudo.

O `type` sai da extensao da URL em vez de ser cravado: os clientes que ainda
nao conhecem a marca (mobile, web) caem no ramo de IMAGEM em vez de desenhar
um cartao de arquivo. `size = 0` porque o tamanho nao e guardado — o backend
exige o campo e so o cartao de arquivo o exibe, que figurinha nunca vira.

**`fun sendBotCommand(serverId: String, content: String)`**

Comando da bot. Sai por socket (evento proprio), NAO como mensagem: o
backend nao guarda o comando, so publica a resposta — mesma coisa que o
cliente web ja fazia e que o desktop simplesmente nunca chamou. Era esse o
"os comandos nao funcionam": a caixinha do "/" existia, preenchia o texto,
e o texto saia como mensagem comum. A bot nunca era acionada.

**`socket.reconnected.collect { load() }`**

Voltou do mundo dos mortos: tudo que passou enquanto o socket
esteve fora nao chega atrasado, simplesmente NAO chega. Recarregar
a conversa aberta e o unico jeito de a tela parar de mentir —
vale pra canal e pra sussurro, por isso fica fora do `when`.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/shell/ShellVm.kt`

**`data class Penalidade(…`**

Fui expulso ou banido de uma constelacao. Vira um aviso no meio da tela.

Existe porque perder acesso sem explicacao e a pior versao do "nao atualizou":
a constelacao some da rail e a pessoa nao sabe se foi expulsa, banida, se saiu
sem querer ou se a constelacao acabou. As quatro pedem reacoes diferentes.

**`val dmPresence: Map<String, String> = emptyMap(),`**

Presenca de quem está do outro lado dos SUSSURROS. Mapa proprio, e nao o
memberPresence: aquele e limpo toda vez que se troca de constelação (ele
pertence a constelação selecionada), e a lista de sussurros nao tem
constelação nenhuma — herdar dali faria a bolinha piscar e sumir.

**`val avisoForcado: Set<String> = emptySet(),`**

Órbitas que dizem "me avise" em VOZ ALTA (mode "all" explícito). Existem só
pra discordar da constelação: sem elas, reativar uma órbita dentro de uma
constelação calada não teria como ser expressado — apagar a preferência faz
a órbita herdar o silêncio de volta.

**`fun orbitaSilenciada(channelId: String): Boolean`**

CASCATA: órbita > constelação > avisa. A MESMA ordem do servidor
(apps/api/src/lib/silencioDeCanal.ts) — as duas metades têm de concordar,
senão o sino fica quieto e a bandeja avisa, ou o contrário.

A escolha mais específica vence porque quem calou a constelação inteira e
depois reativou uma órbita disse exatamente isso; devolver o silêncio ali
seria ignorar a segunda frase por causa da primeira.

**`data class ChamadaNaTela(…`**

Chamada de sussurro na tela.

`euLiguei` decide TUDO que difere nos dois lados: o toque (grave e baixo pra
quem liga, alto e repetido pra quem recebe), o texto ("chamando…" x "está te
chamando") e quais botoes aparecem (desistir x atender/recusar).

**`const val HISTORICO_DESTINOS = "historicoDestinos"`**

Estado do shell. Sem ViewModel no desktop: classe simples presa ao escopo da
composicao (rememberCoroutineScope).
Historico de destinos (ver registrarDestino). Chave e separador ficam aqui pra
a tela de busca, que LE o mesmo formato, importar em vez de repetir a string.

**`private fun pollVoicePresence()`**

Presenca de voz: quem entra/sai AVISA por socket (voice_presence) e aplicamos o
delta na hora. O poll continua como fonte AUTORITATIVA — a verdade mora no
LiveKit e so ele sabe de fantasma (queda de rede/crash não emite 'leave') —, mas
agora bem mais espacado, ja que o caso comum chega pelo evento. Antes era poll de
5s + cache de 5s no servidor = ate ~10s pra ver alguem entrar na call.

**`runCatching { voiceApi.presence(voiceIds.joinToString(",")).data.orEmpty() }`**

onSuccess: uma consulta que falha MANTEM o que se sabia. Com
getOrDefault(emptyMap()), um unico erro de rede esvaziava as
salas de voz na barra lateral — todo mundo sumia da call por
20 segundos, ate o proximo giro, sem nada ter acontecido.

**`_state.update { st ->`**

RASTRO SO QUANDO MUDA. "Alguem em call so aparece depois
que eu entro" tem tres causas possiveis e silencio
identico: o servidor nao devolve a pessoa, devolve um id
que nao casa com nenhum membro carregado, ou devolve certo
e a lista nao desenha. Registrar O QUE VEIO separa as tres
numa reproducao so.

So na mudanca porque a cada 20s a resposta e a mesma, e um
diagnostico que repete vira um arquivo que ninguem le.

**`if (c.video)`**

A CHAMADA DE VÍDEO ENTRA SÓ COM VOZ, e agora por um motivo mais estreito do
que antes: o processo de voz já carrega vídeo — a tela compartilhada sobe
por ele —, mas quem falta é a CÂMERA. Não há captura de webcam do lado de lá,
e ligar a câmera aqui só acenderia a luz do aparelho sem ninguém receber
imagem, que é pior do que não ligar.

A chamada em si funciona, e quem quiser mostrar algo pode compartilhar a
tela: era ela que importava quando a pessoa apertou.

**`private suspend fun <T : Any> insistindo(oQue: String, bloco: suspend () -> T?): T? =`**

Insiste com a politica unica do app (net/Insistencia.kt). No boot isto e
caro: a lista de sussurros voltava vazia, e lista vazia ali nao e "nao
carregou", e uma AFIRMACAO — "voce nao tem conversa nenhuma". Uma tela
mentindo com confianca total.

Devolve null so quando a janela inteira falhou. Resposta bem-sucedida e
VAZIA volta na primeira, sem insistir: vazio de verdade e legitimo.

**`private fun registrarDestino(target: ChatTarget)`**

HISTORICO DE DESTINOS — alimenta a lista que a busca mostra com o campo vazio.

Fica AQUI e nao na tela de busca porque este e o funil por onde passa toda
abertura de conversa: sidebar, Ctrl+K, clique num resultado, retomada do
ultimo lugar. Gravar so o que sai da busca faria o historico nascer vazio e
continuar vazio, ja que quase nada se abre por ali.

Uma linha por destino; campos separados por U+0001, escrito como escape e
NUNCA como caractere literal no fonte (controle invisivel nao sobrevive a
copia/cola e some sem deixar rastro). Separador exotico porque nome de orbita
aceita "|", ":" e praticamente qualquer pontuacao.

**`fun closeDm(conversationId: String)`**

"Fechar sussurro": some da MINHA lista (nada e apagado, o outro lado nem
fica sabendo, e volta sozinho na proxima mensagem). Otimista: tira da lista
na hora e, se a conversa fechada estava aberta no palco, esvazia o palco —
ficar olhando pra uma conversa que "não existe mais" seria esquisito.

**`fun reorderChannel(serverId: String, orderedIds: List<String>)`**

Reordena canais DENTRO de uma secao (soltos, ou de uma categoria) via drag.
orderedIds = nova ordem dos ids da secao. Preserva os VALORES de position já
existentes, so permutando quem fica com qual (não reindexa pra 0-base, pra não
colidir com a position das outras secoes). Otimista: reposiciona local na hora;
persiste PATCHando so os que mudaram; reload reconcilia. So o dono chega aqui
(a UI so habilita o drag pro dono).

**`val newPos = orderedIds.mapIndexed { i, id -> id to i }.toMap()`**

Posicao = INDICE na nova ordem (0,1,2...). Robusto mesmo quando os canais
tem position igual/0 (nascem sem position distinta): o metodo antigo
permutava os VALORES atuais e, com todos = 0, dava sempre "sem mudanca"
-> nenhum moveChannel era enviado e o reload voltava a ordem (o bug).

**`fun moveChannelToCategory(serverId: String, channelId: String, targetCategoryId: String)`**

Mover uma órbita PRA DENTRO de outra categoria (drag cross-categoria). position =
fim da categoria alvo. Otimista: troca categoryId+position local; PATCH com categoryId
(não-nulo -> serializa); reload reconcilia. So o dono chega aqui (a UI so habilita drag
pro dono). Mover pra "solta" (categoryId null explicito) e' bloqueado pelo explicitNulls.

**`_state.update`**

REATIVAR DENTRO DE UMA CONSTELAÇÃO CALADA PRECISA DE "all" EXPLÍCITO.

Apagar a preferência era o que se fazia antes, e ali o clique não fazia
NADA visível: sem preferência própria, a órbita herdava o silêncio da
constelação de volta no instante seguinte. O menu voltava a oferecer
"silenciar órbita" sobre uma órbita que continuava calada.

**`fun updateServer(serverId: String, body: UpdateServerRequest, onResult: (String?) -> Unit)`**

---- Configuracoes da constelação ----
Salvar devolve o erro REAL do backend pra tela mostrar (nome duplicado,
imagem grande demais, sem permissão) em vez de um "não deu" generico.
Sucesso -> recarrega a lista: a rail repinta com o ícone/nome novo.

**`private var announcedVoice: String? = null`**

Canal que ANUNCIAMOS por socket estar na call. Nao e o mesmo que voiceChannel:
aquele e so a sala aberta NO PALCO, e clicar numa sala abre a antessala (ver
quem esta la antes de abrir o microfone) — o que nunca deveria contar como
entrar. Era exatamente essa confusao que fazia "clicar ja aparecer na call".

**`if (_state.value.dms.none { it.id == msg.conversationId })`**

CONVERSA QUE AINDA NAO EXISTIA NA LISTA.

Agora o servidor entrega o sussurro na sala PESSOAL alem da
sala da conversa, entao a primeira mensagem de alguem chega
mesmo sem a conversa existir aqui. Sem este ramo o evento
chegava e era descartado em silencio (o indexOfFirst nao
acha, o update nao muda nada) — e a conversa so aparecia no
proximo boot, que e onde a lista e rebuscada.

**`_state.update { st ->`**

DELTA da barra lateral: aplica a previa ("Você: ..."/texto) e sobe a
conversa pro topo na hora. Antes so marcava não-lida e a previa/ordem
ficavam velhas ate um reload — o classico "chegou mensagem mas a lista
continua igual". Vale pras MINHAS tambem (por isso o filtro de senderId
saiu daqui e virou so a regra do não-lida abaixo).

**`launch`**

Constelacao mexeu. Sao PINGS ("mudou, busca de novo"), nao deltas: canal
privado faz cada membro ver uma lista diferente, entao so o backend sabe
o que cada um deve enxergar. Uma busca extra num evento raro e barato —
o caro era o canal novo so aparecer pros outros no proximo boot do app.

**`val naCallDaqui = st.voiceChannel?.let { canal ->`**

Sair da CALL tambem. A sala de voz ocupa o palco sozinha e
nao olha a selecao: trocar a selecao deixava a pessoa
expulsa dentro da call, ouvindo e sendo ouvida, ate ela
mesma trocar de aba. Era o "a tela continua la".

**`socket.reconnected.collect`**

RECONCILIACAO. Evento e dispare-e-esqueça: o que passou enquanto o
socket esteve fora nao volta sozinho. Sem isto, uma queda de 10s
deixava a tela mentindo ate o proximo boot — e queda ACONTECE
(servidor dormindo, wifi oscilando, notebook fechando).
Rebusca o que esta em cena; o ChatVm cuida das mensagens.

**`dms.forEach { socket.joinDm(it.id) }`**

Entra nas salas DE NOVO a partir da lista fresca. O rejoin
do socket so refaz as salas que ele ja conhecia; conversa
criada enquanto o app estava sem conexao nao estava nessa
lista, e ficava sem sala (sem digitando, sem edicao).

**`val atividade = if (members.isNotEmpty())`**

Atividade ("o que a pessoa está usando"). Chamada à parte da presença
de propósito: são dois recursos com vidas diferentes, e juntá-los numa
resposta obrigaria os quatro clientes a mudar de contrato pra economizar
um pedido por painel aberto. Falhou = mapa vazio = ninguém em nada.
SÓ O TEXTO aqui. A resposta traz também desde quando, mas a linha do
painel mostra só o nome do programa — guardar o instante no estado da
lista seria carregar em memória, por membro, um dado que só o cartão
de perfil usa (e que ele já busca sozinho, fresco, ao abrir).

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/SomDeAviso.kt`

**`private const val TAXA = 44_100f`**

O AVISO SONORO — duas notas curtas subindo, sintetizadas na hora.

SEM ARQUIVO DE ÁUDIO de propósito: um .wav no pacote seria mais um binário pra
versionar, e o som que o dono descreveu ("chamativo mas não alto") são duas
senoides — descrevê-las em vinte linhas é menor e mais ajustável que empacotar
o resultado delas.

POR QUE DUAS NOTAS E NÃO UMA: um toque isolado precisa de VOLUME pra ser notado,
e volume era exatamente o que não se podia gastar. Duas notas com intervalo
ascendente são reconhecidas pelo desenho, não pela força — o ouvido identifica
"subiu" mesmo baixinho, e é por isso que campainha, elevador e mensagem de
celular quase sempre têm mais de uma nota.

A quinta justa (razão 3:2) é o intervalo mais consonante depois da oitava. Isso
importa aqui porque o som vai tocar mil vezes: intervalo dissonante cansa por
repetição, e o pedido explícito era não virar um incômodo quando chegam várias.

**`private const val TRAVA_MS = 1_200L`**

UM SOM POR VEZ, com trava de 1,2s. Cinco mensagens em rajada tocariam cinco
vezes por cima de si mesmas e virariam ruído — que é exatamente a reclamação que
o dono levantou antes de a coisa existir. A primeira toca; as de dentro da
janela são engolidas.

**`val fase = posicao.toDouble() / duracao`**

ENVELOPE, e é ele que separa "nota" de "clique". Ligar e desligar uma
senoide na marra deixa um degrau na forma de onda, e degrau é um estalo
audível — o defeito clássico de som gerado em código. A janela de cosseno
levantado sobe e desce suave, então só sobra a nota.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/AnimatedImage.kt`

**`private data class AnimatedFrames(…`**

Imagens ANIMADAS no desktop (GIF, WebP animado). O Coil3 no JVM so decodifica o
PRIMEIRO frame (não existe coil-gif-jvm nem AnimatedSkiaImageDecoder no JVM —
coil-gif so pública variant Android). Entao a animação vem daqui: decodifica os
frames na mao com o Codec do Skiko (que já vem junto do Compose Desktop) e roda
um loop de frames no Compose. Estatico continua no Coil (sem regressao).

AstraImage e drop-in do AsyncImage: enquanto não sabe se anima (ou se e estatico)
mostra o Coil — que já pinta o 1o frame do gif, entao a troca pro animado não
pisca. So tenta decodificar formatos que PODEM animar (gif/webp) pra não baixar
duas vezes cada foto estatica.

**`val indice = remember(a) { mutableIntStateOf(0) }`**

O NUMERO DO QUADRO NAO PODE SER LIDO AQUI EM CIMA.

Antes era `Image(bitmap = a.frames[idx])`, com o `idx` lido no corpo do
composable. Ler um State na composicao significa: mudou o State,
recompoe e REMEDE o no inteiro. A ~15 quadros por segundo. Por imagem.
No grid do seletor de GIF, com uma duzia animando ao mesmo tempo, isso
e a interface inteira sendo remontada centenas de vezes por segundo pra
trocar uns pixels.

Agora o quadro e lido DENTRO do desenho (QuadrosPainter.onDraw): mudou o
quadro, so redesenha. Mesmo conserto que tirou a travada do video de
chamada na 0.1.26.

Por que Painter e nao Canvas: o Painter tem tamanho intrinseco, entao o
`Image` continua medindo e enquadrando exatamente como antes —
contentScale e alignment seguem funcionando de graca. Um Canvas nao tem
tamanho proprio e obrigaria a refazer Crop/Fit na mao em 8 lugares.

Reduzir movimento: congela no 1o quadro (ainda mostra o gif, so não mexe).

**`modifier = modifier.drawBehind`**

drawBehind e nao um Box atras: o AsyncImage continua medindo e
enquadrando exatamente como antes. Um Box com matchParentSize nao
contribui pro tamanho do pai e colapsaria o layout pra zero enquanto a
imagem nao chegasse.

**`private class QuadrosPainter(…`**

Pinta o quadro ATUAL de uma animacao, lendo o indice so na hora de desenhar.

O tamanho intrinseco vem do primeiro quadro (todos tem o mesmo) — e o que faz o
`Image` medir e enquadrar igualzinho a antes. Como ele nunca muda, trocar de
quadro nao invalida layout nenhum: so o desenho.

**`private const val ANIM_MAX_DIM = 1024`**

Maior lado que um frame animado guarda em memoria. Banner/avatar nunca sao
desenhados perto disso, entao reduzir não tira qualidade visivel — e e o que faz
gif GRANDE continuar animando: na resolucao original, um gif de banner (ex.
1920x1080 = 8MB/frame) estourava o teto e virava "estatico" pra sempre.

**`private const val ANIM_CACHE_BYTES = 48L * 1024 * 1024`**

Cache de frames decodificados. O LRU e por BYTES, não por contagem: contando
itens (12) com um teto de 48MB CADA, o pior caso eram ~576MB de frames vivos —
o app inchava sozinho conforme você passava por avatares/banners animados.
Agora o teto e GLOBAL e a conta e fechada: nunca passa de ANIM_CACHE_BYTES,
independente de quantos gifs aparecerem.

**`private val plain by lazy { OkHttpClient() }`**

Cliente SEM auth pro fallback: quando o banner mora num CDN/R2 publico, mandar
o header Authorization pode ser recusado (o endpoint tenta interpretar como
assinatura) -> o fetch falhava, a animação nunca era decodificada e sobrava o
1o frame do Coil. Era o "gif anima, ai reinicio o Astra e fica parado": antes de
salvar a imagem e data-uri (decodifica local), depois vira URL e passava por aqui.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/AtividadeArte.kt`

**`data class ArteDeAtividade(val glifo: ImageVector, val cor: Color)`**

A MARCA DO PROGRAMA NA ATIVIDADE — catálogo curado, não ícone extraído.

A alternativa era puxar o ícone do próprio .exe com JNA e mandar pela rede. Ela
cobre qualquer jogo, inclusive obscuro, e foi descartada por escolha do dono: o
que trafega hoje é uma string de 64 caracteres, e passar a mandar imagem por
pessoa transforma um recurso de presença num canal de arquivos — com cache,
rota de ícone e bytes de todo mundo no Redis de plano gratuito.

Aqui não trafega NADA: a marca é resolvida no cliente, a partir do nome que já
viajava. Custo de rede zero, custo de memória zero (os glifos são vetores que já
estão no binário), e o mesmo programa fica igual pra todo mundo — coisa que
ícone de máquina não garante (cada instalação tem a sua arte).

O PREÇO, dito na cara: só existe marca pro que está nesta lista. Jogo fora dela
cai no genérico. Esta lista cresce à mão, e é isso que "curado" quer dizer.

Glifo e COR, e não logo de marca: o Astra não empacota arte de terceiro. A cor é
o que faz reconhecer de longe (verde = Spotify, azul = VS Code) e o glifo diz a
CATEGORIA quando a cor não basta. Trocar por PNG de verdade depois é mexer numa
linha por entrada.

**`private val VERDE   = Color(0xFF6EC99B)`**

Cores puxadas pra baixo de propósito. A marca real do Spotify é #1DB954, que
numa superfície escura vibra e rouba a tela inteira — e a norma do app é cor em
pouca área, nunca competindo com o conteúdo. Cada uma aqui é a cor da marca
dessaturada até assentar na paleta editorial.

**`listOf("minecraft") to ArteDeAtividade(Lucide.Boxes, VERDE),`**

--- lojas e jogos ---
A entrada de LOJA vem depois dos jogos de propósito: quem está com o Steam
aberto na biblioteca está no Steam, mas quem está no jogo aparece com o nome
do jogo — e aí é o jogo que tem que ganhar a marca.

**`fun tempoDeAtividade(desde: Long, agora: Long = System.currentTimeMillis()): String?`**

HÁ QUANTO TEMPO, em texto adulto e sem enfeite.

`desde` é epoch em ms e vem do SERVIDOR: ele só muda quando a atividade muda, e
a renovação de 45s que segura o registro vivo reenvia o mesmo instante. Calcular
isso no cliente zeraria o contador três vezes por minuto.

Zero ou futuro (relógio da máquina adiantado, registro do formato antigo) devolve
null em vez de "há -3min": é melhor não dizer nada do que dizer errado.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/Aurora.kt`

**`private fun fbmSigmaRel(octaves: Int): Double`**

Aurora viva em SkSL (Skia RuntimeEffect) — a assinatura visual do desktop.
PORTA DA AURORA DO MOBILE (StarField.kt AURORA_AGSL; AGSL e SkSL são o mesmo
dialeto do Skia). Cortinas organicas por ruido fractal (FBM), prata sobre o
void. O TEMPO ANDA NUM CIRCULO no espaco de ruido (cos/sin * raio), entao o
loop fecha PERFEITO sem salto — corrige o "cortada" do nebula anterior (tempo
linear crescia sem fim -> dominio do ruido estourava a precisao do float e a
animação travava). Tilt/toque do mobile ficaram de fora (são de celular:
acelerometro/dedo). PERF: value-noise ALU-only, 3 oitavas, 2 cortinas.
octaves = qualidade (Settings > Desempenho): mais oitavas = ruido mais rico e
mais caro. SkSL exige bound de loop constante -> a contagem entra no source e
recompila-se uma variante por nível. Normaliza-se por (1-0.5^oct) pra aurora
manter o mesmo brilho em qualquer qualidade (senao LOW fica visivelmente mais
escura, parece bug). accent + void ENTRAM COMO UNIFORMS (uAccent/uVoid) pra a
aurora seguir o tema de Aparencia ao vivo — antes eram cravados (#D4D8E0 sobre
#06060E) e não recoloriam. So o octaves recompila; cor troca por uniform (barato).
Quanto o fbm BALANCA em relacao a propria escala, por numero de oitavas: soma
quadratica das amplitudes (o desvio, já que as oitavas são ~independentes)
dividida pela soma linear (o alcance). 1 oitava = 1.0; 3 oitavas = 0.655, ou
seja o campo de 1 oitava e 1.53x mais largo relativo ao proprio intervalo.
E por isso que uma curva de contraste fixa não serve pras tres qualidades.

**`val steep = 12.5 * (fbmSigmaRel(3) / fbmSigmaRel(octaves))`**

Inclinacao da curva das estrias, ajustada ao quanto o campo BALANCA nesta
qualidade. Menos oitavas = ruido de uma frequencia so = balanca muito mais
em relacao a propria escala (sigmaRel: 0.65 com 3 oitavas, 1.0 com 1). Sem
isto a mesma curva recebe campos de larguras diferentes e o LOW satura em
placas. 12.5 = inclinacao que reproduz no centro a do smoothstep validado.

**`private const val AURORA_LOOP = 62.831853f`**

Periodo do loop: ang = uTime*0.1 fecha o circulo em uTime = 2*PI/0.1 = 20*PI.
flow2 usa ang*2 -> fecha 2 voltas no mesmo intervalo. Enrolar o tempo do
desktop nesse periodo torna o loop imperceptivel (o quadro em uTime=0 e
identico ao de uTime=AURORA_LOOP).

**`val timeSec by produceState(0f, reduceMotion)`**

Relogio de frames com PAUSA: minimizada/na bandeja = nenhum frame pedido
(zero CPU/GPU em segundo plano — guardrail do dono). Enquanto VISIVEL segue
animando, mesmo com um popup/menu focavel aberto por cima (era o "cortada"
de vez em quando: gatear por foco congelava a cada menu). O tempo acumula e
ENROLA no periodo do loop (AURORA_LOOP): mantem o dominio do ruido limitado
(sem estouro de precisao) e o giro fecha sem salto.
Reduzir movimento (Settings): congela num quadro fixo — aurora parada, sem
pedir frame nenhum (a chave e restartar o produceState quando o pref muda).

**`val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)`**

CORTE 3 (ao voltar pra aba): alt-tab / outro monitor NAO mexem em
windowVisible nem isMinimized -> 'active' segue true, mas o SO para
de entregar frames pra janela ocluida. Na volta, o 1o frame traz
'now' varios SEGUNDOS a frente -> dt gigante empurraria o tempo num
salto = a aurora "pula" (o corte "do nada"). Clampo o dt em 50ms
(~3 frames): pior caso a aurora so atrasa um tico imperceptivel.

**`esperarPeloTeto(fpsCap.value, inicioDoQuadro)`**

O TETO DE FPS TEM QUE DORMIR, e nao so deixar de emitir.

Antes o laco pedia `withFrameNanos` TODO frame e apenas segurava a
emissao do valor quando o teto ainda nao tinha vencido. Isso nao poupava
nada: pedir frame e o que faz o Compose compor, o Skia desenhar e o
Direct3D apresentar — e no perfil (JFR) 90% das amostras da thread do
skiko estao exatamente em `Direct3DContextHandler.flush`, esperando a
GPU. Ou seja, o custo era o FRAME, nao o valor; o teto mexia no lado que
nao pesava e o app seguia apresentando a 165Hz (a taxa do monitor).

Dormindo entre um frame e o outro, ninguem pede frame nesse intervalo e o
app fica de fato parado. A aurora deriva devagar: a 30 quadros por
segundo ela e indistinguivel de 165, e custa cinco vezes menos.

**`val mesmo = chave[0] == timeSec && chave[1] == size.width && chave[2] == size.height &&`**

CORTE 9 — a aurora PARADA custava quase tanto quanto a aurora andando.

Com o gate de foco funcionando, o relogio da aurora congela e `timeSec` para de
mudar. Mas o `drawBehind` continua rodando: quem pede o frame nao e so a aurora,
e um frame pedido por qualquer outro motivo repassa por aqui. E aqui se
reconstruia um Shader Skia NOVO a cada passagem, com uniforms identicos aos da
vez anterior — trabalho nativo puro, jogado fora no frame seguinte.

Medido: ceu desligado 0,037 nucleo; ceu ligado e CONGELADO 0,29. A diferenca era
isto. Com a chave, quadro parado reaproveita o shader e volta a custar quase
nada; quadro que muda de fato reconstroi como antes.

**`lastShader[0]?.close()`**

CORTE 2 (parado/idle): makeShader() aloca um Shader Skia NATIVO por frame.
Sem fechar, eles so somem quando o GC roda o cleaner num lote -> engasgo
periodico que parecia a aurora "cortando" do nada. Fecho o do frame
anterior (a celula ainda segura 1 ref; o SkPaint tem a dele) ANTES de
trocar -> liberacao deterministica, sem surto de GC.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/AvisosDaContaBloco.kt`

**`@Composable`**

AVISOS DA CONTA — o bloco de baixo da aba Notificações.

A aba tem DOIS blocos porque tem dois escopos, e misturá-los produzia pares que
parecem duplicados e não são: o "Sussurros" de cima esconde o balão DESTA
máquina; o "Sussurros" daqui impede o aviso de existir — no sino, no push e no
celular. Sem os rótulos de escopo, desligar um e ver o outro continuar
funcionando leria como app quebrado.

A ORDEM é deliberada: local primeiro. Quem abre esta aba quer, na esmagadora
maioria das vezes, calar o balão que acabou de aparecer — e essa é a de cima.

**`.clip(RoundedCornerShape(8.dp))`**

Um degrau ACIMA do fundo do painel e ABAIXO das linhas de
interruptor (que são `raised` a 50%): as linhas compõem por cima
desta placa e se leem como cartões dentro do cartão. Sem borda de
propósito — as linhas já desenham a delas, e duas molduras
concêntricas viram grade.

**`ToggleRow(…`**

Pedido de amizade NÃO tem interruptor, e a ausência é decisão do
servidor (lib/notifications.ts explica): é raro, é dirigido a você e é
acionável — não tem como virar ruído. Uma linha aqui protegeria de um
incômodo que não existe.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/BannerArt.kt`

**`data class BannerGradient(val id: String, val label: String, val css: String)`**

O bannerColor do Astra guarda uma string CSS ("linear-gradient(135deg,#a,#b)")
— foi o web que definiu o formato e o mobile seguiu. O desktop ate agora so
tentava ler como hex (toLongOrNull(16)), entao TODO gradiente virava cinza
liso aqui. Este arquivo traduz CSS -> Brush do Compose pra o banner ficar
igual nos tres clientes. Aceita também hex puro ("#0f0c29"), que e o que
contas antigas podem ter.

**`internal const val ProfileBannerAspect = 3.5f`**

Proporcao UNICA do banner de perfil (largura:altura). Usada no popup, na pagina
completa, na previa das configs e na moldura de enquadramento — asim o Crop mostra a
MESMA fatia da imagem em todo lugar: o que você enquadra = o que todos veem (pedido do
dono). Meio-termo entre a referencia do popup (~4:1) e a da pagina (~3.14:1).

**`fun Modifier.profileCardBackdrop(css: String?, aspect: Float = ProfileBannerAspect): Modifier =`**

Fundo CONTINUO do cartao de perfil. O Discord pinta banner + corpo como UMA
peca so (o gradiente atravessa o cartao inteiro) em vez de dois retangulos com
gradientes independentes. Aqui: o gradiente cobre a caixa toda e, a partir do
fim da faixa do banner, entra um veu escuro.

O veu não e enfeite: sem ele um preset claro (Artico, Menta, Petala) deixaria
nome e bio ilegiveis, porque a paleta de texto do app e clara e fixa. Assim a
cor fica VIVA na faixa (onde não ha texto) e vira tom no corpo — que e como o
cartao do Discord se parece, e casa com o obsidiana do Astra.

Quem usa isto passa `css = null` no ProfileBanner de cima, com o fallback vindo
de `bannerBackdrop()`: TRANSPARENTE quando não ha imagem (o gradiente atravessa
a faixa e o cartao vira uma peca so) e PRETO quando ha (decisao do dono: a sobra
de um recorte menor que a faixa e preta, não acompanha a cor do perfil).

**`val veil = Obsidian.void.copy(alpha = 0.88f)`**

O veu escurece o corpo do cartao pro texto ter contraste. Ele NAO e preto
puro: mantem 88% de void e deixa 12% da cor do banner atravessar, entao o
corpo herda um tom do topo em vez de virar um retangulo preto colado numa
faixa colorida. E a diferenca entre "cartao" e "duas coisas empilhadas".

**`fun bannerBackdrop(imageUrl: String?): Color =`**

Cor que fica ATRAS da imagem do banner dentro do cartao de perfil. Com imagem,
preto: um recorte menor que a faixa deixa sobra, e a sobra e preta em vez de
deixar o gradiente vazar por tras da foto. Sem imagem, transparente pro
gradiente do cartao atravessar.

**`contentScale = ContentScale.Fit,`**

Fit: a imagem aparece INTEIRA (pedido do dono) — o Crop antigo cortava
sozinho pra encaixar na faixa e comia o resto do conteudo. Fit NAO
distorce (diferente do FillBounds que ele rejeitou): so cabe a imagem
toda na caixa e o gradiente/cor aparece nas sobras. Dai o usuario
ajusta como quiser — `scale` (0-300%) enche a faixa e positionY
reposiciona na vertical.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/Bits.kt`

**`val FormaDeBotao = RoundedCornerShape(8.dp)`**

Feedback tatil de clique (decisao do dono): o alvo encolhe pra ~0.96 enquanto
pressionado e volta com mola ao soltar. GPU-only (graphicsLayer scale). Reduzir
movimento -> sem escala. Reaproveita o MESMO InteractionSource que o componente
já usa pro hover; pra funcionar, o clickable precisa receber esse source
(clickable(interactionSource = it, indication = null, ...)). Aplique cedo na
cadeia (antes de clip/background) pra escala envolver o visual inteiro.

TAMBEM desenha o anel de FOCO DE TECLADO, e por isso o nome ficou menor que o
trabalho — mantido assim porque renomear em 29 lugares seria puro ruido de
diff. Este e o "jeito de botao" compartilhado do Astra: aperta e encolhe,
recebe foco e ganha anel.

O anel resolve um buraco que valia pro app INTEIRO: havia 97 lugares com
`indication = null` e ZERO uso de estado de foco no projeto. O Tab andava pelos
botoes, mas nada mostrava onde voce estava — quem navega so por teclado ficava
as cegas. E ficar as cegas nao e um detalhe de acessibilidade: e o app inutil.

Duas escolhas de implementacao que importam:
 - `onFocusChanged`, e nao o InteractionSource. O modificador observa o foco de
   quem vem DEPOIS dele na cadeia — e o clickable vem depois. Assim o anel nao
   depende de o clickable repassar (ou nao) interacao de foco pro source.
 - o anel e pintado no `drawWithContent`, DEPOIS do drawContent. Como o
   clickScale entra cedo na cadeia, uma borda comum aqui seria coberta pelo
   background que vem logo adiante; desenhar por cima e o unico jeito de o anel
   sobreviver a qualquer ordem de clip/background do chamador.

O ANEL SO APARECE PRA QUEM VEIO DE TECLADO. Clicar com o mouse tambem da foco
ao alvo — e por isso a borda ficava acesa depois de cada clique, o que o dono
(com razao) leu como sujeira. Quem separa os dois casos e o
`LocalInputModeManager`: o Compose troca pra InputMode.Keyboard quando alguem
anda de Tab e pra Touch quando mexe o mouse. E o mesmo mecanismo que o
:focus-visible do CSS resolve na web. Apagar o anel de vez nao era opcao: sem
ele, quem navega so de teclado fica sem saber onde esta.

No lugar do anel, o mouse ganha LUZ: um halo curto no accent atras do alvo
enquanto ele esta apertado. Halo em vez de borda porque borda desenha um limite
novo (mais uma linha na tela) e luz so ilumina o limite que ja existe.
FORMA DE TODO BOTAO DE ICONE DO APP: quadrado de pontas quebradas, nunca
circulo. O circulo sobrou de quando cada tela resolvia sozinha, e deixava o app
falando dois idiomas — o rail, o compositor e os menus ja eram 8dp; so os "X" de
fechar e as acoes de banner continuavam redondos.

O que SEGUE redondo, e de proposito: foto de perfil, bolinha de status e anel de
XP (sao identidade, nao botao) e os controles de chamada, onde o circulo e
convencao universal — o vermelho redondo se le como "desligar" sem precisar ler.

**`@Composable`**

CARTAO DENTRO DE CARTAO — a estrutura preferida do projeto, e o que substituiu
as 14 linhas de separacao que existiam no app.

A regra que ele materializa: conteudo se separa por ANINHAMENTO DE SUPERFICIE,
nao por traco. Um painel e um cartao; dentro dele, cada bloco e outro cartao,
um degrau mais claro. Traco de borda a borda le como linha de tabela, e o olho
passa a ver grade em vez de conteudo.

`fundo` fica exposto porque o degrau depende de onde o cartao mora: dentro de
um popup (que ja e `overlay`) subir pra `overlay` de novo nao mostraria nada —
ali o passo certo e `hover`, que e o degrau seguinte da rampa.

**`@Composable`**

Icone Lucide tingido. O desktop NAO tem material (sem Icon()), entao renderiza
o ImageVector via foundation.Image + ColorFilter.tint. Substitui os glifos/emoji
que faziam papel de ícone de chrome; a marca ✦ do Astra fica de fora (e
identidade, não ícone). Mesma lib/versão do :app Android (com.composables.icons.lucide).

`rotulo` e o nome que o leitor de tela anuncia. Fica NULO por padrao de
proposito: icone ao lado de um texto e decoracao, e anunciar "lixeira, Apagar
conversa" faz o leitor repetir tudo duas vezes. Quem PRECISA de rotulo e o
botao so-icone, onde o desenho e a unica pista que existe — e ali estava o
buraco: os 90 usos de LIcon passavam null sem nem ter como mudar isso.

**`@Composable`**

Avatar circular com fallback de inicial — usado no shell e no chat. No HOVER: o
cursor vira mãozinha e um BRILHO acende em volta da foto.

Era um anel que se desenhava de 0 a 360 graus. O anel tinha dois problemas: a
borda dura competia com a propria foto (que ja e um circulo), e a varredura
virava uma animacaozinha que pedia atencao toda vez que o mouse passava — e o
mouse passa por avatar o tempo todo num app de chat. O brilho difuso diz a
mesma coisa ("da pra clicar") sem desenhar uma segunda borda nem chamar aten-
cao pra si. So acende no hover -> custo zero parado.

`externalHover` deixa a LINHA que contem o avatar acender o brilho (ex: hover
na linha de sussurro), não só o hover direto na foto.

**`val LocalReduceMotion = compositionLocalOf { false }`**

"Reduzir movimento" (Settings > Movimento): quando ligado, as animações de
fundo (aurora, cascata, pulsos) param. Provido no ShellScreen a partir do
DesktopPrefs; muda em tempo real. Modifiers @Composable (auroraBackground,
CascadeIn) e os pulsos leem daqui.

NAO E `static`, E ISSO MUDOU DE PROPOSITO.

CompositionLocal estatico nao anota quem leu: quando o valor muda, ele nao tem
como avisar so os leitores, entao invalida a SUBARVORE INTEIRA do provider. Isso
era barato enquanto "reduzir movimento" so mudava quando alguem mexia na
configuracao — uma vez por mes, e recompor o app todo naquele instante nao custa
nada.

Deixou de ser barato quando este valor passou a significar tambem "o app esta
atras de outra janela". Ai ele flipa a cada alt-tab e a cada minimizar/voltar, e
cada flip mandava o app inteiro recompor duas vezes (na ida e na volta). Era
metade do "ao minimizar e abrir de novo ele carrega tudo de novo".

**`data class MinhaConta(val id: String? = null, val usuario: String? = null)`**

Quem sou eu, pra quem desenha mensagem. Vem por CompositionLocal e nao por
parametro porque o destaque de mencao mora na FOLHA da arvore (o span de texto
dentro da bolha), e enfiar dois campos por quatro camadas de assinatura so pra
pintar uma palavra e pior que a magia.

**`class MencaoClicavel`**

Clique num @usuario dentro da mensagem -> mini card de perfil no ponto do clique.

E uma CLASSE com campo mutavel, e não uma lambda, e o motivo esta em onde a
mencao e montada. O texto estilizado da mensagem e memoizado (`remember`) porque
remonta-lo e o caminho mais quente do app — e o que entra nesse texto fica preso
ali ate a chave do remember mudar.

Uma lambda que enxergasse a lista de membros trocaria de identidade quando a
lista chegasse da rede, e a mensagem ja memoizada continuaria segurando a VELHA,
a que ainda não conhecia ninguem. O @ simplesmente não abriria nada — so nas
conversas abertas antes dos membros carregarem. Bug intermitente e por tempo, o
tipo mais caro de achar.

Com o objeto estavel, a mensagem guarda a REFERENCIA e le o campo na hora do
clique, que e quando a resposta certa existe.

**`val LocalWindowActive = compositionLocalOf { true }`**

Janela "ativa" = visivel, NAO minimizada **E com o app na frente**. Aurora, estrelas e
o resto do enfeite gastam frame so quando ativa.

O FOCO ENTROU DEPOIS, E POR MEDICAO: congelar so o ceu nao bastava — com ele ja parado o
app ainda gastava 0,28 nucleo em segundo plano, porque o resto do enfeite continuava
pedindo quadro (em especial o pulso do marcador de nao-lida, que e um relogio POR canal
nao lido). Ver o bloco que provê isto em Main.kt.

Nao e `static` pelo mesmo motivo do LocalReduceMotion acima: muda toda vez que a
janela sai da frente, e static faria isso recompor o app inteiro.

NAO USE ISTO PARA VIDEO. Ver `LocalJanelaNaTela` logo abaixo.

**`val LocalJanelaNaTela = compositionLocalOf { true }`**

Janela VISIVEL e nao minimizada — sem exigir foco. E o sinal certo para conteudo que a
pessoa esta OLHANDO, em oposicao a enfeite que ela so percebe de relance.

A diferenca importa e custaria caro confundir: com a janela do Astra numa segunda tela
enquanto se trabalha na primeira, `LocalWindowActive` e falso — e para a aurora isso
esta certo, ninguem repara nela. Mas a tela que alguem compartilha na chamada esta
sendo vista naquele instante, e cortar a imagem porque o foco esta noutra janela seria
o app estragando exatamente o que se pediu para ele mostrar.

Popup focavel (menu de botao direito, dialogo) tambem rouba o foco da janela; aqui isso
nao pisca, porque visibilidade nao muda quando um menu abre.

**`val semMovimento = LocalReduceMotion.current`**

O "REDUZIR MOVIMENTO" NAO PODE SER UM DESVIO DE ESTRUTURA. Aqui era:

    if (LocalReduceMotion.current) { content(); return }
    ... Box { content() }

Duas chamadas de content() em lugares diferentes da funcao sao dois GRUPOS
diferentes pro Compose. Ele nao "reaproveita" um no outro: ao trocar de ramo,
descarta o grupo antigo (com todo `remember`, `LaunchedEffect` e requisicao de
imagem que morava la dentro) e compoe o outro do zero.

Enquanto isso so dependia da configuracao, ninguem via. Quando "reduzir
movimento" passou a valer tambem pro app em segundo plano, cada minimizar-e-
voltar virou dois descartes de subarvore em fila — a lista de canais, a de
membros, a de sussurros, os avatares. E como o `enter` renascia em 0f, a
cascata tocava de novo na volta: o app parecia estar carregando o que ja
estava carregado, porque estava mesmo.

Agora a estrutura e uma so, e o "reduzir movimento" decide apenas o VALOR da
animacao. Norma do projeto: gatear animacao por valor, nunca por ramo — o ramo
leva junto o conteudo que nao tem nada a ver com a animacao.

**`@Composable`**

"Estouro" de entrada/saida de gente na call (pedido do dono, no idioma do
Discord): quem chega ENTRA estourando — nasce pequeno e passa do tamanho antes
de assentar (mola com pouco amortecimento) — e quem sai encolhe e some. E o que
faz a call parecer viva em vez de a lista so trocar de conteudo.

Uso: envolver cada item de uma lista que muda, com `key` = id da pessoa. O
AnimatedVisibility precisa nascer com visible=false e virar true no 1o frame,
senao ele considera o item "ja estava la" e não anima.
Reduzir movimento -> aparece pronto, sem estouro. Pelo mesmo motivo do CascadeIn,
isso e escolha de TRANSICAO e não desvio de ramo: trocar de ramo descartaria a
pessoa inteira da lista da call (avatar, medidor de voz) so pra parar um pop.

**`private val urlsMortas = object : LinkedHashMap<String, Boolean>(64, 0.75f, true)`**

IMAGEM QUE MORREU: volta pra inicial em vez de deixar um buraco pra sempre.

O caso real: as imagens salvas quando o storage ainda era o disco da instância
viraram endereços `/uploads/…` que hoje dão 404 permanente — o arquivo foi
embora num redeploy e o endereço ficou no banco. Sem isto, o avatar dessas
contas é um círculo vazio; COM isto, é a letra inicial, que é exatamente o que
aparece pra quem nunca subiu foto nenhuma.

Não é remendo pro caso antigo só: vale pra qualquer URL que pare de responder
(CDN fora, imagem apagada no bucket). O estado de erro do Coil é a informação
certa, e ela estava sendo jogada fora.

O registro é GLOBAL e não por composable de propósito. A mesma foto aparece em
dezenas de lugares ao mesmo tempo (lista de membros, autor de cada mensagem,
cartão), e um estado por peça faria cada uma descobrir sozinha que a URL está
morta — dezenas de requisições condenadas por rolagem. Descobriu uma vez, todo
mundo já sabe.

Teto de 512 pra não virar vazamento numa sessão longa; quando estoura, esquece
e no máximo se tenta de novo — que é o comportamento certo se a URL voltar.

**`val novidade = synchronized(urlsMortas) { urlsMortas.put(url, true) == null }`**

REGISTRA UMA VEZ SÓ, e é o mesmo `put` que decide: o mapa já servia para não
repetir a requisição condenada, e agora serve também para não repetir a linha no
registro. Sem isso, uma lista de membros com quarenta avatares mortos escreveria
quarenta linhas por rolagem e afogaria o resto do arquivo.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/Blurhash.kt`

**`private const val ALFABETO =`**

BLURHASH — as cores borradas da imagem, em ~30 bytes.

O servidor JA calculava isto em todo upload (routes/upload.ts) e o desktop nunca
usou: a conta era feita, o dado viajava, e a tela mostrava um buraco cinza
esperando a foto. Aqui ele vira pixel.

Nao ha biblioteca de blurhash no JVM, entao a decodificacao e na mao. O formato e
fechado e minusculo — 83 caracteres de alfabeto, uma DCT de no maximo 9x9
componentes — entao "na mao" aqui sao 80 linhas, nao uma aventura.

Decodifica em 32px de largura DE PROPOSITO: o resultado e um borrao, e borrao em
alta resolucao e desperdicio. A GPU estica com filtro bilinear e o resultado e
exatamente o degrade suave que se quer.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/BotsSection.kt`

**`@Composable`**

A APARÊNCIA DAS BOTS — painel que só o dono do Astra enxerga.

A Sparkle e a Sparxie são uma conta só, compartilhada por todas as constelações:
a cara delas é a mesma em qualquer lugar. Por isso isto não é permissão de
constelação — quem administra uma estaria mudando a bot para todo mundo — e sim
uma lista fora do banco, no ambiente do servidor.

A rota responde 404 (e não 403) para quem não é dono, pra não confirmar que há
um painel a ser procurado. O cliente usa a própria falha como teste: deu certo,
a aba existe; falhou, ela nunca aparece.

**`var zoom by remember(p.chave, p.bannerScale) { mutableStateOf(p.bannerScale) }`**

Zoom e posição vivem AQUI e não num modal: eles são de ajuste fino, e ajuste
fino se faz vendo o resultado. Um diálogo por cima da faixa esconderia
justamente a coisa que se está tentando enquadrar — foi o motivo de o zoom
do banner das bots ter levado três tentativas com números chutados no código.

**`if (p.personalizado.bannerUrl)`**

Só aparece quando há para onde voltar. Oferecer "voltar ao
original" numa imagem que JÁ é a original seria um botão
que não faz nada — e um botão inerte ensina a desconfiar
dos outros.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/CallDock.kt`

**`@Composable`**

Card flutuante da call — aparece quando você esta conectado mas navegou pra
outra tela. Espelha o VoiceCallPanel do web (arrastavel, avatares de quem
fala, mic/desligar, botao de voltar pro palco) com tres cortes de custo que
pesavam la:

 1. O web roda uma animação infinita POR PARTICIPANTE (o anel de quem fala) e
    mais uma no ponto pulsante. Aqui existe UMA transicao infinita so, e o
    valor dela e lido dentro do drawBehind — muda o desenho, não a composicao.
 2. O anel de "falando" e pintado (drawCircle no draw scope), não um layout a
    mais por avatar entrando e saindo da arvore.
 3. Parado (ninguem falando) NENHUM frame e pedido: a transicao infinita so e
    criada quando ha alguem falando, e some junto.

Arrastar guarda a posição em memoria (não persiste entre sessões de propósito:
menos I/O e o card sempre volta pro canto conhecido ao reabrir o app).

**`var meu by remember { mutableStateOf(IntSize.Zero) }`**

O ARRASTO PARA NA BORDA. Nada segurava o cartao: dava pra empurrar ele pra
fora da janela e perder de vista o unico botao de desligar que existe depois
que navegar deixou de desconectar. Sumir com o controle da call e pior do que
qualquer limitacao de onde ele pode ficar.

O limite e conferido nos DOIS lugares de proposito: no gesto (senao o dx
acumula pra sempre e voltar exige arrastar a mesma distancia de volta) e na
hora de posicionar (senao encolher a janela deixaria o cartao do lado de fora
sem ninguem ter arrastado nada).

**`.shadow(20.dp, RoundedCornerShape(14.dp))`**

O card sumia no fundo, e por dois motivos somados: ele flutua sobre a
AURORA (que e escura e viva, entao nao serve de contraste estavel) e
usava `overlay` — um tom que existe justamente pra encostar no fundo,
nao pra se destacar dele.

Tres coisas resolvem, e as tres fazem falta separadas: sombra (e o que
diz "isto esta FLUTUANDO", e nao havia nenhuma), uma superficie um
degrau mais clara, e opacidade cheia — a 0.94 a aurora atravessava o
card e mexia por baixo do texto.

**`Row(…`**

Acoes: calar/abrir mic e desligar. Desligar e a UNICA saida da call
agora que navegar não desconecta mais.

Faixa propria em vez de traco em cima: os dois botoes aqui embaixo
sao a unica parte CLICAVEL do dock, e uma superficie propria diz
isso melhor do que uma linha.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/CallTimer.kt`

**`@Composable`**

Cronometro da call.

Existe por dois motivos que se somam: saber ha quanto tempo a conversa esta
rolando e, agora, enxergar o XP de call — que e pago por MINUTO. Sem relogio, "eu
ganho 8 por minuto" e uma frase sobre nada.

Uma batida por segundo, e so enquanto a tela que usa esta viva. O texto e um
State proprio: quem chama le dentro do seu Text e recompoe SO esse Text, nao a
call inteira.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ChamadaScreen.kt`

**`@Composable`**

TELA DE CHAMADA (sussurro) — ocupa a janela inteira enquanto toca.

Uma tela so pros dois lados. O que muda vem de `euLiguei`: quem ligou ve
"chamando…" e um botao de desistir; quem recebe ve "está te chamando" e o par
atender/recusar. Duas telas quase iguais so dariam duas chances de uma delas
ficar velha.

O halo que pulsa em volta do avatar e a unica coisa em movimento — desenhado no
`drawBehind`, ou seja, fase de DESENHO: pulsa 60fps sem recompor nada.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ChatView.kt`

**`val mencao = remember { MencaoClicavel() }`**

CLIQUE NO @: quem sabe traduzir o nome escrito em uma pessoa e ESTA camada, não
a mensagem. A mensagem so tem o texto; a lista de membros da constelacao mora
aqui. Por isso a mencao pergunta pra ca em vez de resolver sozinha.

Em sussurro `membros` vem vazio de proposito (mencionar a unica outra pessoa da
conversa não serve pra nada), entao la o @ não tem quem procurar e o clique não
faz nada — em vez de abrir o card de alguem parecido.

**`val ponteiro = remember { intArrayOf(0, 0) }`**

Onde o cursor estava no ultimo evento. Um array cru e não um State, e a
diferenca não e estilo: isto muda a cada pixel de movimento do mouse, e um
State faria a conversa inteira recompor so por passar o mouse por cima dela.
Ninguem le este valor durante a composicao — so no instante do clique.

**`itemsIndexed(…`**

contentType: o LazyColumn RECICLA o esqueleto de composicao de
um item que saiu de tela pro que entrou. Reciclar entre tipos
diferentes (texto puro <-> imagem <-> enquete) nao aproveita
nada: ele descarta e reconstroi. Dizendo o tipo, ele so recicla
entre iguais — e a rolagem rapida numa conversa misturada para
de reconstruir arvore a toa.

**`val reduzirMovimento = LocalReduceMotion.current`**

ERA UM SLOT FIXO DE 16dp, sempre reservado pra o "digitando…" caber sem
empurrar o layout. O preço aparecia o tempo todo: uma faixa vazia
permanente entre a conversa e o compositor, que com a barra de resposta
aberta virava um degrau escuro sem função nenhuma.

A troca é pelo MESMO idioma que a barra de resposta logo abaixo já usa:
cresce de altura zero em vez de existir vazia. Não há pulo — há
animação, que é o que o slot fixo estava tentando evitar do jeito caro.

**`if (state.error != null && state.messages.isNotEmpty())`**

Falha de CARGA com o palco vazio ja e contada no palco (PalcoQueFalhou);
repetir aqui embaixo diria a mesma coisa duas vezes. Esta linha fica
pros erros de acao — enviar, reagir, apagar — que acontecem com a
conversa na tela.

**`val respondendo = state.replyingTo`**

A barra abre de dentro do compositor: cresce de altura zero e o texto
acende 40ms depois, ja com a caixa aberta — acender junto faria a
frase aparecer espremida. Fecha pelo mesmo caminho, mais rapido,
porque cancelar nao merece a mesma cerimonia de comecar.

O ultimo alvo fica guardado: `replyingTo` vira null no instante do
cancelamento, e sem isto a barra ficaria vazia no meio do fechamento.

**`val mencaoAlvo = remember(draft, membros)`**

Autocomplete de @: olha o token no FIM do rascunho.

Sem posicao de cursor — o compositor guarda uma String, nao um
TextFieldValue — completar no meio do texto exigiria trocar o campo
inteiro, e junto dele o envio, o insert de emoji e o de GIF. O caso
real e digitar @ e continuar escrevendo; volto no meio do texto se
isso incomodar de verdade.

**`val emojiAlvo = remember(draft, emojisDaSala)`**

Autocomplete de `:nome` — mesma leitura do fim do rascunho que o @ faz.

EXIGE DUAS LETRAS depois dos dois-pontos, e o @ nao exige nenhuma. A
diferenca nao e capricho: arroba solto praticamente nao aparece em
conversa, enquanto dois-pontos aparece em hora ("20:"), em link
("https:") e no meio de frase. Abrir a lista no `:` sozinho poria uma
caixinha na frente do texto varias vezes por conversa, sem ninguem ter
pedido nada. Duas letras tambem e o minimo de um nome de emoji, entao
nao se perde nenhum caso real.

**`val prefixosBot = remember(allCommands)`**

Os prefixos VEM DA LISTA que o backend mandou (o 1o pedaco de cada
comando), nao de uma copia cravada aqui. Eles mudam de nome conforme
o dia (/sparkle na semana, /sparxie no fim de semana); uma lista
local ficaria velha e o comando voltaria a sair como mensagem.

**`draft = picked.name.substringBefore(" <") + " "`**

Escolher deixa o comando pronto com um espaco. O que a caixinha
mostra inclui o rotulo do argumento ("/sparxie desejo <seu
desejo>"); esse rotulo NAO entra no compositor — seria texto pra
apagar antes de escrever.

**`val meMencionou = LocalMinhaConta.current.id?.let { it in msg.mentions } == true`**

animateXAsState SEM `by`: guarda o State e le o valor DENTRO do lambda de
desenho (graphicsLayer/drawBehind). Antes o valor era lido no corpo do
composable e alimentava .alpha()/.background() — a linha inteira (avatar,
texto, timestamp) recompunha 60fps durante todo hover/fade. Agora so
redesenha. (Auditoria de movimento, achado #3.)
Por ID e nao pelo texto: e o servidor quem decide quem foi mencionado (ele
resolve @nome contra os membros REAIS da constelacao). Escrever "@fulano"
sem fulano existir nao acende barra nenhuma, que e o certo.

**`val bg = animateColorAsState(…`**

HOVER NAO CLAREIA MAIS O FUNDO DA MENSAGEM (pedido do dono). A faixa clara
atras do texto era ruido em cima do unico conteudo que importa na tela — e
a barra de acoes que aparece no canto ja diz, sozinha, qual linha esta sob o
mouse. O destaque de "pulei pra esta mensagem" (highlighted) FICA: aquele e
um evento, nao um estado de mouse.

**`if (meMencionou)`**

Barra de mencao: 2dp na borda esquerda, so quando VOCE foi
chamado. Fica na fase de desenho junto do resto — um Box a mais
no layout custaria medida em todas as linhas pra pintar duas
colunas de pixel em quase nenhuma.

**`val showPill = (hovered || pillHovered || pickerOpen) && !msg.deleting && !editing`**

A PILULA SEGUE O FIM DO TEXTO, e nao a borda direita do palco.

Ela morava num Popup ancorado em TopEnd: um "oi" de dois caracteres tinha
os botoes de responder e apagar a meia tela de distancia, sem nada ligando
um ao outro. Agora ela e medida junto do conteudo (ver PilulaJuntoDoTexto)
e encosta onde a mensagem acaba — grudando na borda direita so quando o
texto de fato chega la.

**`if (pickerOpen)`**

O SELETOR DE EMOJI ANCORA NA PILULA, e nao mais na linha.

Enquanto a pilula vivia colada na direita, "abaixo da linha, a
direita" e "abaixo do botao" eram o mesmo lugar. Agora que ela
segue o texto, ancorar na linha abriria o painel longe do botao
que o chamou — e o painel tem que sair de onde se clicou.

**`val meuUsuario = LocalMinhaConta.current.usuario`**

O texto estilizado tambem e memoizado, nao so o fatiamento. Monta-lo na
composicao significa varrer a mensagem atras de crases e alocar um
AnnotatedString novo TODA vez que a linha recompoe — e ela recompoe por
motivos que nao tem nada a ver com o texto (passar o mouse, chegar
mensagem nova, mudar a densidade). E o caminho mais quente do app.
Entra na chave do remember: trocar de conta muda QUAL @ ganha fundo.

**`val emojis = LocalEmojisDaSala.current`**

Emoji da constelacao entra NAS CHAVES do remember, ao contrario do clique
da mencao: a lista muda quando alguem sobe ou apaga um emoji, e ai o texto
TEM que ser remontado — senao o emoji novo so apareceria nas mensagens
seguintes, e as antigas ficariam com o `:nome:` escrito pra sempre.

**`if (att.sticker == true)`**

FIGURINHA antes de tudo: tamanho fixo e SEM abrir em tela cheia. Figurinha
e expressao, nao arquivo — ampliar nao serve pra nada e ainda rouba o
clique. Tamanho fixo tambem mantem o ritmo da conversa, em vez de deixar
uma imagem grande dominar a linha.

**`modifier = if (proporcao != null)`**

Com a proporcao conhecida, o teto vale pro LADO MAIOR e a figurinha
reserva o espaco exato antes de a imagem chegar — sem isso a
conversa pula quando ela carrega. Sem proporcao, quadrado e o Fit
resolve as sobras.

**`url = att.thumbUrl ?: att.url,`**

A bolha mostra a MINIATURA (~720px); o original so e baixado ao abrir
em tela cheia. Aqui a imagem aparece com ~320dp — baixar o WebP de
2048px pra isso era dez a vinte vezes mais bytes do que a tela usa.
Anexo antigo (ou imagem que ja era pequena) nao tem thumb e cai no url.

**`private fun androidx.compose.ui.text.AnnotatedString.Builder.appendComMencoes(…`**

@usuario em ambar; o MEU ganha fundo. Ambar sozinho ja diz "tem gente marcada",
mas nao diz "e voce" — e essa e a unica distincao que muda o que voce faz com a
mensagem. Por isso o peso visual fica reservado pro seu caso.

Clicar abre o mesmo mini card do avatar. Isso e feito com LinkAnnotation e nao
com hit-test na mao: o BasicText ja sabe em que span o cursor esta, entao o
hover e o clique saem de graca e certos, inclusive quando a mencao quebra de
linha no meio — que e justamente onde calcular na mao erra.

**`val aceso = repouso.copy(background = Obsidian.accent.copy(alpha = if (minha) 0.30f else 0.14f))`**

A SUA mencao ja nasce acesa, entao o hover dela sobe MAIS um degrau em vez
de repetir o mesmo tom. Se os dois usassem 0.16 aconteceriam duas coisas
ruins de uma vez: passar o mouse na propria mencao nao daria retorno
nenhum, e o fundo deixaria de significar "e voce" pra significar "o mouse
esta aqui" — perdendo a unica distincao que muda o que voce faz com a
mensagem.

**`@Composable`**

CONTEUDO DA MENSAGEM + PILULA DE ACOES, com a pilula ancorada no FIM DO TEXTO.

Nao da pra fazer isso com Row comum: a pilula so aparece no hover, e um filho
que entra e sai do layout empurraria o texto — a mensagem inteira refluiria
debaixo do mouse. Aqui ela e medida FORA da faixa do conteudo e posicionada por
cima, entao aparecer e sumir nao move um pixel do texto.

A regra de posicao e uma linha so: encosta no fim do texto; se nao couber, gruda
na borda direita. Mensagem curta ganha a pilula ao lado; mensagem que ocupa a
largura toda mantem o comportamento antigo, que ali e o unico possivel.

**`@Composable`**

Popup ancorado ACIMA do gatilho (o composer fica no rodape), alinhado a direita.
Seletor de reacao: 6 rapidos + grade expansivel. internal: a aba Perfil das
configurações reusa esta mesma grade pro emoji do recado (não duplicar).

`personalizados` VAZIO por padrao, e o padrao e o caso comum: quem escolhe aqui
e a reacao (que manda o emoji como texto pro servidor e volta em toda plataforma)
e o emoji do recado do perfil. Nos dois, um `:nome:` sairia escrito assim mesmo,
porque nao ha constelacao por perto pra traduzir o nome em imagem. So o
compositor — que grava o `:nome:` numa mensagem de uma constelacao — preenche.

**`@Composable`**

Botao de ENVIAR: minimalista estilo "seta pra cima". Sem texto = seta apagada,
fundo transparente (so hover leve). COM texto = circulo BRANCO preenchido + seta
escura (contraste alto = "pronto pra enviar"). As cores animam (fade), não um
liga/desliga seco -> transicao fluida (pedido do dono).

**`@Composable`**

Empty-state do chat: 3 pontinhos saltando em fila (onda da esquerda p/ direita)
e voltando. A fase e lida DENTRO do draw (phase.value no lambda do Canvas) ->
invalida so o desenho, sem recompor a arvore a cada frame. Reduzir movimento ->
pontos parados na base (ainda visiveis).

**`@Composable`**

A primeira tentativa caiu e o app esta insistindo. NAO e erro: e espera, e a
espera pode passar de um minuto porque a API dorme no plano free. Um minuto de
esqueleto parado le como travamento — dizer o que esta acontecendo custa uma
linha e evita a pessoa fechar o app achando que quebrou.

**`@Composable`**

A carga falhou de vez. Isto ocupa o palco INTEIRO de proposito: antes a falha
aparecia como uma linha vermelha de 12sp junto do campo de escrever, enquanto o
palco mostrava "nada por aqui ainda" — ou seja, a tela afirmava que a conversa
estava vazia quando ela nem tinha sido lida.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/Clicavel.kt`

**`private fun Modifier.cursorDeClique(enabled: Boolean): Modifier =`**

O CURSOR DE MÃO EM TUDO QUE CLICA — resolvido na raiz, não em 203 lugares.

O app tinha 203 alvos clicáveis e cursor de mão em SEIS. Num app de janela, a
seta parada sobre um botão é a interface dizendo "aqui não tem nada": o cursor é
o único retorno que existe ANTES do clique, e sem ele a pessoa descobre o que é
clicável tentando.

POR QUE ISTO E NÃO 203 EDIÇÕES: um `.pointerHoverIcon(Hand)` colado em cada
chamada resolveria hoje e apodreceria amanhã — o próximo botão escrito nasceria
sem cursor de novo, e ninguém lembraria. Aqui a propriedade passa a valer por
construção: todo `.clickable` do pacote `ui` ganha o cursor, inclusive os que
ainda não existem.

COMO FUNCIONA (e por que não é mágica): estas funções têm a MESMA assinatura das
do Compose e vivem no mesmo pacote dos 39 arquivos de tela. Em Kotlin, um import
explícito ganha de uma função do próprio pacote — então bastou APAGAR a linha
`import androidx.compose.foundation.clickable` desses arquivos pra que estas
aqui passassem a ser as escolhidas. Nenhuma chamada mudou. A original continua
acessível pelo apelido `clicavelDoCompose`, e é ela que faz o trabalho.

O CUSTO HONESTO: quem abrir uma tela e ler `.clickable {}` não vê, ali, que
passa por aqui. É o preço de a regra valer sozinha, e ele se paga porque o
comportamento é o esperado — cursor de mão em botão é o que qualquer um assume.
Se um dia alguém precisar do Compose puro, `clicavelDoCompose` está exportado.

**`.clicavelDoCompose(enabled, onClickLabel, role, null, onClick)`**

CINCO ARGUMENTOS POSICIONAIS, e o `null` do meio não é enfeite: o Compose
1.11 tem TRÊS `clickable`, e as duas primeiras (4 e 5 parâmetros) ficam
ambíguas quando a fonte de interação é omitida. Passar ela explicitamente
deixa só uma candidata com essa aridade. Nulo aqui quer dizer "cria a sua",
que é exatamente o que a de 4 parâmetros fazia — a indicação continua vindo
do LocalIndication, então o brilho de toque não muda em lugar nenhum.

**`fun Modifier.semCursorDeClique(): Modifier = pointerHoverIcon(PointerIcon.Default)`**

SUPERFÍCIE QUE FECHA, e não botão: o fundo escurecido atrás de um modal.

Ele é clicável de verdade (clicar fecha), mas não é um alvo — é a tela inteira.
Mão sobre a janela toda transformaria "não estou apontando nada" em "tudo aqui é
botão", que é o oposto do que o cursor serve pra dizer. A seta continua sendo a
resposta certa; quem quiser fechar acha o X ou aperta Esc.
Aplicado DEPOIS do `.clickable`, porque o último da corrente é o mais interno —
e o mais interno ganha. Uma palavra por lugar, sem import: estes arquivos já
estão neste pacote.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/CommandPalette.kt`

**`private var cache: List<BotCommandDto>? = null`**

Caixinha de comandos: abre ao digitar "/" no comeco da mensagem e filtra
conforme se escreve, no idioma do Discord.

A lista vem do BACKEND (GET /api/bot/commands), do mesmo array que monta o
`/astra ajuda`. Uma copia aqui seria mais rapida de escrever e ficaria velha no
primeiro comando novo — e ninguem notaria, porque nada quebra: a caixinha so
deixaria de mostrar.

Buscada UMA vez por sessão (o catalogo não muda enquanto o app roda) e
compartilhada por todas as conversas.

**`fun matchCommands(draft: String, all: List<BotCommandDto>): List<BotCommandDto>`**

Filtra pelo que foi digitado. So vale quando a mensagem COMECA com "/" e ainda
não virou um texto qualquer — "/astra qual a boa?" ja e uma pergunta, não uma
busca de comando, entao a caixinha sai do caminho depois do primeiro espaco
que passe de um comando conhecido.

**`val abertura = remember { Any() }`**

A CASCATA TOCA UMA VEZ POR ABERTURA, e essa chave e o motivo.

`commands` e recalculado a cada tecla (a lista filtra enquanto se digita).
Usar a propria lista como chave faria os itens re-entrarem a cada caractere:
vira pisca-pisca, nao entrada. Um objeto criado no primeiro composicao vive
enquanto a caixinha estiver aberta e morre junto com ela -- exatamente o
escopo de "uma vez por abertura".

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ComposerStar.kt`

**`internal enum class Seletor { EMOJI, GIF, FIGURINHA }`**

Botoes do compositor.

Antes existia UMA estrela ✦ que abria um menu com emoji/GIF/arquivo. Virou o
contrario (pedido do dono, padrao Discord): os seletores ficam A MOSTRA na
barra e o '+' passa a ser o menu do que "cria coisa". A estrela foi removida
junto — deixar um botao sem chamador so serviria pra confundir depois.

Regra que continua valendo: UM Popup por botao, nunca popup dentro de popup.
No desktop cada Popup focavel e uma janela de verdade, e empilhar duas rouba o
foco da primeira.

**`private class AcimaDoBotao(private val pelaDireita: Boolean) : PopupPositionProvider`**

Ancora o painel ACIMA do botao. O lado importa: alinhar SEMPRE pela direita
funcionava quando o unico botao era a estrela, no canto direito do compositor.
O '+' mora no canto ESQUERDO — alinhado pela direita, o menu era empurrado
pra fora da barra, sobrando pra esquerda do botao. Cada botao pede a borda em
que ele encosta. Clampa pra não sair da janela nos dois casos.

**`@Composable`**

A moldura comum dos botoes: quadrado de 28, SEM fundo e SEM borda — no hover so
o glifo acende no accent.

A borda saiu (pedido do dono). Ela era a unica linha desenhada dentro do
compositor, que ja tem a propria borda: tres retangulos menores encostados na
barra liam como grade, e nao como botao. Sem elas o olho ve os glifos, que e o
que se clica. O alvo continua de 28dp — some o desenho, nao a area.

**`@Composable`**

Icone de TRAÇO (Lucide), nao glifo de texto.

O emoji era o caractere "☺": a fonte de emoji do Windows sequestra esse ponto
de codigo e desenha a bolinha amarela PREENCHIDA — cor propria, fundo proprio,
nada a ver com o resto da barra. Nenhum ajuste de cor resolve, porque quem
pinta e a fonte, nao nos. Icone vetorial de traco obedece o tint.

**`@Composable`**

Abre DIRETO no painel pedido (emoji, GIF ou figurinha) — sem passar por menu.

serverId so importa pra figurinha: elas pertencem a uma constelacao, e em
sussurro nao ha de onde tirar. Quem chama nao oferece o botao la (ChatView).

**`@Composable`**

O '+' agora e menu, nao atalho de anexo.

onCriarEnquete = null em sussurro: enquete so existe em canal no backend, e
oferecer um item que sempre falha e pior que nao ter o item.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/DesejosPanel.kt`

**`@Composable`**

ESTRELA DOS DESEJOS — o que as pessoas gostariam que o Astra tivesse.

A metade de escrever já existia (`/sparxie desejo …`) e a de LER não existia em
lugar nenhum: os desejos entravam no banco e ninguém, nem quem escreveu, tinha
como ver que tinham chegado. Pedido que some é pior que pedido recusado.

Mesma moldura do sino, e isso é escolha: as duas são "isto toma a tela até você
resolver", abrem do mesmo canto da barra e leem a mesma natureza de conteúdo —
coisa de todo mundo, que não pertence a nenhuma constelação. Duas telas com a
mesma função devem falar a mesma língua.

**`LaunchedEffect(lista)`**

PAGINAÇÃO POR PROXIMIDADE DO FIM, e não por botão "carregar mais": o céu não
tem fim conhecido, e um botão obrigaria a pessoa a pedir de novo a cada vinte
linhas. Dispara três antes do último pra a próxima leva chegar antes de a
rolagem bater no fundo.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/DiagnosticsSection.kt`

**`private val HORA = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())`**

"O que o app esta vendo AGORA".

Existe por um motivo especifico: quase todo bug daqui chegou como "não
funciona pra mim" — e sem nada pra olhar, a unica saida era adivinhar. O caso
que doeu: o áudio escolhia o dispositivo errado e ninguem tinha como ver QUAL
ele tinha escolhido. Esta aba responde as perguntas que separam "o aviso nunca
chegou" de "chegou e o app ignorou", que são problemas em pontas opostas.

So LE estado — não muda nada. O botao de copiar existe pra a resposta caber
numa mensagem, quando quem esta com o problema e outra pessoa.

**`@Composable`**

Mesmo passo a passo da call, mas pra QUALQUER pessoa (a aba de Diagnóstico só
existe pra dev). Fica em Configurações > Voz porque é ali que a pessoa vai
procurar quando ninguém a escutar — e porque quem mais precisa disto é o amigo
do outro lado, que não tem como abrir o app em modo dev.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/DiscoverView.kt`

**`@Composable`**

Descobrir constelações públicas (paridade com web/mobile). Palco central: busca
no topo (?q= com debounce) + grid de cards com banner. Entrar chama
/discover/:id/join e o onJoined recarrega os servidores + cai na constelação.
API/DTOs vem do :shared (DiscoverApi movida do :app).

**`Spacer(Modifier.width(8.dp))`**

A CONTAGEM SUBIU pro canto direito, na linha do nome, e perdeu a
moldura. Ela e um dado, nao um alvo de clique: chip com borda
prometia que dava pra clicar e disputava atencao com o botao de
entrar, que e a unica coisa clicavel do cartao. Aqui em cima ela
se le junto com o nome ("Autism Gang, 5 pessoas") e o rodape fica
livre pra acao.

**`Row(…`**

JA E MEMBRO: o botao vira ABRIR, e nao um tique.

O tique com moldura anunciava um estado ocupando o lugar de uma
acao — e um alvo do tamanho de um botao que nao faz nada convida
o clique e devolve silencio. "abrir" e a mesma moldura fazendo
algo util: leva pra constelacao. Estado que vira acao.

**`@Composable`**

#11: vazio da Descoberta = mapa do tesouro. Uma rota tracejada entre nos-estrela
se desenha devagar ate um ✦ (o "X" que marca o tesouro), com estrelinhas piscando
ao fundo e o destino pulsando. Movimento contido, respeita reduzir-movimento.
A tela (grid) e a sidebar dividem ESTE canvas — muda so tamanho/legenda.

**`if (draw.value > 0.98f)`**

halo do tesouro (pulsa) + o ✦ NO MESMO ponto, desenhado aqui no Canvas.
Antes o ✦ era um Text por cima via BiasAlignment e o centro do glifo
descolava do brilho; agora a estrela nasce dentro da luz ressoante e
pulsa junto com ela.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/EditorialMenu.kt`

**`@Composable`**

Envolve o alvo: botao direito abre o menu no ponto do clique. So OBSERVA os
eventos (não consome) — cliques normais seguem funcionando por baixo.

O `modifier` E OBRIGATORIO NUM CONTAINER, e a falta dele ja custou caro: quem
chama passa o modificador pro conteudo la dentro, o Box daqui fica sem nada, e
intencao de LAYOUT se perde no caminho. Foi o que jogou o rodape do usuario pro
canto SUPERIOR esquerdo: o `.align(BottomStart)` chegava num filho deste Box
(que envolve o conteudo) em vez de chegar no Box de fora, onde o alinhamento
significa alguma coisa. Tamanho aplicava, posicao nao.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/EmblemaDaBarra.kt`

**`private const val LADO = 64          // desenha grande e deixa o Windows reduzir`**

O EMBLEMA COLADO NO ÍCONE DA BARRA DE TAREFAS — o círculo com o número.

Desce pro `ITaskbarList3::SetOverlayIcon` do Win32, que é a mesma API que o
Discord usa. O JDK expõe isso como `Taskbar.setWindowIconBadge`.

O WINDOWS NÃO SABE DESENHAR O NÚMERO. Conferido nesta máquina:
  ICON_BADGE_NUMBER       = false   ← "escreva 5 pra mim" não existe aqui
  ICON_BADGE_IMAGE_WINDOW = true    ← "cole esta imagem" existe
Em macOS seria só passar a string. No Windows a imagem é obrigação nossa — o
que acaba sendo melhor, porque o emblema nasce com a cor do tema em vez de
herdar um círculo vermelho de fábrica.

A CONTAGEM É A DO SINO, e isso é decisão de produto, não preguiça: a tabela de
notificações só ganha linha para o que é dirigido a você (menção, sussurro,
reação, resposta, pedido de amizade). Mensagem de canal não entra. Se o emblema
contasse toda mensagem de todo canal, ele viveria em três dígitos e viraria
enfeite — número que nunca zera deixa de ser informação.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/EmojiCatalogo.kt`

**`data class CategoriaEmoji(val id: String, val nome: String, val atalho: String, val itens: List<String>)`**

O CATALOGO DE EMOJIS.

Os GLIFOS moram aqui (e o unico dado que nao da pra derivar). Os NOMES nao: a JVM
ja conhece o nome Unicode de cada caractere via Character.getName, entao embutir um
dicionario de 700 nomes seria carregar de novo uma coisa que ja vem com a
plataforma — e que envelheceria junto do meu arquivo em vez de junto do JDK.

O PRECO disso: os nomes Unicode sao em INGLES ("GRINNING FACE"). Buscar "risada"
nao acharia nada. Dai os apelidos la embaixo — poucos, so os termos que alguem
realmente digita em portugues.

Escrito como UMA STRING por categoria, separada por espaco, em vez de listOf de
strings citadas: mesmo dado, um terco do arquivo, e da pra ler a categoria inteira
de relance em vez de rolar 150 linhas de aspas.

**`private val INDICE: Map<String, String> by lazy(LazyThreadSafetyMode.NONE)`**

Cada emoji -> texto de busca, montado UMA vez no primeiro uso.

O nome vem do PRIMEIRO ponto de codigo: emoji composto (👨‍👩‍👧, bandeira, tom de
pele) e uma sequencia, e Character.getName so entende um caractere por vez. Pro
que a busca precisa fazer — achar "heart" em ❤️ — o primeiro basta.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/EmojiPicker.kt`

**`private const val COLUNAS = 8`**

O SELETOR DE EMOJI.

Antes eram 34 glifos numa lista cravada dentro do ChatView, sem categoria e sem
busca. Agora sao ~700 em oito categorias, com busca e recentes.

UMA GRADE SO, com os titulos ocupando a linha inteira (span cheio), em vez de uma
Column de grades por categoria. Assim a rolagem e continua, os atalhos de baixo
podem pular pra um indice exato, e — o que importa de verdade — a LazyVerticalGrid
so compoe as celulas visiveis. Oito grades empilhadas dentro de um scroll comum
comporiam as 700 celulas de uma vez, toda vez que o painel abrisse.

**`if (personalizados.isNotEmpty())`**

Os da CONSTELACAO vem primeiro, antes ate dos recentes: sao poucos,
sao os unicos que so existem aqui, e sao os que se procura quando se
abre este painel numa constelacao que tem os seus. Escolher um
insere `:nome:` no rascunho — o texto e que viaja, nao a imagem.

Eles NAO entram nos recentes: a fileira de recentes desenha glifo de
texto, e um `:nome:` la sairia escrito em vez de desenhado.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/EmojisDaConstelacao.kt`

**`internal val REGEX_EMOJI_PERSONALIZADO = Regex(":([A-Za-z0-9_]{2,32}):")`**

EMOJI PERSONALIZADO DA CONSTELACAO.

O que se digita e `:nome:`, e a MESMA regra do servidor e do site — 2 a 32
caracteres, letras, numeros e underscore. Divergir aqui faria a conversa se ler
diferente em cada cliente: o que o site desenha como imagem sairia como texto
cru no desktop, na mesma mensagem.

O texto guardado E o `:nome:`. Nao ha id de emoji dentro da mensagem, e isso e
deliberado no backend: mensagem antiga continua legivel depois que o emoji some
(vira o proprio `:nome:` escrito), em vez de virar um retangulo quebrado.

**`internal object EmojisDaConstelacao`**

Cacheado por constelacao e nao por tela: a conversa e o seletor pedem a mesma
lista, e sem cache o painel do compositor buscaria de novo a cada abertura.

`versao` e o que faz a aba de configuracoes conversar com a conversa: subir ou
apagar um emoji invalida, a versao muda, e quem estiver lendo recarrega. Sem
isso, o emoji que voce acabou de subir so apareceria depois de trocar de
constelacao — e a primeira coisa que alguem faz depois de subir e usar.

**`@Immutable`**

A lista ja montada nas duas formas que a tela precisa: por nome (pra achar) e
como conteudo embutido (pra desenhar). Montar isto uma vez por constelacao e o
que permite o texto da mensagem ser memoizado — ver o comentario do ChatView
sobre o caminho mais quente do app.

**`val m = REGEX_EMOJI_PERSONALIZADO.matchAt(texto, i)`**

matchAt ANCORA no indice; `find(texto, i)` varreria o resto do texto
atras de um casamento mais adiante. A diferenca so aparece no caso
ruim — uma mensagem cheia de dois-pontos que nao formam emoji nenhum
faria uma varredura ate o fim a cada um deles.

**`private const val ALTURA_EM = 1.4f`**

O TAMANHO VEM EM `em`, NAO EM `dp`, e isso resolve dois problemas de uma vez: o
emoji acompanha o ajuste de tamanho de fonte das configuracoes sem uma segunda
conta, e a mensagem que e so emoji fica grande apenas aumentando a fonte da
linha — sem precisar de um segundo mapa de conteudo embutido so pro tamanho
dobrado.

**`private fun ehGlifoDeEmoji(cp: Int): Boolean = when (cp)`**

Emoji unicode, so o bastante pra saber se a mensagem e "so emoji". Nao e uma
tabela do padrao Unicode e nao precisa ser: errar pra menos deixa a mensagem no
tamanho normal, que e o comportamento de sempre.

As setas tipograficas (U+2190..21FF) ficaram DE FORA de proposito — "→" sozinho
e pontuacao, nao emoji, e desenha-lo ao dobro seria estranho.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/EmptyStage.kt`

**`@Composable`**

Estado vazio do palco (nada selecionado): glifo celeste com estrelas orbitando
(calmo, discreto — não briga com nada) + legenda serifada contextual + dica do
atalho. Reduzir movimento -> órbita congela num quadro bonito. A fase e lida
DENTRO do draw (State.value no lambda do Canvas) -> invalida so o desenho, sem
recompor a arvore a cada frame.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/FotoEditavel.kt`

**`class AcoesDoBanner`**

A FOTO É O BOTÃO — e não três botões ao lado dela.

Antes havia uma fileira de ícones soltos embaixo do avatar (trocar, reenquadrar,
remover). Três alvos permanentes na tela pra uma coisa que se mexe uma vez por
mês, e nenhum deles ENCOSTANDO no que opera: era preciso ler os três rótulos pra
descobrir qual mexia na foto.

O padrão que o dono pediu (o do Discord): passar o mouse escurece a imagem e
acende um lápis; clicar abre as opções ali mesmo. As ações somem da tela até
serem necessárias, e quando aparecem estão EM CIMA do que vão mudar — não há o
que ler pra saber o que o botão faz.

Reaproveita o MenuCard dos menus de botão-direito de propósito: o app já ensinou
como um menu dele se parece, e um segundo desenho pra mesma função seria uma
segunda convenção pra ninguém aprender.
Portador do menu do banner: o FORMULÁRIO monta as ações (ele tem o rascunho e
hospeda os diálogos) e a PRÉVIA as consome. Os dois são irmãos na tela de
Configurações, então nenhum pode receber do outro por parâmetro sem mudar de
lugar. Classe estável com campo mutável — mesmo padrão do MencaoClicavel, pelo
mesmo motivo: publicar o fechamento mais recente sem forçar recomposição.

**`class AcoesDoCartao`**

Portador dos menus do cartão: o FORMULÁRIO monta as ações (ele tem o rascunho e
hospeda os diálogos de recorte) e a PRÉVIA as consome. Os dois são irmãos na tela
de Configurações, então nenhum recebe do outro por parâmetro sem mudar de lugar.
Classe estável com campos mutáveis — mesmo padrão do MencaoClicavel, pelo mesmo
motivo: publicar o fechamento mais recente sem forçar recomposição.

**`menuEm = IntOffset(0, 0)`**

Abre a partir do centro de baixo da peça. Posição fixa e não o
ponto do clique: aqui o alvo é UMA coisa (a foto), então o menu
sempre no mesmo lugar é previsível — diferente do botão-direito,
onde o ponto do clique é o que diz sobre O QUE o menu fala.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/FriendsView.kt`

**`runCatching { api.requests().data.orEmpty() }.onSuccess { incoming = it }`**

onSuccess, e nao getOrDefault(emptyList()): falha de rede virava lista
VAZIA, e lista vazia aqui e uma afirmacao — "voce nao tem pedido
nenhum". Com a API dormindo no plano free do Render, a primeira carga
depois do sono cai, e a tela mentia com toda a confianca. Agora uma
falha apenas mantem o que ja se sabia.

**`LaunchedEffect(Unit)`**

Presenca AO VIVO. A lista vinha do /friends e congelava ali: quem entrasse ou
saisse depois continuava com a bolinha antiga ate a tela ser reaberta — era a
"confirmacao de online muito atrasada". O backend ja emitia presence_update no
connect e no disconnect; faltava alguem escutando aqui.
Reordena junto (online sobe), senao a bolinha muda mas a lista fica torta.

**`LaunchedEffect(Unit)`**

AMIZADE MUDOU DO OUTRO LADO. A tela só se atualizava quando VOCÊ agia: quem
aceitava via a lista mudar (tinha a resposta do POST em mãos) e quem tinha
mandado o pedido continuava vendo "pendente" até reabrir a tela. Do lado dele
nada tinha acontecido — e era esse o "tempo real não está bom".

Recarrega as três listas em vez de aplicar delta: o mesmo evento significa
coisas diferentes nos dois lados (pra quem mandou some de "enviados", pra quem
recebeu sai de "pendentes" e entra em "amigos"), e um delta teria que carregar
esse ponto de vista. Recarregar são três consultas pequenas, num evento que
acontece algumas vezes por dia.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/GatoDoAstra.kt`

**`enum class PetEvento { MENSAGEM, CALL }`**

O COMPANHEIRO DO ASTRA — o "pet" que estava anotado como uma palavra só no
ESTADO.md, e que hoje já são três bichos.

Ele anda livre por cima da interface inteira (escolha do dono) e é PIXEL ART. As
licenças viajam junto da arte, em `resources/pet/`.

TRÊS REGRAS QUE NÃO SE QUEBRAM, porque ele passa por cima de tudo:

1. **Não intercepta ponteiro.** Um bicho que anda sobre a conversa e engole
   cliques é um bug com pelo. O `pointerInput` vazio abaixo existe pra deixar
   isso explícito no código, e a camada inteira é irmã do conteúdo, nunca pai.
2. **Dorme quando ninguém vê.** Janela oculta ou minimizada e ele para de vez —
   não é só invisível, é sem quadro nenhum. Gato de enfeite não tem o direito de
   custar bateria enquanto o app está na bandeja.
3. **Reduzir movimento tira ele da tela.** Não "diminui": tira. O recurso inteiro
   é movimento contínuo, e é justamente isso que a pessoa desligou. Fingir que
   obedece com uma animação mais lenta seria pior que ignorar.

TENSÃO COM A NORMA DO APP, dita em voz alta em vez de escondida: a norma diz
"movimento é sinal, não enfeite; repouso deliberadamente quieto". Um gato que
caminha é enfeite contínuo por definição, e contraria isso. O acordo é o ritmo:
ele passa a MAIOR PARTE do tempo parado (pausas de 4 a 13 segundos) e caminha em
trechos curtos, para não virar um piscar de canto de olho que ensina o olho a
ignorar o resto do app. Mais interruptor próprio, para quem discordar do acordo.

**`object PisoDoPet`**

O CHÃO do gato: a borda de cima do cartão do usuário, no rodapé da barra lateral.

Ele andava solto pela tela inteira, e isso era pior do que parecia no papel. Bicho
flutuando no meio de uma conversa não lê como bicho, lê como adesivo colado no
vidro — falta o chão que diz "ele está APOIADO em alguma coisa". Uma prateleira
resolve as duas coisas de uma vez: dá peso ao gato e tira ele de cima do texto.

O `UserFooter` publica a própria caixa aqui; quem desenha o gato lê. É um ponto de
encontro em vez de um `CompositionLocal` porque são dois pontos distantes da mesma
árvore, e passar isso de mão em mão atravessaria meia dúzia de telas que não têm
nada com o assunto.

**`class Passo(…`**

---------------------------------------------------------------------------
GEOMETRIA DAS FOLHAS — medida no arquivo, não estimada.

Cada folha é uma tira horizontal de quadros de 80x64. O gato ocupa só o miolo:
varrendo o alfa de todos os 42 quadros das quatro folhas, o conteúdo cabe em
x 7..64 e y 16..49. Recortar nessa caixa ÚNICA (a mesma para toda animação) é o
que mantém o alinhamento de graça — cada quadro continua no lugar exato em que o
artista o desenhou, só sem a margem vazia.

As patas repousam em y=47 do quadro, ou seja, na linha 31 do recorte. É por isso
que a âncora do desenho é o PÉ e não o centro: com o pé fixo, o pulo sobe de
verdade em vez de o bicho inteiro escorregar pra cima.
Um QUADRO de uma animação. `linha` é a linha da grade da folha — o gato do Elthen
guarda todas as animações num arquivo só, uma por linha.

**`enum class Bicho(…`**

OS BICHOS. Tudo que muda de um pra outro está aqui como DADO — geometria, paleta,
para que lado a arte olha — e por isso o desenho e a máquina de estados não sabem
qual bicho estão animando. É o que permite somar um pet novo sem tocar em lógica.

O CRITÉRIO PARA ENTRAR É TER RESPOSTA AO CARINHO. Um quarto bicho existiu aqui
(pacote grátis do Mattz Art) e saiu: ele só tinha andar, correr, pular e parado,
então o clique nele não tinha o que mostrar. Pet que ignora carinho é pior do que
pet a menos, e por isso a pergunta "o que ele faz quando eu clico" vem antes de
qualquer medição de folha.

`escala` é o multiplicador base, e existe porque folhas de artistas diferentes vêm
em tamanhos MUITO diferentes. O ALVO é a foto do usuário, ali do lado — uns 34dp:
um bicho de estimação tem que caber no canto do olho, e grande demais ele deixa de
ser companhia e vira obstáculo em cima da conversa.

A ESCALA TEM DE SER INTEIRA, e isso não é preciosismo. Em 2,5x metade das colunas
do sprite ocupa 2 pixels e metade ocupa 3 — aparece uma listra que o artista nunca
desenhou. Só múltiplo inteiro do pixel físico preserva o desenho.

E é essa regra que decide as escalas de cada um, medindo o CORPO desenhado (do topo
da cabeça até a linha dos pés), não a folha:

    Travesso  30px em 2x    = 60px
    Simples   17px em 2x    = 34px
    Sátiro    23px em 2x    = 46px

OS DEGRAUS SÃO DESIGUAIS DE PROPÓSITO, e a regra do pixel inteiro é o motivo. Cada
bicho só pode dobrar ou triplicar — não existe "um pouco maior". O sátiro saía com
23px em 1x, um quarto menor que os gatos; foi para 2x. O travesso saía com 30px, e
ao lado do sátiro de 46 lia como filhote de outro app; foi para 2x também, e no
caminho passou o sátiro.

O que emparelharia os três (travesso 60, simples 51 em 3x, sátiro 46) foi oferecido
e recusado: mexer no simples não era o pedido. Fica anotado como o passo seguinte
caso o simples comece a destoar.

`base` são as cores de pelo da folha e `destino` diz em que degrau da
`Pelagem.rampa` cada uma cai. Este gato só tem dois tons, e eles vão pro degrau
claro e pro escuro (0 e 2) pra manter contraste — mandar os dois pra degraus
vizinhos achataria o bicho.

**`val olhaParaDireita: Boolean,`**

Pra que lado a ARTE olha, em repouso. Os dois artistas escolheram lados
opostos, e essa é a única razão de este campo existir: sem ele, espelhar
"quando anda pra direita" acerta um bicho e erra o outro — foi o que fez o
gato Simples parecer que só sabia andar num sentido.

**`TRAVESSO(…`**

TRAVESSO — um arquivo por animação, tira horizontal de 32px.

Medido varrendo o alfa dos 14 quadros das cinco folhas (não estimado): o
conteúdo cabe em x 3..26 e y 1..31, e as patas repousam na última linha do
recorte. Está em 2x: em 1x saíam 30px, e ao lado do sátiro de 46px ele lia
como filhote. O receio de que dobrar o tornasse obstáculo não se confirmou —
quem decide isso é a prateleira (a borda de cima do rodapé), e nela 60px
continuam cabendo com folga.

A rampa de pelo tem quatro degraus e o CONTORNO fica de fora de propósito.
Contorno recolorido junto some quando a pelagem escurece: o bicho perde a
silhueta e vira uma mancha. Deixá-lo escuro é o que pixel art faz, e é o que
mantém o gato legível em qualquer uma das sete cores.

**`SATIRO(…`**

SÁTIRO — o único que não é gato, e o único com escada de reações.

Grade de 10 colunas por 11 linhas, 32px. A folha veio de um jogo, então a
maior parte dela é combate e morte e fica de fora; o que se aproveita são as
sete linhas abaixo. É por ter TRÊS reações desenhadas que ele ganha a escada
de carinho — não foi uma regra inventada para ele, foi a folha que permitiu.

Caixa medida sobre as sete linhas usadas: conteúdo em x 2..29 e y 3..28. As
patas ficam na linha 23 do recorte, e não na última — porque o anel dourado
da conjuração se abre ABAIXO dos pés, e a caixa precisa caber ele.

Rampa de três degraus. O contorno escuro e os chifres ficam de fora: chifre
recolorido para bege deixa de ler como chifre.

**`mapOf(…`**

TRÊS GESTOS, e a escolha é do dono: repouso, caminhada e o ataque.

A folha tem onze fileiras e quase todas foram descartadas de propósito. As
que saíram, e por quê:

  fileira 2  pulo        pouco legível fora de um jogo de plataforma
  fileira 3  conjuração  vira enfeite quando não há o que conjurar
  fileira 5  "cansaço"   é OUTRO repouso de pé, não um sentar (medido:
                         ampliada ao lado da fileira 0, difere em dois
                         pixels do braço) — foi o que fazia o terceiro
                         cutucão parecer que ele tinha travado
  fileira 6  morte       dissolve numa poça; não é assunto de bicho de
                         estimação
  fileira 8  arrancada   era a corrida, e corrida saiu de todos os bichos
  fileira 10 golpe escuro  a forma some no fundo escuro do app

**`Anim.ATAQUE to Passo("satiro.png", 9, 10, 12, 0f),`**

O ATAQUE — fileira 9, dez quadros: ele arma, dispara uma lança dourada
e recolhe. É a fileira mais legível da folha inteira, e o motivo é a
cor: dourado sobre obsidiana é a única coisa ali que não some no fundo
do app. As duas outras candidatas a "reação" eram escuras.

12 quadros por segundo dá 0,83s de gesto — tempo de o olho pegar o
arco inteiro sem que o bicho fique ocupado quando alguém clica de novo.

**`enum class Anim(val rotulo: String)`**

PARADO e ANDANDO todo bicho tem; o resto é OPCIONAL, porque uma folha pode ser
mais rica que outra. Quem não declara um gesto simplesmente não o usa, e nada no
código precisa saber de qual bicho se trata.

NÃO EXISTE MAIS CORRIDA. Ela saiu dos três a pedido do dono, e o motivo aguenta
ser dito: bicho que dispara pela prateleira puxa o olho para longe da conversa, e
é justamente o oposto do que um companheiro de canto de tela deve fazer. Quem
andava depressa agora anda.

O `rotulo` é o que a vitrine em Configurações escreve sob cada gesto. Fica no
enum, e não numa tabela na tela, porque somar uma animação a um bicho novo já
obriga a passar por aqui — e assim é impossível declarar o gesto e esquecer o nome.

**`val Bicho.escadaDeCarinho: List<Anim>`**

A ESCADA DE CARINHO sai dos DADOS, não de um `if` por bicho.

São as reações não-cansadas, na ordem em que o clique as percorre. O gato tem só
`CARINHO` e por isso repete essa; o sátiro tem só `ATAQUE` e repete aquela. Somar
um bicho com quatro reações não exigiria tocar em nada aqui — bastaria declará-las.

**`val Bicho.gestoDeSusto: Anim?`**

O GESTO DE SUSTO: o que o bicho faz quando chega mensagem ou alguém entra na call.

Pulo, para quem tem um. Quem não tem usa a primeira reação de clique — e essa
linha não é conveniência, é o conserto de um defeito real: mandar tocar `PULO`
num bicho sem folha de pulo fazia `folhas[anim]` voltar nulo, e o desenho caía na
RESERVA VETORIAL. Ou seja: o sátiro virava um gato de traço por meio segundo toda
vez que chegasse mensagem.

**`private val RAMPA_BASE = intArrayOf(0xF6CA9F, 0xE69C69, 0xBF6F4A, 0x8A4836)`**

PELAGEM — troca de cor do jeito que pixel art pede: remapeando a rampa que o
artista desenhou, cor por cor, e não jogando um filtro por cima.

A folha inteira tem 12 cores. Só QUATRO são pelo (`RAMPA_BASE`, do mais claro ao
mais escuro); as outras oito são o peito branco, as patinhas cinza, o rosa do
focinho e da orelha, e o azul dos olhos. Trocar só as quatro é o que faz o gato
continuar sendo um gato quando muda de cor: filtro de matiz mexeria nos olhos e
no focinho junto, e o bicho viraria uma mancha monocromática.

Cada pelagem foi gerada girando matiz e saturação sobre a rampa original e
PRESERVANDO o valor de cada degrau — ou seja, a sombra continua exatamente onde o
artista pôs. Foram revisadas a olho antes de entrar aqui.

Não existe "preto": preto de verdade some no fundo escuro do app. `CARVAO` é o
mais escuro que ainda se enxerga, e chamá-lo de preto seria mentir no rótulo.

**`internal object FolhasDoGato`**

Carrega as quatro folhas UMA vez POR PELAGEM, sob demanda, e nunca mais. São 12 KB
de PNG somados; decodificados viram ~700 KB de bitmap por pelagem. O cache é por
pelagem porque trocar de cor no meio da sessão não pode custar recarregar arquivo,
e voltar pra anterior tem que ser instantâneo.

`getOrNull` de propósito: se a folha faltar (recurso removido, jar estranho), o
gato cai pro desenho vetorial mais abaixo em vez de derrubar a tela inteira. Pet
quebrado não pode ser motivo de crash de app de conversa.
`internal` e não privado porque a vitrine de Configurações (`PetPalco.kt`) desenha
os MESMOS quadros já repintados. Deixar privado obrigaria a vitrine a decodificar e
repintar as folhas de novo — o mesmo trabalho e o dobro de bitmap na memória, para
mostrar exatamente a mesma coisa.

**`val densidadeLocal = LocalDensity.current`**

Pixel art só fica nítida em MÚLTIPLO INTEIRO de pixel físico: em 2,5x metade
das colunas do sprite ocupa 2 pixels e a outra metade 3, e o bicho ganha uma
listra que o artista não desenhou. Por isso a escala é um inteiro derivado da
densidade da tela, e não um valor em dp — assim o gato tem mais ou menos o
mesmo tamanho aparente em 100% e em 200% de escala do Windows, sempre nítido.

**`var caricias by remember { mutableStateOf(0) }`**

Quantos carinhos seguidos, e até quando ele está de mal.

Gato de verdade aceita atenção por um tempo e depois se manda. Isso também dá
de graça um travamento útil: clique repetido não consegue reiniciar a mesma
animação para sempre, porque na terceira insistência ele sai andando.

**`olhandoPraDireita = dx > 0f`**

O RUMO SAI DO PASSO QUE ESTÁ SENDO DADO, e é a única linha
que o define. Antes ele era decidido junto com o alvo, lá
atrás, e as duas coisas podiam discordar: basta a prateleira
mudar de tamanho no meio do caminho (troca de canal, janela
redimensionada) para `x` ser trazido de volta pra dentro dos
limites e passar do alvo — daí em diante o bicho andava pra
um lado olhando pro outro. Derivar do passo torna a
discordância impossível: o rumo É o movimento.

**`val c = bicho.passos[anim]`**

As três se comportam igual: tocam UMA volta e voltam a ficar
paradas. O que muda é quanto ele demora a se mexer depois —
quem acabou de sentar não levanta na mesma hora em que quem
acabou de ser acariciado volta a passear.

**`val p = bicho.passos[Anim.PULO]`**

Único one-shot: quando o último quadro passa, ou emenda outro
pulo (call) ou volta a ficar parado, com pausa curta — ele
acabou de reagir, então continuar o passeio na hora seria negar
a própria reação.

**`)`**

NESTA CAMADA, NENHUM MODIFICADOR DE PONTEIRO. É obrigatório que continue
assim: ela cobre a tela inteira.

Já teve um `pointerInput(Unit) { }` vazio aqui, com um comentário jurando
que bloco vazio não registra gesto. Mentira — o NÓ entra no teste de
acerto de qualquer jeito, e o app inteiro ficou sem clique.

Foi esse acidente que me fez concluir, errado, que o gato nunca poderia
receber mouse. O problema nunca foi registrar gesto: foi registrar gesto
do TAMANHO DA TELA. Numa caixa do tamanho do bicho — que é o que existe
logo abaixo — hover e clique não tiram nada de ninguém.

**`alvoX = if (x < piso.center.x) limiteDir else limiteEsq`**

Sem animação de cansaço, cansar é ir embora — sai
andando para o lado oposto. É a reação mais legível que
existe, porque o que se lê não é a pose e sim o
DESLOCAMENTO: o bicho some do lugar onde estava sendo
cutucado.

**`if (nome.isNotBlank() && comMouse)`**

O nome vai FORA do `scale`: espelhar o gato não pode espelhar a
escrita. E ele só aparece com o mouse em cima — em nenhum outro
momento, que foi o pedido: o nome é uma coisa que você VAI VER, não
que aparece sozinha por cima da conversa.

**`private fun DrawScope.desenharGato(…`**

O gato, em traço — a RESERVA de quando o sprite não carrega. Corpo e cabeça são
curvas fechadas; patas e cauda são linha.

Ele é desenhado olhando pra DIREITA e espelhado quando anda pra esquerda — meia
figura pela metade do trabalho, e o espelho é exato porque nenhuma parte do
desenho depende de qual lado é qual.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/IconAction.kt`

**`private const val ESPERA_DICA_MS = 420L`**

Acao virada em ICONE, com o nome dela aparecendo ao parar o mouse.

Nasceu do trio do banner ("subir banner" / "reenquadrar" / "remover banner"), que
ocupava a largura inteira do painel e ainda quebrava em duas linhas no botao mais
comprido. Tres quadrados de 34dp fazem o mesmo trabalho em um terco do espaco.

A DICA NAO E OPCIONAL. Icone sozinho e adivinhacao: um quadrado com uma seta pode
ser "subir imagem", "exportar" ou "mover pra cima", e quem nao acertar de primeira
vai clicar pra descobrir — num botao que APAGA o banner, descobrir clicando e caro
demais. A dica so custa parar o mouse meio segundo.

**`LIcon(icone, tint = conteudo, size = 16.dp, rotulo = dica)`**

A `dica` VIRA o nome acessivel. Ela ja e exatamente isso — o nome
da acao em portugues — e estava sendo escrita duas vezes no
codigo pra ser mostrada uma so, pro mouse. Quem usa leitor de
tela nao alcancava o texto que ja existia.

**`private class AbaixoCentralizado(private val margem: Int) : PopupPositionProvider`**

`calculatePosition` fala PIXEL, nao dp — e o `margem` chega ja convertido pelo
chamador. Somar 6 cru dava ~4dp numa tela a 150% e ~3dp a 200%: o respiro
encolhia justamente onde a tela e maior e a dica encosta no botao. Mesmo
tropeco do cartao de perfil, que ja foi consertado uma vez.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ImageCrop.kt`

**`sealed interface CropSource`**

Recorte de imagem no estilo do Discord: em vez de guardar posição/zoom em
metadado (e cada tela recortar de um jeito), o enquadramento e ASSADO na
imagem que sobe. O modal mostra a imagem INTEIRA com uma máscara escurecida em
volta da janela de corte; arrastar move nos dois eixos, a roda do mouse da
zoom, e "aplicar" gera um data-uri já na proporcao final.

Consequencia boa: quem exibe não precisa saber de nada — o banner assado em
ProfileBannerAspect cai perfeito no ProfileBanner (Fit + posição 50 + 100%).
Tradeoff (o Discord tem o mesmo): assar PERDE o original, entao reenquadrar
depois recorta o recorte. Pra voltar atras, subir a imagem de novo.

LIMITE HONESTO: gif/webp ANIMADO não pode ser assado (recortar exigiria
recodificar a animação). Quem chama testa `isAnimated` e manda o animado pro
caminho antigo (posição+zoom em metadado, animação preservada).

**`const val BANNER_OUT_W = 2560`**

DOBRO DA TELA, de proposito. Estes numeros sao a resolucao que FICA salva —
depois deles nao existe volta, entao errar pra baixo e irreversivel.

Eram 1280/512, calculados como "o tamanho que a tela desenha". A conta
esquecia a densidade: no Windows a 150% (o padrao de fabrica em quase todo
notebook novo) um banner de 840dp pede 1260 pixeis FISICOS, e o de 1280
chegava sem folga nenhuma; a 175% ou num monitor 4K ele era ESTICADO. Era
isso o "pixelado" — a imagem sendo ampliada na hora de desenhar.

O dobro cobre 200% de escala sem nunca ampliar, que e o teto que o Windows
oferece. Custo: o arquivo salvo fica ~3x maior (a area quadruplica, o WebP
do servidor come a maior parte). Isso e espaco em bucket, nao memoria de
quem usa — o Coil3 decodifica no tamanho que vai desenhar, nao no do arquivo.

**`private const val SRC_MAX = 3200`**

Teto da FONTE em memoria enquanto o modal esta aberto. Precisa ficar ACIMA
do maior destino (2560), senao a fonte ja chega reduzida e o recorte so
reamplia o que foi jogado fora. 3200 da folga pra um zoom leve e mantem o
pior caso em ~41MB de raster (x2, porque o preview guarda a copia dele).

**`fun isAnimated(url: String?): Boolean`**

Animado por PISTA (url já salva): so gif, porque o backend converte
PNG/JPEG em .webp — tratar webp como animado tiraria o reenquadrar de quase
todo banner salvo. Webp animado cai no recorte e perde a animação (raro, e
o preco de não baixar a imagem so pra decidir).

**`val larguraFinal = min(outW, max(1, sw.roundToInt()))`**

NUNCA AMPLIAR NA HORA DE ASSAR. Se o pedaco escolhido tem menos pixeis
que o destino, esticar ate `outW` nao inventa detalhe — so assa borrao
no arquivo e multiplica o peso. Salvar no tamanho real da o MESMO
resultado na tela (quem desenha amplia de qualquer jeito, com filtro) e
um arquivo menor. Acontece com fonte pequena ou zoom alto.

**`surface.canvas.drawImageRect(…`**

MITCHELL, e nao LINEAR. O bilinear le so 2x2 pixeis vizinhos: numa
reducao de 4x (a fonte vem capada em 2048 e o avatar sai em 512) ele
ignora quase toda a informacao e devolve serrilhado — era isso o
"pixelado", nao o tamanho final. O cubico do Mitchell amostra uma
vizinhanca maior e foi desenhado justamente pra reduzir.

**`val data = out.encodeToData(if (alpha) EncodedImageFormat.PNG else EncodedImageFormat.JPEG, 95)`**

95 e nao 92: o servidor ainda re-encoda isto em WebP, entao o JPEG daqui
e um INTERMEDIARIO — todo artefato que ele criar e herdado pelo arquivo
final e ainda serve de material pra segunda compressao errar em cima.
Perda dupla e o que mais estraga foto de pele e ceu.

**`val nosso = abs.startsWith(AstraShared.BASE_URL.trimEnd('/'))`**

Authorization SO pro nosso backend. O banner do perfil vive no R2 (o
persistDataUri troca o data-uri por uma URL de la), e mandar o Bearer
pra um host de terceiro (a) vaza o token pra fora e (b) costuma tomar
400 — vários storages recusam requisição com dois mecanismos de auth.
Antes tentava o autenticado PRIMEIRO em toda URL, inclusive nessas.

**`var pct by remember { mutableStateOf(100) }`**

pct = porcentagem do "cobre a janela", e 100 e o MINIMO (escolha do dono:
"igual Discord"). Assim a imagem sempre preenche a moldura — nunca sobra
borda — e sempre ha pedaco escondido pra revelar, entao arrastar funciona
nos dois eixos. O preco: não da pra ver a imagem inteira, você escolhe QUAL
pedaço aparece. E exatamente como o recortador do Discord se comporta.

**`err = if ("404" in motivo)`**

404 não é "erro ao ler a imagem": é a imagem NÃO EXISTIR MAIS no
servidor. Some quando o arquivo foi gravado no disco da instância
(storage local) e a hospedagem reiniciou — o endereço continua
salvo no perfil, apontando pra um arquivo que evaporou. Mandar
"HTTP 404" pra quem só queria mexer no banner não ajuda ninguém a
fazer a única coisa que resolve: subir a imagem de novo.

**`fun clampPan(p: Offset, z: Float): Offset`**

Limite do arrasto por eixo: a janela nunca sai da imagem (e, quando a
imagem e menor que a janela, ela nunca sai da janela). O zoom vem por
PARAMETRO de proposito: o bloco do `pointerInput` so reinicia quando a
chave muda, entao capturar o zoom da composição deixaria o limite velho
depois de dar zoom.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/InviteDialog.kt`

**`fun inviteLink(code: String): String = AstraShared.BASE_URL.trimEnd('/') + "/i/" + code`**

Convite nativo do Astra. Dois caminhos, porque resolvem coisas diferentes:
  - por @usuario: entra na hora, a outra ponta não faz nada (backend checa
    permissao, banimento e se já e membro).
  - por LINK: pra quem você não sabe o @, ou pra mandar por fora. O link e o
    atalho curto que o proprio backend serve (`/i/<codigo>`, index.ts).
A ponta que faltava era ENTRAR: o desktop sabia gerar convite mas não sabia
usar um, entao convite recebido morria.

**`val reduce = LocalReduceMotion.current`**

ENTRADA E SAIDA ANIMADAS, aqui e nao em cada dialogo: o DialogShell e o casco
do convite E da enquete, entao animar num lugar so anima os dois e impede que
um dia eles animem diferente.

OS DOIS DIALOGOS DAS CONFIGURACOES ENTRARAM DEPOIS, e o motivo de terem ficado
de fora e instrutivo: eles montavam o mesmo Popup a mao, com um
`PopupPositionProvider` identico a este mas chamado `OverlayCenter` em vez de
`CenterOverlay` — dois nomes para o MESMO objeto, em arquivos diferentes. O
custo nao foi a duplicacao: foi que os dois nasceram SEM a animacao daqui e
apareciam secos, exatamente o "piscar" que este casco existe para evitar.

Largura e respiro viraram parametro so por causa deles (360/18 e 400/20). O
resto era igual linha a linha.

Mesma coreografia do perfil completo (ProfilePage): o scrim faz fade, o cartao
escala de 0,94 e sobe 16dp. Aparecer seco e o que fazia o dialogo "piscar" na
tela — sem movimento nenhum, o olho nao acompanha de onde ele veio.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/LoginConstellation.kt`

**`private val NODES = listOf(…`**

Constelação que SE FORMA conforme o formulario e preenchido, e se DESFAZ quando
se apaga (pedido do dono). Não e enfeite solto: e o medidor de preenchimento.

Cada estrela e um marco do progresso; a linha ate a próxima cresce enquanto o
trecho e percorrido. Apagar uma letra devolve o progresso, entao o traco RETRAI
— o desenho e funcao pura do que esta digitado, sem estado escondido. Fechado o
último traco, a mensagem de conclusao acende no meio.

Posicoes normalizadas (0..1) desenhando algo próximo da Cassiopeia (o "W"), que
fecha bem num painel mais alto que largo.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/LoginScreen.kt`

**`private enum class AuthMode { LOGIN, SIGNUP }`**

Login split editorial: ceu vivo a esquerda (o MESMO planeta do gate de boot, as
estrelas do app e a constelação que se forma ao digitar), formulario a direita.

A aurora NAO e pintada aqui: ela vive na janela (Main.kt), atrás do login e do
shell. Foi o que tornou a entrada continua — antes a aurora do login ocupava 45%
da largura e a do shell 100%, e como o uv do shader e normalizado pelo tamanho,
as duas imagens eram completamente diferentes: a troca saltava.

**`Text(…`**

CAIXA-ALTA NO DESENHO, "Astra" NA LEITURA.

A Cinzel é uma fonte de capital de inscrição: escrever em caixa-alta
é usá-la como ela foi desenhada. Mas leitor de tela trata palavra
toda maiúscula como sigla e soletra — "A, S, T, R, A" —, então o
rótulo acessível repõe a palavra inteira. O desenho é para os olhos,
o rótulo é para quem não os usa, e os dois dizem a mesma coisa.

**`modifier = Modifier.weight(0.55f).fillMaxHeight().background(…`**

Translucido (não mais opaco): a aurora da janela passa por baixo, no
mesmo idioma dos paineis do shell. O ENCONTRO com o ceu (esquerda) não
e um corte seco: um gradiente horizontal funde ceu -> sombra suave ->
painel (mesmo idioma do scrim do banner da constelação, #13). A faixa
de fade cai no vao vazio antes do formulario (360dp centralizado).

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/MissaoToast.kt`

**`private const val VIDA_MS      = 3_400L`**

O AVISO DE MISSAO COMPLETA.

Canto inferior direito, some sozinho. Nao interrompe, nao pede clique, nao escurece
nada — porque missao diaria acontece TODO DIA, e o que interrompe todo dia vira
irritacao na terceira vez. O anel do rodape pulsa junto (XpRing.kt), o que amarra
a recompensa ao lugar onde o XP mora.

A FILA E O PROPRIO SharedFlow. Fechar as tres do dia dispara quatro eventos quase
juntos (as tres + o bonus); como este coletor demora ~3s por item, os outros ficam
no buffer e entram em sequencia. Uma lista de espera aqui seria reimplementar o que
o buffer ja faz.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/MissoesOverlay.kt`

**`private const val MIN_MS  = 60_000L`**

A TELA DE MISSOES.

Tres blocos com ritmos diferentes, na ordem em que importam: o que vira hoje, o
que vira na semana, e o que nunca vira. Quem abre isto quer saber "o que da pra
fazer agora" — e por isso as diarias vem primeiro, mesmo pagando menos.

Sem abas por enquanto. O passe e a colecao vao morar aqui quando existirem;
desenhar abas vazias agora seria prometer duas telas que ainda nao ha.

**`@Composable`**

O estado da conta: quem você é, em que nível está e quanto falta pro próximo.

O anel em volta da foto é o MESMO do rodapé (anelDeXp + rememberVisualDeXp), e
isso importa: a pessoa vê aquele anel o dia inteiro no canto da tela sem saber
o que ele mede. Aqui ele aparece grande, ao lado do número — é onde o anel
finalmente se explica.

A barra embaixo repete a mesma fração de propósito. O anel é bonito e vago; a
barra com "340 / 500" é a leitura exata. Quem quer sentir olha o anel, quem
quer saber lê a barra.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/NotifPanel.kt`

**`fun parse(it: NotificationItemDto): NotifPayload`**

O runCatching aqui e uma fabrica de bug invisivel: qualquer problema no
payload virava um NotifPayload vazio, e a linha aparecia como "alguém" sem
pista nenhuma do motivo. Continua não quebrando a tela — mas agora deixa
rastro no Diagnostico, que e o que separa "chegou torto" de "chegou vazio".

**`var limpando by remember { mutableStateOf(false) }`**

LIMPAR e diferente de MARCAR TUDO, e por isso sao dois botoes: marcar zera o
sino e mantem o historico; limpar apaga. A lista some na hora (otimista) e
VOLTA se o servidor recusar — sumir e reaparecer e chato, mas fingir que
apagou o que continua la e pior.

**`Box(…`**

NO MEIO DA TELA (pedido do dono). Era um dropdown colado sob o sino, e o
dropdown tem um defeito que so aparece com a lista cheia: ele desce a partir do
canto superior direito, ou seja, obriga o olho a ler a coisa mais importante no
ponto mais distante de onde ele estava. Centralizado, a lista nasce onde o olho
ja esta — e a largura pode crescer sem espremer o conteudo contra a borda.

O veu escuro e o mesmo da Busca de proposito: as duas sao "isto toma a tela ate
voce resolver", e duas coisas com a mesma funcao devem falar a mesma lingua.

**`"friend_request" -> "$author quer te adicionar" to "pedido de amizade"`**

Sem destino próprio: clicar não navega (o `open` não trata este tipo), e
isso é deliberado — a ação mora na tela de Amigos, e mandar a pessoa pra
lá com um clique atravessaria o que ela estava fazendo. A notificação
avisa; aceitar continua sendo uma ida consciente.

**`val quem = p.authorName ?: p.authorUsername`**

O ROSTO RESPONDE "QUEM", que é a primeira pergunta de quem abre o sino.

Aqui havia o ícone do TIPO, e ele era informação repetida: o título já diz
"mencionou você", "respondeu você", "reagiu ❤" com todas as letras. Um
balãozinho igual em cada linha fazia a lista inteira parecer a mesma coisa
— o painel ficava sendo uma coluna de nomes, e reconhecer quem escreveu
exigia LER, em vez de bater o olho.

Sem autor (convite de constelação) o ícone continua: não há rosto pra
mostrar, e um espaço vazio seria pior que o símbolo.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/OnboardingChecklist.kt`

**`@Composable`**

O ASTRA SE AJUSTOU À MÁQUINA — e este cartão é a metade "e diz que fez".

Ajustar em silêncio seria mais limpo de programar e pior de usar: a pessoa com um
computador apertado ganharia um app econômico sem saber que existe um bonito
esperando por ela, e concluiria que o Astra é feio de fábrica. Pior ainda no sentido
contrário — quem tem 4 GB porque o pente queimou não entenderia por que o fundo
mudou.

O cartão traz A MEDIDA ("3,9 GB de memória"), não a conclusão. Mostrar o que foi
visto na máquina se defende sozinho; só afirmar "achamos melhor" vira desconfiança.

Some no "entendi" — e some SÓ o cartão: o modo econômico continua ligado, porque
dispensar um aviso não é discordar dele. Desligar de verdade é em Desempenho, e o
texto diz onde.

**`@Composable`**

Metade "checklist" do onboarding (combo): cartao flutuante no rodape do palco
vazio, so pra quem acabou de passar pelo takeover (Main liga a pref
"checklist:<userId>"). Risca sozinho conforme o usuário cumpre cada passo — os
estados vem da state do shell (servers/dms/avatar), não ha rastreio proprio.
Some ao completar os dois passos-nucleo (constelação + sussurro) ou no "pular".

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/OnboardingScreen.kt`

**`private enum class OnbStep { WELCOME, SKY, PHOTO, PERMS }`**

Onboarding de primeiro acesso (combo: takeover curto + checklist no palco). ESTE
arquivo e o TAKEOVER — 3 passos sobre o mesmo ceu (aurora/estrelas já vivem na
janela, Main.kt), no idioma editorial do login. Dispara so depois de CRIAR CONTA
(Main passa isNew=true) e some ao concluir; o gatilho persiste numa pref local
por conta (uiPref "onboarded:<userId>"). O checklist residual vive no palco vazio.

Passos: boas-vindas -> o idioma do ceu (constelação/órbita/sussurro) -> sua foto
-> permissões. A foto e opcional e usa o MESMO caminho do perfil (data-URI via
AvatarPicker, não upload). Reduzir movimento: as trocas viram instantaneas.

Permissões vem POR ULTIMO de proposito: e o passo que manda a pessoa sair do
app (as Configurações do Windows abrem por cima). No meio do fluxo, voltar
significaria cair num passo intermediario sem saber quanto falta; no fim, ela
volta pro botao "concluir" e entra.

**`@Composable`**

O passo das permissões. A lista em si e a MESMA de Configurações > Permissões
(PainelDePermissoes) — aqui só o enquadramento.

`detalhado = false`: nas linhas que ja estão certas, repetir "ouvindo
normalmente (Microfone Realtek)" seis vezes vira parede de texto num primeiro
contato. O que interessa aqui e o que FALTA. Em Configurações, onde a pessoa
vai pra investigar, o detalhe aparece.

A lista rola por dentro: seis linhas cabem na moldura, mas cada linha com
problema cresce (ganha a explicação do que houve), e uma janela redimensionada
pra baixo não pode esconder a ultima permissão sem jeito de alcançar.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/PermissoesDialog.kt`

**`@Composable`**

Aviso de "o Windows está deixando?" na primeira abertura de quem JÁ TINHA conta
— quem cria conta agora vê a mesma lista dentro das boas-vindas, e quem quiser
rever encontra em Configurações > Permissões.

Existe porque no Windows não há janelinha de permissão: com a privacidade
fechada o microfone entrega silêncio calado, e o sintoma chega como "meu mic
não funciona no Astra" — sem log, sem erro, sem pista. Melhor descobrir aqui
que no meio da primeira conversa.

A lista em si mora em PainelDePermissoes (usada também nas boas-vindas e nas
configurações). Aqui só a moldura e o texto de contexto.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/PermissoesPainel.kt`

**`private const val ESPERA_MS = 2_000L`**

A lista de permissões — UMA implementação, três telas: boas-vindas (1º acesso),
Configurações > Permissões (quem já usava o app, ou quem pulou) e o aviso da
primeira abertura. Manter três cópias faria os textos divergirem na primeira
vez que um deles mudasse.

Sobre o botão "permitir": no Windows, aplicativo de área de trabalho NÃO
consegue pedir permissão — não existe a janelinha do navegador. Quem manda é um
interruptor global do sistema. Então o botão faz o mais próximo possível disso:

  • leva direto à página exata das Configurações do Windows (não à raiz, onde
    a pessoa teria que caçar); e
  • FICA CONFERINDO sozinho enquanto ela mexe lá.

Esse segundo ponto é o que faz a tela valer a pena. Sem ele a pessoa liga o
interruptor, volta pro Astra e encontra o mesmo vermelho de antes — e conclui
que não adiantou. Com ele a linha vira verde sozinha, que é a prova de que
funcionou.

A exceção é "Avisos": ali não há interruptor pra ligar, o Windows só registra o
app quando ele manda o primeiro aviso. Então permitir MANDA um aviso.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/PetPalco.kt`

**`private const val ALTURA_ALVO_NO_PALCO = 92`**

A VITRINE DO COMPANHEIRO — o palco de Configurações › Pets.

Existe porque escolher pet às cegas é escolher errado: a diferença entre os três
não está na miniatura parada, está no que cada um FAZ. O sátiro tem três reações
desenhadas e o gato tem uma, e isso é invisível até você ver as duas.

É um desenhista SEPARADO do `GatoDoAstra`, não uma opção dele, e a razão é que os
dois têm trabalhos opostos. O do app é uma máquina de estados: ele decide sozinho
quando andar, quando parar, para onde ir, e se apoia na prateleira que o rodapé da
barra lateral publica. Aqui não existe prateleira nem decisão — o gesto é o que a
pessoa apontou, em laço, parado no meio do palco. Espremer as duas coisas na mesma
função significaria carregar a máquina de estados inteira desligada por um `if`.

O que os dois COMPARTILHAM é o que importa: as mesmas folhas já repintadas, vindas
do cache de `FolhasDoGato`. Trocar a pelagem aqui reaproveita o bitmap que o pet de
verdade vai usar, e o inverso também.

**`private val Bicho.escalaDePalco: Int`**

A escala do palco sai do DADO que já existe (`pes` é a altura do corpo dentro do
recorte), e não de um número escolhido a mão por bicho. Assim os três aparecem do
mesmo tamanho no palco mesmo tendo sido desenhados por artistas diferentes, e um
bicho novo já nasce enquadrado sem ninguém precisar medir nada.

Continua INTEIRA. A regra de nitidez do pixel art não afrouxa por ser prévia — se
afrouxasse, a prévia mentiria justamente sobre o desenho que ela existe pra mostrar.

**`val reduzir = LocalReduceMotion.current`**

MOVIMENTO REDUZIDO CONGELA O PALCO, e isto não é uma limitação a contragosto: o
pet inteiro não existe sob movimento reduzido (o `GatoDoAstra` sai da composição
na primeira linha). Uma vitrine animada de um bicho que a pessoa nunca vai ver
se mexer seria propaganda enganosa. Congelado no primeiro quadro, ela ainda
escolhe a cor e o bicho, que é o que a tela precisa entregar.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/Poll.kt`

**`@Composable`**

ENQUETE.

O backend ja tinha tudo (criar, votar, encerrar, e o evento poll_updated); o
desktop e que nao sabia DESENHAR — entao o item "criar enquete" ficou fora do
menu '+' ate agora, porque botao que cria mensagem que o app nao exibe e pior
que a ausencia do botao.

Duas decisoes que valem registro:

1. O RESULTADO APARECE SEMPRE, mesmo pra quem ainda nao votou. Enquete de chat
   nao e urna: as pessoas conversam sobre ela na mesma tela. Esconder a parcial
   ate votar (padrao de pesquisa seria) so cria o "vota qualquer coisa pra ver".

2. A barra fica DENTRO da linha da opcao, nao embaixo. Uma linha por opcao le
   mais rapido que duas, e o chat e uma coluna estreita disputada por mensagem.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/PopupReveal.kt`

**`private const val REVEAL_MS = 150`**

Entrada padrao de popup/menu que abre por clique: fade + leve escala nascendo
da origem ancorada no gatilho — a MESMA linguagem do menu de botao direito
(EditorialMenu.MenuCard). Existe pra que os popups que apareciam "secos" (sem
transicao) passem a nascer com o mesmo idioma, sem repetir o boilerplate.

`originX/originY` = de qual canto o popup cresce, no eixo 0..1:
  (0f,0f) topo-esquerda (menus)      · (1f,0f) topo-direita (sino, pill do chat)
  (0.5f,1f) de baixo pra cima (call) · (1f,1f) rodape-direita (estrela)
  (0f,0.5f) da borda esquerda (submenu ao lado)

Respeita LocalReduceMotion: movimento reduzido -> aparece pronto (0ms), sem escala.

**`@Composable`**

Variante Modifier: pro caso comum em que o filho do Popup JA tem cadeia de
Modifier (Column/Row com clip/background). Aplicar CEDO na cadeia (antes de
clip/background) pra escala/alpha envolverem o visual inteiro. Mesma entrada,
via um único Animatable lido dentro do graphicsLayer (frame sem recomposicao).

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ProfileCard.kt`

**`enum class CardVariante`**

O CARTAO DE PERFIL — um so, duas variantes.

POR QUE ISTO EXISTE: o cartao era desenhado em ARQUIVOS diferentes, e as copias
divergiam. Primeiro foram duas (o popup e a previa das Configuracoes). Depois de
unificar essas duas, sobrou a divergencia MAIOR: o "cartao completo" de verdade
era o ProfilePage — outro composable, com secoes que o COMPLETO daqui nao tinha
(sobre / membro / servidores em comum). Ou seja, a previa chamada "cartao
completo" mostrava um cartao que ninguem via, e as duas previas saiam quase
iguais entre si, porque a de cima era so a de baixo com numeros maiores.

Agora o COMPLETO daqui E o cartao do perfil completo. O ProfilePage virou so a
moldura (fundo escuro, animacao de entrada, rolagem) em volta deste desenho.
A previa nao pode mentir porque e literalmente a mesma funcao.

**— sobre o arquivo inteiro —**

OS DOIS CARTOES USAM A MESMA PROPORCAO DE BANNER, e isto nao e escolha de
gosto — e obrigacao.

O recorte e ASSADO na imagem que sobe (ImageCrop): o arquivo salvo JA tem a
proporcao ProfileBannerAspect, e o `scale`/`positionY` ficam em 100/50. Uma
faixa com outra proporcao nao tem como exibir esse arquivo direito: ou sobra
tarja, ou corta. E o zoom nao salva, porque o problema e o FORMATO da caixa,
nao o tamanho da imagem dentro dela.

Eu tinha posto 2.6 aqui pra alongar o cartao completo no eixo Y. Funcionou
pra altura e quebrou todo banner ja salvo — a troca nao vale. A altura do
cartao completo vem do conteudo (nome maior, secoes), que e de onde ela
deveria ter vindo desde o comeco.

**`val LARGURA_CARTAO_NORMAL = LARGURA_CARTAO_COMPLETO`**

Largura do cartao compacto (o que abre ao clicar num avatar).

IGUAL a do completo (pedido do dono). Antes eram 320 e 330 — dez pixels de
diferenca que ninguem enxerga isolado, mas que apareciam justamente onde doem:
os dois lado a lado na previa das Configuracoes, um levemente mais estreito que
o outro sem motivo nenhum. Mesma pessoa, mesmo cartao, mesma largura.

**`acoesDaFoto: (() -> List<MenuEntry>)? = null,`**

EDIÇÃO NO PRÓPRIO CARTÃO. Só a prévia das Configurações passa isto — é o
único cartão que é seu e editável; em todo o resto ele é peça de leitura.

Fica aqui e não no formulário porque é aqui que as imagens existem, no
tamanho e no contexto em que os outros vão vê-las. O padrão "passa o mouse,
acende o lápis" precisa de algo embaixo do mouse, e o formulário não tem.

**`acoesDoBanner?.let { acoes ->`**

POR CIMA da faixa já desenhada, com conteúdo vazio: o véu e o lápis
escurecem a imagem que está embaixo. Assim o cartão continua tendo UM
desenho de banner só — a edição é uma camada, não uma segunda cópia.

Antes do bloco de fechar/ações: aqueles botões precisam ficar por cima
desta camada, senão o clique neles cairia no menu do banner.

**`val px = if (completo) 88 else 64`**

99->88 e 72->64. A foto tinha crescido junto com o cartao e nao voltou a
encolher quando o cartao ficou mais estreito e mais alto: a 99 ela ocupava
quase um terco da largura, e um retrato desse tamanho rouba a atencao do
nome, que e o que a pessoa foi ali ler.

O vao entre a foto e o nome NAO muda com isto: a caixa da foto continua no
fluxo com a altura dela e o texto vem logo depois, entao encolher a foto
sobe o bloco inteiro sem abrir buraco.

**`val vinculos = buildList`**

O QUE VOCES TEM EM COMUM, numa linha so. No cartao completo isto vira duas
secoes com os icones das constelacoes; aqui e um resumo — a graca do cartao
pequeno e caber, e uma grade de icones aberta sobre a lista de membros e
exatamente a poluicao que o dono pediu pra evitar.

**`var agora by remember { mutableStateOf(System.currentTimeMillis()) }`**

O CRONÔMETRO ANDA SOZINHO enquanto o cartão está aberto. Sem isto ele
congelaria no valor do instante em que abriu — e "há 12min" parado por
meia hora é pior que nenhum número, porque parece atual.

30s e não 1s: a menor unidade que ele mostra é o minuto, então acordar a
cada segundo gastaria 30 recomposições pra mudar texto uma vez.

**`Row(verticalAlignment = Alignment.CenterVertically)`**

A MARCA vive só aqui, e não no painel de membros: lá a linha tem 26dp e
texto de 10sp, onde um quadrado de 32 não cabe sem virar outra lista. A
divisão é a mesma do resto do app — a lista dá o sinal, o cartão dá o
detalhe. O ponto de cor continua sendo a convenção da lista.

**`@Composable`**

Cada secao do cartao virou um CARTAO, e nao mais um bloco antecedido por
traco. Com tres ou quatro secoes seguidas, o traco em cima de cada uma
desenhava uma grade — o olho lia tabela, nao perfil.

`hover` e nao `raised`: o cartao de perfil ja mora num popup em `overlay`, e
subir pra `raised` seria DESCER na rampa (raised vem antes de overlay). Este e
o degrau seguinte de verdade.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ProfileFonts.kt`

**`data class ProfileFont(val id: String, val label: String, val family: FontFamily)`**

displayFont = a fonte do TEU NOME (chat, sussurros e perfil). O web guarda um
id ("serif", "mono", ...) e resolve numa stack CSS; aqui traduzimos pro que o
desktop realmente tem. O app empacota 4 fontes (DmSans/DmSerif/DmMono/
GreatVibes); as demais caem nas familias genericas do sistema.
NOTA: "rounded", "condensed" e "modern" não tem arquivo proprio e caem todas
na sans do sistema -> ficam parecidas entre si no desktop. Renderizamos os 8
mesmo assim porque o valor pode vir do web/mobile e não pode "sumir".

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ProfilePage.kt`

**— sobre o arquivo inteiro —**

Perfil completo (F: pagina de perfil) — modal CENTRAL sobre scrim, o irmao
maior do ProfilePopup. Abre pelo botao "ver perfil completo" do card pequeno.
Alem do que o card mostra, traz "membro desde" e os SERVIDORES EM COMUM (já vem
do GET /api/profile/:id -> ProfileViewWrapper; o card so descartava). A entrada
(scrim + card + cascata das secoes) segue o idioma do CenteredConfirmDialog;
fable refina a coreografia depois. Respeita LocalReduceMotion.

**`val LARGURA_PAGINA_PERFIL = 840.dp`**

Proporcoes do painel, calcadas na referencia do Discord: ~1290x940 com a coluna
da identidade em ~40% da largura. Aqui: 840 de largura, 380 pra coluna do
cartao e o resto pros vinculos. A ALTURA e fixa (nao "o que o conteudo pedir"),
porque uma conta nova tem duas linhas de conteudo e o painel encolheria pra um
retangulo estranho no meio da tela.

**`private fun Modifier.veuNoPe(): Modifier = drawWithContent`**

Rodape dissolvido. Quando o conteudo passa da altura maxima, o scroll FATIA o
texto no meio e o corte seco parece quina dura; este veu faz o conteudo sumir
em vez de ser cortado. Fica ANTES do verticalScroll de proposito: assim ele
desenha no espaco da JANELA (fixo no pe da coluna) e nao rola junto.

**`var progresso by remember(userId) { mutableStateOf<ProgressoDto?>(null) }`**

Progressão e insígnias chegam por FORA do /profile, em duas leituras próprias.

Elas não bloqueiam o cartão: o perfil aparece com o que já tem e os dois blocos
entram quando chegam. Amarrá-los ao mesmo `await` faria o cartão inteiro
esperar pela informação menos importante dele.

**`Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center)`**

Card central. DUAS COLUNAS (referencia do dono: o perfil do Discord):
a esquerda e quem a pessoa E — banner, foto, nome, bio, desde quando.
A direita e o que voces tem EM COMUM. Antes era uma coluna so, e as
constelacoes em comum ficavam no pe de um cartao que ja rolava.

A aba "Atividade" da referencia ficou de FORA de proposito: nao
existe fonte pra ela (nem rota, nem tabela). Uma aba que so sabe
dizer "nada aqui" e pior que nao ter aba.

**`Column(…`**

Coluna da identidade. Rola sozinha: bio longa nao pode
empurrar a coluna dos vinculos pra fora da tela.
fillMaxHeight NO CARTAO, e nao so na coluna: o cartao tem
fundo e borda proprios, entao parar na altura do conteudo
deixava um retangulo curto boiando num painel alto — foi
exatamente o que o dono viu. Com a altura toda, ele vira a
coluna da esquerda de verdade.
SEM verticalScroll aqui, e isso e deliberado: dentro de um
scroll a altura maxima e INFINITA, e `fillMaxHeight` com
maximo infinito nao faz nada — o cartao continuaria curto.
Era essa a armadilha. Bio muito longa fica cortada com o
veu no pe avisando; a coluna da direita e que rola.

**`@Composable`**

A coluna da direita: o que voces tem EM COMUM. Cada bloco e um cartao, nao um
item separado por traco — e a mesma regra do resto do app.

Nao ha aba aqui. A referencia tem tres ("Atividade", "N amigos mutuos",
"N servidores mutuos"), mas duas delas seriam abas de UMA lista curta cada, e
a terceira nao tem dado nenhum por tras. Aba que esconde tres linhas custa um
clique pra economizar nada.

**`deServidor.forEach { b ->`**

A de constelação carrega DE ONDE ela veio: "Veterano" sozinho
não diz veterano de onde, e a mesma palavra pode ser concedida
por duas constelações diferentes querendo dizer coisas opostas.
Sem o nome (o campo é opcional), fica só a insígnia — melhor
que um "· null" pendurado.

**`val recuo = 6`**

A sobreposição é feita com `offset`, e não com espaçamento
negativo: padding e width recusam valor negativo em Compose (é
exceção, não layout torto). O offset é só desenho — a linha
continua medindo a largura cheia, e por isso o "+N" também
precisa do mesmo recuo pra não flutuar longe da pilha.

**`cargoLegivel(s.role)?.let { rotulo ->`**

O cargo já vinha na resposta e era jogado fora. "Dono" e
"admin" dizem mais sobre a pessoa do que qualquer bio, e
custaram zero: nenhuma requisição a mais.

MEMBRO não é rótulo: é o padrão, e etiquetar o padrão em
toda linha viraria ruído que some por repetição.

**`@Composable`**

Uma insígnia: emoji + nome, num cartão do tamanho do conteúdo.

A cor vem do servidor e é usada só na BORDA e no texto — nunca como fundo. Ela é
escolhida por quem criou a insígnia e pode ser qualquer coisa, inclusive um tom
que engole texto claro; como borda ela identifica sem apostar em contraste.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ProfilePopup.kt`

**`private class AoLadoDaAncora(private val folgaPx: Int, private val margemPx: Int) : PopupPositionProvider`**

Abre ao LADO da ancora (direita; vira pra esquerda se não couber) e clampa
na vertical — funciona tanto no chat quanto no painel de membros na borda.
AO LADO da ancora, encostando em nada.

As medidas chegam aqui em PIXEL, não em dp — `calculatePosition` fala a lingua
da tela crua. A versao antiga somava `8` direto, o que numa tela a 150% (o
normal no Windows) dava ~5dp de folga: o card ficava colado no painel de
membros, cruzando a linha que marca o limite dele. Por isso a folga agora
chega convertida de dp pelo chamador, que tem o LocalDensity.

A margem existe pelo mesmo motivo, na vertical: o clamp antigo era
`coerceAtLeast(0)`, e zero e a borda EXATA da janela — o card do primeiro
membro da lista encostava na barra de titulo.

**`@Composable`**

O MESMO card, aberto NO PONTO DO CLIQUE — o caminho do @ dentro da mensagem.

O ProfileAnchor acima não serve aqui, e a diferenca e de natureza: ele envolve um
ALVO (avatar, linha de membro) e ancora o popup nos limites desse alvo. Uma mencao
não e um alvo — e um pedaco de texto no meio de um paragrafo, que pode ate quebrar
de linha. O unico ponto que significa alguma coisa e onde o cursor tocou.

**`var atividade by remember(userId) { mutableStateOf<AtividadeDto?>(null) }`**

Atividade FORA do cache do perfil, e isso é o ponto: o perfil fica guardado 5
minutos porque nome e foto não mudam nesse intervalo. "O que está usando"
muda, e servir isso do cache mostraria o jogo de cinco minutos atrás com a
cara de informação atual. Uma consulta por abertura, sempre fresca.
O par inteiro (texto + desde quando), porque o cartão mostra o cronômetro.

**`modifier = Modifier.width(320.dp).heightIn(min = ALTURA_MIN_CARTAO),`**

ALTURA MÍNIMA, não altura fixa (pedido do dono: o cartão de quem
não tem bio nem cargo saía atarracado ao lado do da bot).

O cartão do Astra é um objeto reconhecível, e objeto que muda de
tamanho conforme quem está dentro deixa de ser um objeto: passa a
ser uma caixa. Quem acabou de criar conta tem duas linhas de
conteúdo, e o cartão encolhia até virar um retângulo estranho —
exatamente o motivo pelo qual a página de perfil inteira já tem
altura calçada (ver ALTURA_PAGINA_PERFIL).

Mínima e não fixa porque bio longa + vários cargos precisam crescer.
Piso dá consistência; teto cortaria conteúdo.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/Ritmo.kt`

**`suspend fun esperarPeloTeto(cap: Int, inicioDoQuadroNanos: Long)`**

O TETO DE FPS DO CÉU (aurora e estrelas), num lugar só.

Dormir é o que faz o teto valer: pedir quadro é o que custa (o `flush` do
Direct3D esperando a GPU), e não atualizar o valor — segurar a emissão e seguir
pedindo quadro não poupava nada. O comentário longo em Aurora.kt conta essa
história inteira.

O QUE ESTA FUNÇÃO CONSERTA: dormir `1000/cap` **depois** do quadro soma ao tempo
do próprio quadro em vez de ser o período total. Num monitor de 165Hz o quadro
leva ~6ms, então pedir 60 dava 6+16 = 22ms, ou seja **45fps**; pedir 30 dava
~25fps. O ajuste entregava sempre menos do que dizia — e "menos" aqui é a
diferença entre 30fps fluido e 25fps visivelmente aos trancos.

A divisão em NANOS, e não em milissegundos, mata o segundo erro: `1000L / 60`
é 16 (não 16,666), e 0,66ms perdidos por quadro viram ~4% de desvio acumulado.

Sem teto (`cap <= 0`, o padrão LIVRE) isto não faz nada e nem chama `delay` —
quem escolheu seguir o monitor não paga por uma conta que não pediu.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/SearchOverlay.kt`

**`val temBusca = query.trim().length >= 2`**

Abas + toggle de escopo.

AS ABAS SO APARECEM QUANDO HA O QUE FILTRAR. Com o campo vazio a lista
mostra os destinos RECENTES, e aquele ramo nao le a aba nenhuma — entao as
abas ficavam ali, clicaveis, mudando de cor e nao mudando mais nada. Abrir
a lupa e clicar em "Canais" sem ter digitado era exatamente a cara de
"os filtros nao funcionam", e era: um controle que nao tem sobre o que agir
e pior do que um controle ausente.

**`val showMsgs = tab == SearchTab.ALL || tab == SearchTab.MESSAGES`**

VAZIO E POR ABA, nao no total. Era `msgs && chans && people` todos vazios —
entao, com resultados de mensagem e nenhuma pessoa, clicar em "Pessoas"
caia no ramo da lista e desenhava uma lista SEM NADA DENTRO: sem resposta,
sem explicacao, do jeito que um filtro quebrado se pareceria. Agora a aba
ativa e quem decide se ha o que mostrar, e quando nao ha, ela diz.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/SeletorDeTela.kt`

**— sobre o arquivo inteiro —**

A ESCOLHA DE QUAL TELA COMPARTILHAR.

Até aqui o botão mandava sempre o monitor principal e não perguntava. Numa máquina de
uma tela isso acerta por acaso; em duas, é metade de chance de mostrar a errada — e
quem erra descobre pelo "não é essa" de outra pessoa, com a tela errada já no ar.

A MINIATURA É A INFORMAÇÃO, e é por isso que este seletor é uma janela e não um menu
de texto. O Windows chama os monitores de `\\.\DISPLAY1` e `\\.\DISPLAY2`; dois
monitores do mesmo modelo têm a mesma resolução e nomes que só diferem no dígito.
Nenhum rótulo que eu escrevesse separaria os dois. O que está NA tela separa na hora.

A JANELA ABRE ANTES DA RESPOSTA, com o aviso de que está procurando. Amostrar custa
uma duplicação de tela por monitor — uns 100ms cada —, e segurar a abertura por isso
faria o clique parecer que não pegou.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ServerBotTab.kt`

**`@Composable`**

O QUE A BOT PODE FAZER NESTA CONSTELAÇÃO.

A aparência dela não está aqui de propósito: a Sparkle e a Sparxie são uma conta
só, compartilhada por todas as constelações, então trocar o rosto delas seria
trocar pra todo mundo. O que é legitimamente desta constelação é o que ela pode
FAZER aqui — e é só isso que esta aba oferece.

A lista guardada no servidor é a dos DESLIGADOS, não a dos ligados: assim um
comando novo nasce ligado em todas as constelações sem precisar de migração.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ServerEmojisTab.kt`

**`private const val TETO_EMOJIS = 50`**

EMOJIS DA CONSTELACAO — aba de gerenciamento.

AO CONTRARIO DE SOM E FIGURINHA, a imagem vai MULTIPART pra propria rota, sem
passar pelo /api/upload. O servidor reduz pra 128px e re-encoda em WebP, e e por
isso que a diferenca existe: figurinha QUER o original byte a byte (transparencia,
nada de recompressao), enquanto emoji e desenhado com vinte pixels de lado em
cada linha da conversa — guardar o original faria baixar meio megabyte pra pintar
um selo.

O NOME E O EMOJI. E ele que se digita entre dois-pontos, entao ele nasce do nome
do arquivo (limpo pro que o servidor aceita) e pode ser corrigido na propria
linha. Sem renomear, errar o nome obrigaria a apagar e subir de novo.

**`internal fun nomeDeEmoji(bruto: String): String`**

O servidor so aceita 2 a 32 caracteres de [a-z0-9_]. Um arquivo chamado
"Estrela feliz (1).png" viraria 422 na cara de quem so escolheu uma imagem, entao
o nome e limpo aqui: acento e espaco viram underscore, o resto cai fora, e nome
curto demais ganha um sufixo em vez de ser recusado.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ServerRolesTab.kt`

**`private val PERMISSIONS = listOf(…`**

Aba CARGOS. Lista -> clicar abre o editor NO LUGAR da lista (decisao do dono):
os 7 interruptores de permissão cabem sem apertar e cada tela respira.

Regra que molda esta tela: o backend passa toda permissão por grantableSubset,
que DESCARTA em silencio o que o ator não possui (menos o dono) e ainda assim
responde 200. Se a UI deixasse marcar o que você não tem, ela diria "salvo" e a
permissão não estaria la. Por isso os interruptores fora do teu alcance ficam
desligados e explicados, em vez de mentir.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ServerSettingsScreen.kt`

**`internal enum class ServerTab(val label: String, val sub: String, val icon: ImageVector, val ready: Boolean)`**

Configuracoes da CONSTELACAO — o MESMO cartao das configuracoes do usuario (veu
preto, painel com teto de 1180dp, nav de 220dp a esquerda, coluna de conteudo
capada em 720dp), pra as duas telas de configuração se lerem como a mesma coisa.

As tres abas estão prontas. `ready` fica no enum de proposito: e o interruptor
pra listar uma aba futura apagada e inerte, sem mudar a forma da navegacao.

**`Row(…`**

CARTAO, e nao tela cheia. Era um Row(fillMaxSize) com a coluna de conteudo
capada em 720 e encostada a esquerda: 220 + 720 = 940dp usados, e num
monitor largo sobrava METADE da tela de veu vazio a direita — a tela lia
como algo que nao terminou de carregar.

A moldura e a MESMA das configuracoes do usuario (padding, teto de 1180,
fundo base, borda, cantos de 16). Nao e coincidencia: sao duas telas
irmas, abertas do mesmo jeito, com a mesma nav de 220 a esquerda. O vazio
some porque a tela deixa de ser tela e vira painel, e o teto de 1180
impede o esparramo em qualquer resolucao daqui pra frente.

**`Column(verticalArrangement = Arrangement.spacedBy(10.dp))`**

A lista tem espaçamento PRÓPRIO, maior que o da coluna. O `spacedBy`
de fora rege o nome da constelação e a legenda abaixo dele, onde 4dp
é o certo — duas linhas do mesmo bloco. Entre abas, 4dp encostava as
bordas de cartões vizinhos e a lista lia como grade; 10dp devolve a
cada aba um contorno seu. Mesmo valor do menu da conta.

**`ServerTab.EMOJIS -> EmojisSection(…`**

MANAGE_CHANNELS, e não MANAGE_SERVER como as duas
abas acima: é o que a rota de emoji exige do lado do
servidor (routes/emojis.ts). Copiar a permissão da
vizinha deixaria o botão à mostra pra quem levaria
403 ao clicar, e escondido de quem podia.

**`ServerTab.BOT -> if (isOwner || "MANAGE_SERVER" in myPermissions)`**

Aqui a regra é do LUGAR e não de quem olha: som e
figurinha todo mundo vê (ouvir o que existe antes
de tocar é cortesia), mas o que a bot pode fazer
é decisão de quem cuida da constelação.

**`Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(80.dp))`**

Form (esquerda) + card de previa ao vivo (direita). O form segue no fluxo
scrollavel do pai; a previa acompanha no topo-direita.
O vao e generoso de proposito: a previa NAO e um campo do formulario, e a 48dp
ela parecia mais uma coluna do form do que o resultado dele.

**`SettingsDivider()`**

---- Órbita dos avisos da bot ----
Vale pra tudo que ela diz sem ser chamada: troca de turno e chegada de gente.
Subir de nível não entra aqui de propósito — aquele aviso é sobre a conversa
em que a pessoa estava, não sobre a constelação.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ServerSoundsTab.kt`

**`@Composable`**

EFEITOS SONOROS DA CONSTELACAO.

O arquivo escolhido e convertido pra WAV com o ffmpeg que o app JA empacota pra
transmissao de tela. Isso NAO perde qualidade: decodificar um MP3 e uma operacao
exata, e o WAV guarda exatamente o que saiu do decodificador. O que se perde e
quando se RE-ENCODA (MP3 -> MP3), e nao e o caso aqui.

Converter existe por um motivo pratico: o JDK toca WAV sozinho, sem biblioteca
nenhuma. Aceitar MP3 direto exigiria embarcar um decodificador so pra isso.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ServerStickersTab.kt`

**`@Composable`**

FIGURINHAS DA CONSTELACAO — aba de gerenciamento.

A imagem sobe INTEIRA pelo /api/upload, que desde a 0.1.73 guarda o original
byte a byte. Nao ha recorte nem recompressao aqui de proposito: figurinha com
fundo transparente perde o fundo se passar por re-encode pro formato errado, e
o dono pediu explicitamente que arquivo nao perca qualidade.

O upload devolve width/height medidos — guardamos os dois pra conversa reservar
o espaco da figurinha antes de a imagem chegar.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ServidorAcordando.kt`

**`@Composable`**

A FAIXA QUE EXPLICA A ESPERA.

Nao existe pra ser bonita: existe porque um minuto de tela parada sem palavra nenhuma
e indistinguivel de um app quebrado. Aparece so quando a espera vira espera de
verdade (segunda tentativa em diante, ver Servidor) e some sozinha quando a API
responde.

Diz o MOTIVO, nao so o sintoma. "Aguarde" nao informa nada; "a hospedagem gratuita
desliga depois de quinze minutos parada" informa, e a pessoa deixa de achar que o
proprio computador esta com problema.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/SettingsScreen.kt`

**`PETS("Pets", "companheiro, cor e gestos", Lucide.PawPrint),`**

ABA PRÓPRIA, e não um bloco dentro de Aparência como era. O que decidiu foi a
VITRINE: escolher entre três bichos exige ver o que cada um faz, e o palco que
mostra isso não cabe espremido no fim de uma aba que já trata de tema, fundo,
fonte e densidade. O interruptor continua em Acessibilidade — "quero um bicho
se mexendo na tela?" é pergunta de movimento, não de gosto.

**`private val abaDeDev: Boolean =`**

Rodando pelo Gradle/IDE = dev. No app empacotado o jpackage define
`jpackage.app-path`; sem essa propriedade, estamos no ambiente de
desenvolvimento. Da pra forcar no pacote com -Dastra.dev (util pra pedir o
diagnostico a alguem sem publicar uma versão especial).

Esconder a aba NAO cega o suporte: o diagnostico de boot e o registro de falhas
continuam sendo gravados em %LOCALAPPDATA%\Astra pra todo mundo, entao ainda da
pra pedir o arquivo a um amigo quando algo quebrar.

**`private val abasVisiveis: List<SettingsTab> =`**

A aba Bots FICA DE FORA desta lista de propósito: quem a libera é o servidor.
Só o dono do Astra recebe resposta em /api/bots (pra todo mundo mais a rota
responde "não existe"), e a aba entra quando essa chamada dá certo — ver
`ehDonoDoAstra` abaixo. Uma bandeira local seria só uma sugestão: o portão de
verdade está no servidor, e a tela apenas evita oferecer o que ia dar erro.

**`private val LARGURA_DA_PREVIA = 470.dp`**

Largura da coluna de previa, UMA para todas as secoes. Era 300 fixa (420
empilhada): com duas previas lado a lado sobravam ~145dp pra cada cartao, e um
cartao encolhido a 40% vira mancha — da pra ver que ha um cartao, nao COMO ele
esta. Depois houve um segundo valor, so pra secao do Perfil; ele morreu quando a
tela virou pagina unica, porque largura variavel ali remexeria a coluna no meio
da rolagem (ver o comentario de `larguraPrevia`). 470 e o valor que sobrou: o
que o cartao de perfil pede, que e o maior pedido da tela.

**`var tab by remember(initialTab) { mutableStateOf(initialTab) }`**

ABA DE VOLTA. A rolagem unica (0.1.95) foi testada e reprovada pelo dono: a
secao trocava sozinha conforme a pagina descia, e o item aceso no menu
virava consequencia da rolagem em vez de escolha. Uma pagina por aba devolve
o controle — e o cartao grande, que veio junto, ja da a sensacao de
sobreposicao que o Discord tem sem precisar da rolagem continua.

**`val jaAnimaram = remember { mutableSetOf<SettingsTab>() }`**

Abas que ja fizeram a cascata NESTA visita as configuracoes.

O `remember` sem chave e o mecanismo inteiro: ele morre quando a tela sai da
composicao, ou seja, quando as configuracoes fecham. Dai sai de graca a regra
que o dono pediu — trocar de aba e voltar encontra a aba ja montada; fechar
as configuracoes e abrir de novo faz a cascata acontecer outra vez.

**`Box(…`**

Fundo do takeover = a MESMA aurora do shell, continua, por baixo (o dono
pediu "mesma aurora, no mesmo lugar independente da aba"). O shell segura a
aurora/estrelas montadas e esconde o proprio conteudo enquanto isto abre ->
nada vaza atrás. Aqui so um veu segura a leitura. Pintar aurora nova aqui
era o "salto de posição" ao abrir configurações (relogio independente).
Scrim mais escuro que o veu antigo: as configuracoes deixaram de tomar a
tela e viraram um CARTAO GRANDE por cima do shell (referencia do dono, o
Discord). Com o app aparecendo nas beiradas, o veu precisa empurrar o
fundo pra tras — senao os dois competem pela leitura.

**`Column(…`**

A LISTA ROLA; o título fica. Antes a coluna inteira era rígida e,
quando as abas passaram de onze, o Compose fez a única coisa que
podia: espremeu todas até o rótulo e o subtítulo se encostarem.
Aba achatada não é aba menor — é aba ilegível.

O `weight(1f)` é o que resolve: ele dá à lista exatamente a altura
que sobra do título, e o `verticalScroll` transforma o excesso em
rolagem em vez de compressão. Assim caber deixa de ser problema de
layout e vira problema do dedo, que é onde ele deve estar.
O RESPIRO ENTRE AS ABAS É O QUE AS SEPARA, já que cada uma é um
cartão com borda própria. Com 4dp as bordas de duas abas vizinhas
quase se encostavam e a lista lia como uma grade contínua — catorze
células empilhadas, não catorze destinos. 10dp devolve a cada aba um
contorno seu, e é o mesmo raciocínio que tirou os traços de dentro
das abas: quem separa é o espaço.

**`BoxWithConstraints(Modifier.weight(1f).fillMaxHeight())`**

Conteudo da secao — coluna capada (~720) estilo Discord: não esparrama
pelo palco todo (o "enxuto" que o dono pediu). O Box segura a coluna
encostada a esquerda; os controles leem como uma coluna so em vez de
soltos num vazao grande a direita. Titulo + fechar vivem dentro dela.

**`val larguraPrevia = LARGURA_DA_PREVIA`**

LARGURA DA PREVIA FIXA PRA PAGINA INTEIRA, e nao mais por aba.

Antes ela variava (a do Perfil e maior), e a coluna de conteudo era
calculada em cima dela. Numa pagina unica isso seria veneno: a
coluna inteira mudaria de largura no meio da rolagem, so porque a
secao do Perfil entrou na tela. Layout que se remexe enquanto se
rola e pior que uma previa 50dp menor em duas secoes.

**`val acoesDoCartao = remember { AcoesDoCartao() }`**

Ponte entre o FORMULÁRIO (dono das ações: tem o rascunho e hospeda os
diálogos de recorte) e a PRÉVIA (onde as imagens existem pra serem
clicadas). Os dois são irmãos nesta tela, e um portador mutável evita
mudar o cartão de lugar só pra dar acesso.

**`AnimatedContent(…`**

Troca de secao: SO fade. A entrada de verdade e a cascata, e ela
e vertical. O SizeTransform explicito continua sendo necessario —
o padrao dele e MOLA, e mola tem duracao proporcional a
distancia, entao a mesma troca de aba saia curta ou longa
conforme a diferenca de altura entre as duas.

**`val jaVisto = current in jaAnimaram`**

A CASCATA agora acontece quando a secao ENTRA NA TELA pela
primeira vez, e nao quando se troca de aba. O conjunto
`jaAnimaram` continua sendo o que impede o replay: a
LazyColumn descarta o item que sai da tela e o recompoe ao
voltar, entao sem ele a cascata tocaria de novo a cada
rolagem pra cima. Ele morre quando as configuracoes fecham —
exatamente a regra que o dono pediu.

**`BlocoDeAjustes(…`**

DOIS BLOCOS porque são dois escopos. O de cima manda no
balão desta máquina; o de baixo manda na conta inteira.
Antes só existia o de cima, e as preferências da conta
ficavam alcançáveis apenas pelo site — quem desligasse
"reações" por lá simplesmente parava de receber reação no
desktop, sem explicação e sem caminho de volta.

**`if (pinned)`**

Coluna fixa da direita: fechar em cima, previa embaixo. Ela nao
rola junto — e a previa acompanha a secao que esta NO TOPO da
rolagem, com um fade curto na troca. Sem o fade, rolar entre duas
secoes com previa trocaria o cartao de uma vez so, seco.

**`if (temPrevia(secao))`**

A regra de "esta secao tem previa?" vale AQUI
tambem. Ela existia so no ramo empilhado, entao na
coluna fixa Sessões, Permissões e Sobre mostravam o
rotulo "previa" com o vazio embaixo — anunciando
uma coisa que nao ha como existir nessas telas.

**`private fun temPrevia(tab: SettingsTab): Boolean = when (tab)`**

Sessões, Permissões, Sobre e Diagnostico sao listas e acoes — nao ha estado
visual pra antecipar. Uma unica regra pros DOIS jeitos de mostrar a previa
(empilhada embaixo do titulo e fixa na coluna da direita): duplicada, ela
divergiu, e foi assim que o rotulo "previa" apareceu sozinho nessas telas.

**`SettingsTab.ACCOUNT -> false`**

CONTA NÃO TEM MAIS PRÉVIA, e isso é o fim de uma sequência, não um buraco: a
coluna já teve um segundo cartão de perfil (cópia do que a aba vizinha mostra),
depois o estado da conta em quatro linhas (que desceram pro formulário, onde
tinham companhia) e por último o retrato. Sem o retrato não sobra nada que
precise de ESPAÇO em vez de largura, e prévia vazia é pior que prévia nenhuma.

**`@Composable`**

Previa ao vivo (lado das configs). Cada aba mostra o efeito real do que se
mexe: Conta = teu perfil como os OUTROS veem; Notificacoes = aviso deslizando
na bandeja; Aparencia = mini-janela no tema/fonte/densidade; Desempenho =
medidor de custo GPU/CPU; Voz = moldura da transmissão + nível do mic ao vivo.

**`Box`**

A PREVIA NAO RESPONDE AO PONTEIRO.

Ela e feita dos componentes DE VERDADE (o mesmo ProfileCard do popup, o
mesmo aviso da bandeja) — e era esse o objetivo, pra previa e realidade
nao divergirem. O efeito colateral e que os cliques deles vinham junto:
clicar no cartao da previa abria o perfil por cima das configuracoes.

O veu por cima resolve num lugar so. A alternativa seria uma bandeira
"sou previa" em cada componente compartilhado, espalhando pelo app inteiro
uma regra que e desta tela.

**`SettingsTab.SESSIONS, SettingsTab.ABOUT, SettingsTab.DIAGNOSTICS,`**

Sessões, Sobre e Permissões são listas/ações — não ha o que previsualizar.
Bots também não: lá o cartão de cada irmã JÁ é a prévia, e é nele
que se arrasta o enquadramento — uma segunda cópia à direita
mostraria a mesma coisa longe do controle que a muda.
Atalhos igual: a lista de teclas já é a própria demonstração.

**`if (tab != SettingsTab.PROFILE)`**

O VÉU NÃO COBRE A ABA PERFIL. Lá o cartão é EDITÁVEL — foto e banner
se trocam clicando neles — e engolir o ponteiro mataria exatamente a
interação que a aba passou a existir pra ter.

É seguro porque o motivo do véu não se aplica a este cartão: o
ProfileCard tem UM alvo clicável só (o "fechar" do canto do banner), e
ele é nulo na prévia, então nunca é desenhado. O que sobra é o clique
do CartaoDaPrevia — "ver em tamanho real" —, que continua valendo por
fora das duas imagens; nelas, o alvo de dentro consome primeiro.

**`@Composable`**

--- Previa do cartao de perfil. ---

Usa o MESMO composable do cartao de verdade (ProfileCard), nao uma copia. Ja
houve duas copias aqui e as duas divergiram do original — previa que mente e
pior que previa nenhuma.

As duas LADO A LADO (pedido do dono): cabem juntas, da pra comparar sem rolar,
e clicar numa abre ela no tamanho de verdade. Espremidas em meia largura elas
ficam apertadas de proposito — a previa serve pra ver a CARA do cartao; quem
quiser conferir detalhe clica.

draft = null -> perfil SALVO (aba Conta); draft != null -> rascunho ao vivo
(aba Perfil), campo a campo, antes de salvar.

**`var mutuais by remember(me.id) { mutableStateOf<List<MutualServerDto>>(emptyList()) }`**

POR QUE ISTO BUSCA DE NOVO um perfil que a tela ja tem: `me` vem de
/users/me, e la NAO existe "servidores em comum" — nao faria sentido a
rota que devolve voce mesmo calcular o que voce tem em comum com voce.
O cartao de verdade vem de /profile/{id}, que calcula. Resultado: a previa
desenhava um cartao mais CURTO que o real, faltando a secao inteira, e ela
existe justamente pra nao mentir. Uma chamada, uma vez, ao abrir.

**`Column(Modifier.fillMaxWidth())`**

UM cartao so, e grande. Eram dois lado a lado (completo e o do avatar), cada
um em metade da coluna: dois desenhos pequenos demais pra conferir qualquer
coisa, e o segundo mostrando um recorte do primeiro. Com a largura inteira o
completo aparece no tamanho em que da pra julgar foto, banner e bio — que e
pra isso que a previa existe. O cartao pequeno continua a um clique daqui.

**`@Composable`**

Rotulo + a MINIATURA clicavel.

O cartao e desenhado na largura de VERDADE e depois encolhido por escala, em
vez de ser desenhado apertado numa largura pequena. A diferenca importa: numa
largura pequena o texto quebra em outros lugares, o avatar fica gigante perto
do resto e a previa passa a mostrar um cartao que ninguem vai ver. Encolhido
por escala, e o cartao real visto de longe — proporcao intacta.

O clique fica na caixa de FORA: o cartao de verdade nao e clicavel inteiro, e
enfiar um clickable nele so pra previa mudaria o componente compartilhado por
causa de um caso de uso.

**`@Composable`**

"Testar" dispara um aviso de bandeja DE VERDADE — o mesmo caminho do aviso de
mensagem, nao um toast falso desenhado dentro do app. O que costuma falhar e
justamente o lado do SO (foco de assistencia, notificacao do app desativada no
Windows), e um toast interno passaria por cima disso e diria "funciona" quando
nao funciona.

Nao precisa minimizar antes: a regra "so avisa com a janela atras" mora no
shell, no ponto em que a mensagem chega — nao dentro do envio. Aqui chamamos o
envio direto, entao o aviso sai mesmo com o Astra na frente.

**`private const val PASSEIO_DO_AVISO = 14f`**

--- Notificacoes: o aviso entra, segura e sai — em loop.

SIMPLIFICADO (pedido do dono). Antes percorria 44dp em movimento LINEAR: um retângulo
atravessando a tela em velocidade constante, que é a assinatura de movimento
mecânico — nada no mundo começa e para instantaneamente na mesma velocidade.

Agora anda 14dp com curva: sai devagar no fim ao entrar, ganha velocidade ao sair.
A distância curta é o ponto — o aviso ASSENTA no lugar em vez de viajar até ele, e o
olho lê "chegou" sem precisar acompanhar a viagem.

reduceMotion trava parado e visível (respeita o ajuste de movimento). ---

**`@Composable`**

PRIVACIDADE. Hoje tem um item só, e ainda assim ganha aba própria: um
interruptor que conta aos outros o que você está usando não pode morar em
"Notificações" nem em "Conta". Onde uma configuração mora é parte do que ela
diz, e esta precisa dizer "isto é sobre o que sai de você".

**`@Composable`**

SALVA NO CLIQUE, sem botão de "salvar". É o comportamento certo pra um ajuste
de UMA escolha: não há rascunho pra revisar nem outro campo pra combinar, e um
filtro de privacidade que fica esperando confirmação é um filtro que a pessoa
pensa que ligou e não ligou.

Enquanto o servidor não responde, a escolha já aparece marcada (o `escolhido`
local): a alternativa é o rádio ficar parado meio segundo depois do clique, que
se lê como "não pegou". Se falhar, ele volta para onde estava e diz por quê.

**`Box(Modifier.fillMaxWidth())`**

O AVISO APARECE POR CIMA DE UMA CONVERSA, e não sozinho no vazio.

Antes era um balão solto num retângulo grande e vazio, e ele não mostrava a
única coisa que importa saber sobre um aviso de bandeja: que ele chega POR
CIMA do que estiver na tela. Com a conversa por baixo (a mesma mini-janela da
aba Aparência), a prévia passa a mostrar o comportamento, não só o balão — e
de quebra ocupa a coluna inteira em vez de deixar dois terços de vazio.

A conversa fica apagada de propósito: quem olha precisa ver o AVISO. Se as
duas camadas tivessem o mesmo peso, o olho não saberia qual delas a aba está
configurando.

**`if (discreto)`**

A prévia mostra o aviso QUE VAI SAIR, e não um aviso genérico: com
"sem conteúdo" ligado, ver aqui um nome e uma frase que o balão real
nunca vai ter é a prévia mentindo sobre a única coisa que ela existe
pra mostrar.

**`Column(…`**

As duas mensagens ganham superficie propria — a previa passa a imitar o
proprio shell (cabecalho, palco, campo de escrever em degraus), que e
exatamente o que ela promete mostrar. Os dois tracos que havia aqui
faziam a mini-janela parecer uma tabela de tres linhas.

**`@Composable`**

Medidor de nível do mic: abre um TargetDataLine (Java Sound) numa thread
daemon ENQUANTO O TESTE ESTA LIGADO, le o RMS dos samples e move as barras.
onDispose fecha a linha (parar o teste / troca de aba / fecha configurações).
Best-effort: sem mic ou em uso -> mostra aviso, não quebra.

**`val meterColor by animateColorAsState(…`**

Termometro de qualidade, não alarme: CINZA parado (teste desligado ou
silencio — antes ficava vermelho o tempo todo, como se algo estivesse
errado), AMBAR quando o sinal e fraco demais pra te ouvirem bem, VERDE
quando esta bom. Ambar = warning (fixo) e não accent: com o tema branco
padrao o accent e quase cinza e a faixa do meio some.
Anima a troca de cor pra não piscar seco entre faixas.

**`if (busyAvatar || busyBanner)`**

SEM CONTROLES DE IMAGEM AQUI. Foto e banner se editam no CARTÃO ao lado:
passar o mouse escurece a imagem e acende um lápis, o clique abre as opções.

A escolha é a mesma que o dono já tinha feito pro banner e agora vale pros
dois: a imagem só precisa aparecer uma vez na tela, e o lugar em que ela vale
é o cartão — lá ela está no tamanho e no contexto em que os outros vão vê-la.
Um retrato no formulário seria uma segunda cópia competindo com a primeira, e
uma fileira de ícones ao lado dele obrigaria a ler três rótulos pra descobrir
qual mexe na foto. Aqui fica só a frase que diz onde clicar.
SÓ O ESTADO, sem a explicação. As duas frases que moravam aqui ("passe o
mouse por cima e clique" e a das resoluções) saíram a pedido do dono, e o
cartão não perde nada com isso: passar o mouse já acende um véu com ícone de
lápis (ver FotoEditavel), que diz a mesma coisa sem ocupar linha. Resolução e
limite de tamanho eram detalhe de implementação — quem sobe uma foto quer ver
a foto, não saber em quantos pixels ela foi guardada.

**`r.onSuccess { img ->`**

Chega JA PREENCHENDO a faixa. O estatico e assado em 3,5:1 pelo
recorte e cai exato; o animado pula o recorte (recortar mataria a
animação) e vinha com zoom 100, que em Fit quer dizer "cabe
inteira" — e uma imagem 16:9 numa faixa 3,5:1 cabe inteira
ocupando metade da largura, com tarja preta dos lados.

**`ColorPickerButton(draft.bannerColor)`**

UM seletor so: o gradiente atravessa banner + corpo como uma peca (Discord).
Grava nas DUAS colunas (bannerColor e profileTheme) de proposito — o web e o
mobile ainda pintam a faixa e o corpo separados, entao escrever as duas
mantem a mesma cor em todo cliente em vez de deixar um deles pra tras.

**`else msg = saveErrorMessage(r.exceptionOrNull()) to false`**

O ERRO REAL do backend. "tenta de novo" era conselho ruim: as
causas comuns aqui (imagem grande demais -> 413, nome de
usuário em uso -> 409) não melhoram tentando de novo, e a
mensagem generica escondia justamente qual delas era.

**`private const val ZOOM_MIN = 50`**

Zoom do banner: trilha arrastavel simples, de 50% a 300%.

A FAIXA E A DO SERVIDOR, e nao um numero escolhido aqui. Ela ia de 0 a 300 e o
schema aceitava 50 a 200: passar de 200 (ou ficar abaixo de 50) fazia o servidor
recusar o PATCH INTEIRO com "Dados inválidos" — sumia o salvamento do nome e da
bio junto, sem dizer de qual campo. O teto virou 300 nos dois lados; o piso de 50
ficou, porque abaixo disso a imagem vira um ponto no meio da faixa.

**`@Composable`**

Modal de "redimensionar banner": mostra o MINI CARD (o que os outros veem) com a
imagem arrastavel + zoom. Vive FORA da coluna das configs, entao arrastar aqui
recompoe so este cartaozinho — não a aba inteira — e o gif do banner continua
animando (era o bug: o drag na previa recompunha a pagina toda e matava o ticker).
Trabalha em estado LOCAL (posY/scl) e so aplica no "salvar"; cancelar descarta.

**`@Composable`**

Campo de cor solida. Grava no MESMO campo do gradiente ("#rrggbb" e um valor
valido pro bannerBrush e pro web/mobile), entao escolher um gradiente depois
simplesmente sobrescreve. So aplica quando fecham 6 digitos: teclar no meio
não deve pintar um valor pela metade.

**`@Composable`**

A ABA CONTA NÃO TEM CAMPO NENHUM À MOSTRA. Dado é dado; formulário só aparece
quando alguém pediu para mudar alguma coisa.

A diferença não é de gosto. Três campos de senha abertos numa aba que a pessoa
abriu para CONFERIR o e-mail transformam a leitura em formulário: o olho procura
o que preencher, e a aba passa a parecer pendente mesmo quando não há nada a
fazer. Guardá-los atrás de "Editar" devolve à aba a função que ela tem — mostrar
o estado da conta — e dá ao formulário um começo e um fim claros.

SÓ A SENHA TEM BOTÃO, e isso é uma limitação real, não um esquecimento: a API
tem rota para trocar senha (`/api/auth/password`) e NÃO tem para trocar nome de
usuário ou e-mail. Botão que abre um formulário sem para onde enviar é pior que
linha sem botão — promete e falha depois do trabalho de preencher.

**`val emTransmissao by ModoTransmissao.ativo.collectAsState()`**

Em transmissão o e-mail vira máscara. É a única coisa desta aba que não é
pública: o @ todo mundo já vê, a senha nunca aparece. Máscara e não sumiço
porque a linha some do lugar e a aba muda de forma na frente de todo mundo —
e ainda dá pra conferir que é a conta certa pelo começo.

**`var sessoes by remember { mutableStateOf<Int?>(null) }`**

O ESTADO DA CONTA MORA AQUI, e não na coluna da prévia onde já morou: e-mail
conferido, sessões abertas e membro desde respondem perguntas que alguém abre
ESTA aba pra responder, e o cartão de linhas que já existia é onde elas sempre
pertenceram. Duas listas do mesmo assunto em duas colunas é que era o arranjo
estranho — e é por isso que a Conta hoje não tem prévia nenhuma.

**`@Composable`**

Uma linha de "Informações da conta": rótulo à esquerda, valor à direita, e o
botão só onde existe ação.

O valor fica colado no botão, e não espalhado pela largura, porque é ele que o
botão governa — separados pelas duas pontas da linha, viram duas colunas sem
relação e o olho precisa costurar as duas de volta.

**`@Composable`**

APAGAR CONTA. Fica no fim da aba, e isso é layout com opinião: é a última coisa
da última seção, longe de tudo que se clica por engano.

O que acontece está escrito ANTES do botão, e não num aviso depois do clique:
"some para sempre" e "suas mensagens continuam nas conversas" são as duas
perguntas que a pessoa tem, e responder só depois que ela decidiu é responder
tarde.

**`Text(…`**

UMA FRASE, e não os dois parágrafos de antes. O conteúdo que importa antes de
clicar cabe numa linha; o resto — que o texto escrito fica assinado "conta
apagada" — é explicado no passo de confirmação, que é onde a pessoa realmente
está decidindo. Explicar tudo antes do primeiro clique fazia ler dois blocos
pra descobrir onde ficava o botão.

**`BotaoIcone(Lucide.LogOut, "derrubar esta sessão", danger = true, ocupado = busy)`**

Só ícone: repete uma vez por linha e cada um está encostado na
sessão que derruba. É onde ícone puro mais compensa — o rótulo
repetido três vezes só empilhava ruído. E é reversível: quem
for derrubado por engano só entra de novo.

**`private const val PISO_DA_BUSCA = 1_800L`**

O botão de procurar atualização, com PISO DE TEMPO.

O piso é a funcionalidade, não um atraso enfeitando. A busca real termina em menos de
um segundo, e um "procurando" que pisca e some lê como botão quebrado — a pessoa clica
de novo achando que não funcionou. Com o piso, "tudo em dia" chega como RESPOSTA, e não
como ausência de resposta.

As duas frases nomeiam passos que de fato acontecem — consultar o repositório e
comparar as versões —, então o tempo é ganho e não enchido. Frase genérica ("aguarde…")
teria o custo do piso sem o proveito.

Se a busca demorar MAIS que o piso, não há espera extra: o piso é chão, não teto.

**`@Composable`**

Uma linha fina varrendo — o mesmo vocabulário da tela de atualização, que já usa barra
fina em vez de roda girando.

`tween` explícito e não mola: mola tem duração proporcional à distância, e a mesma
varredura sairia com ritmos diferentes conforme a largura do botão, sem nada no código
dizer isso.

**`@Composable`**

BOTÃO DE AÇÃO DESTRUTIVA. O `AboutButton` normal desenha borda apagada e texto
cinza — do lado dos campos de senha, "apagar minha conta" ficava com o MESMO
peso visual de "salvar", e a única coisa que separava as duas era ler o rótulo.

Aqui o vermelho está na borda e no texto em repouso, e só INVADE o fundo no
hover. Isso mantém a regra 60-30-10 da casa (cor em pouca área) e ainda assim
dá o susto certo no instante em que o ponteiro chega: quem passou por acidente
vê a superfície ficar vermelha antes de clicar.

**`.widthIn(max = 420.dp)`**

Campo de formulario (~420), NAO a coluna toda. A ordem importa:
widthIn ANTES de fillMaxWidth — invertido, o fillMaxWidth fixava a
largura no pai e o cap de 360 era reconstrangido de volta (era o bug
do input de senha esticando pelo eixo X inteiro).

**`@Composable`**

Aba Voz: qualidade da transmissão de tela (presets) + processamento do mic.
Seletor de dispositivo de audio. null = padrao do sistema — e a PRIMEIRA opcao
de proposito: e o que funciona pra maioria e o que o dono pediu ("seguir o
Windows"). Lista vazia (nenhum dispositivo achado) ainda mostra o padrao.

**`@Composable`**

TÍTULO QUE EXPLICA NO HOVER — ideia do dono, e ela resolve uma tensão real.

O pedido era "menos o que ler". A resposta preguiçosa seria apagar as
explicações; a boa é tirá-las do caminho SEM perdê-las. Quem já sabe o que a
seção faz lê três palavras e segue; quem não sabe passa o mouse no título e
recebe o parágrafo inteiro. A tela em repouso fica com a densidade de um índice,
e a informação continua a um gesto de distância.

O PONTINHO É OBRIGATÓRIO. Sem uma marca visível, a explicação só existiria pra
quem passasse o mouse por acaso — recurso escondido não é recurso. Ele é
discreto (4dp, cor terciária) e acende junto do título quando o ponteiro chega.

**`@Composable`**

Aba Permissões — a casa de quem já usava o Astra antes desta tela existir, ou
de quem passou reto pelas boas-vindas. A lista e a mesma de lá
(PainelDePermissoes); a diferença e o `detalhado`, que aqui mostra o estado até
das linhas certas: quem abre esta aba veio investigar, e "ouvindo normalmente
(Microfone Realtek)" e justamente o que responde "então o problema não é esse".

**`if (p.modoDeFala == ModoDeFala.APERTAR && p.teclaFalar == 0)`**

O MODO fica aqui (é comportamento de voz); as TECLAS foram pra aba Atalhos.
Quem escolhe "apertar para falar" precisa saber pra onde ir, e sem tecla
escolhida ninguém o ouve — por isso este é o único aviso que sobreviveu à
mudança, e só aparece quando ele é verdade.

**`ToggleRow("Cancelamento de eco", "evita o retorno do audio dos outros pelo seu mic", p.micEchoCancel, prefs::s`**

O ECO VEM PRIMEIRO PORQUE OS OUTROS DOIS DEPENDEM DELE, e a ordem na tela é a
única pista de graça que existe pra isso. No Windows os três tratamentos moram
dentro do MESMO objeto — o cancelador de eco. Sem ele no caminho, o microfone
entra cru: não existe "só supressão de ruído" pra oferecer.

**`@Composable`**

Escolher uma tecla apertando ela, e não caçando o nome numa lista de duzentas.

Quem lê a tecla é o PRÓPRIO gancho global, e não um `onKeyEvent` do Compose. Duas
razões: o gancho entrega o código virtual do Windows, que é exatamente o que vai
ser gravado (traduzir do código da JVM pro do Windows tem exceções que só
aparecem em teclado ABNT2); e a mesma peça que vai escutar a tecla depois é a que
escuta agora — se ela não estiver funcionando, você descobre aqui, escolhendo, e
não no meio de uma partida.
ABA ATALHOS. As três teclas moravam no fim da aba Voz, onde só as achava quem
tinha ido mexer no microfone — e a nota longa sobre o gancho do Windows ficava
entre elas e o resto. Aqui elas são o assunto, e a nota vira o rodapé.

As FIXAS entram como lista, e não como mais três `CapturaDeTecla` desligados:
Ctrl+K, Esc e Enter estão cravados no código de várias telas, e desenhá-los com
a mesma casca das configuráveis prometeria uma troca que não existe.

**`if (p.perfAutomatico.isNotBlank())`**

POR QUE O AJUSTE JÁ ESTAVA LIGADO. Sem esta linha, quem chega aqui encontra um
interruptor ligado que jura não ter ligado — e a explicação estava num cartão
que talvez já tenha sido dispensado. O cartão é o aviso; esta linha é o registro,
e ela fica enquanto o modo automático estiver valendo.

**`@Composable`**

Abrir junto com o Windows. Sem preferência local por trás: quem responde é o
próprio registro (ver InicioComWindows), que é o mesmo lugar que o Gerenciador
de Tarefas mexe. Se a pessoa desligar por lá, este interruptor já nasce
desligado na próxima vez que a aba abrir — em vez de afirmar uma coisa que o
Windows não vai cumprir.

**`@Composable`**

Escolha da placa de video.

So aparece em maquina com MAIS DE UMA placa. Com uma so nao ha escolha a fazer, e um
ajuste de uma opcao so e ruido: ocupa espaco, sugere que ha algo a decidir e nao
decide nada.

A LINHA SOBRE TRANSMITIR NAO E RESSALVA, e o ajuste inteiro. Quadro de captura de tela
nasce no aparelho da placa que desenha o monitor, e so ela consegue comprimi-lo -- pedir
pra outra nao da erro, da silencio. Entao a placa que nao desenha a tela e mostrada,
escolhivel para a interface, e dita como incapaz de transmitir, com o motivo. Esconder
isso deixaria a pessoa achando que escolheu e nao funcionou.

**`bloqueada = !placa.desenhaATela,`**

AVISAR E NAO DEIXAR (decisao do dono). A opcao continua visivel com o
motivo escrito: esconder faria a pessoa procurar pelo resto da vida por
um ajuste que ela jura ter visto, e nunca saber por que a placa boa nao
aparece.

**`@Composable`**

ABA PETS. O interruptor do bicho mora em ACESSIBILIDADE, e continua lá de
propósito: são duas perguntas diferentes. "Quero um bicho se mexendo na tela?" é
sobre movimento, e quem precisa desligar movimento procura isso em Acessibilidade,
não numa aba de gosto. "Qual bicho, de que cor e com que nome?" é gosto, e é o que
esta aba responde.

A ORDEM DA TELA É PROPOSITAL: palco, gestos, e só então as escolhas. Escolher
primeiro e ver depois obrigaria a decidir às cegas, que é exatamente o defeito que
esta aba existe pra corrigir — a diferença entre os três bichos não está na pose
parada, está em quantas reações cada um tem desenhada.

**`Box(…`**

Cartão, e não texto solto: numa aba inteira dedicada ao bicho, "ele está
desligado" é a informação mais importante da tela, e uma linha cinza no meio
do respiro passa batida. O palco continua abaixo — desligado não quer dizer
que não se possa escolher a cor pra quando ligar.

**`@Composable`**

ABA ACESSIBILIDADE — no espírito da do Discord (pedido do dono).

O que o Discord acerta e que aqui estava espalhado: acessibilidade é uma aba, não
um rodapé de "Aparência". Legibilidade, contraste e movimento são o mesmo assunto
— "consigo usar isto confortavelmente?" — e estavam em duas abas diferentes, com
"reduzir movimento" morando em Desempenho, onde ele parecia um ajuste de placa de
vídeo e não uma necessidade de quem passa mal com animação.

A prévia ao lado é a mini-janela do chat, e ela reage AO VIVO ao tamanho e à
densidade — mesma ideia da prévia do Discord: mexer no controle e ver a frase
mudar de tamanho responde melhor que qualquer rótulo em pixels.

**`private const val CASCATA_PASSO_MS = 40`**

Cascata de entrada pra conteudo ARBITRARIO.

O CascadeIn que ja existe (Bits.kt) precisa de um indice, porque nasceu pra
lista: quem chama esta dentro de um itemsIndexed e sabe quem e o item 3. Aqui
nao ha lista — cada secao de configuracao emite os proprios filhos, e sao os
filhos que devem entrar um a um. Este Layout resolve olhando os filhos DEPOIS
de medidos: cada um ganha o degrau seguinte de atraso.

Por que isso funciona: um @Composable que nao se embrulha em Column/Box emite
os nos direto no pai. AccountSection, VoiceSection e as outras sao assim, entao
o `measurables` daqui chega com os controles todos, separados.

LARGURA > 0 e o filtro que pula os Spacer verticais — Spacer(Modifier.height(x))
mede zero de largura. Sem ele, cada respiro entre controles gastaria um degrau e
a cascata sairia com buracos no ritmo.

O alpha e o deslocamento vao no placeWithLayer, ou seja, na fase de PLACEMENT:
o relogio avancando re-executa o posicionamento, nunca a recomposicao. Numa tela
com previa ao vivo, recompor 30 controles por frame seria bem caro.

**`val filhos = medidos.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }`**

minWidth = 0 TAMBEM, nao so minHeight. Este Layout recebe
fillMaxWidth(), ou seja, constraints com minWidth == maxWidth: repassar
isso pros filhos OBRIGA cada um a ocupar a largura inteira. Era por isto
que "derrubar todas as outras", "salvar" e "procurar atualizações"
apareciam esticados de ponta a ponta — um botao de texto curto medindo
700dp. Botao deve ter a largura do que ele diz.

**`val progresso = EaseOutSoft.transform(bruto)`**

O relogio mestre e LINEAR de proposito (ele so distribui o tempo);
a curva vive aqui, em cada filho. Sem ela cada controle subia com
velocidade constante e parava seco no fim — e isso que se sente
como cascata "dura". EaseOutSoft chega desacelerando.

**`private enum class FundoPref(val label: String)`**

Escolha de FUNDO. Não e uma preferencia nova: e a leitura conjunta de
auroraEnabled + starsEnabled como uma escada de custo. "Aurora sem estrelas"
era uma combinacao possivel que ninguem pedia, e cada combinacao a mais e uma
pergunta a mais pra quem so quer decidir como o app parece.
AURORA E ESTRELAS SAO INDEPENDENTES no `DesktopPrefs` — sempre foram. Quem
amarrava as duas era esta escada: "Aurora" acendia as estrelas junto, e nao
havia como pedir aurora SEM elas. O dono quis as duas soltas, entao a escada
ganhou o quarto degrau em vez de virar dois interruptores: quatro opcoes
nomeadas dizem o custo de cada escolha, dois interruptores fariam a pessoa
descobrir a combinacao cara sozinha.

**`@Composable`**

Quebra entre grupos de configuração — SÓ RESPIRO, sem traço nenhum.

O traço já encolheu uma vez: era de borda a borda, virou curto e centralizado
para deixar de ler como linha de tabela. Curto ele parou de fazer mal, mas
também parou de fazer bem — vinte e nove deles espalhados pelas abas viravam uma
pontilhação vertical que o olho conta ao descer a página. Quem separava de
verdade já era o espaço.

Traço continua existindo onde ele carrega informação: na barra lateral, onde
marca a fronteira entre grupos de destino diferente (`DivisoriaDaRail`). Ali há
o que separar. Dentro de uma aba, os títulos já dizem onde um assunto acaba.

Continua sendo uma função, e não um `Spacer` solto nas 29 chamadas, porque o
ritmo vertical das abas é uma decisão só e precisa continuar tendo um lugar.

**`FamiliaDeTema.entries.forEach { familia ->`**

AGRUPADO POR FAMILIA DE COR (escolha do dono). Quinze cartoes iguais em
duas colunas nao davam ao olho por onde comecar: achar o que se quer
exigia ler os quinze nomes, e nome de tema ("Nortada", "Véspera") nao diz
a cor. Com os grupos, a busca vira "quero algo frio" — que e como a
escolha acontece na cabeca de quem escolhe.

A ordem dos GRUPOS vem do enum, nao da lista de presets: assim a tela nao
depende de ninguem lembrar de manter a lista ordenada por familia ao
acrescentar um tema novo.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/ShellScreen.kt`

**`val sessionStore = remember { koin.get<SessionStore>() }`**

Permissões do Windows na PRIMEIRA abertura, pra quem JÁ TINHA conta — quem
cria conta agora vê a mesma lista dentro das boas-vindas (e o onDone de lá
marca esta pref, pra não mostrar duas vezes seguidas). Depois fica em
Configurações > Permissões. Vem antes da primeira call de propósito:
descobrir que o mic está bloqueado no meio da conversa é o pior momento.

**`var notifRefresh by remember { mutableStateOf(0) }`**

Badge do sino: o servidor AVISA (evento 'notification' na sala user:<id>), então
o badge sobe na hora em vez de esperar o próximo poll. O poll continua, mas
lento (2min) e so como rede de seguranca — ele e a fonte AUTORITATIVA da
contagem (corrige o palpite local e conta o que chegou com o app fechado).

**`val silencioso = runCatching`**

O SOM MORA NESTE EVENTO e não no do balão, por dois motivos. Primeiro:
é o único que carrega o `silent` do servidor — a resposta autoritativa
sobre não-perturbe e horário de descanso, calculada com as prefs da
conta e não com um palpite local. Segundo: `notification` só nasce
para o que é dirigido a você, então o som já herda a discrição certa
sem precisar de mais nenhuma condição.

Toca com a janela aberta também, de propósito: é assim que se percebe
que chegou algo em outra órbita sem estar olhando pra ela.

**`fun avisoDeTeste()`**

O aviso do botão "testar" das configurações. Ele reproduz o desvio do aviso
discreto de propósito: quem ligou o discreto e apertar testar precisa ver o
balão curto, e não o cartão — senão o teste promete uma coisa e a mensagem de
verdade entrega outra. Usa a própria foto porque é a única que o app tem certeza
de poder mostrar, e porque "é assim que vai aparecer" só se entende com um rosto
no lugar do rosto.

**`val avisosDaConta = remember { koin.get<AvisosDaConta>() }`**

Aviso quando chega mensagem com a janela fechada/minimizada/sem foco.

OS DOIS CAMINHOS AGORA TÊM AUTOR E CONTEÚDO. Sussurro sempre teve (o `new_dm`
carrega a mensagem inteira); canal não tinha, porque o `channel_activity` era só
`{ channelId, lastMessageAt }` — e por isso o aviso de canal dizia "nova
mensagem" e mais nada. O servidor passou a mandar quem escreveu, o trecho e a
foto no mesmo evento, filtrado por quem enxerga o canal.

NÃO PERTURBE E HORÁRIO DE DESCANSO CALAM A BANDEJA — e até agora não calavam
nada. O servidor já marcava `silent: true` no evento `notification` nos dois
casos, e o desktop nunca leu esse campo: dava pra pôr o status em "não
perturbe" e o balão do Windows pulava igual, de madrugada inclusive.

A decisão é tomada AQUI, com o relógio local, em vez de esperar o `silent`:
o balão nasce de `new_dm`/`channel_activity`, que são eventos diferentes do
`notification`. Amarrar um ao outro criaria dependência de ordem entre dois
caminhos que o servidor emite separados.

**`if (prefs.state.value.avisoDiscreto || ModoTransmissao.ativo.value)`**

Aviso discreto: nem quem escreveu, nem o que escreveu. Continua
dizendo QUE TIPO chegou — dá pra decidir se vale interromper o que
se está fazendo sem que a tela conte nada a quem estiver vendo.

O discreto continua saindo pelo BALÃO DO WINDOWS de propósito. O
cartão do Astra existe pra mostrar rosto, nome e trecho; sem nada
disso ele seria uma janela grande dizendo duas palavras — e uma
janela que só aparece é mais chamativa que o balão do sistema, o
oposto do que "discreto" pede.

**`if (estado.orbitaSilenciada(ev.channelId)) return@collect`**

SILÊNCIO DA ÓRBITA VALE AQUI, no cliente, e não tem como ser no
servidor: o `channel_activity` é o MESMO evento que acende a bolinha
de não-lido, e ele precisa chegar sempre. Filtrar na origem apagaria
o não-lido junto com o aviso — silenciar viraria "fingir que não
chegou mensagem", que é outra coisa.

**`val quem = ev.authorName`**

SEM AUTOR NO EVENTO, CAI PRO AVISO ANTIGO. Acontece com servidor
ainda não atualizado — e nesse caso o cartão do Astra mostraria um
rosto vazio e a palavra "alguém", que é pior que a linha honesta do
balão dizendo só o nome do canal.

**`val emSegundoPlano = !LocalWindowActive.current`**

Desempenho (Settings): reduzir movimento + prefs de render descem por
CompositionLocal. auroraOn/starsOn/reduceMotionEff já aplicam o modo
desempenho (kill-switch) por cima dos toggles individuais.
APP FORA DA FRENTE = "REDUZIR MOVIMENTO" LIGADO.

Medido: com o Astra atras de outra janela, congelar so o ceu levou o custo de 0,42
pra 0,28 nucleo — e a aurora ja estava comprovadamente parada (dois quadros com 4s
de intervalo diferiam em 15 pixels de 8600). Ou seja, o que sobrava nao era o ceu:
era o RESTO do movimento pedindo quadro, e cada quadro repinta a aurora inteira.
Ligando "reduzir movimento" no app todo, o mesmo cenario cai pra 0,003 nucleo.

O sinal vem do `LocalWindowActive`, que ja carrega visibilidade + foco (Main.kt).
Escolher este caminho, em vez de sair gateando animacao por animacao, tem uma razao
que vai valer pro codigo futuro: toda animacao nova ja nasce respeitando o
"reduzir movimento" por norma do projeto, entao ja nasce gratis em segundo plano.

**`val onbStore = remember { GlobalContext.get().get<SessionStore>() }`**

Aurora e estrelas NAO moram mais aqui: subiram pra janela (Main.kt), atrás
do login e do shell ao mesmo tempo. Sem isso a entrada saltava — a aurora
do login ocupava 45% da largura e a do shell 100%, e o uv do shader e
normalizado pelo tamanho, entao eram duas imagens diferentes. Uma so
instancia também significa um shader em vez de dois durante a transicao.
Paineis = cartoes flutuantes (estilo mobile): gap entre eles + cantos
arredondados deixam a aurora respirar nas juntas (impressao de
sobreposicao). Margem externa de 8dp separa do titulo/bordas da janela.
Escondidos enquanto o Settings (takeover) esta aberto: assim a UNICA aurora
do shell (montada acima) fica continua por baixo do Settings — sem aurora
nova, sem salto de posição ao trocar de aba. Crossfade rapido.
Checklist de 1o acesso (metade "checklist" do onboarding): so pra quem
acabou de passar pelo takeover (pref "checklist:<id>"=1). Risca sozinho
conforme cria constelação / manda sussurro; some ao completar os dois ou
no "pular".

**`val avisoDePerf = prefs.state.value.perfAutomatico`**

A VAGA DA SIDEBAR CABE OS DOIS. O aviso de máquina econômica vem PRIMEIRO
quando existe: ele explica por que a tela está diferente do que a pessoa viu
num vídeo ou no computador do amigo, e essa dúvida chega antes da vontade de
criar a primeira constelação.

**`val shellCoberto = settingsOpen || serverSettingsOpen`**

ESCONDER O SHELL NAO PODE SER TIRA-LO DA COMPOSICAO.

Aqui era um `AnimatedVisibility`, e ele nao esconde: ele DESCARTA. Terminado o
fade de saida, a subarvore inteira sai da composicao — rail, sidebar, palco,
conversa. Abrir as configuracoes matava o shell; fechar montava um shell novo.

O que se via era a cascata tocando de novo nas orbitas e nas mensagens, e essa
era a parte BARATA do estrago. A cara: o `ChatVm` nasce dentro do palco, com
`DisposableEffect { onDispose { chatVm.dispose() } }`. Descartado o shell, a
conversa aberta era destruida e RECARREGADA DO SERVIDOR na volta. Ida e volta
as configuracoes custava uma viagem de rede e uma remontagem completa, para
reexibir exatamente o que ja estava na tela.

Agora esconder e so ALPHA, e a subarvore nunca morre: a conversa continua viva
por baixo, nada e refeito, e voltar e instantaneo porque nao ha o que refazer.

O `drawWithContent` existe para que invisivel tambem seja BARATO: com alpha 0
o Skia ainda percorreria a arvore de desenho inteira por quadro. Pular o
`drawContent` corta a pintura sem tocar na composicao — que e exatamente a
divisao que se quer aqui.

E o "reduzir movimento" entra pelo mesmo motivo que ja vale para o app em
segundo plano (ver a nota do CompositionLocalProvider acima): shell vivo e
shell que continua pedindo quadro. Coberto pelas configuracoes ele nao tem
por que animar nada.

**`Box(…`**

O SHELL INTEIRO E UM CARTAO. Os paineis continuam encostados entre si —
o que mudou e a relacao com a JANELA: antes o conteudo morria colado na
moldura dos quatro lados. Agora ha 10dp de respiro em volta, e o conjunto
ganha canto e borda propria. O app le como uma peca apoiada no fundo em
vez de papel de parede.

O clip vai no bloco, nao em cada painel: e ele que arredonda as quatro
quinas de uma vez, e por isso a rail e o painel de membros podem seguir
quadrados por dentro sem vazar pra fora.

**`Column(Modifier.width(LARGURA_RAIL + LARGURA_SIDEBAR).fillMaxHeight())`**

COLUNA ESQUERDA: rail + sidebar em cima, e o rodape do usuario atravessando
as duas embaixo.

O caminho ate aqui, porque ele explica o desenho: o rodape atravessava a
rail desde o 0.1.92 e, do 0.1.97 em diante, ainda passava 26dp por cima do
PALCO. Os 26dp incomodavam; a travessia, nao. Tirar as duas de uma vez
deixou um vazio de 72dp no pe da rail, e foi ele que o dono viu. Entao:
atravessa a rail (o cartao tem a largura das duas colunas), e para na borda
da sidebar (nada de sobra por cima do palco).

**`botDoOutroLado = (chat as? ChatTarget.Dm)?.let { alvo ->`**

Chamada é coisa de gente. A bot é uma conta de verdade no banco, e por
isso o sussurro dela vinha com os mesmos dois botões de qualquer
sussurro — só que do outro lado não há ninguém pra atender, e a chamada
tocava até desistir sozinha. O servidor recusa também (dmCalls.ts):
esconder botão não é proibir ação.

**`joinedServerIds = remember(state.servers) { state.servers.map { it.id }.toSet() },`**

COLECAO MONTADA NA HORA E VENENO PRA RECOMPOSICAO.

`Set` e instavel pro Compose, entao ele so consegue pular quando
recebe A MESMA INSTANCIA (comparacao por identidade, que e como o
strong skipping trata instavel). Montado aqui dentro, o Set nasce
NOVO a cada recomposicao deste shell — e o shell recompoe a cada
mensagem, presenca, alguem digitando. Resultado: o palco inteiro
nunca pulava, por causa de um argumento que quase nunca muda.

Com o remember, a instancia so troca quando a lista de constelacoes
troca de verdade.

**`UserFooter(…`**

O RODAPE DO USUARIO E DESENHADO POR CIMA, na faixa que a coluna reservou.

Ele nao e filho da coluna: e irmao do Row inteiro, ancorado no canto
inferior esquerdo. Desenhar por cima (em Compose, quem vem por ultimo na
Box) e o que deixa o cartao ter borda propria sem empurrar a lista.

Largura = rail + sidebar. Ele atravessa a rail e PARA na borda da sidebar.

**`GatoDoAstra(…`**

O GATO. Fica ACIMA da conversa e ABAIXO de tudo que cobre a tela — telas
cheias e o card da call. Essa ordem é o recurso, não detalhe de arrumação.

Ele já morou na última camada, por cima de tudo. O efeito era o bicho
andando no ar sobre as configurações. Consertar zerando o chão quando o
rodapé saía criou o defeito seguinte: o gato PISCAVA a cada ida e volta,
porque nascia e morria junto da navegação.

Cobrir resolve os dois: ele nunca sai de cena, então não pisca; e quem vem
depois desenha por cima, então ele não aparece onde não deve.

POR ISSO ELE É O PRIMEIRO DAS CAMADAS DE CIMA, e não o último. Ele estava
entre as duas telas de configuração — depois da constelação e antes da
conta —, então cobria uma e era coberto pela outra. O mesmo bicho, com o
mesmo código, aparecia por cima em Configurações da Constelação e
corretamente escondido em Configurações da Conta. Ordem de irmãos num
`Box` é z-order, e uma camada no meio da pilha só está certa por acidente.

**`myPermissions = remember(state.myPerms) { state.myPerms?.permissions.orEmpty().toSet() },`**

isAdmin (cargo legado) concede o conjunto que o backend trata
como de admin; senao, so as permissões granulares dos cargos.
Mesmo caso do joinedServerIds acima: Set novo a cada
recomposicao impedia a tela de configuracoes de pular.

**`onTestarNotificacao = { avisoDeTeste() },`**

O teste usa o MESMO caminho do aviso de verdade, e agora isso quer
dizer o cartão do Astra — inclusive o desvio do aviso discreto, que
continua saindo pela bandeja do SO. Testar sempre pelo balão passou a
provar a coisa errada: ele deixou de ser por onde a mensagem chega.

**`convidarPelaFaixa?.let { alvo ->`**

Convite aberto pela faixa do banner. Mora AQUI, no Box de fora, e nao
junto do Sidebar: um Popup escrito dentro daquele Row conta como filho
pro Arrangement.spacedBy, entao o layout inteiro ganhava 8dp de
deslocamento no instante em que o dialogo abria. Popup nao ocupa espaco,
mas ocupa VAGA na contagem de filhos — e essa e a pegadinha.

Estado proprio (a rail tem o dela): sao dois pontos de entrada distantes
pro mesmo dialogo, e hoistar o da rail pra ca mexeria numa assinatura
que ja esta grande demais.

**`private const val USUARIO_DA_BOT = "astra_bot"`**

Largura dos dois paineis da esquerda. Viraram constantes porque agora TRES
lugares precisam concordar: a rail, a sidebar e a coluna que embrulha as duas
pra o rodape do usuario atravessar exatamente a soma delas.
A conta da bot. Uma so pras duas personas: o nome exibido e a foto trocam com o
turno (Sparkle / Sparxie), o `username` nao.

**`private fun Modifier.panelSurface(bg: Color, alpha: Float): Modifier =`**

Superficie do shell. Os quatro paineis (rail, sidebar, palco, membros) se
ENCOSTAM: nao ha folga entre eles, nem canto arredondado, nem borda.

Era o contrario — cada painel era um cartao flutuante com 8dp de respiro em
volta, e o respiro fazia o papel de separador. O dono apontou o problema: sobra
e espaco que nao carrega nada, e tres sobras somam ~24dp de largura que
poderiam estar mostrando conversa. Agora quem separa e a RAMPA DE ELEVACAO —
void na rail, base na sidebar, raised no palco. Mais claro = mais perto = mais
importante, que e a norma 2 do projeto e o que o Discord faz.

O canto arredondado da JANELA nao se perde: quem clipa e a raiz (windowShape,
em Main.kt), entao os paineis podem ser quadrados sem vazar pra fora.

**`@Composable`**

Confirmacao "Tem certeza?" reusavel: popup obsidiana no ponto, ação em danger.
Usada por todo delete/sair (canal, categoria, constelação, expulsar, banir,
logout). O chamador guarda um Boolean e renderiza isto quando true.

ATENCAO ao usar: sem `posicao`, o Popup ancora no CONTAINER onde ele foi
escrito, nao no botao que o abriu. Dentro de uma tela de configuracoes inteira
isso joga a caixinha no topo da pagina, longe do botao — foi o que aconteceu
com o regenerar convite. Se o chamador nao estiver colado no botao, passe
`posicao = AoLadoDoBotao` e escreva o ConfirmPopup DENTRO de um Box que
embrulhe so o botao (o Box e quem vira a ancora).

**`@Composable`**

Confirmacao CENTRAL (logout): scrim escurecido em tela cheia + card no centro,
entrada em escala+fade. Diferente do ConfirmPopup ancorado — aqui a decisao e
modal (sair da conta merece uma pausa). Clique no escurecido (fora do card)
cancela; o card engole o proprio clique pra não vazar.

**`Spacer(Modifier.height(2.dp))`**

2dp, e nao 12: a caixa do halo tem 72dp em volta de um botao de 44, entao
ja existem 14dp de vazio entre o icone e o fim dela. Somados aos 12 de
antes, o buraco ate o traco dava 26dp — que e o "espaco grande" que o dono
viu. Espaco de layout nao se conta pelo numero no Spacer, e sim pelo que
sobra depois que o conteudo se acomodou dentro da propria caixa.

**`if (isOwner || canManageSelected(srv.id))`**

"configurações" so pra quem manda: dono (o app já sabe pelo
ownerId) ou MANAGE_SERVER — este último so e conhecido na
constelação SELECIONADA, porque as permissões são buscadas
uma vez por selecao (buscar de todas seria N requisicoes no
boot). Clicar seleciona antes de abrir, entao a tela sempre
abre com as permissões certas em mao.

**`item(key = "descobrir")`**

A BUSSOLA ENTROU NA LISTA (pedido do dono): antes ela era ancorada no
rodape da rail, o que deixava um vazio enorme entre a ultima
constelacao e ela. Agora ela vem logo depois do "+", colada.

O preco assumido: com constelacao demais, ela sai da tela junto com a
lista. Vale porque Descobrir tambem esta no Ctrl+K, e um botao que
some quando ha muita coisa e melhor que um vazio permanente quando ha
pouca.

**`val shape = RoundedCornerShape(8.dp)`**

Quadrado de canto quebrado SEMPRE (pedido do dono). Antes o item nascia
circulo (22dp) e so virava quadrado no hover/ativo — o morph de forma fazia a
fila inteira parecer respirar quando o mouse passava de raspao. Agora a forma
e constante e so fundo e borda transicionam; 8dp e o mesmo raio dos botoes do
compositor e das linhas de navegacao, entao o rail para de falar sozinho.

**`@Composable`**

#13: cabecalho da constelação = faixa de banner (imagem, animavel via AstraImage)
com o nome em serifa por cima, legivel gracas a um scrim de baixo pra cima. Sem
banner: um degrade sobrio do tema no lugar da imagem. So constelação usa isto;
sussurros/descobrir seguem no header de texto.

MESMO desenho da previa das configuracoes: mesmo ProfileBanner, mesma proporcao
(ServerBannerAspect) e o mesmo enquadramento (positionY/scale) que o dono ajustou.
Antes era um AstraImage cru com ContentScale.Crop numa altura fixa de 104dp — ou
seja, outra proporcao E sem enquadramento nenhum, entao o que se via aqui nunca
batia com o que a previa prometia.
Botao de icone da faixa abaixo do banner. SEM MOLDURA NENHUMA: o mesmo gesto da
engrenagem e do sair no rodape — em repouso e so o icone apagado, e o fundo so
aparece debaixo do mouse.

Duas tentativas de moldura vieram antes. Primeiro cada botao com a sua: tres
retangulos iguais lado a lado, ruido de borda pra dizer uma coisa so ("aqui se
clica"). Depois uma moldura em volta do grupo, atravessando a beirada da sidebar
pra ler como cartao continuando por baixo — e ai os proprios icones e que
batiam na quina. Sobreposicao so funciona quando o que o corte atravessa e
superficie; conteudo cortado le como bug, porque e.

`aceso` (painel de membros aberto) fica com fundo de accent apagado e o icone no
accent. A pulsacao saiu junto com a borda: ela existia pra dar peso a uma linha
de 1px, e uma coisa piscando pra sempre num canto e o que a norma de movimento
manda evitar. Fundo preenchido diz "ligado" parado.

**`@Composable`**

A faixa logo abaixo do banner: nome da constelação, quantas pessoas ha, e as
acoes (convidar, membros, configuracoes).

O NOME MUDOU DE LUGAR. Vivia por cima da imagem, com um scrim escuro embaixo
tentando salvar a leitura — e banner claro ganhava do scrim. Aqui ele fica sobre
a obsidiana, sempre legivel, e de quebra a faixa deixa de ser dois botoes
flutuando num vazio.

A engrenagem so aparece pra quem manda. Ela existia so no menu de botao
direito da rail — atalho invisivel pra quem nao sabe que ele existe.

**`Text(…`**

"2/4 membros online", e nao "2 online · 4 membros". A fracao diz
as duas coisas de uma vez e cabe: a versao antiga repetia a
palavra online/membro em duas metades e estourava a largura da
faixa — o que se via de verdade era "1 online · 5 memb…".

**`@Composable`**

As tres acoes, soltas na faixa. Sem cartao em volta e sem sangria pra fora da
sidebar: quem separa esse grupo do texto a esquerda e o respiro, e quem diz "aqui
se clica" e o fundo que acende no hover. O espacamento de 2dp e o mesmo do par
engrenagem/sair do rodape, de proposito — sao a mesma coisa em dois lugares.

**`val forma = RoundedCornerShape(10.dp)`**

O banner virou CARTAO: recuado das bordas da sidebar, canto de 10dp e borda
fina. Antes ele sangrava de ponta a ponta e encostava no topo da janela —
uma imagem colada na moldura le como fundo de tela, nao como a capa da
constelacao. O `clipToBounds` do proprio clip e o que segura a imagem
dentro do canto arredondado.

**`AnimatedContent(…`**

Transicao ao trocar na rail (sussurros <-> constelação): header + lista
viram uma "pagina" que desliza de leve e faz fade. A pagina que sai
resolve o servidor pela PROPRIA selecao antiga (por isso a lista
inteira de servers entra aqui, não so o selecionado).

**`firstSteps?.let { fs ->`**

Checklist de 1o acesso, LOGO ACIMA do rodape. Ficava sobre o palco vazio,
onde tapava a arte e — pior — SUMIA assim que o usuário abria qualquer
órbita, justo enquanto ele cumpria os passos. Aqui ele acompanha o
caminho todo, e numa conta nova esta coluna esta vazia mesmo.

**`val pessoaPorId = remember(members) { members.associateBy { it.userId } }`**

Deriva a estrutura da sidebar (filtro/sort/groupBy) SO quando canais/categorias
mudam — não a cada recomposição. Sem isto, o poll de presença de voz (5s) e cada
mensagem em qualquer canal (state novo) refaziam tudo do zero. (Perf P0-2.)
Id vira pessoa por BUSCA DIRETA, e não varrendo a lista a cada linha de presença.
Numa constelação grande, `find` dentro do laço de quem está na call é uma varredura
por pessoa por órbita de voz — e ela se repetia a cada mensagem que chegasse em
qualquer canal, porque é isso que recompõe esta lista. Mesmo idioma do `VoiceView`.

**`key(ch.id)`**

POR QUE o indice aqui e LOCAL da categoria, e nao a linha da
lista inteira: a cascata so anima os primeiros CASCADE_MAX
itens (senao o ultimo de uma lista longa entraria segundos
depois). Com o indice global, toda orbita a partir da 15a
linha caia fora do limite e aparecia SECA — e era exatamente
o que se via ao abrir uma categoria mais pra baixo.
Local: uma categoria raramente passa de 14 orbitas, entao
todas animam. O lugar da categoria na lista vira um atraso de
largada, limitado a ~150ms pra que abrir no clique continue
parecendo resposta, e nao espera.
A chave inclui o colapso: reabrir toca a entrada de novo.
E o key(ch.id) faz o estado da animacao seguir a ORBITA, nao a
posicao — sem ele, colapsada->aberta reaproveitava o estado da
vizinha e uma entrava pronta enquanto a outra animava.

**`@Composable`**

A bolha flutuante (Popup em coords de janela, segue o cursor 1:1 — sem inercia).
Entrada = "gota" que coalesce (comeca alongada na vertical e assenta redonda, com
leve overshoot da mola); saida = "esparrama" (achata na horizontal e some), e so
entao reseta. Tudo em graphicsLayer com leitura DIFERIDA (scaleX/scaleY/alpha lidos
dentro do lambda de draw) -> so a camada re-renderiza por frame, sem recompor: leve.

**`val alvo = if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent`**

HOVER INSTANTANEO (pedido do dono), e o motivo e de percepcao, nao de gosto.

Realce de hover nao e uma transicao de estado da tela: e a RESPOSTA ao gesto de
apontar. Qualquer curva entre o cursor chegar e o realce acender e lida como
atraso do app, mesmo curta — e 120ms e o suficiente pra sensacao de peso, porque
o olho compara com o proprio movimento do mouse, que e continuo.

A SELECAO continua animada. Sao dois sinais diferentes: hover responde a onde
voce esta apontando (tem que ser imediato) e selecao conta que a pagina mudou
(a curva ajuda a nao piscar). Instantaneo nos dois faria a troca de orbita
estalar; animado nos dois faz o app parecer lento.

**`@Composable`**

Badge de não-lidas: circulo BRANCO com o numero preto (cap 99+), nao vermelho
e nao mais ambar (pedido do dono).

Branco funciona melhor do que parece: o accent de fabrica JA e um branco
quebrado, e o accent e configuravel — pintar o badge de accent fazia a
contagem trocar de cor junto com o tema, e num preset escuro ela quase sumia.
Branco puro sobre obsidiana e o maior contraste que a paleta tem, e e o unico
lugar do app que usa ele: por isso ele so pode aparecer onde importa.

`internal` (era private) porque as abas de Amigos passaram a usar o MESMO
badge. Contagem redonda e o vocabulario do app pra "isto tem um numero"; ter
duas versoes desse desenho seria garantir que uma delas envelhecesse sozinha.

`destaque` separa duas coisas que parecem iguais e nao sao: ambar cheio quer
dizer PRECISA DE VOCE (nao lida, pedido recebido); apagado e so um total, sem
pedido nenhum. Pintar todo numero de ambar dilui o unico sinal que o app tem
pra dizer "olha aqui" — e app que pisca por tudo ensina a ignorar o piscar.

**`val glow = if (LocalReduceMotion.current || !LocalWindowActive.current) null else`**

Pulso sutil (F6): o marcador "respira" devagar pra puxar o olho sem gritar.
Movimento reduzido / janela em segundo plano: fica aceso e parado. O valor e
lido DENTRO do graphicsLayer — antes o .value saia no corpo e recompunha o
item a cada frame, um clock por não-lida. (Auditoria de movimento, achado #2.)

**`val alvo = if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent`**

Hover instantaneo, MESMA regra da órbita (ver OrbitItem): apontar é
gesto, e a resposta ao gesto não pode ter curva. A seleção segue
animada, que é o outro sinal. As duas listas da barra lateral têm que
responder igual — o mesmo gesto no mesmo lugar da tela não pode ter
duas velocidades dependendo de a linha ser órbita ou sussurro.

**`Box`**

Hover na LINHA inteira dispara o anel 360 em volta da foto
(não so o hover direto na foto) -> a hitbox toda fica viva.

A bolinha de presenca fica NUM BOX POR FORA do avatar: o
DesktopAvatar clipa em circulo, e qualquer coisa desenhada
dentro dele seria cortada na borda da foto.

**`}`**

Sem divisoria. Ela era um traco de borda a borda no rodape de cada
card — padrao de TABELA, e o olho passa a ler a coluna como grade
em vez de gente. Quem separa agora e o respiro (o vertical padding
do Row dobrou) e o hover, que acende o fundo e desenha o limite do
card exatamente quando ele importa: na hora de clicar.

**`@Composable`**

Escolher PRA ONDE convidar. O menu de contexto não tem submenu generico, e um
dialogo aqui e mais honesto que uma lista escondida: da pra ver o ícone e o nome
de cada constelação antes de decidir. Adiciona pelo @usuario (a pessoa entra na
hora) — a mesma rota do "convidar pessoas" da rail.

**`val bareLanding = chat == null && voiceChannel == null`**

Top bar do palco. ESCONDIDO em QUALQUER tela vazia (nada aberto) — constelação OU
sussurros: ali o palco vira um componente so, com a animação central de fato no
centro (e, em constelação, os membros já na lateral). Estados com nome (órbita ou
sussurro aberto, voz) mantem o top bar. O botao de membros saiu.

**`TrocaDePagina(…`**

Troca de conversa em DOIS TEMPOS (ver TrocaDePagina.kt). Antes era um
AnimatedContent com fade cruzado, e as duas conversas — cada uma com seu
ChatVm, sua lista e suas imagens — desenhavam no mesmo frame. Era esse o
engasgo. Agora a antiga apaga primeiro e a nova so e composta depois.

**`key(target.id)`**

UM ChatVm POR CONVERSA — e o `key` e o que garante isso.

Sem ele, este `remember` guarda UM slot so na composicao: o
primeiro ChatVm criado na sessao era devolvido pra TODA conversa
aberta depois. Trocar de sussurro trocava o cabecalho e nada mais;
as mensagens continuavam vindo do VM da PRIMEIRA conversa, e o
"tentar de novo" pedia de novo o alvo dela, nao o que estava na
tela. Se essa primeira carga pegou o servidor dormindo e falhou,
o palco ficava vazio pro resto da sessao, em qualquer conversa,
com o servidor de pe — que e exatamente o sintoma relatado.

`openChat` nunca passa por nulo entre uma conversa e outra (ver
ShellVm), entao o ramo do `if` nunca era abandonado e o slot nunca
era descartado. So reiniciar o app dava um VM novo.

**`val forma = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 0.dp, bottomEnd = 0.dp)`**

PAINEL DE MEMBROS "POR CIMA": curva so nos dois cantos da ESQUERDA, mais uma
borda daquele lado. O lado direito continua colado na moldura do shell — e
justamente isso que faz ele parecer uma folha apoiada sobre a conversa em
vez de mais um cartao solto. Curvar os quatro cantos traria de volta a
gramatica flutuante que acabou de sair dos outros paineis.

**`fun online(uid: String) = uid == myId || presence[uid]?.let { it != "OFFLINE" } == true`**

Eu SEMPRE conto como online (estou olhando o app agora): a presenca do proprio
usuário nunca chega via socket (o broadcast do connect vai so pros OUTROS) e o
snapshot inicial pode ter pego antes do socket subir. Sem isso meu nome ficava
apagado mesmo online. Os demais vem da presenca real (o heartbeat a mantem viva).

**`val chave = HashMap<String, String>(members.size)`**

A chave de ordenacao sai UMA VEZ por membro, e nao dentro do comparador.

`sortedBy { nome.lowercase() }` parece inocente e nao e: o comparador roda O(n log n)
vezes e cada passada alocava uma String nova. Numa constelacao de cinquenta pessoas
isso e perto de mil Strings descartaveis -- a cada mudanca de presenca de qualquer
um, que e o evento mais frequente que existe aqui. O resultado e identico; o lixo
gerado, nao.

**`Column`**

Nome + (se houver) o que a pessoa está usando. A segunda linha
só existe quando há o que dizer: reservar altura pra ela sempre
deixaria a lista frouxa pra mostrar nada na maioria das vezes,
que é o caso normal — o recurso nasce desligado.

**`Row(verticalAlignment = Alignment.CenterVertically)`**

DESTAQUE POR PONTO, não por cor no texto (escolha do dono).

O accent entra em pouca área e por isso continua
significando algo — 60-30-10. Pintar o texto inteiro de
accent poria duas cores na mesma linha, disputando com o
nome colorido do cargo, que é o único uso de cor no painel
hoje. Duas cores competindo e nenhuma manda.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/StarField.kt`

**`private fun lcg(seed: Int): () -> Float`**

Campo de estrelas portado do mobile (StarField.kt do :app): estrelas fixas +
piscar + meteoros ("estrelas caindo"). Fica entre a aurora e os paineis, sutil.
DROPADO do mobile: tilt (acelerometro, não existe no desktop). MANTIDO: o
relogio com PAUSA sem foco (guardrail de perf) e o respeito ao reduzir
movimento (LocalReduceMotion) — congela num campo estatico, sem meteoros.

**`esperarPeloTeto(fpsCap.value, inicioDoQuadro)`**

Mesmo conserto da aurora: o teto DORME em vez de so deixar de emitir.
Pedir frame e o que custa (o flush do Direct3D esperando a GPU), nao
atualizar o valor — segurar a emissao e continuar pedindo frame nao
poupava nada. Ver o comentario longo em Aurora.kt.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/StickerPicker.kt`

**`@Composable`**

FIGURINHAS — painel do compositor.

Escolher JA ENVIA, como o GIF. Figurinha e uma reacao, nao um rascunho: obrigar
a escolher e depois apertar enviar poria um passo no meio de um gesto que quer
ser imediato.

Sem busca: o teto e 60 por constelacao e elas cabem na grade. Campo de busca
aqui seria peso de interface pra filtrar uma lista que ja se ve inteira.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/TelaCompartilhada.kt`

**`private const val SKSL_NV12 = """`**

A TELA DE OUTRA PESSOA, DESENHADA — o fim do caminho que começa na placa dela.

O QUADRO CHEGA EM NV12, e é de propósito. O decodificador do Windows não oferece RGB, e
converter no caminho custaria CPU: uma volta por pixel em 720p são 921 mil iterações
por quadro, umas trinta vezes por segundo. Medido no papel: 10 a 18% de um núcleo só
para trocar o arranjo das cores, numa máquina que já está decodificando.

AQUI A PLACA FAZ ISSO DE GRAÇA. O NV12 sobe como duas imagens de um canal só — o brilho
e a cor — e a conversão acontece no shader, um pixel por vez, em paralelo. O que a CPU
paga é copiar 1,4 MB por quadro em vez de converter 921 mil pixels; a diferença é de
uma ordem de grandeza, e a máquina fraca é justamente quem vai assistir.

E A BANDA ENTRE OS PROCESSOS CAI JUNTO: NV12 é 1,5 byte por pixel contra 4 do BGRA. Em
720p são 1,3 MB por quadro em vez de 3,5 MB. A escolha do formato paga duas vezes.

COMO O NV12 É ARRUMADO, porque o shader depende disso e não é óbvio:

brilho (Y)   uma amostra POR PIXEL — `altura` linhas de `passo` bytes
cor (UV)     uma amostra a cada 2x2 pixels, com U e V ALTERNADOS na mesma linha —
             `altura/2` linhas de `passo` bytes, logo depois do brilho

Ou seja: a linha de cor tem o mesmo comprimento em BYTES da linha de brilho, mas
metade dos pixels, porque cada pixel de cor ocupa dois bytes. É por isso que a conta de
coluna no shader é `floor(x/2)*2` e não `x/2`.

**`@Composable`**

Desenha a tela de [de], lida de [fonte], preenchendo o espaço sem distorcer.

NADA É DESENHADO enquanto não houver quadro — quem chama decide o que pôr no lugar.
Este componente não inventa estado vazio: ele sabe desenhar imagem, e só.

---- RECEBE O FLUXO, E NÃO O QUADRO, E ISSO É O PONTO DO ARQUIVO ----

Um parâmetro `quadro: QuadroDeTela?` obrigaria quem chama a observar o mapa de quadros
na COMPOSIÇÃO, e aí trinta quadros por segundo viram trinta recomposições por segundo
de tudo que estiver naquele escopo. Era o que acontecia: a tela de chamada inteira — a
legenda, a faixa de participantes, a conta de quem está no palco — refeita a cada quadro
que chegava de qualquer pessoa, para desenhar uma imagem que o Skia já sabia desenhar
sozinho.

Aqui o quadro vive num estado que é lido SÓ dentro do `drawBehind`. O Compose registra
leitura por fase: um estado lido apenas no desenho invalida apenas o desenho — a
composição e o layout nem acordam. O vídeo passa a custar o que um vídeo custa.

O par [fonte] + [de] em vez do quadro pronto também mantém a conta certa quando três
pessoas transmitem: o mapa muda a cada quadro de QUALQUER uma delas, mas `it[de]`
devolve a MESMA instância para quem não mandou nada, e igualdade estrutural segura a
invalidação ali mesmo.

**`val quadro = remember(de) { mutableStateOf(fonte.value[de]) }`**

Não é `collectAsState()` de propósito: aquele devolveria um `State` lido na
composição, que é exatamente o custo que este componente existe para não pagar.

NASCE COM O QUADRO QUE JÁ EXISTE, e a chave é `de`. O `LaunchedEffect` só roda
DEPOIS do primeiro desenho, então começar em nulo pintaria um quadro preto antes da
imagem — e, na troca de palco, um lampejo da tela da pessoa ANTERIOR, que é bem pior
que preto. Ler o valor atual do fluxo aqui fecha os dois casos de uma vez.

**`val anteriores = remember { arrayOfNulls<AutoCloseable>(5) }`**

As peças nativas do quadro anterior, guardadas para serem FECHADAS na hora.

Cada quadro cria duas imagens e um shader do Skia, e nenhum deles é memória da
JVM: some quando o coletor roda o limpador, em lote, muito depois. A 30 quadros por
segundo isso vira um engasgo periódico com cara de "a imagem cortou do nada" — foi
exatamente esse o defeito da aurora. Fechar o anterior antes de trocar torna a
liberação determinística.

**`val bytesDoBrilho = q.passo * q.altura`**

O plano de brilho e o de cor são fatias do MESMO vetor, e viajam para o
Skia por `Data` com deslocamento — não por `copyOfRange`. A diferença
não é estilo: `copyOfRange` alocaria 1,4 MB na JVM por quadro, quarenta
megabytes por segundo de lixo, no app em que já se lutou para segurar a
memória. O `Data` copia uma vez, para dentro do Skia, e é a cópia que
liberta o rodízio de vetores do cano a seguir em frente.

**`ImageInfo(q.largura, q.altura / 2, ColorType.ALPHA_8, ColorAlphaType.OPAQUE),`**

LARGURA CHEIA E ALTURA PELA METADE: a linha de cor tem o mesmo
comprimento em bytes da de brilho, mas cada pixel de cor ocupa
dois (U e V). Declarar metade da largura aqui faria o shader
ler a cor de um lugar que não existe.

**`val vizinho = FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)`**

AMOSTRAGEM VIZINHA nos dois planos, e não linear. Interpolar aqui
misturaria amostras de cor de blocos 2x2 vizinhos ANTES da conversão,
e o resultado é franja colorida nas bordas de contraste — texto branco
sobre fundo escuro, que é o conteúdo mais comum de tela compartilhada.

**`val escala = minOf(size.width / q.largura, size.height / q.altura)`**

A ESCALA VAI NO CANVAS, e o retângulo é desenhado no tamanho do quadro.
Assim as coordenadas que chegam ao shader são PIXELS DA IMAGEM, e as
contas de plano de cor fecham em número inteiro. Escalar o retângulo em
vez do canvas faria `p` chegar na escala da tela, e a conta de coluna
passaria a pular ou repetir amostras conforme o tamanho da janela.

**`for (i in anteriores.indices)`**

FECHA OS CINCO DO QUADRO ANTERIOR, e os cinco importam: as duas imagens,
os dois shaders que saem delas, e o shader do efeito. Esquecer os de
dentro é o vazamento silencioso — eles não aparecem em heap nenhum
porque não são memória da JVM.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/TextContextMenu.kt`

**`object AstraTextContextMenu : ContextMenuRepresentation`**

Botao direito DENTRO de campo de texto (Recortar/Copiar/Colar/Selecionar tudo).

Esse menu não era nosso: sem ninguem fornecer o LocalContextMenuRepresentation, o
Compose usa o `LightDefaultContextMenuRepresentation` — cartao BRANCO de canto
reto, que num app obsidiana aparece como um retangulo de luz no meio da tela e
ignora completamente o tema que a pessoa escolheu em Aparencia.

Aqui so trocamos a REPRESENTACAO: os itens, os atalhos e a traducao continuam
vindo do Compose (por isso saem em portugues junto com o resto do sistema). O
desenho reusa o MenuCard do EditorialMenu — o mesmo cartao dos menus de canal,
mensagem e membro. Reusar em vez de copiar e o que garante que os dois nunca
divirjam, e faz o menu herdar de graca os tokens reativos do Obsidian: mudou o
accent/fundo em Settings > Aparencia, esse menu muda junto, sem reiniciar.

**`private fun iconeDaAcao(rotulo: String): ImageVector? = when (rotulo.trim().lowercase())`**

O ÍCONE DE CADA AÇÃO, DESCOBERTO PELO RÓTULO — e é feio, mas é o que dá.

Os itens deste menu não são nossos: vêm do Compose, junto com a tradução e os atalhos
(é por isso que saem em português sem ninguém traduzir nada aqui). E `ContextMenuItem`
expõe DUAS coisas, `label` e `onClick` — não há tipo, não há identificador, não há
enumeração. Casar pelo texto é a única porta que existe.

DAÍ A LISTA COBRIR PORTUGUÊS E INGLÊS: o rótulo sai no idioma do sistema, e uma máquina
com Windows em inglês mostraria "Cut/Copy/Paste" — que num mapa só em português cairia
no `null` e voltaria ao menu sem ícone. Nenhum estrago, mas também nenhum ícone, e o
motivo seria invisível.

O `null` é resposta legítima e não falha: item desconhecido (o Compose pode ganhar
outros, e campos diferentes oferecem conjuntos diferentes) aparece só com o texto, que
é exatamente como este menu era antes.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/theme/Obsidian.kt`

**`object Obsidian`**

Tokens obsidiana do desktop — agora REATIVOS. Os campos de cor que dependem do
tema (accent + rampa de fundo) são mutableStateOf, entao os ~300 usos
`Obsidian.xxx` dentro de @Composable recompoem sozinhos quando o tema muda. Os
call sites não mudam. apply() deriva a paleta do par (accentId, bgId) escolhido
em Settings > Aparencia (mesma logica do buildAstraColors do mobile). text/border
/status ficam fixos (funcionam em qualquer fundo escuro).

**`var borderDim by mutableStateOf(Color(0xFF363741))`**

AS BORDAS SEGUEM O TEMA. Eram fixas (#363741 e #494A54) e por isso a mesma
linha azul-acinzentada aparecia por cima de qualquer fundo: no tema Eclipse,
de vinho, o cartao ficava contornado por uma cor fria que nao existia em
lugar nenhum da tela — parecia recortado de outro app.

Elas nao ganharam cor propria: saem do MESMO `raised` do tema. Assim o tom
acompanha o fundo de graca, e nao entra cor nova no sistema pra resolver
hierarquia (que e o que as normas do produto proibem).

NAO E `lift`, E `clarear`, E A DIFERENCA E O BUG QUE ISTO CONSERTA. A primeira
versao somava a mesma quantidade nos tres canais. Somar preserva a diferenca
ABSOLUTA entre eles e destroi a RELATIVA: o raised da Aurora (#0C1A10) mais
0,145 vira #313F35 — verde na conta, cinza no olho, porque 14/255 de vantagem
do verde sobre um nivel alto nao se enxerga. Multiplicar ANTES de somar
preserva a proporcao, e a cor sobrevive ao clareamento.

O passo foi calibrado pra Obsidiana continuar em ~1,6:1 contra o `raised`,
igual ao que era. Borda de 1dp entre duas superficies e separador, nao
componente — perseguir os 3:1 de UI aqui desenharia um risco duro em volta de
cada cartao, que e exatamente o que o app evita.

**`var text1 by mutableStateOf(TEXT1_PADRAO)`**

Fixos (independentes do tema).

text1 desceu de #F5F5F7 pra #E4E4EB, e o motivo NAO e contraste — e o
contrario dele. Sobre o void (#06060E), o valor antigo dava ~19:1, quase o
dobro do que a norma pede pra texto pequeno. Contraste ALTO DEMAIS em fundo
escuro produz halacao: a borda clara da letra parece vibrar, e o efeito
aparece justamente em quem passa horas no app a noite — que e o uso real
daqui. O valor novo continua em ~15:1, folgado acima do minimo de 4,5:1.

Se algum dia isto parecer apagado demais, o caminho e subir ESTE numero, e
nao mexer no fundo: a rampa de elevacao inteira e calibrada a partir do void.
VIRARAM `var` POR CAUSA DO ALTO CONTRASTE, e o parágrafo acima continua
valendo inteiro: ele descreve o PADRÃO, que não mudou. A halação é real e é
por isso que ninguém é empurrado pra cima dela.

Mas "o padrão é calibrado pra sessão longa à noite" e "existe gente que não
enxerga esse padrão" são duas verdades ao mesmo tempo, e a segunda não tem
escapatória sem isto. Quem liga o alto contraste está dizendo que troca o
conforto pela legibilidade — e essa troca é dela, não minha.

**`aplicarContraste(altoContraste)`**

As bordas saem daqui E do contraste, então quem manda nelas é uma função
só. Sem isto, trocar de tema com alto contraste ligado devolveria as
bordas fracas em silêncio — e ninguém liga o alto contraste de novo pra
testar se ele sobreviveu à troca de cor.

**`fun aplicarContraste(alto: Boolean)`**

ALTO CONTRASTE. Sobe texto e borda; NÃO mexe no fundo, e isso é regra: a
rampa de elevação inteira (void → active) é calibrada a partir do void, e
clarear o fundo pra ganhar contraste destruiria a hierarquia que ela existe
pra criar — o remédio apagaria a estrutura da tela.

text3 é o que mais sobe. Ele é o terciário, o primeiro a sumir pra quem tem
baixa visão, e no padrão ele vive perto do piso de propósito.

**`private fun clarear(c: Color, ganho: Float, piso: Float): Color = Color(…`**

Clareia MANTENDO a cor. `lift` soma o mesmo valor nos tres canais, o que preserva
a diferenca absoluta entre eles e afunda a relativa: sobre um nivel alto, os
14/255 de vantagem do verde da Aurora deixam de ser enxergados e a borda le como
cinza. Multiplicar antes de somar mantem a proporcao entre os canais — o ganho
e o mesmo, a cor sobrevive.

Serve pras BORDAS. A rampa de superficie continua no `lift`, e de proposito: ali
o que se quer e justamente subir um degrau sem mexer no tom do fundo.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/theme/ThemeOptions.kt`

**`enum class FamiliaDeTema(val titulo: String)`**

FAMILIA DE COR — como os quinze temas se apresentam na tela.

Quinze cartoes iguais em duas colunas nao dao ao olho por onde comecar: a unica
forma de achar o que se quer e ler os quinze nomes, e nome de tema ("Nortada",
"Véspera") nao diz a cor. Agrupar troca a busca de "leio tudo" por "quero algo
frio" — que e como a escolha acontece na cabeca de quem escolhe.

Tres grupos e nao cinco de proposito: com poucos itens por grupo, o titulo custa
mais atencao do que economiza. Neutro/quente/frio e o corte mais grosso que ainda
e util, e cabe em tres respiros.

**`val ThemePresets = listOf(…`**

A ORDEM DA LISTA NAO E MAIS A ORDEM DA TELA: a tela agrupa por familia (ver
FamiliaDeTema). Esta lista fica na ordem HISTORICA — os sete originais primeiro,
depois os oito da pesquisa de paletas —, porque e assim que ela se le junto do
comentario que explica de onde cada leva veio.

**`ThemePreset("nortada", "Nortada", "Ardósia no gelo", "slate", "arctic", FamiliaDeTema.NEUTRO),`**

As oito da pesquisa de paletas (09/08/2026). Cada uma nasceu de uma familia
que as pessoas ja escolhem de verdade — Nord, Gruvbox, Tokyo Night, Rose Pine,
Solarized, Everforest, Dracula, One Dark — e nao de gosto proprio.

Todas passam o piso de 4,5:1 nos quatro pares que importam (text1 sobre o
void; text2, text3 e o acento sobre o raised), com a conta feita, nao no olho.
A mais apertada e a Equinócio: 4,75:1 do acento sobre o raised.

Nenhuma delas inventa cor de borda: desde que as bordas passaram a derivar do
`raised` (ver Obsidian.kt), cada preset novo ja nasce com a linha na
temperatura certa.
Nortada e Véspera sao NEUTROS apesar do azul no nome: ardosia e cobalto
discreto pesam como cinza na tela, e e o peso que a pessoa procura quando
procura neutro — nao a familia tecnica da cor.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/theme/Type.kt`

**`@OptIn(ExperimentalTextApi::class)`**

OS TÍTULOS SÃO CORMORANT, e o nome da variável continua `DmSerif` de propósito —
ela é usada em umas quarenta telas, e renomear tudo seria um diff enorme sobre uma
troca que é de arquivo, não de papel. O papel é o mesmo: a serifa editorial dos
títulos. (Se um dia isto incomodar, é um renomear mecânico e seguro; hoje não paga.)

POR QUE TROCAR. O DM Serif Display é uma boa serifa de display e é genérica: ela
serve a qualquer produto editorial. A Cormorant tem contraste grosso/fino extremo,
terminais em gota e ascendentes muito altos — em corpo grande o fio fino é
literalmente a estética da gravura em cobre dos atlas estelares, que é o vocabulário
do produto (constelação, órbita) aparecendo na forma da letra e não só na palavra.

O PESO PADRÃO É 600, E ISSO NÃO É GOSTO. A instância padrão da fonte variável é
LIGHT (300) — o próprio arquivo se chama "Cormorant Light". A 300, num fundo
#06060E, o traço fino da Cormorant desaparece: contraste alto de desenho encontra
contraste alto de fundo e a haste some. Foi medido em título de 15sp, que é o
tamanho real de "notificações" no painel do sino.

Mapear `Normal` para o eixo em 600 resolve os dois lados de uma vez: mantém a cor
tipográfica parecida com a do DM Serif Display (que só tem um peso, e ele é cheio),
então nenhuma das telas existentes precisou ser tocada; e deixa os pesos reais
disponíveis para quem quiser um título mais leve num tamanho grande, onde o fio fino
vira qualidade em vez de defeito.

**`val Cinzel = FontFamily(Font(resource = "font/cinzel.ttf"))`**

O LETREIRO "ASTRA" — e só ele.

Cinzel é desenhada sobre as capitais de INSCRIÇÃO romanas, as talhadas em pedra:
só caixa-alta, serifa fina e afiada, proporção clássica. Com tracking largo ela lê
como letreiro na fachada de um observatório, que é exatamente o registro do
produto — frio, cerimonial, adulto.

SAIU A BABYLONICA, que é escrita à mão de laçada larga. Ela assinava bonito, mas
assinava — e assinatura à mão diz "feito por uma pessoa", enquanto o Astra quer
dizer "instrumento". Eram duas vozes brigando: o resto do app é editorial e frio,
e o letreiro era caloroso e manuscrito.

OS CORPOS FORAM MEDIDOS, não estimados, porque as duas ocupam o corpo de formas
muito diferentes: a Babylonica gasta metade da altura em laçada e a Cinzel é toda
letra. Pela LARGURA da palavra, que é o que o olho compara num letreiro:

Babylonica 72sp -> 138px de largura     (era o da entrada)
Cinzel     44sp -> 140px                 (o equivalente)
Babylonica 32sp ->  61px                (era o da atualização)
Cinzel     20sp ->  64px

Os corpos que entraram são um pouco maiores que esses porque o tracking largo, que
é metade do efeito, precisa de onde caber.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/TitleBar.kt`

**`atualizacao?.let { PontoDeAtualizacao(it) }`**

Aviso de atualização: um PONTO à esquerda da lupa, e o card se abre
na horizontal a partir dele (pedido do dono). Fica aqui e não no
rodapé porque a barra é onde já moram os avisos do app — o canto
inferior era um segundo lugar pra "olhe isto" sem nada que ligasse
um ao outro.

**`@Composable`**

PONTO DE ATUALIZACAO + card horizontal.

O card antigo morava no canto inferior direito com 240dp de largura e tres
linhas. Aqui ele nao cabe em altura, entao vira uma FAIXA: uma linha so, mais
larga, na altura da barra. Nada de conteudo se perdeu — o que era empilhado
virou sequencia, que e o que uma barra comporta.

O ponto vem ANTES do card (pedido do dono): fechado, o aviso ocupa 7dp e nao
disputa com nada; aberto, ele se desenrola na horizontal a partir dali. Um card
permanentemente aberto na barra seria uma tarja fixa dizendo a mesma coisa o dia
inteiro — e aviso que nao se pode encolher vira moldura.

Cresce PRA ESQUERDA (expandFrom = End) porque a direita esta ocupada pela lupa,
pelo sino e pelos botoes da janela. Crescer pra cima deles cobriria controles em
uso; a esquerda e o vazio da barra, que existe justamente pra isso.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/TrocaDePagina.kt`

**`private const val SAIDA_MS = 80`**

TROCA DE PAGINA EM DOIS TEMPOS.

O AnimatedContent nao servia aqui, e o motivo importa: ele compoe o conteudo
NOVO ja no primeiro frame da saida. Durante a transicao existiam duas paginas
vivas ao mesmo tempo — duas listas de mensagem desenhando, o ChatVm novo abrindo
socket e rede, as imagens decodificando — tudo espremido nos mesmos ~11 frames.
Era isso que engasgava, nao o custo da animacao.

Atrasar o fadeIn nao resolveria: adia o DESENHO, nao a composicao.

Aqui a pagina antiga apaga primeiro, e so entao a nova e composta. O frame caro
cai numa janela em que a tela esta apagada e nada esta se movendo — um frame
perdido ali e literalmente invisivel. A entrada comeca depois disso, com o
trabalho pesado ja feito.
Tempos apertados ate o limite do que ainda le como transicao e nao como piscada.

A saida e MAIS CURTA que a entrada de proposito: sumir e lido como instantaneo
pelo olho, aparecer nao — cortar a entrada pro mesmo tempo da saida faz a pagina
"estalar" na tela. 80 + 150 com dois frames de respiro no meio da ~265ms porta a
porta, contra os ~300ms do fade cruzado antigo — e sem os frames perdidos, que
eram o que fazia parecer lento mesmo sendo curto.

**`withFrameNanos {}`**

Dois frames de respiro com a tela apagada: um pra composicao da pagina
nova acontecer, outro pro primeiro layout/desenho dela assentar. Sem
isto o frame caro vira o PRIMEIRO frame da entrada — e o engasgo so
troca de lado.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/UpdateUi.kt`

**`@Composable`**

---- Gate de boot (estilo Discord): janelinha que verifica a versão ----
Logo do Astra no centro com estrelas orbitando (sensacao de carregando) sobre o
ceu do app. A barra segmentada embaixo mostra o download quando ha um; senao
varre. Atualizado/falha/timeout seguem pro app. Janela pequena e frameless.

**`val gateStart = remember { System.currentTimeMillis() }`**

PISO, e nao duracao. O portao dura o que a verificacao de versao durar; isto aqui
so garante que ele nao PISQUE quando a resposta vem instantanea.

Era 4800ms fixos, pedido pra dar tempo de aproveitar a animação. Medido tres vezes
na maquina do dono: o Astra instalado abria em ~9,3s, dos quais ~4,8s eram esta
espera — metade do carregamento era decoracao esperando a si mesma. Com o piso de
1,2s a abertura cai pra ~4,5s. A entrada da constelação foi encurtada junto (abaixo)
pra caber: animação curta inteira e melhor que animação longa cortada no meio.

**`val entrance: State<Float>? = if (reduceMotion) null else`**

Entrada "constelação se forma", toca UMA vez ao abrir o gate: as 14
estrelas do anel nascem espalhadas e convergem pra órbita (ver
RotatingStarsLogo). null = direto no estado final, sem stagger — e o que
reduceMotion pede e também o default dos outros lugares que usam o logo
(login/onboarding), que não tocam essa entrada.

**`val rotulo = when (val s = st)`**

Palavra acima da barra: nos estados reais, o que esta havendo; no hold comum, uma
palavra "espacial" que avanca junto com o preenchimento (pedido do dono).

A FRASE e a PORCENTAGEM andam separadas de proposito. Elas tem ritmos
opostos: a frase muda 5 vezes na tela inteira, a porcentagem muda 100 vezes
so no download. Enquanto as duas moravam na MESMA String, cada 1% virava um
targetState novo e remontava a animacao — a frase saia antes de terminar de
entrar e nunca dava pra ler nada. Agora o numero anda por fora e atualiza no
lugar: numero trocando no lugar e o esperado, texto piscando le como falha.

**`StarField(Modifier.fillMaxSize())`**

Fundo: o gate era logo + preto liso. Ganha o MESMO ceu do app (estrelas
fixas, piscar e meteoros) e um halo atrás do planeta — reuso do StarField
que já existe, não arte nova. Aurora ficou de fora de proposito: e um
shader por pixel e isto e a tela de BOOT, tem que abrir na hora.

**`@Composable`**

Barra de progresso FINA e minimalista (pedido do dono): um trilho de 2dp que
enche da esquerda pra direita, com uma palavra "espacial" centralizada acima.
No download o preenchimento e o progresso REAL; no hold comum e a barra sintetica
que sobe devagar. Canvas (não Box) pra uma única passada numa tela que abre na hora.

**`Box(Modifier.dissolverNasBordas(ZONA_ESCURA))`**

As palavras trocavam por corte seco, o que numa tela parada e a unica
coisa que se move — lia como falha de renderizacao. Agora a que sai
desliza pra DIREITA e some no escuro, e a proxima nasce do escuro pela
ESQUERDA: uma passa pela outra, como um letreiro.

A entrada espera a saida terminar (delay = duracao da saida). Cruzadas,
as duas frases se sobrepoem no mesmo ponto e viram borrao ilegivel.

**`Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterEnd)`**

Largura FIXA: "7%" e "100%" tem que ocupar o mesmo espaco.
Como a linha inteira e centralizada, sem isso ela andaria
de lado a cada ponto percentual — o tremor que estamos
justamente tirando daqui.

**`private fun Modifier.dissolverNasBordas(zona: Dp): Modifier = this`**

Dissolve nas bordas: um gradiente horizontal aplicado com DstIn zera o alpha do
que chega perto das laterais. A palavra entra e sai deslizando POR BAIXO dessa
mascara, entao ela some no escuro em vez de ser cortada na borda da caixa.

CompositingStrategy.Offscreen e OBRIGATORIO: sem ele o DstIn apagaria tambem o
que ja esta pintado embaixo (o ceu de estrelas do gate), abrindo um buraco.
O recorte da camada cai exatamente onde o gradiente ja chegou a zero — o que
fica de fora ja era invisivel, entao o corte nao aparece.

**`private val SPACE_WORDS = listOf(…`**

Palavras "espaciais" que trocam conforme a barra enche — dao a sensacao de que
algo esta sendo feito. Acentuadas: a convencao de ASCII vale pra COMENTARIO e
nome de coisa no codigo, nunca pra texto que a pessoa le. "tracando" e "quase
la" tinham escapado e apareciam errados na primeira tela do app.

**`@Composable`**

internal + tamanho parametrizavel: a tela de login reusa o MESMO planeta, pra o
objeto que abre o app ser o mesmo que recebe no login (continuidade de marca).

entrance: progresso 0..1 do one-shot "constelação se forma", tocado SO pelo
gate de boot (UpdaterGate) num Animatable/animateFloatAsState proprio. null
(default) = já assentado, ou seja, o comportamento de sempre — login,
onboarding e o proprio gate com reduceMotion continuam iguais, sem esse custo.
planetRes: recurso do planeta no centro. O gate passa a variante TRANSPARENTE
(astra-glyph.png = so o planeta branco, anel/estrela vazados, sem o quadrado preto
que poluia o ceu); gate/login/onboarding/rail usam essa mesma marca transparente.

**`val phaseState = if (reduceMotion || !LocalWindowActive.current) null else`**

Fase lida DENTRO do draw (drawRing roda no DrawScope do Canvas): o composable
não recompoe por frame, so os Canvas redesenham. Antes o .value saia no corpo
e a tela de login recompunha 60fps enquanto você digitava a senha. Movimento
reduzido / janela em segundo plano: anel parado. (Auditoria de movimento, #4.)

**`fun DrawScope.scatterPos(i: Int): Offset`**

Posicao espalhada em t=0 da entrada: um circulo bem mais largo que o anel,
angulo por indice via angulo aureo (~137.5deg) pra não empilhar duas
estrelas na mesma direcao. Deterministico por indice — sem Random, sem
alocar nada no draw.

**`fun DrawScope.drawConstellationLines()`**

Linhas da constelação sendo esbocada: conecta vizinhos na ORDEM do anel
(i -> i+1), o mesmo desenho que a órbita final, so que ainda se formando.
Sobe durante o "reune", some no "assenta" (1.1..1.7s) — so existe durante
a entrada do gate, entrance == null sai no primeiro if.

**`Column(…`**

COLUNA, e não mais uma linha unica. Em 240dp o texto, o botao e o fechar
nao cabem lado a lado — antes o aviso tinha 560dp de largura, entao a
linha unica funcionava. Agora o titulo ocupa a largura toda (podendo
quebrar em duas linhas) e a acao desce pra baixo dele.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/UserFooter.kt`

**`val xpStore = remember { GlobalContext.get().get<XpStore>() }`**

Progressao: o anel em volta do avatar e o numero no lugar do status quando o
mouse passa. O `progresso` e lido na composicao (muda no maximo 1x por
minuto, e so pra trocar o TEXTO); a barra em si e animada na fase de desenho
pelo VisualDeXp, entao ganhar XP nao recompoe a barra lateral.

**`EditorialContextMenu(modifier = modifier, entries =`**

Botao direito no rodape: definir status / abrir perfil / copiar ID / configurações / sair.

O `modifier` do CHAMADOR entra AQUI, e nao na Row la embaixo: quem ancora o
rodape no canto inferior esquerdo do shell e este Box de fora. Na Row, o
alinhamento morria dentro do proprio menu.

**`val forma = RoundedCornerShape(10.dp)`**

FAIXA, nao mais cartao flutuante. Ele atravessa a rail E a sidebar (ver
ShellScreen): era um cartao de 244dp dentro da sidebar, e os 72dp embaixo da
rail ficavam vazios. Sem canto arredondado e sem borda porque agora ele e uma
superficie do shell como as outras — quem separa e o degrau de elevacao
(void, um abaixo da sidebar em `base`).

**`.onGloballyPositioned { PisoDoPet.caixa = it.boundsInWindow() }`**

A borda de cima deste cartao e o CHAO do pet (ver PisoDoPet). Fica
depois do padding externo de proposito: o gato tem que apoiar na
borda desenhada, nao na caixa de layout que sobra em volta dela.

A caixa NAO e zerada quando o rodape sai de cena. Ja foi, e o preco
era o gato PISCAR a cada ida e volta das configuracoes -- ele nascia
e morria junto da navegacao. Guardar a ultima posicao conhecida deixa
ele vivo e parado onde estava; quem esconde e a tela cheia desenhada
por cima (ver a ordem das camadas no ShellScreen).

**`.clickable(onClick = onAbrirJornada),`**

Clicar na foto abre SUA JORNADA — nível, missões e conquistas —
e não mais a aba Perfil das configurações.

A troca é do dono, e a razão é boa: a foto com o anel de XP em
volta promete progresso, não formulário. Quem clica ali quer
saber onde chegou; quem quer editar avatar e banner vai pela
engrenagem, que está a três centímetros de distância.

**`AnimatedContent(…`**

A linha de baixo tem dois papeis. Em repouso e o status ("brilhando");
com o mouse em cima vira o numero do nivel. Escolher assim em vez de
mostrar os dois evita crescer o cartao — e ninguem precisa do XP o
tempo todo, so quando quer saber.
Desliza em vez de dissolver: o status sai por cima e o nível entra
por baixo, como um marcador girando. Dissolução lê como "a tela
piscou"; movimento lê como troca de conteúdo — e a altura do cartão
não muda, então a barra lateral não reflui.

**`FooterIcon(…`**

MUDO E ENSURDECER FICAM AQUI O TEMPO TODO, e nao so durante a call
(escolha do dono, mesmo lugar do Discord). Chegar mudo na proxima sala e
uma decisao que se toma ANTES de entrar — e e por isso que o estado mora
na VoiceSession e nao no motor, que so existe enquanto a call existe.

**`@Composable`**

`aceso` = estado LIGADO e permanente (mudo, ensurdecido): vermelho sem depender
de hover, porque a informacao tem de estar na tela mesmo com o mouse longe.
`riscado` desenha a diagonal por cima do glifo — o Lucide 1.1 tem `MicOff` mas
nao tem um fone cortado, e trocar a metafora (fone -> alto-falante com X) so pra
ter um icone pronto faria os dois botoes falarem de coisas diferentes.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/VoiceLobby.kt`

**`@OptIn(ExperimentalLayoutApi::class)`**

Antessala da órbita de voz: clicar numa sala NAO entra mais na call. Mostra
quem já esta la dentro (presenca do /voice/presence, a mesma que alimenta a
sidebar) e um botao verde de telefone pra entrar de fato. Assim da pra ver se
vale a pena entrar antes de abrir o microfone.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/VoiceView.kt`

**`is VoiceStatus.Connected ->`**

"Conectado" era MENTIRA quando o canal de áudio não subia: entrar na
sala (sinalização) e a voz achar caminho pela rede são duas coisas
diferentes, e só a segunda faz alguém ouvir alguém. Dizer "conectado"
nas duas escondia justamente a falha que a pessoa está sentindo — ela
ficava olhando pro verde sem entender por que ninguém a escuta.

**`val pessoaPorId = remember(members) { members.associateBy { it.userId } }`**

TRADUZIR ID EM GENTE É TRABALHO DESTA TELA, e isso mudou com a malha.

Antes o nome vinha nos metadados do token do LiveKit, porque havia um
servidor de mídia para carregá-los. Ponto a ponto não tem esse servidor:
o que circula é só o id. Quem tem a lista de membros é esta tela, então é
aqui que o id vira nome e foto.

No sussurro não há lista de membros — são duas pessoas, e o nome da sala
JÁ É o nome da outra. Daí o `channel.name` como último recurso.

**`val comTela by call.quemTemTela.collectAsState()`**

QUANDO ALGUÉM COMPARTILHA, A TELA TOMA O PALCO e as pessoas descem para uma
faixa. É a escolha de todo aplicativo de chamada, e a razão é o conteúdo: quem
compartilha quase sempre está mostrando TEXTO — código, um documento, uma
planilha —, e texto pequeno numa moldura do tamanho de um avatar não se lê.
Rosto encolhido continua reconhecível; letra encolhida vira borrão.

UMA TELA NO PALCO POR VEZ, e quem escolhe é quem assiste: clicar na pessoa na
faixa troca o palco para a tela dela. Não há aba nem seletor à parte porque a
faixa JÁ está desenhada ali com as pessoas todas — acrescentar uma segunda
fileira de nomes seria repetir a mesma lista roubando altura do palco, que é
justamente o que o palco não tem de sobra.
OBSERVA QUEM TEM TELA, NUNCA O QUADRO. O mapa de quadros muda trinta vezes por
segundo por pessoa transmitindo, e observá-lo aqui recompunha ESTA TELA INTEIRA
nesse ritmo: a legenda de baixo remontava a frase, a faixa de participantes
refazia os tiles, e a escolha de palco abaixo era recalculada — tudo para
desenhar uma imagem que o Skia já desenha sozinho no `drawBehind`.

`quemTemTela` é o conjunto de chaves daquele mapa, e só se mexe quando uma
transmissão começa ou acaba. O quadro em si desce direto para quem desenha, por
fora da composição — ver `TelaCompartilhada`.

**`val mostrando = remember(mostrandoOutros, transmitindo)`**

A MINHA TELA ENTRA NA MESMA LISTA DE TODO MUNDO, e é o que faz a prévia custar
quase nenhum código: a partir daqui "eu transmitindo" é só mais uma pessoa
transmitindo, e o palco, a faixa e a troca por clique já sabem o que fazer.

A alternativa seria um caminho separado só para a própria tela — outra caixa,
outra regra de quando aparece, outra de quando some. Duas máquinas para a mesma
coisa divergem, e a que divergiria seria a menos usada.

**`val quemMostra = remember(comTela, mostrando, mostrandoOutros, telaEscolhida)`**

A PRÓPRIA TELA SÓ SOBE AO PALCO POR CLIQUE — repare que a escolha automática
olha `mostrandoOutros`, e só a escolha EXPLÍCITA olha `mostrando`.

Subir sozinha seria errado por dois motivos: compartilhar existe para os
OUTROS verem, então roubar o palco de quem eu estou assistindo inverte a
intenção; e a própria tela dentro dela mesma é o espelho infinito, que
assusta quem nunca viu. Na faixa a miniatura já responde "estou mostrando a
janela certa?", que era a pergunta.

**`val naTela = LocalJanelaNaTela.current`**

O PALCO AVISA O PROCESSO DE VOZ, e é o que faz a tela fora dele custar zero.

Decodificar 720p custa 1,03 ms por quadro. Numa sala com três pessoas
transmitindo, olhar UMA custava as três — e a que mais pesava era a de quem não
está olhando nada: sair daqui para uma conversa de texto sem largar a chamada
desmonta esta tela inteira, e até agora a máquina seguia decodificando imagem
para uma janela que não existe mais. Daí o `onDispose`, que é a metade
importante deste efeito.

O aviso é de quem ASSISTE porque a malha entrega a todo mundo de qualquer jeito
— aqui se economiza processador, não banda. Cortar banda exigiria avisar quem
transmite, e isso é outra conversa.
A JANELA MINIMIZADA TAMBÉM NÃO ESTÁ OLHANDO, e este é o caso que faltava.

O Astra vive na bandeja: fechar no X não fecha o app. Uma chamada continua de pé
com a janela guardada, e até aqui a máquina seguia pagando 1,03 ms por quadro
para decodificar imagem que ninguém tinha como ver.

POR QUE NÃO `LocalWindowActive`: aquele exige FOCO, e para enfeite está certo.
Aqui seria errado — com o Astra numa segunda tela enquanto se trabalha na
primeira, a janela não tem foco e a transmissão está sendo assistida.

**`LaunchedEffect(quemMostra, naTela) { call.assistir(if (naTela) quemMostra else null) }`**

SÃO DOIS EFEITOS E NÃO UM, e a razão é o que o `onDispose` de um
`DisposableEffect(quemMostra)` significaria: ele dispara TAMBÉM na troca de
palco, e mandaria um "ninguém" no meio do caminho entre olhar A e olhar B —
fechando e reabrindo à toa. Preso a `Unit`, ele só fala quando a tela sai
mesmo de cena, que é o que se quer dizer.

**`CallEmMalha.EU, "você", connected.mySpeaking, me?.avatarUrl,`**

A CHAVE VIRA `EU` (vazia) porque ela é o que o clique
devolve para `telaEscolhida`, e é com ela que se procura o
quadro no mapa de telas. Um "me" aqui e um "" ali seriam
dois nomes para a mesma pessoa, e a tradução no meio é
exatamente onde esse tipo de coisa se perde.

**`Text(…`**

O VAZIO DURA UM INSTANTE E TEM NOME. Entre o aviso de que
alguém começou a transmitir e o primeiro quadro passam-se
alguns décimos — o descompressor precisa da sequência de
parâmetros e de um quadro-chave. Um retângulo preto mudo
nesse intervalo parece defeito.

**`Box`**

SOUNDBOARD. Clicar num som NAO mistura audio no seu microfone: o
servidor avisa a sala e cada um toca o arquivo original localmente
(ver SoundboardPlayer). Passar pelo mic faria o som atravessar o Opus
da voz, que e afinado pra fala e esmaga efeito.

Sem freio entre disparos — decisao explicita do dono.

**`val monitores by call.monitores.collectAsState()`**

TRANSMITIR A TELA.

O botão NÃO ACENDE AO SER APERTADO: acende quando o processo de voz
confirma que a captura e o compressor subiram. Isso leva quase um segundo
e pode falhar — nem toda máquina tem compressor de H.264 —, e acender no
clique para apagar depois é o padrão que ensina a pessoa a desconfiar do
próprio botão. Apagar, sim, é imediato: quem manda parar quer parar já.

O toque no botão abre a placa; enquanto está no ar, ele mostra o que está
subindo de verdade. Ainda não há imagem para ver deste lado (o
decodificador é a próxima fatia), e o relatório é o que prova que a coisa
está viva no lugar dela.

**`escolhendoTela = true`**

O CLIQUE ABRE A ESCOLHA em vez de transmitir direto. Antes
ele mandava o monitor principal sem perguntar, o que acerta
por acaso em quem tem uma tela só e erra metade das vezes em
quem tem duas — com o erro acontecendo ao vivo.

**`LaunchedEffect(Unit) { call.atualizarAparelhos() }`**

Reconsulta ao ABRIR o painel, e não uma vez só: aparelho vai e
vem no meio de uma call — fone USB plugado, monitor com caixa
ligado. Uma lista buscada na entrada da sala estaria velha
justamente quando a pessoa foi lá procurar o aparelho novo.

**`@Composable`**

Config da call (gear): escolher microfone e saída.

A LISTA VEM DO PROCESSO DE VOZ, e essa é a parte que importa. Ele é quem fala
WASAPI e quem vai abrir o aparelho; a JVM enxerga uma lista diferente e menor.
Listar por um caminho e abrir por outro é como se acaba escolhendo um aparelho e
ouvindo outro — e ninguém consegue explicar por quê.

A escolha guarda o IDENTIFICADOR do Windows, não o nome: nome muda com atualização
de driver e se repete entre placas iguais.

**`@Composable`**

Seletor de aparelho: mostra o atual e abre a lista num popup.

"Padrão do Windows" é uma opção de verdade e vem primeiro, porque é o certo para a
maioria — é o aparelho que a pessoa já escolheu no sistema para conversar.

**`@Composable`**

O QUE ESTÁ SUBINDO, escrito embaixo do palco enquanto a transmissão está no ar.

POR QUE SAIU DE DENTRO DO PAINEL. O relatório sempre existiu, mas só dentro do popup
que abre ao começar a transmitir — e um painel que se dispensa some justamente quando a
pergunta aparece. A pergunta é sempre a mesma e é sempre depois: "isto está em 60 ou
caiu para 30?". Um instrumento que só se lê no primeiro segundo não responde a nada.

Ele é o ÚNICO lugar onde três coisas invisíveis aparecem, e cada uma muda o que fazer:

 - o nome do compressor, e "sem aceleração de placa" quando não há placa. Um compressor
   de software custa cinco vezes mais por quadro, e é isso que rebaixa a taxa.
 - o tamanho e a taxa que de fato subiram, que podem não ser os do preset.
 - os quadros por segundo reais, uma vez por segundo.

DISCRETO DE PROPÓSITO: `text3`, 10sp, fonte mono. É instrumento, não recado — quem está
assistindo a tela de alguém não deve ler isto antes de ler o conteúdo. Mono porque os
números mudam a cada segundo, e fonte proporcional faria a linha inteira dançar quando
"58" virasse "60".

**`@OptIn(ExperimentalLayoutApi::class)`**

Grid que quebra linha sozinho (FlowRow), centralizado.

`previa` é o mapa de quadros, e ele desce até aqui SÓ para o cartão de quem transmite
a própria tela desenhar a miniatura viva. Nulo = sem prévia, e o cartão mostra o avatar.

**`val podeTrocar = tile.transmitindo && (!tile.emCartaz || tile.isMe)`**

Só é alvo de clique quem tem tela para pôr no palco E ainda não está nele.
Cartão clicável que não faz nada ensina a desconfiar do próprio clique.

O MEU CARTÃO É A EXCEÇÃO, e é uma saída de emergência: com a própria tela no palco e
ninguém mais transmitindo, deixar de ser clicável trancava a pessoa lá dentro — não
havia gesto nenhum que devolvesse a faixa de participantes. No meu cartão o clique
ALTERNA: põe no palco e tira de lá.

**`val orbit = if (tile.speaking && !reduce && active)`**

Estrela de fala: UMA fase de órbita, so quando fala + janela visivel +
movimento ligado, lida DENTRO do drawBehind. Antes um halo pulsante era
lido no corpo e recompunha o cartao inteiro (avatar/nome/mic) 60fps por
pessoa falando; agora so redesenha. (Auditoria de movimento, achado #1.)

**`.background(if (tile.emCartaz) Obsidian.overlay else Obsidian.raised.copy(alpha = 0.5f))`**

QUEM ESTÁ NO PALCO SOBE UM DEGRAU DA RAMPA, e não ganha cor. O accent já
significa "está falando" nesta faixa; usá-lo também para "está no palco"
faria duas coisas diferentes acenderem igual. Elevação é o que o app usa
para dizer "este é o mais próximo", e é o que cabe aqui.

**`if (tile.isMe && tile.transmitindo && previa != null)`**

A MINIATURA VIVA, E SÓ NO MEU CARTÃO. Não é assimetria por preguiça: os
quadros dos OUTROS só chegam de quem está no palco (é o corte que faz assistir
uma tela custar uma, e não três — ver `recepcao.go`), então um retângulo aqui
para eles ficaria preto na maioria das vezes e pareceria defeito. A minha
sempre chega, porque ela nasce da minha própria captura.

Formato de tela e não círculo: 16:9 num quadrado de avatar deixaria a imagem do
tamanho de uma tarja, e o que se quer conferir aqui é QUAL janela está subindo.

**`if (tile.transmitindo)`**

O SINAL DE QUEM ESTÁ COMPARTILHANDO. Sem rótulo de leitor de tela porque
vem colado no nome: rotulado, o leitor diria "compartilhando tela, fulano"
toda vez que passasse pelo cartão, e o ícone aqui é decoração de um texto
que já está escrito.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/ui/XpRing.kt`

**`private val ESPESSURA = 2.dp`**

O ANEL DE XP em volta do avatar do rodape.

Anel e nao barra porque nao ha onde por barra: o cartao do rodape tem ~48dp e ja
carrega avatar, bolinha de presenca, nome, status e dois botoes. O anel nao ocupa
altura nenhuma — e desenhado PRA FORA da caixa do avatar, dentro da folga que o
cartao ja tinha. E porque a linguagem do app ja e orbita: o halo do DesktopAvatar
e o anel de quem fala na call sao a mesma familia.

REGRA DE OURO DESTE ARQUIVO: nada que muda por frame pode ser lido na composicao.
Todos os valores animados entram como lambda e sao lidos dentro do drawBehind, ou
seja, na fase de DESENHO. Ler direto recomporia a barra lateral inteira a cada
ganho de XP — e o ganho pode chegar enquanto a pessoa esta rolando o chat.

**`val forte = g.origem == "missao"`**

XP de missao respira MAIS LONGO que o de conversa. Sao dois eventos de
peso diferente: mensagem rende 12 e acontece o tempo todo; missao rende
dezenas e acontece porque a pessoa foi atras. Mesmo pulso pros dois faria
o segundo passar despercebido bem na hora que ele deveria ser sentido.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/update/UpdateService.kt`

**`private const val REPO = "Xryonz/Astra"`**

Auto-update DIY do Astra desktop, modo PORTATIL (decisao do dono: "pasta com
todas as versões + sempre abrir a mais nova"). Sem lib: bate na API pública de
releases do GitHub, compara semver com a versão embutida (-Dastra.version),
baixa o .zip do app-image novo com progresso (retry + resume), arquiva o zip e
extrai a versão nova numa subpasta propria. No "reiniciar" so abre o Astra.exe
da versão nova e sai — sem swap in-place (nada de rename/.old/.bat, que era o
que dava permissão/race). O launcher (launch.vbs) sempre reabre a MAIOR versão.

Layout portatil (o app roda de versions/<v>/Astra.exe):
  C:/Astra/
    Astra.lnk            atalho fixo -> launch.vbs
    launch.vbs           acha a maior versão e roda seu Astra.exe (sem console)
    versions/<v>/Astra.exe ...   uma pasta por versão (historico completo)
    zips/Astra-<v>-win-x64.zip   cada zip baixado fica arquivado aqui

Convencao de release (o dono segue ao publicar):
  tag  : desktop-v<versão>            ex: desktop-v0.1.7
  asset: Astra-<versão>-win-x64.zip   (contem a pasta Astra/ do createDistributable)

**`private const val REPO = "Xryonz/Astra"`**

Checagem SEM a API do GitHub (api.github.com tem rate-limit anonimo de 60/h por
IP: varios boots/checagens + amigos no mesmo IP/CGNAT estouravam -> 403 -> falso
"sem conexão"). github.com/<repo>/releases/latest responde 302 pra .../tag/<tag>
e a pagina web NAO tem esse limite; da tag monta-se o zip por convencao.

**`data class UpToDate(val vista: String, val conferidoEm: Long = System.currentTimeMillis()) : UpdateState`**

`vista` = a versao que o GitHub disse ser a mais nova, e QUANDO isso foi
conferido. Sem os dois, "você está na última versão" e uma afirmacao sem
lastro: nao da pra saber se ele olhou agora ou quando o app abriu, nem
contra o que ele comparou. Quem esta esperando uma release sair precisa
exatamente dessa informacao.

**`val installed: Boolean get() = !Multi.ligado && appRootDir() != null`**

So o app empacotado (Astra.exe) tem pasta pra versionar. Dev/IDE = nulo.

A SEGUNDA CONTA NAO ATUALIZA, e agora nem precisa: ela abre o mesmo Astra.exe da
instalacao principal, entao ja nasce na versao mais nova. Quem baixa e instala e a
janela principal, uma vez, pra as duas.

Continua desligado aqui porque duas janelas baixando e trocando a MESMA pasta ao
mesmo tempo e receita de instalacao pela metade — e porque a segunda reiniciaria
sem a variavel de ambiente, caindo na trava de instancia unica e saindo calada.
Era exatamente o antigo "reinicia e nao liga de novo".

**`private var ronda: Job? = null`**

---- Ronda: conferir sozinho enquanto o app esta aberto ----

O app so olhava o GitHub na ABERTURA. Quem deixa o Astra aberto o dia todo
(o caso normal — ele mora na bandeja) so descobria uma versao nova no
proximo boot, ou clicando "procurar atualizacoes" na sorte. Publicar uma
correcao nao adiantava nada pra quem ja estava com o app aberto.

20 minutos: uma release leva ~5min pra sair do build, entao o pior caso e
saber ~25min depois de eu subir o commit — sem ninguem fazer nada. E uma
requisicao que so le um cabecalho de redirecionamento, sem corpo; 3 por hora
por pessoa nao pesa em lado nenhum.

NAO mexe no estado quando ja ha download/instalacao em andamento: uma ronda
no meio do caminho jogaria a barra de progresso de volta pra "procurando".

**`if (!ocupado) check(mostrarFalha = false)`**

Falha da ronda NAO vira aviso na tela: ela roda enquanto a
pessoa conversa, e um "sem conexão com o GitHub" brotando do
nada no meio de uma conversa e susto por nada. A proxima volta
tenta de novo.

**`private val ESPERA_FAXINA_MS = 20_000L`**

---- Faxina: so a versao atual sobrevive ----

O layout portatil guardava TUDO: uma pasta por versao em versions/ e cada zip
baixado arquivado em zips/. Bonito na teoria, 5 GB na pratica depois de umas
vinte atualizacoes — num PC de estudante isso e o disco inteiro.

Roda no BOOT, nao logo depois de atualizar, porque so aqui as versoes velhas
estao garantidamente paradas: uma versao nao consegue apagar a si mesma (o
Windows tranca o proprio exe e a runtime em uso).

ESPERA antes de apagar: se o pacote novo estiver quebrado e o app morrer nos
primeiros segundos, a versao anterior continua no disco e o launch.vbs volta a
abrir ela. E o unico plano B que existe depois que o historico morre — barato,
e a diferenca entre "atualizou errado" e "o Astra nao abre mais".

**`if (release == null)`**

NAO CONFUNDIR "nao consegui ler" COM "voce esta atualizado".

Antes, qualquer resposta que o app nao soubesse interpretar (redirect sem
Location, tag em outro formato, pagina fora do ar) caia em "você está na
última versão" — uma afirmacao TRANQUILIZADORA sobre uma pergunta que
ficou SEM RESPOSTA. Quem esta esperando uma release sair le isso e
conclui que a release nao existe. Uma falha de rede virava a mesma
mentira pelo mesmo caminho.

**`private fun download(url: String, dest: File, onProgress: (Float) -> Unit)`**

Download grande (~140MB) resiliente: retry ate 3x e RESUME via Range quando o
servidor aceita (206) — um engasgo de rede >readTimeout não joga fora o que já
baixou. Sem callTimeout (a chamada inteira não tem teto), so readTimeout por
leitura. OkHttp segue o 302 do asset pro storage sozinho.

**`private fun conferirHash(zipUrl: String, zip: File)`**

Confere o zip baixado contra o SHA-256 publicado ao lado dele.

O que isto pega: zip corrompido ou trocado no caminho. O que NAO pega:
quem tem a credencial de publicacao — essa pessoa publica o zip e o hash
juntos. Assinatura de codigo resolveria isso, e foi deixada de fora de
proposito: exige gerenciar chave e rotacao, trabalho que nao se mantem num
projeto de uma pessoa so.

AUSENTE = SEGUE. As releases ate a 0.1.76 nao tem o arquivo de hash, e
recusar atualizacao por causa disso deixaria todo mundo preso na versao
instalada — o remedio seria pior que a doenca.

**`fun restartToInstall()`**

Portatil: a versão nova já esta pronta em versions/<v>/. So abre o Astra.exe
dela e sai — sem swap, sem .bat, sem esperar. O launcher (e o atalho) sempre
reabrem a MAIOR versão, entao os proximos boots também caem na nova.

POR QUE ISTO NAO ERA SO "start + exit":

1. A TRAVA DE INSTANCIA UNICA. O processo velho ainda segura a porta quando o
   novo abre. O novo entao conclui "já tem um Astra aberto", avisa o velho e
   SAI — e logo depois o velho sai também. Resultado visto de fora: a janela
   pisca, tudo fecha e nada reabre. Por isso a trava e solta ANTES de abrir o
   novo: quando ele chegar na checagem (~1s de JVM), a porta já esta livre.

2. FECHAR SEM TER ABERTO. Se o start falhasse, o exitProcess vinha do mesmo
   jeito e o app sumia sem substituto. Agora so sai se o novo de fato subiu.

3. stagedExe NULO. Ele so existe na sessão em que a versão foi baixada. Quem
   baixou, fechou e reabriu caia num `return` mudo — a tela dizia "reiniciando"
   e nada acontecia. O fallback acha a maior versão instalada no disco.
4. A JANELA NOVA NASCIA ATRAS DE TUDO. O Windows nao deixa um processo roubar a
   frente de quem voce esta usando, e nao passa esse direito de pai pra filho
   sozinho — entao o Astra novo abria por baixo do navegador e parecia que o app
   tinha sumido. Duas metades resolvem, e as duas sao necessarias: o velho CEDE o
   direito (AllowSetForegroundWindow) e o novo PEDE a frente ao abrir. Ceder sem
   pedir nao move nada; pedir sem ceder e negado.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/voice/AparelhosDoSistema.kt`

**`object AudioDevices`**

OS APARELHOS DE ÁUDIO, para o Diagnóstico e para a checagem de permissões.

Isto vivia dentro do motor de voz antigo, e a listagem de SAÍDAS era feita pelo
`AudioDeviceModule` do webrtc-java. Quando a voz migrou para o processo em Go, esse
motor virou ilha morta e foi removido — e com ele iria a única razão de a biblioteca
(8 MB de nativo do Windows) continuar no pacote. Reescrever a listagem em JavaSound
foi o que permitiu a biblioteca sair.

NÃO CONFUNDIR COM A LISTA DA CALL. Quem escolhe microfone e saída durante uma
conversa é o processo de voz, que enumera pelo WASAPI e devolve id estável além do
nome (ver `AparelhoDeAudio` em SidecarDeVoz.kt) — é a lista certa para ESCOLHER, e é
a que as Configurações usam. Esta aqui é para PERGUNTAR "o que existe nesta
máquina?" fora de uma call, quando o processo de voz nem está rodando: o Diagnóstico
e o painel de permissões abrem sem conversa nenhuma acontecendo.

O JavaSound cobre esse caso sem custo, e a diferença de qualidade entre as duas
listas não atrapalha aqui: nome repetido ou driver com apelido esquisito é ruim para
escolher, e irrelevante para responder "existe microfone instalado?".

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/voice/CallEmMalha.kt`

**`class CallEmMalha(…`**

A CALL EM MALHA — quem conversa com quem, e o que a tela vê disso.

Em ponto a ponto não existe "entrar numa sala": existe conectar em CADA pessoa
que já está lá, uma conexão por companheiro. Esta classe é quem sabe disso, e ela
é a única que sabe — o sidecar só recebe ordens ("conecte-se a fulano") e o resto
do app não faz ideia de que há N conexões por baixo.

TRÊS FONTES DIZEM QUEM ESTÁ NA SALA, e as três são necessárias:

 1. A CONSULTA ao entrar. Sem ela, quem chega numa call em andamento não
    conectaria em ninguém — só veria as pessoas que entrassem DEPOIS dele.
 2. O AVISO por socket (entrou/saiu). É o caminho rápido, e o que faz alguém
    aparecer na hora em vez de no próximo giro.
 3. A CONSULTA periódica. Rede de segurança: aviso perdido por reconexão de
    socket deixaria um par mudo para sempre, sem nada indicando o porquê.

Uma só das três não basta, e é por isso que as três existem.

**`private val _microfones = MutableStateFlow<List<AparelhoDeAudio>>(emptyList())`**

Os aparelhos que o Windows tem. Chegam do processo de voz, que é quem fala
WASAPI — a JVM enxerga uma lista diferente e incompleta, e listar por um
caminho e abrir por outro é como se acaba escolhendo um aparelho e ouvindo
outro.

**`private val _transmitindo = MutableStateFlow(false)`**

A TRANSMISSÃO DE TELA, e QUEM MANDA NO ESTADO É O PROCESSO DE VOZ.

O botão não acende ao ser apertado: acende quando o outro lado confirma que a
captura e o compressor subiram — o que leva quase um segundo e pode falhar (não
existe compressor de H.264 em toda máquina). Acender no clique e apagar depois é
o padrão que ensina a pessoa a não confiar no próprio botão.

**`val telasDosOutros = sidecar.quadros.telas`**

A TELA DE CADA PESSOA, vinda pelo cano à parte. O mapa é do cano e não daqui: os
quadros nunca passam pela ponte de comandos, então não há o que traduzir no meio.

MUDA NO RITMO DO VÍDEO. Só quem desenha deve observá-lo — ver `TelaCompartilhada`.

**`const val EU = ""`**

O ENDEREÇO DA PRÓPRIA TELA no mapa de quadros.

Vazio porque é o que o protocolo do cano já dizia desde o começo: "`Par` vazio
significa EU" (ver `ponte.go`). A prévia entrou por essa porta que já existia
em vez de abrir outra — nenhum campo novo, nenhum formato novo, e o mesmo
shader desenha.

Não colide com id de gente: id de usuário nunca é vazio.

**`private val _mostrandoTela = MutableStateFlow<Set<String>>(emptySet())`**

Quem ANUNCIOU que está transmitindo, o que é diferente de quem já mandou quadro.
A diferença dura um instante — o descompressor precisa da sequência de parâmetros
e de um quadro-chave antes de abrir imagem —, e é justamente esse instante que
permite mostrar "abrindo a tela de fulano" em vez de um retângulo preto.

**`private val _monitores = MutableStateFlow<List<MonitorDaTela>?>(null)`**

AS TELAS DESTA MÁQUINA, com miniatura. Chega em resposta a `pedirMonitores`, que a
janela de escolha dispara ao abrir — amostrar custa uma duplicação por monitor.
Nula enquanto a resposta não chegou, e a janela usa isso para mostrar que está
procurando em vez de dizer que não há tela nenhuma.

**`if (_transmitindo.value) pararDeTransmitir()`**

A TELA PARA JUNTO. Sair da call sem parar a transmissão deixaria a captura
e o compressor rodando — placa ocupada e a luz de "transmitindo" acesa numa
sala de que a pessoa já saiu. O processo cai logo depois e resolveria isso
sozinho, mas depender disso é depender de um efeito colateral.

**`fun assistir(par: String?)`**

QUAL TELA O PALCO ESTÁ MOSTRANDO — e, por tabela, qual vale a pena decodificar.

Quem chama é a `VoiceView`, no efeito preso ao palco: a cada troca, e uma vez com
nulo quando ela sai de cena. Sair de cena é o caso que mais pesa — trocar a sala de
voz por uma conversa de texto sem largar a chamada desmonta a tela inteira, e sem
este aviso a máquina seguiria decodificando imagem para uma janela que não existe.

GUARDA O ÚLTIMO E NÃO REPETE: a `VoiceView` recompõe por qualquer motivo, e mandar
a mesma ordem dezenas de vezes por segundo entupiria a ponte que carrega o aperto
de mão da call.

**`@Volatile private var noPalco: String? = null`**

NULO É "AINDA NÃO DISSEMOS NADA", e não "ninguém" — os dois valores existem e são
diferentes dos dois lados da ponte.

Se aqui começasse em string vazia, entrar numa call e a `VoiceView` mandar o
primeiro "ninguém" seria suprimido como repetição, o processo de voz continuaria em
"o Astra não disse nada" e decodificaria tudo. O bug seria invisível: a imagem
apareceria certinha, só custando o que esta fatia existe para não custar.

**`_transmitindo.value = false`**

APAGA AQUI, sem esperar a confirmação — ao contrário de acender.

A assimetria é de propósito: acender cedo promete o que ainda não existe;
apagar cedo cumpre na hora o que a pessoa pediu. Quem aperta "parar" quer
parar de mostrar a tela AGORA, e o evento de volta só confirma.

**`@Volatile private var microfoneEscolhido: String? = null`**

A ESCOLHA FICA GUARDADA AQUI, e não só enviada.

O processo de voz pode reiniciar no meio da call (é justamente para isso que
ele é um processo à parte). Quando volta, ele volta com o aparelho padrão do
Windows — a escolha da pessoa mora do lado de cá. Sem reenviar no `pronto`, a
primeira queda do processo desfaria silenciosamente a configuração dela.

**`@Volatile private var eco = true`**

O tratamento do microfone entra na MESMA regra dos aparelhos, e pelo mesmo
motivo: o processo de voz volta do zero quando cai, e volta nos padrões dele.
Quem desligou algo de propósito veria a escolha se desfazer sozinha, sem nada
na tela mudando — o pior tipo de defeito, o que se desmancha sem aviso.

**`sidecar.tratamento(eco, ruido, ganho)`**

SEMPRE, e não só quando difere do padrão do processo. Os padrões dos dois
lados não coincidem — o objeto do Windows nasce com o ganho automático
DESLIGADO e o Astra mostra ele ligado — e "mandar só o que mudou" exigiria
manter aqui uma cópia fiel dos padrões de lá. Cópia de padrão alheio é a
coisa que envelhece errado em silêncio.

**`if (!sidecar.conectar(meuId, outro)) return`**

SÓ CONTA COMO CONECTADO SE A ORDEM SAIU DE VERDADE.

O processo de voz demora um instante para subir, e comando mandado antes
disso não vai a lugar nenhum — some em silêncio. Se marcássemos a pessoa
como conectada mesmo assim, a conferência seguinte veria "já está lá" e
NUNCA tentaria de novo: um par mudo para o resto da call, sem erro em
lugar nenhum. Registrar só o que saiu faz a próxima volta consertar.

QUEM OFERECE É DECIDIDO PELO ID, e a regra é a mesma dos dois lados. Sem
regra, os dois ofereceriam ao mesmo tempo assim que se vissem, e as duas
ofertas colidiriam — o encontro de ofertas, que produz uma conexão que
nunca fecha. Comparar os ids resolve sem nenhuma troca de mensagem.

**`private fun aoMudarEstado(quem: String, estado: String)`**

QUINZE SEGUNDOS É TEMPO DEMAIS PARA FICAR MUDO.

A reconferência periódica já reabriria a conexão, mas só no próximo giro — e
quinze segundos de alguém sumido é tempo suficiente para a pessoa concluir que
a call quebrou e sair dela. O processo de voz já relata o estado de cada par;
faltava alguém escutando.

**`"disconnected" -> agendarResgate(quem, imediato = false)`**

ESPERAR ANTES DE AGIR, e essa espera é o ponto.

`disconnected` é quase sempre passageiro: uma troca de rede, um pico de
perda, o Wi-Fi hesitando. O ICE se recupera sozinho na maior parte das
vezes. Derrubar e refazer na primeira suspeita transformaria um engasgo
de dois segundos numa reconexão completa — trocar um tropeço por uma
queda.

**`val espera = if (imediato) (1_000L shl (n - 1).coerceAtMost(3)).coerceAtMost(10_000L)`**

Espera crescente com teto: 1s, 2s, 4s, 8s, e daí em diante 10s. Sem o
crescimento, um par que falha por um motivo permanente (rede que não deixa)
viraria um laço de refazer conexão sem parar, gastando processador para
não resolver nada.

**`conectados.remove(quem)`**

Fecha e reabre em vez de remendar. Refazer do zero é o caminho que já
sabemos que funciona — é o mesmo que acontece quando alguém entra na
sala — e não depende de o outro lado cooperar com uma renegociação.

Os dois lados detectam a queda e refazem, mas isso não gera colisão: a
regra de quem oferece continua sendo o id menor, e quem receber uma
oferta de alguém que não está mais no conjunto abre a conexão ao
recebê-la (ver `ouvirSinais`).

**`abrirCom(de)`**

Oferta de quem ainda não conhecemos ABRE a conexão em vez de ser
descartada. Acontece de verdade: quem entrou depois pode nos ver antes
de nós o vermos, porque as três fontes de presença não chegam na mesma
ordem para os dois lados.

**`salaAtual?.let { sala -> scope.launch { conferir(sala) } }`**

CONFERIR AGORA, e não daqui a quinze segundos. Este é o único
instante em que sabemos que os comandos passam a chegar — e
tudo que foi tentado antes disso se perdeu. Esperar o giro
normal seria entrar numa call e ficar quinze segundos em
silêncio sem nada explicando por quê.

**`ev.tipo == "taxa" ->`**

POR QUE A TAXA CAIU — dito uma vez pelo emissor, GUARDADO aqui.

É a única explicação que existe para uma transmissão não estar
na taxa escolhida, e ela chega uma vez só, no aquecimento.
Enquanto vinha como "ritmo", o relatório do segundo seguinte a
apagava — a causa aparecia por um segundo e o que sobrava era um
número baixo sem motivo. Guardada, ela acompanha a linha
enquanto a transmissão durar.

**`pronto = false`**

O processo voltou do zero: ele não tem mais conexão nenhuma.
Esquecer quem estava conectado faz a próxima conferência
reabrir tudo — sem isto, o conjunto diria "já conectado" e a
call ficaria muda para sempre depois de um reinício.

**`val vozPassando = pronto &&`**

"A VOZ ESTÁ PASSANDO" É DIFERENTE DE "ESTOU NA SALA".

Sozinho na sala, passando: não há a quem ouvir, e nada está errado. Com
gente e nenhum par conectado, a tela precisa dizer isso — é exatamente a
falha que a pessoa está sentindo quando ninguém a escuta, e chamar aquilo
de "conectado" esconde a única pista que ela tinha.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/voice/CanoDeQuadros.kt`

**— sobre o arquivo inteiro —**

O CANO DE QUADROS — por onde a tela de outra pessoa chega ao Astra.

A ponte de comandos (stdin/stdout, JSON por linha) carrega coisas de dezenas de
bytes. Um quadro de 720p em NV12 são 1,4 MB, e a 30 por segundo são 40 MB/s: passar
isso por lá faria o aviso de "fulano está falando" esperar atrás de megabytes de
imagem. Por isso um cano à parte.

QUEM ESCUTA É ESTE LADO, e é o contrário do que parece natural — o dono do dado é o
processo de voz. A razão é uma corrida: se ele abrisse a porta, teria de ANUNCIAR o
número dela pela ponte, e nós teríamos de estar ouvindo antes de ele falar. Com a
porta aberta AQUI, o endereço já existe quando o processo é lançado e viaja no
ambiente dele. Não há o que perder e não há o que reencontrar depois de uma queda.

O SEGREDO NÃO É CERIMÔNIA. Uma porta de escuta na volta local aceita conexão de
qualquer programa da máquina, e o que passa por aqui é a TELA DE ALGUÉM. O segredo é
sorteado a cada execução, viaja pelo ambiente (que só pai e filho enxergam) e é a
primeira coisa que a conexão precisa apresentar.

FORMATO DE CADA QUADRO — implementado duas vezes, aqui e em `sidecar-voz/entrega.go`,
e nenhum compilador confere que os dois concordam. Do lado de lá há um teste que trava
campo a campo; aqui o que protege é a marca no começo, que transforma
desalinhamento em erro alto em vez de imagem embaralhada.

0  uint32  marca ('ASTV')
4  uint32  bytes do id do par
8  uint32  largura
12 uint32  altura
16 uint32  passo — bytes por linha, PODE SER MAIOR que a largura
20 uint32  bytes do quadro
24 [..]    id do par, em UTF-8
   [..]    o quadro, em NV12

**`class QuadroDeTela(…`**

Um quadro pronto para desenhar, em NV12.

[passo] separado de [largura] porque o decodificador alinha as linhas ao que a placa
gosta, e assumir que são iguais não dá erro — dá imagem enviesada em diagonal.

[serie] existe para o desenho saber que o conteúdo mudou: o vetor de bytes é
REAPROVEITADO (ver `Rodizio`), então comparar a referência diria "é o mesmo" quando já
é outro quadro.

**`private val ouvinte: ServerSocket? = runCatching`**

A PORTA NÃO PODE DERRUBAR A VOZ.

Este objeto é campo do `SidecarDeVoz`, então uma exceção aqui aconteceria durante a
construção dele — e o que morreria não seria a imagem, seria a CHAMADA INTEIRA.
Trocar conversa por nada porque uma porta de escuta não abriu é a pior troca
possível, e "não abre" é raro mas existe: política de segurança da máquina,
esgotamento de portas efêmeras, um antivírus com opinião.

Sem porta, `endereco` fica vazio, o processo de voz não recebe o ambiente, e ele
mesmo decide não montar o cano (ver `NovaEntrega`). A voz segue inteira; o que se
perde é ver a tela dos outros.

**`val telas: StateFlow<Map<String, QuadroDeTela>> = _quadros.asStateFlow()`**

A última tela de cada pessoa que está transmitindo.

MUDA TRINTA VEZES POR SEGUNDO POR PESSOA, e quem observar isto na COMPOSIÇÃO paga
essa conta inteira. Este fluxo é para quem DESENHA — ver `TelaCompartilhada`, que o
lê dentro do `drawBehind`. Para saber apenas *quem* tem imagem, use
[quemTransmite], que fica parado enquanto ninguém começa nem para.

**`val quemTransmite: StateFlow<Set<String>> = _quemTransmite.asStateFlow()`**

DE QUEM já chegou imagem — o conjunto de chaves de [telas], e nada mais.

Existe porque a interface faz duas perguntas muito diferentes ao mesmo mapa: "qual é
o quadro de agora?" (trinta vezes por segundo) e "há tela de fulano?" (quase nunca).
Servir as duas pelo mesmo fluxo obriga a segunda a acordar no ritmo da primeira, e
era o que acontecia: a tela de chamada inteira recompunha a cada quadro recebido.

Separar não é otimização de estilo — é a diferença entre um estado que muda no ritmo
do vídeo e um que muda quando alguém aperta um botão.

**`if (novo)`**

O PRIMEIRO QUADRO DE CADA PESSOA VAI PARA O REGISTRO, e uma vez só.

Este formato é escrito em Go e lido aqui, e nenhum compilador confere que
os dois concordam. Quando a imagem não aparecer, a primeira pergunta será
"chegou alguma coisa, e com que forma?" — e a resposta precisa existir sem
depender de alguém estar com o depurador aberto no momento certo.

**`if (par !in antes) _quemTransmite.value = _quemTransmite.value + par`**

O CONJUNTO SÓ ACORDA QUANDO A CHAVE É NOVA — uma vez por transmissão, e não
uma vez por quadro. É isso que deixa a interface perguntar "há tela de
fulano?" sem herdar o ritmo do vídeo.

NÃO USA `novo` (o de `rodizios`), e a distinção importa: `esquecer` limpa o
mapa mas não o rodízio, então quem parasse e voltasse a transmitir na mesma
chamada teria `novo == false` e nunca reapareceria. Quem manda aqui é o mapa,
porque é dele que este conjunto é a sombra.

**`private class Rodizio`**

TRÊS VETORES POR PESSOA, EM RODÍZIO — e não um vetor novo por quadro.

Um novo a cada quadro seriam 1,4 MB trinta vezes por segundo: 40 MB/s de lixo, no
app onde já se lutou para segurar a memória. Um só seria pior de outro jeito — a
leitura sobrescreveria o quadro que a tela está desenhando naquele instante.

Três resolve os dois: quando a leitura volta ao primeiro, já se passaram dois
quadros (uns 66ms a 30 por segundo). Se o desenho ainda estiver no de 66ms atrás,
ele já está perdendo quadros de qualquer jeito, e o pior caso é UM quadro rasgado
— não um travamento.

**`private fun descartarParados(rodizios: HashMap<String, Rodizio>)`**

SOLTA OS VETORES DE QUEM NÃO TRANSMITE MAIS.

Três vetores de 1,4 MB por pessoa são **4,2 MB cada**, e até aqui eles ficavam
pendurados pelo resto da chamada. Numa sala longa em que dez pessoas transmitiram
por vez, são ~42 MB retidos para nada — num app onde já se lutou para segurar a
memória (ver `project_memory_leaks`).

POR TEMPO E NÃO POR AVISO, de propósito. `esquecer(par)` existe e é chamado quando
alguém para, mas ele resolve a parte VISUAL e roda em outra thread. Amarrar a
memória a ele deixaria de fora o caso que mais importa: a queda abrupta, em que
aviso nenhum chega. O relógio cobre os dois sem coordenação entre threads — este
mapa é tocado só pela thread de leitura.

TRINTA SEGUNDOS é folgado de propósito. Uma tela PARADA manda pouco (o sinal de
vida do emissor sai a cada 2s), então um limite curto descartaria o rodízio de quem
está compartilhando um documento e não mexe — e recriá-lo custa três alocações de
1,4 MB justamente no meio da transmissão. Trinta segundos sem um único quadro
significa que aquela transmissão acabou de verdade.

**`const val CONFERIR_A_CADA = 600`**

De quantos em quantos quadros vale conferir quem parou.

A varredura é sobre um mapa de poucas entradas, mas fazê-la a cada quadro seria
trabalho constante para um evento raro. Com três pessoas transmitindo a 30 por
segundo isto dá uma conferida a cada ~7s — bem mais frequente que os 30s do
limite, então nada fica pendurado além do previsto.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/voice/ConversorDeSom.kt`

**`object ConversorDeSom`**

Converte o arquivo que a pessoa escolheu para WAV, no momento de subir um som da
soundboard. Roda UMA vez por som cadastrado — a reprodução depois só baixa o WAV
pronto e toca com o JavaSound.

ISTO CUSTAVA 137,8 MB.

Antes a conversão era um `ffmpeg.exe` completo, empacotado dentro do Astra. Ele
tinha entrado para capturar tela; quando a transmissão saiu, sobrou como o único
binário de 137,8 MB do instalador — **quase metade do app** — para atender a uma
ação que um administrador faz de vez em quando. E cada atualização automática
baixava ele de novo, para todo mundo.

Hoje quem decodifica são dois provedores do JavaSound (~300 KB somados). Eles não
têm API para chamar: registram-se sozinhos no `AudioSystem`, e a partir daí abrir
um MP3 é igual a abrir um WAV. É por isso que não há nenhum import deles aqui.

POR QUE CONVERTER, se o pedido foi "não perder qualidade": decodificar um MP3 é
uma operação EXATA — o WAV guarda exatamente o que saiu do decodificador, amostra
por amostra. Perda só existe ao RE-ENCODAR (MP3 → MP3), e não é o que acontece
aqui. O arquivo fica maior, só isso.

**`private const val SEGUNDOS_MAXIMOS = 300`**

TETO DE DURAÇÃO, e ele não é preciosismo.

O áudio decodificado vai inteiro para a memória antes de virar arquivo, porque
escrever WAV exige saber a contagem de quadros de antemão e um MP3 não a
declara. Sem teto, escolher um álbum de uma hora por engano viraria ~600 MB de
PCM num heap com teto de 1 GB — ou seja, o app fechando na cara da pessoa.

Cinco minutos é generoso para soundboard (o uso real é de dois a dez segundos)
e ainda deixa o pior caso em ~50 MB.

**`val canais = origem.format.channels.coerceAtLeast(1)`**

PCM de 16 bits: é o que placa de som toca sem conversão extra, e o
que o JavaSound abre sem depender de codec do sistema.

O formato de origem de um MP3 vem com taxa de quadros
desconhecida; a taxa de AMOSTRAGEM é o campo confiável, e é dela
que o formato de destino é montado.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/voice/EstadoDaVoz.kt`

**`sealed interface VoiceStatus`**

O VOCABULÁRIO DA CALL, separado de quem a implementa.

Estes tipos nasceram dentro do VoiceEngine, e ficar lá deixou de fazer sentido
quando a voz mudou de casa: hoje quem preenche isto é a CallEmMalha, falando com
o processo em Go. A tela não deve saber qual dos dois está do outro lado — ela lê
"quem está na sala e quem está falando", e é só isso que estes tipos dizem.

Ficar num arquivo próprio também deixa o corte limpo pra quando o motor antigo
finalmente sair: nada da interface segue junto.

**`data class VoiceParticipant(…`**

`label` e `avatarUrl` chegam vazios da malha e são preenchidos pela tela, com a
lista de membros que ela já tem em mãos.

Isso mudou com a malha e vale registrar: antes o nome vinha nos metadados do
token do LiveKit, porque havia um servidor de mídia para carregá-los. Ponto a
ponto não tem esse servidor, então quem circula é só o id — e traduzir id em
gente é trabalho de quem tem a lista, não de quem carrega o som.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/voice/PermissoesWindows.kt`

**`enum class Acesso`**

"O Windows está deixando o Astra usar isto?"

No Windows não existe janelinha de permissão como no navegador: o app tenta
usar o microfone e, se a privacidade estiver fechada, ele simplesmente recebe
SILÊNCIO — sem erro, sem aviso, sem nada. Do lado de quem usa isso vira "meu
mic não funciona no Astra", e não há como adivinhar de fora.

Por isso a checagem aqui TENTA DE VERDADE em vez de perguntar ao sistema: abre
o microfone e escuta um pedaço. É a única resposta que vale, porque é
exatamente o que a call vai fazer depois.

Consequência disso, e o motivo de o botão da interface se chamar "permitir"
sem nunca abrir uma janelinha: aplicativo de área de trabalho não tem API pra
PEDIR permissão. Quem decide é um interruptor global do Windows ("deixar
aplicativos da área de trabalho acessarem o microfone"). Tudo o que dá pra
fazer é levar a pessoa até o interruptor certo e ficar conferindo até virar.

**`PENDENTE,`**

Ninguém negou; o Windows só ainda não decidiu. Firewall antes da primeira
call, avisos antes do primeiro aviso. NÃO é defeito — e por isso não pinta
de vermelho nem de amarelo: mandar consertar o que não está quebrado é o
jeito mais rápido de ensinar alguém a ignorar a tela inteira.

**`enum class Permissao(val titulo: String, val oQueE: String)`**

O que cada permissão É, em português de gente. Fica AQUI e não na tela porque
as três telas que mostram isto (boas-vindas, configurações e o aviso da
primeira abertura) precisam do mesmo texto — e texto duplicado é texto que
diverge.

**`fun notificacoes(): Checagem`**

Avisos do Windows.

Aqui NAO ha permissao pra pedir: app de area de trabalho nao tem janelinha
de "permitir notificacoes". O que da pra conferir sao duas coisas de
verdade: se os avisos estao ligados no sistema, e se o Windows JA CONHECE o
Astra (a entrada so nasce depois do primeiro aviso, e so se o processo tiver
identidade — ver WindowsAppId).

"Ainda nao conhece" NAO e defeito: e o estado normal antes do primeiro
aviso. Por isso o botao "permitir" desta linha DISPARA UM AVISO em vez de
abrir configuracao — e o unico jeito de fazer o Windows registrar o app.

**`fun microfone(): Checagem`**

Abre o mic e escuta ~400ms.

O sinal de bloqueio é o silêncio EXATO: privacidade fechada entrega zeros
perfeitos, enquanto microfone de verdade sempre traz um chiadinho de fundo,
nem que seja de 1 bit. Não dá pra afirmar 100% (mic mudo no botão físico dá
o mesmo resultado), então o texto fala das duas causas em vez de cravar uma.

**`fun camera(): Checagem`**

A CÂMERA É PERGUNTADA AO REGISTRO, e não enumerada.

Enumerar era o que fazia antes, pelo webrtc-java — e essa era a última coisa que
segurava 8 MB de biblioteca nativa no pacote depois que a voz migrou para o
processo em Go. Trocar por esta consulta foi o que permitiu ela sair.

O que se perde é saber QUANTAS câmeras existem; o que se ganha é responder à
pergunta que a tela realmente faz, que é "o Windows deixa?". E essa resposta é
melhor que a antiga em um caso concreto: com a privacidade fechada, a
enumeração devolvia lista vazia e a tela dizia "nenhuma câmera encontrada" —
mandando a pessoa procurar um aparelho que está ali, ligado, apenas bloqueado.

Continua sem abrir a câmera: acender a luz de alguém numa tela de boas-vindas
assusta, e com razão.

**`fun tela(): Checagem = Checagem(…`**

A CHECAGEM PASSOU A DIZER A VERDADE.

Ela procurava o ffmpeg e, sem ele, mandava reinstalar o Astra. Agora que a
transmissão está em migração e o ffmpeg saiu do pacote, essa mensagem seria
pior do que inútil: manda a pessoa reinstalar um app que está inteiro, para
resolver algo que nenhuma reinstalação resolve.

**`private const val REGRAS_FIREWALL =`**

Firewall.

Esta é a permissão que o Windows REALMENTE pergunta ("Permitir acesso?"),
uma vez só, na primeira vez que a call abre uma porta. Quem clica em
Cancelar naquele susto ganha uma regra de BLOQUEIO permanente e nunca mais
vê o aviso — e a partir daí a call falha calada, igual ao microfone.

A leitura é do registro em vez de `netsh` de propósito: netsh demora
~1s e pisca uma janela de console. Aqui são ~750 valores de texto no
formato "…|Action=Allow|Active=TRUE|Dir=In|App=C:\…\Astra.exe|…".

**`fun liberarNoFirewall(): Boolean`**

Cria a regra de liberação no firewall. Pede elevação — uma janela do UAC.

Esta é a única linha do painel com uma ação DE VERDADE; as outras só
conseguem levar a pessoa até o interruptor certo do Windows, porque quem
decide ali é uma chave global do sistema. Aqui não: regra de firewall
qualquer administrador escreve.

O roteiro APAGA todas as regras deste executável antes de criar as duas
novas, e isso é o ponto inteiro. Regra de bloqueio vence regra de liberação,
então só acrescentar um "permitir" não conserta o caso mais comum de call
que não conecta: quem clicou Cancelar naquele aviso do Windows ganhou um
bloqueio permanente. Achar essa regra na mão, no meio de ~750 outras, não é
coisa que se peça a ninguém.

Vai por arquivo .ps1, e não por linha de comando, por causa das aspas: o
caminho do executável entra dentro de um argumento do netsh que já é
`program="..."`, e escapar isso através de cmd -> powershell -> netsh dá
exatamente o tipo de erro que só aparece na máquina de outra pessoa. O BOM
é obrigatório: sem ele o Windows PowerShell lê o arquivo como ANSI e um
caminho acentuado (C:\Users\João\...) chega corrompido no netsh.

false = a pessoa recusou o UAC, ou não há executável (rodando pelo Gradle).

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/voice/Sfx.kt`

**`object Sfx`**

Sons do app SINTETIZADOS em runtime (sem arquivos .wav): senoides curtas com
envelope (fade in/out) pra não estalar. Convencao do dono:
  entrar na call  = agudo/fino  (sobe)
  sair da call     = grave/grosso (desce)
  transmitir tela  = 3 fases subindo (cada fase mais fina)
  parar transmissão= as MESMAS 3 fases, invertidas (descendo)
Toca numa thread daemon (não trava a UI); so JDK (javax.sound), zero dependencia.

**`fun aviso() = play(…`**

---- Aviso de mensagem ----

Duas notas subindo uma TERÇA MAIOR (Lá5 → Dó#6). Terça é consonante e soa
como pergunta amável; segunda ou trítono soariam como alarme, e alarme é o
que faz alguém desligar o som do app no terceiro dia.

POR QUE NÃO É O TRAPÉZIO DOS OUTROS SONS: o envelope de sustentação plana
soa como BIPE de aparelho. Aviso toca dezenas de vezes por dia — precisa
desaparecer da consciência entre uma vez e outra, e é o decaimento rápido
que faz isso. Junto vai uma 4ª harmônica fraca, que é o truque clássico do
timbre de marimba: dá corpo de madeira a uma senóide sem custar nada.

Total ~350ms e ganho abaixo do toque de chamada de propósito: isto avisa,
não convoca.

**`fun carinho() = play(…`**

---- Carinho no gato ----

Isto é um TRINADO, não um ronronar, e a diferença é honesta. Ronronar de
verdade é ruído de banda larga modulado a ~25 Hz; com tons puros, a imitação
sai como zumbido de motor. O trinado — aquele "prrup" curto de gato
cumprimentando — é justamente uma nota subindo depressa, e essa este motor
faz bem.

Ganho baixo e 200ms no total: acontece quando você clica no bicho, ou seja,
dezenas de vezes por sessão se a pessoa gostar. Som de interação frequente
precisa ser mais curto e mais quieto do que o instinto pede.

**`@Volatile private var tocando = false`**

---- Toque de chamada no sussurro ----

Repete ate alguem parar (atender, recusar ou o servidor desistir em 45s).
Uma thread so, com bandeira: chamar duas vezes nao empilha dois toques.

O silencio entre as repeticoes e rendido como amostra ZERO em vez de um
sleep entre duas aberturas de linha de audio: abrir e fechar a
SourceDataLine a cada 3 segundos estala em alguns drivers do Windows, e o
estalo chega mais alto que o proprio toque.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/voice/SidecarDeVoz.kt`

**`@Serializable`**

O SIDECAR DE VOZ — o processo à parte que carrega a mídia.

POR QUE ELE EXISTE. A voz do Astra vivia dentro da JVM, falando com objetos
nativos por JNI. Objeto nativo liberado enquanto outra thread ainda o usa não
lança exceção em Kotlin: derruba o processo inteiro. Na prática, abrir uma call
podia fechar o Astra, e encerrar uma call também — inclusive em máquina folgada,
o que descartou "falta de recurso" como explicação.

Num processo separado, o pior caso de um defeito de mídia deixa de ser "o Astra
fechou" e passa a ser "a call caiu e voltou". A conversa em texto, os servidores
e as janelas abertas continuam de pé. Essa é a razão principal desta classe
existir, e ela vale mesmo que o código do outro lado tenha os próprios defeitos —
defeitos vão existir de qualquer jeito, e o que muda é o tamanho do estrago.

O PROTOCOLO é uma linha de JSON por mensagem, pela entrada e saída padrão. Sem
porta de rede: um socket local acorda o Firewall do Windows, e ninguém merece um
alerta de segurança por causa de um app de conversa. A entrada padrão fechando
também mata o processo filho sozinha — Astra fechado não deixa sidecar órfão
segurando o microfone.

**`@Serializable`**

Um microfone ou uma saída, do jeito que o processo de voz os enxerga.

O `id` é o identificador do Windows, e é ele que viaja e é guardado — não o nome.
Nome muda com atualização de driver e é repetido entre aparelhos iguais; o
identificador é estável e único, e é por isso que a preferência guarda ele.

**`@Serializable`**

Uma tela desta máquina, do jeito que o processo de voz a enxerga.

A MINIATURA É A INFORMAÇÃO, não o enfeite. O Windows chama os monitores de
`\\.\DISPLAY1` e `\\.\DISPLAY2`; dois monitores do mesmo modelo têm a mesma resolução
e nomes que só diferem no dígito. A única coisa que distingue um do outro é o que está
nele.

Vem vazia quando a tela não pôde ser amostrada — quase sempre porque ela já está sendo
transmitida, que é justamente o caso de quem abriu o seletor para trocar.

**`val quadros = CanoDeQuadros()`**

POR ONDE A IMAGEM CHEGA — cano à parte, porque um quadro de 720p (1,4 MB) na mesma
fila que carrega "fulano está falando" faria o aviso esperar atrás da imagem.

Nasce com este objeto e SOBREVIVE À QUEDA do processo: a porta continua a mesma, e
o processo que reinicia recebe o mesmo endereço no ambiente e religa sozinho.

**`mandar(ComandoDeVoz(cmd = "sair"))`**

"sair" primeiro, e destruir depois só se ele não obedecer: o desligamento
limpo solta microfone e conexões na ordem certa. Matar direto deixa o
aparelho de áudio preso por alguns segundos, e a próxima call abre com
erro de dispositivo ocupado.

**`fun tratamento(eco: Boolean, ruido: Boolean, ganho: Boolean) =`**

OS TRÊS AJUSTES DO MICROFONE NUM COMANDO SÓ, e isso é do Windows, não nosso: os
três moram no mesmo objeto (o cancelador de eco), e mudar qualquer um obriga a
reabrir a fonte — alguns quadros de silêncio. Mandados separados, mexer em dois
interruptores seguidos cortaria o som duas vezes.

O lado de lá ignora o comando quando nada mudou de verdade.

**`fun transmitir(monitor: Int, largura: Int, altura: Int, fps: Int, kbps: Int) =`**

Transmitir a tela. Mandar de novo com `ligado` troca o preset em pleno ar — o
lado de lá desliga o laço e sobe outro, que é o mesmo caminho de sempre.

NÃO DEVOLVE SE DEU CERTO, e não é descuido: montar a captura e o compressor leva
quase um segundo, e a ponte é assíncrona. Quem responde é o evento `transmissao`
— ou o `erro`, quando a máquina não tem compressor de H.264.

**`fun assistir(par: String?) = mandar(ComandoDeVoz(cmd = "assistir", par = par.orEmpty()))`**

QUAL TELA ESTÁ NO PALCO. `par` nulo quer dizer "nenhuma".

É o que autoriza o processo de voz a decodificar — e só ela. As outras chegam, são
lidas (obrigatório: faixa sem leitor entope o buffer) e jogadas fora sem custo.
Decodificar 720p custa 1,03 ms por quadro, e numa sala com três transmitindo isso
era o preço de olhar UMA.

Precisa ser mandado também quando ninguém está olhando nada, que é o caso de sair
da sala de voz para uma conversa de texto sem largar a chamada.

**`private fun mandar(c: ComandoDeVoz): Boolean`**

DEVOLVE SE A ORDEM SAIU MESMO, e quem chama precisa disso.

O processo demora um instante para subir, e comando mandado antes disso não
vai a lugar nenhum. Engolir esse caso em silêncio foi o que fez a malha achar
que tinha conectado em gente que nunca recebeu ordem nenhuma — e como o
conjunto de conectados já continha a pessoa, nenhuma conferência tentava de
novo. Um par mudo pelo resto da call, sem erro em lugar nenhum.

**`return synchronized(w)`**

Escrita SINCRONIZADA no escritor: os comandos nascem em várias
corrotinas (botão de mudo, socket, navegação) e duas escritas
concorrentes intercalariam bytes no meio de uma linha. O outro lado lê
linha a linha — meia linha de JSON é um erro que só aparece sob carga.

**`delay(esperaMs)`**

Espera CRESCENTE, com teto. Sem o crescimento, um defeito que impede
o processo de subir viraria um laço de milhares de execuções por
minuto — que aquece a máquina e enche o disco de log sem nunca
consertar nada.

**`if (quadros.endereco.isNotEmpty())`**

O CANO DE QUADROS VIAJA NO AMBIENTE, e por isso não há nada a descobrir depois:
a porta já está aberta deste lado quando o processo nasce, então ele pode ligar
no primeiro quadro que tiver. Anunciar pela ponte criaria uma corrida entre o
anúncio e o nosso leitor — e essa corrida voltaria a cada queda do processo.
Endereço vazio = a porta não abriu, e aí o processo não recebe nada e não monta
cano nenhum. A voz continua inteira; o que se perde é ver a tela dos outros.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/voice/SoundboardPlayer.kt`

**`object SoundboardPlayer`**

TOCADOR DA SOUNDBOARD.

O servidor nunca manda audio — manda o aviso "tocou o som X, a URL e essa". Quem
baixa e toca e cada cliente, localmente. Por isso todo mundo ouve o arquivo
ORIGINAL, sem passar pelo Opus da voz (que e afinado pra fala e esmaga efeito).

Cache em memoria por URL: numa call, o mesmo som toca dez vezes seguidas, e
baixar dez vezes seria desperdicio de rede e atraso ate o som sair. O teto existe
porque isto vive num app que fica aberto o dia inteiro.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/voice/Transmitindo.kt`

**`object Transmitindo`**

"Estou transmitindo agora?" — sinal global, e o global aqui e proposital.

O CEU (aurora + estrelas) mora no Main, acima de tudo; a sessao de voz nasce dentro do
ShellScreen. Nao ha caminho de composicao de um pro outro, e criar um so pra descer um
booleano custaria mais do que um objeto de uma linha.

PRA QUE: transmitindo, o orcamento da placa pertence a captura e ao compressor. Num
notebook hibrido a MESMA placa integrada desenha a tela, captura os quadros e ainda
comprime — e a aurora e um shader de tela cheia rodando na taxa do monitor. Enquanto a
transmissao esta no ar ela sai da frente. Quem esta assistindo esta olhando o conteudo
compartilhado, nao o fundo do Astra.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/voice/VoiceLog.kt`

**`object VoiceLog`**

Diario da call.

POR QUE ISTO EXISTE: "ninguem me escuta e eu nao escuto ninguem" pode quebrar em
oito lugares diferentes — a lib nativa, o token do backend, o signaling, o join,
a captura do mic, a publicacao da track, a assinatura do audio do outro, a saida
de som. Todos dao exatamente o mesmo sintoma: silencio. E o codigo ja sabia de
qual se tratava — so nao contava pra ninguem.

Aqui cada etapa deixa uma linha. Com isso, "nao funciona" vira "parou no passo
4", que e a diferenca entre consertar em minutos e chutar por semanas.

Grava em arquivo de proposito: na maioria das vezes quem esta com o problema e
um amigo, do outro lado, e pedir print da tela nunca traz a informacao certa.
O arquivo pode ser mandado inteiro.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/voice/VoiceSession.kt`

**`@Stable`**

Sessao de voz VIVA acima da navegacao.

Antes o VoiceEngine nascia dentro do VoiceView (`remember(channel.id) { ... }`)
e um DisposableEffect o matava quando a tela saia da composicao. Como abrir uma
órbita de texto limpa o `voiceChannel` do palco, navegar DESCONECTAVA a call —
era o "kick automatico". Aqui a sessão mora no shell: so desligar (ou entrar em
outra sala) encerra.

Dois conceitos que antes eram um so:
  - `voiceChannel` (ShellVm) = que sala esta NO PALCO. Some ao navegar. Certo.
  - `joined` (aqui)          = em que sala você esta CONECTADO. Sobrevive.

**`var call by mutableStateOf<CallEmMalha?>(null)`**

A CALL AGORA É UMA MALHA, e não um motor falando com um servidor de mídia.

O que trocou por baixo: a voz saiu da JVM e foi para um processo à parte, em
Go. O motivo é concreto — objeto nativo liberado enquanto outra thread ainda o
usava derrubava o Astra inteiro ao abrir e ao encerrar uma call. Num processo
separado, o pior caso vira "a call caiu e voltou".

**`fun joinDm(conversationId: String, titulo: String) =`**

Chamada de sussurro. A sala do LiveKit e `dm:<conversationId>` e o token
dela ja existia — o `connect` sempre foi generico, so ninguem chamava com
"dm".

O ChannelDto aqui e SINTETICO: `id` = conversa, `name` = nome da pessoa.
Ele existe porque a tela de call inteira (VoiceView) e desenhada a partir
dele, e os participantes de la ja vem do LiveKit, nao da lista de membros
da constelacao — entao a mesma tela serve pra sussurro sem mudanca.

**`var mudo by mutableStateOf(false)`**

MUDO E ENSURDECER MORAM AQUI, e nao no motor, por um motivo simples: os
botoes ficam no rodape, que esta na tela o tempo todo, e o motor so existe
durante a call. Estado na sessao = a escolha atravessa entrar e sair de sala,
e chegar mudo na proxima e uma decisao que da pra tomar antes de entrar.

O motor nao guarda nada disto: recebe a ordem ao nascer e a cada mudanca.

**`scope.launch`**

O TRATAMENTO DO MICROFONE VALE NA HORA, e não na próxima call. Quem desliga
um desses interruptores quase sempre está ouvindo o problema naquele
instante, com a call aberta — mandar o efeito para a próxima sala é o mesmo
que não ter interruptor.

Fluxo próprio porque a chave é outra: junto do de cima, trocar uma tecla de
atalho reabriria o microfone sem motivo nenhum.

**`if (joined?.id == sala.id && call != null) return`**

"JA ESTOU NESTA SALA" TEM QUE INCLUIR TER MOTOR, e nao so o id bater.

`joined` e o motor sao dois campos, e nada garantia que andassem juntos:
bastava um caminho deixar `joined` apontando pra sala e o motor nulo
(connect que falhou, dispose sem limpar) pra esta funcao virar um beco —
ela devolvia na hora, achando que ja estavamos dentro, e NUNCA mais se
entrava naquela sala. Sem erro, sem log: o botao simplesmente nao fazia
nada, que e o formato do "aceitei a chamada e nao entrei em call nenhuma".

Com o motor na condicao, o estado inconsistente se conserta sozinho na
proxima tentativa em vez de travar pra sempre.

**`val meuId = koin.get<SessionStore>().load()?.userId`**

SEM ID DE USUÁRIO NÃO HÁ MALHA. O id é o que decide quem oferece a
conexão (regra determinística: o menor oferece), e sem ele os dois lados
ofereceriam ao mesmo tempo — o encontro de ofertas, que produz uma conexão
que nunca fecha.

**`fun encerrar()`**

Fim da sessão inteira (sair da conta, fechar o shell) — não é o mesmo que
desligar de uma call. Solta o gancho de teclado junto: gancho global
instalado depois que a tela morreu é a sobra que ninguém vê e todo mundo
sente, porque o processo segue escutando o teclado sem ninguém pra ouvir.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/WindowsAppId.kt`

**`object WindowsAppId`**

A IDENTIDADE DO ASTRA PRO WINDOWS.

No Windows nao existe "pedir permissao de notificacao" pra app de area de
trabalho — nao ha janelinha como a do navegador. O que existe e IDENTIDADE: o
AppUserModelID. E por ele que o Windows sabe que aquele aviso e "do Astra",
junta os avisos do mesmo app, mostra o nome e o icone certos e cria a entrada
em Configuracoes > Sistema > Notificacoes.

Sem AUMID o processo e anonimo. Foi o que confirmei no registro desta maquina:
dos 36 apps que o Windows conhece em Notifications\Settings, NENHUM era o
Astra. O aviso ate podia sair da bandeja, mas nao havia app nenhum pra ligar,
desligar ou configurar — nem pro Windows nem pra quem usa.

TEM QUE SER ANTES do AWT/bandeja: a chamada vale pro processo inteiro, e o
Windows carimba a identidade quando o icone de bandeja nasce. Depois disso, e
tarde.

O valor segue a convencao Empresa.Produto e NAO pode mudar entre versoes: e a
chave da entrada nas configuracoes do Windows. Mudar equivale a virar outro
app, e as preferencias do usuario ficariam orfas.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/xp/MissoesStore.kt`

**`class MissoesStore(…`**

Missoes, do lado do app.

Duas coisas diferentes, de proposito:

  `painel`     — ESTADO. O que a tela desenha. Buscado ao abrir a tela.
  `concluidas` — EVENTO. "fechei a missao X" ACONTECE. Num StateFlow, fechar duas
                 missoes iguais seguidas seria o mesmo valor e o segundo aviso
                 nunca apareceria.

O painel se atualiza sozinho quando um evento chega, sem voltar no servidor: o
evento diz qual missao fechou, e marcar aquela linha como concluida da o mesmo
resultado que refazer a busca inteira. O progresso PARCIAL (2 de 5) e a unica
coisa que so a busca sabe — e ela roda toda vez que a tela abre, que e o unico
momento em que alguem esta olhando.

### `mobile-native/desktopApp/src/main/kotlin/app/astra/desktop/xp/XpStore.kt`

**`class XpStore(…`**

Progressao do usuario, do lado do app.

SEM POLL, de proposito. Le uma vez ao entrar e depois vive do evento `xp_gain`
do socket, que ja chega com o progresso inteiro. Perguntar de tempos em tempos
custaria mais requisicao do que o proprio ganho: a trava do servidor deixa passar
no maximo um ganho por minuto.

---

## shared — rede e DTOs (Kotlin)

### `mobile-native/shared/build.gradle.kts`

**`plugins`**

:shared — codigo comum (dominio/dados) compartilhado entre :app (Android) e
:desktopApp. Kotlin/JVM puro: os dois alvos rodam sobre a JVM, entao NAO
precisamos de KMP/expect-actual (o AGP 9 quebrou o KMP-Android classico).
REGRA: nada de android.* nem Compose aqui -> so tipos puros, rede, serializacao.

### `mobile-native/shared/src/main/kotlin/app/astra/mobile/core/network/dto/BotPersonaDtos.kt`

**`@Serializable`**

Campo nulo = NAO MEXI. Com `encodeDefaults=false` no Json do app, nulo nem
chega a ser serializado, entao o corpo carrega so o que foi trocado.

E por isso que "voltar ao original" NAO pode ser um nulo: ele sairia igual a
"nao mexi" e o servidor nao teria como distinguir os dois. Daí `limpar`, que
lista pelo NOME os campos que devem voltar ao que esta no codigo.

### `mobile-native/shared/src/main/kotlin/app/astra/mobile/core/network/dto/ChannelMsgDtos.kt`

**`val authorId: String = "",`**

COM DEFAULT, de proposito. Campo obrigatorio aqui significa: qualquer
mensagem que chegue sem ele explode a desserializacao, o runCatching de quem
escuta o socket engole a excecao e a mensagem some SEM SINAL NENHUM. Foi
exatamente o que aconteceu com a bot: o backend mandava so o objeto `author`
aninhado, e o desktop descartou toda resposta dela por meses.
Quem le deve preferir authorId e cair pro author?.id quando vier vazio.

**`val channelName: String? = null,`**

QUEM e O QUÊ, para o aviso de mensagem nova. Antes o evento trazia só o id do
canal, e por isso o aviso do desktop era obrigado a dizer "nova mensagem" — o
bastante para interromper e insuficiente para decidir se valia ser interrompido.

Todos opcionais porque o mesmo evento é a fonte da BOLINHA de não-lido, e essa
parte não pode depender de campo nenhum: um servidor mais antigo (ou um caminho
que ainda não enriquece) continua acendendo o não-lido normalmente, só sem
conteúdo no aviso.

### `mobile-native/shared/src/main/kotlin/app/astra/mobile/core/network/dto/DmDtos.kt`

**`val call: CallLogDto? = null,`**

Registro de CHAMADA. Nulo = mensagem normal. Preenchido = a linha desenha
diferente (centralizada, com icone), como o `poll` faz nos canais. O
`content` vem preenchido junto ("Chamada perdida", "Chamada de 12 min"):
e o que aparece na previa da lista e nos clientes que nao conhecem isto.

### `mobile-native/shared/src/main/kotlin/app/astra/mobile/core/network/dto/MissaoDtos.kt`

**`@Serializable`**

Espelha o PainelDeMissoes de apps/api/src/lib/missoes.ts.

O TITULO vem do servidor, nao de um mapa de ids aqui. Missao nova no catalogo
aparece no app sem release — e o oposto disso seria a tela mostrar "d.orbitas7"
pra quem atualizou o backend e nao o cliente.

### `mobile-native/shared/src/main/kotlin/app/astra/mobile/core/network/dto/NotifPrefDtos.kt`

**`@Serializable`**

AVISOS DA CONTA — o que o SERVIDOR decide, e não esta máquina.

Não confundir com os interruptores do DesktopPrefs: aqueles mandam no balão da
bandeja deste computador. Estes decidem se o aviso CHEGA A EXISTIR — o servidor
consulta antes de gravar a notificação, então desligar "reações" aqui apaga a
reação do sino, do push e do celular de uma vez.

`sounds` NÃO está aqui de propósito: o campo existe no backend, é guardado e
devolvido, e nenhuma linha do servidor o lê. Expor um interruptor morto é pior
que não ter interruptor — a pessoa desliga, o som continua, e ela conclui que o
app mente.

**`@Serializable`**

PEDIDO SEM VALOR PADRÃO EM NENHUM CAMPO, e isso é obrigatório, não estilo.

O Json do app é `Json { ignoreUnknownKeys = true; explicitNulls = false }`, e o
`encodeDefaults` do kotlinx é FALSO por omissão: todo campo igual ao seu próprio
default sai do JSON. Como o servidor faz `{ ...atual, ...patch }`, um campo
ausente significa "não mexe". Se `mentions` tivesse default `true`, RELIGAR
menções mandaria um corpo sem `mentions` e o servidor manteria o `false` — o
interruptor voltaria sozinho, e o motivo seria invisível.

A HORA VAI COMO -1 E NÃO COMO NULO pelo mesmo tipo de motivo: `explicitNulls =
false` apaga nulo na saída, então "limpar o descanso" nunca chegaria. Mandar
sentinela é o que este repositório já faz no `botNoticeChannelId` (string vazia
= voltar ao automático); o servidor traduz de volta.

### `mobile-native/shared/src/main/kotlin/app/astra/mobile/core/network/dto/ServerDtos.kt`

**`@Serializable`**

Liga/desliga a bot NESTA orbita. So true/false: com explicitNulls=false o null
sumiria do JSON e o backend leria "nao mudar" — entao "voltar a herdar da
categoria" nao tem como ser expresso por aqui, e a decisao vira definitiva
pra esta orbita. E o comportamento certo mesmo: quem decidiu na mao, decidiu.

**`@Serializable`**

Reordenar / mover canal (drag na sidebar). O backend (PATCH .../channels/:cid) aceita
name/categoryId/position. position = ordem na secao; categoryId != null MOVE pra dentro
da categoria. categoryId fica null (default) no reorder simples e, com explicitNulls=false
(AppModule), e OMITIDO -> backend mantem a categoria atual. NAO da pra mandar null explicito
(mover pra "solta") por causa disso — caso deferido.

**`@Serializable`**

`activity` nulo = a pessoa parou de mostrar (desligou, fechou o app, ou o
registro venceu no servidor). Nulo e string vazia significam a mesma coisa aqui,
e quem consome trata os dois como "apaga a linha".
`since` = epoch em ms de quando a pessoa ABRIU aquilo. Vem do servidor e nao do
relogio local porque ele so muda quando a ATIVIDADE muda: a renovacao de 45s que
segura o registro vivo reenvia o mesmo instante. Calcular aqui zeraria o
cronometro tres vezes por minuto.

### `mobile-native/shared/src/main/kotlin/app/astra/mobile/core/network/dto/UploadDtos.kt`

**`val sticker: Boolean? = null,`**

Marca de FIGURINHA: desenha em tamanho fixo e sem abrir em tela cheia, em
vez de virar mais uma imagem grande e clicavel. Precisa existir tambem no
AttachmentSchema do backend (packages/types) — o Zod apaga chave que o
schema nao declara, e a marca sumiria em silencio no caminho.

### `mobile-native/shared/src/main/kotlin/app/astra/mobile/core/network/dto/UserDtos.kt`

**`@Serializable`**

A SACOLA DE PREFERÊNCIAS DA CONTA (/api/profile/preferences).

`JsonObject` cru, e não uma classe com `accent` e `bg`, por um motivo concreto:
o servidor **substitui** a sacola inteira (`set({ preferences: serialized })`),
ele não faz merge. Uma classe tipada mandaria de volta só os campos que ela
conhece — e o dia em que o site guardasse uma chave nova, o desktop a apagaria
no primeiro clique de tema. Guardando o objeto inteiro, chave alheia sobrevive
ao ir e voltar mesmo sem ninguém aqui saber o que ela significa.

### `mobile-native/shared/src/main/kotlin/app/astra/mobile/core/network/dto/XpDtos.kt`

**`@Serializable`**

Espelha o Progresso de apps/api/src/lib/xp.ts.

O NIVEL vem pronto do servidor em vez de ser calculado aqui. Duplicar a curva no
cliente daria duas contas da mesma coisa, e no dia em que a taxa fosse ajustada o
app mostraria um nivel e o servidor pagaria outro.

### `mobile-native/shared/src/main/kotlin/app/astra/mobile/core/network/EmojiApi.kt`

**`interface EmojiApi`**

EMOJIS DA CONSTELACAO.

Diferente de som e figurinha, que sobem pelo /api/upload e depois se registram:
aqui a imagem vai MULTIPART pra propria rota. O motivo esta no servidor — ele
redimensiona pra 128px e re-encoda em WebP antes de guardar. Passar pelo
/api/upload guardaria o original inteiro (que e o que figurinha QUER) e um emoji
de 512KB seria baixado inteiro pra ser desenhado com 20 pixels de lado, em toda
linha da conversa onde aparecesse.

### `mobile-native/shared/src/main/kotlin/app/astra/mobile/core/network/InviteApi.kt`

**`interface InviteApi`**

Convite por CODIGO. A previa (`preview`) e publica no backend — não exige login —
mas aqui ela sobe pelo cliente autenticado mesmo, que e o unico que existe depois
do gate. `join` devolve o servidor JA com os canais, entao quem chama pode
selecionar direto sem esperar o proximo carregamento da lista.

### `mobile-native/shared/src/main/kotlin/app/astra/mobile/core/network/StickerApi.kt`

**`interface StickerApi`**

Figurinhas. A imagem sobe pelo /api/upload de sempre; estas rotas so registram,
listam e apagam.

Nao ha "enviar figurinha" aqui: mandar figurinha e mandar MENSAGEM com um anexo
marcado. Rota propria duplicaria resposta, reacao e exclusao.

### `mobile-native/shared/src/main/kotlin/app/astra/mobile/core/network/UserApi.kt`

**`@GET("api/profile/activity")`**

Atividade em lote ("o que a pessoa está usando"), por userId. Quem não tem
atividade simplesmente NÃO VEM no mapa — a resposta traz só os poucos que
estão em alguma coisa, e não uma linha vazia por membro do painel.
Cada entrada traz o texto E desde quando (ver AtividadeDto) — o cartao de
perfil mostra "há 2h 14min" ao lado do nome do programa.

---

## app — Android (Kotlin)

### `mobile-native/app/build.gradle.kts`

**`create("benchmark")`**

So pra GERAR baseline profile (o plugin androidx.baselineprofile
quebra o KSP na variante que ele cria, entao usamos o fluxo manual):
release sem minify (regras saem com nomes reais; o R8 remapeia via
mapping ao empacotar) + <profileable> no manifest overlay.

**`constraints`**

Alinha o Compose core (ui/foundation/animation/runtime) com o que a RikkaUI
arrasta em runtime (androidx compose 1.10.0 via org.jetbrains.compose). Sem isso o
compile fica no 1.7.5 do BOM e o runtime no 1.10.0 -> NoSuchMethodError (ex: FlowRow,
cuja assinatura mudou entre 1.7 e 1.10). material3 segue no 1.3.1 do BOM (= runtime).

**`implementation(libs.androidx.profileinstaller)`**

Baseline Profile: o :baselineprofile gera as regras no device e elas
vivem em src/main/baseline-prof.txt (AGP empacota sozinho); o
profileinstaller (explicito, antes vinha so transitivo) instala o
profile no primeiro run = startup AOT.

**`debugImplementation(libs.androidx.ui.tooling)`**

icones Lucide (mesma familia do web)
blur/vidro-fosco (backdrop do ProfileSheet, estilo iOS)
Custom Tabs (OAuth Google)
RikkaUI: sistema de tema (tokens)
RikkaUI: componentes (Input, Dialog, Avatar...); ejeta p/ copy-paste ao customizar
grade oficial de emojis (busca + recentes), estilo Gboard

### `mobile-native/app/src/main/java/app/astra/mobile/core/crash/CrashReporter.kt`

**`object CrashReporter`**

Rede de seguranca: captura excecoes nao tratadas (inclui crashes de composicao),
grava o stacktrace em arquivo e deixa o processo morrer normalmente. No proximo
launch o app le esse arquivo e mostra a CrashScreen pro user copiar o erro.

### `mobile-native/app/src/main/java/app/astra/mobile/core/push/DmNotifier.kt`

**`object DmNotifier`**

Notificacao conversacional de DM (MessagingStyle): avatar + historico da
conversa + campo "Responder" direto na bandeja, como WhatsApp/Discord.
Compartilhado entre o service FCM (mensagem recebida) e o ReplyReceiver
(eco da propria resposta, que encerra o spinner do RemoteInput).

### `mobile-native/app/src/main/java/app/astra/mobile/core/voice/VoiceManager.kt`

**`screenShareTrackPublishDefaults = VideoTrackPublishDefaults(…`**

Screenshare: fps > tudo. H264 usa o encoder de HARDWARE do device (sustenta
60fps sem engasgar, ao contrario do VP8 por software); MAINTAIN_FRAMERATE faz
o congestion-control derrubar RESOLUCAO antes de fps; simulcast off = camada
unica em qualidade cheia; bitrate/fps altos. Validar codec no device real.

### `mobile-native/app/src/main/java/app/astra/mobile/ui/components/ColorGradientPicker.kt`

**`private enum class PickMode { SOLID, GRADIENT }`**

Construtor de cor: toggle Solido | Gradiente. Emite uma string CSS que o
backend/parseNameColor/parseGradientBrush ja entendem:
  solido   -> "#rrggbb"
  gradiente-> "linear-gradient(Ndeg,#a,#b)"
Substitui os presets (escolha do user). Seed uma vez do valor inicial; depois
vive do proprio estado e avisa o pai a cada mudanca.

### `mobile-native/app/src/main/java/app/astra/mobile/ui/components/EmojiPicker.kt`

**`@Composable`**

Grade oficial de emojis (androidx.emoji2-emojipicker: busca, categorias,
recentes — igual Gboard) no painel editorial do app (mesmo padrao do GifPicker:
scrim + folha inferior). View clássica embrulhada em AndroidView.

### `mobile-native/app/src/main/java/app/astra/mobile/ui/components/FlowRowCompat.kt`

**`@Composable`**

Substituto de androidx.compose.foundation.layout.FlowRow, feito com Layout (API core
estavel). A assinatura 1.7 do FlowRow (com FlowRowOverflow) nao existe na
foundation-layout empacotada em runtime (skew de versao arrastado por lib de terceiro),
causando NoSuchMethodError. Este wrap quebra os filhos em linhas conforme a largura.

### `mobile-native/app/src/main/java/app/astra/mobile/ui/components/ProfileHero.kt`

**`@Composable`**

Header de perfil estilo Discord, compartilhado entre o proprio perfil (ProfileSheet)
e o de outra pessoa (UserProfileScreen): banner (imagem c/ zoom/posicao ou cor solida)
com scrim, avatar sobreposto na borda inferior c/ anel void e ponto de status, e
nome + subtitulo abaixo. Coloque o conteudo (bio, etc.) depois deste bloco.

### `mobile-native/app/src/main/java/app/astra/mobile/ui/components/SettingsGroup.kt`

**`@Composable`**

Card suave agrupado: um marginalia opcional acima + uma superficie raised leve
com fios finos entre os itens. Substitui o "empilhado de caixas com borda".
O grupo intercala os hairlines sozinho -> o chamador so passa as linhas.
Na abertura, cada linha faz fade + slide-up escalonado (entrada em cascata).

### `mobile-native/app/src/main/java/app/astra/mobile/ui/components/StarField.kt`

**`private const val AURORA_AGSL = """`**

Aurora AGSL v2: cortinas organicas por ruido fractal (FBM), nao mais senos.
O tempo anda num CIRCULO no espaco de ruido (cos/sin * raio), entao o loop de
60s fecha perfeito sem salto. Ainda barato: value-noise ALU-only, sem textura.
Extras: tilt (parallax por sensor) desloca o uv; tap no fundo vazio = pulso de
glow + anel que expande (rippleAge < 0 desliga o branch).

**`Canvas(…`**

MEIA RESOLUCAO: o AGSL custa por pixel. O canvas e MEDIDO na metade do
tamanho e composto com scale 2x a partir de um buffer offscreen -> o
shader calcula ~25% dos pixels. O upscale bilinear e invisivel aqui
(aurora e gradiente desfocado); estrelas/conteudo seguem em res cheia.
O shader e independente de resolucao (uv = fragCoord/iResolution), entao
toque/tilt continuam alinhados com a tela sem ajuste.

**`@Composable`**

Fundo cosmico REAL: uma unica instancia global no AstraApp, atras do NavHost
(1 shader + 1 starfield + 1 sensor pro app todo; transicoes de tela deslizam
o conteudo sobre o ceu parado, estilo Discord/iOS). Overlays que cobrem tudo
(ex.: ProfileSheet) usam este direto pra ficarem opacos.

**`@Composable`**

Cauda do cometa: liga os pontos recentes do dedo. Cada segmento pega alpha e
largura por IDADE (esvai em ~0.5s) e por POSICAO (cauda fina/apagada, cabeca
grossa/brilhante) + um ponto com glow na cabeca — estilo dos meteoros. O
ticker so gira enquanto ha pontos vivos (ceu parado = zero custo).

---

## mobile-native — build

### `mobile-native/baselineprofile/build.gradle.kts`

**— sobre o arquivo inteiro —**

Modulo de TESTE que gera o Baseline Profile do :app rodando o journey de
startup num device real (fluxo manual: o plugin androidx.baselineprofile
quebra o KSP na variante nonMinified que ele cria com o AGP 9 em modo
legado; aqui usamos um build type "benchmark" comum + BaselineProfileRule
e copiamos o txt gerado pra app/src/main/baseline-prof.txt).

### `mobile-native/baselineprofile/src/main/java/app/astra/baselineprofile/BaselineProfileGenerator.kt`

**`@RunWith(AndroidJUnit4::class)`**

Journey de startup: abrir o app frio e deixar a primeira tela assentar
(splash -> auth ou home). Tudo que executar aqui vira AOT no APK final.
Nao loga em conta nenhuma: o ganho esta na inicializacao do Compose, Hilt,
rede e navegacao, que rodam antes de qualquer sessao.

---

## apps/api — servidor (TypeScript)

### `apps/api/src/__tests__/types.test.ts`

**`it('PRESERVA o blurhash (era descartado na validacao)', () =>`**

Este par de testes existe por causa de um bug que nao dava erro em lugar
nenhum: o Zod DESCARTA chave que o schema nao declara. O upload calculava o
blurhash, devolvia pro cliente, o cliente reenviava na mensagem — e ele morria
aqui, na validacao, antes de chegar no banco. Trabalho feito e jogado fora em
silencio, em todo upload, por meses.

### `apps/api/src/config/socket.ts`

**`if (regra.guarda)`**

---- Orbita que GUARDA a conversa ----
Vira mensagem de verdade, no banco, e o comando vai junto: resposta
sozinha no historico e uma resposta sem pergunta — em papo livre com a
IA, ninguem entende amanha o que foi perguntado hoje.

### `apps/api/src/db/diagnosticoDeImagens.ts`

**`type Linha = { tabela: string; coluna: string; total: number; uploads: number; bucket: number; dataUri: number`**

DIAGNÓSTICO DE IMAGENS — conta o estado de cada imagem do banco, sem mudar nada.

Existe para responder duas perguntas que estavam travando decisões, e nenhuma delas
dava para responder de fora:

 1. "Conta nova não carrega as imagens já colocadas."  Há uma hipótese forte: as
    imagens salvas quando o storage era LOCAL viraram `/uploads/xxx`, e esses arquivos
    morreram no disco efêmero do Render. Contas antigas ainda veem por causa do cache em
    disco do Coil; conta nova pede ao servidor e leva 404. Se este relatório mostrar
    linhas em `/uploads/`, a hipótese está confirmada — e o número diz o tamanho do
    estrago.

 2. "Vale reprocessar o que já está no bucket?"  A versão de exibição (256px) só é
    gerada em envio novo. Este relatório diz quantas imagens ainda estão sem ela, que é
    exatamente o custo de um backfill — e se ele se paga.

SÓ LÊ. Nenhum UPDATE, nenhum DELETE, nenhuma chamada ao bucket. Rodar isto em produção é
tão seguro quanto abrir o painel do banco, e é para ser rodado em produção mesmo: é o
banco de verdade que tem a resposta.

  cd apps/api && npm run img:diag

Precisa de DATABASE_URL no ambiente — o mesmo que o `db:migrate` usa.

### `apps/api/src/db/encolherImagens.ts`

**`const LADO_DE_EXIBICAO = 256`**

BACKFILL: gera a versão de exibição das imagens que já estão no bucket.

`persistImagemDeExibicao` só age em envio NOVO, então toda imagem que já existia
continua descendo em tamanho cheio — 1024px para desenhar um círculo de 22. Este script
passa por elas uma vez.

  cd apps/api && npm run img:encolher          # SIMULA: diz o que faria, não muda nada
  cd apps/api && npm run img:encolher -- --vai  # executa de verdade

SIMULA POR PADRÃO, e é deliberado: um backfill escreve no banco E no bucket, e é o tipo
de coisa que se roda uma vez, às pressas, no fim do dia. Ter de digitar `--vai` é o
atrito que separa "quero ver o que aconteceria" de "faz".

---- O que ele NÃO faz, e por quê ----

NÃO APAGA NADA. A imagem antiga vira o valor da coluna `...FullUrl` e continua no
bucket. Isso custa espaço e é o preço de poder voltar atrás: se a versão pequena sair
ruim num caso que ninguém previu, o conserto é um UPDATE trocando as colunas de volta,
e não uma imagem perdida para sempre.

NÃO TOCA EM /uploads/. Essas URLs apontam para o disco efêmero do Render e os arquivos
não existem mais (ver `diagnosticoDeImagens`). Baixar daria 404 e o único efeito seria
poluir o relatório com erro que não é erro deste script.

NÃO TOCA EM GIF, pelo mesmo motivo do caminho de envio: esta configuração do `sharp`
devolveria só o primeiro quadro, e avatar que para de animar é regressão.

NÃO TOCA EM HOST DE TERCEIRO (avatar do Google, por exemplo): não é nosso para
reprocessar, e nem sempre continua lá.

### `apps/api/src/db/ensureSchema.ts`

**`export async function ensureCategorySchema(): Promise<void>`**

Guard idempotente de schema, rodado 1x no boot. Existe porque o Render NÃO roda
migration no deploy: colunas/tabelas que entram no schema.ts mas nunca viram
migration aplicada deixavam o Neon defasado e a operação dava 500 (ChannelCategory,
e depois o sistema de cargos — getMemberPerms faz join em ServerRole/ServerMemberRole).
Toda a DDL vive em guardDdl.ts (fonte única, compartilhada com o script manual).
Tudo IF NOT EXISTS / IF EXISTS / duplicate_object -> no-op; rodar N vezes é seguro.

### `apps/api/src/db/guardDdl.ts`

**`export const GUARD_DDL = `**

FONTE ÚNICA de DDL idempotente do Astra. Rodada AUTOMATICAMENTE no boot
(ensureSchema) E disponível pro script manual (manualMigrate). Existe porque o
Render NÃO roda migration no deploy: colunas/tabelas que entram no schema.ts
mas nunca viram migration aplicada deixavam o Neon defasado e a operação
correspondente dava 500 "Erro interno" (foi assim com ChannelCategory, e de
novo com o sistema de cargos: getMemberPerms faz join em ServerRole/
ServerMemberRole — sem as tabelas, expulsar/checar permissão estoura).

Regras: TUDO IF NOT EXISTS / IF EXISTS / duplicate_object -> no-op. Rodar isto
N vezes é seguro e barato (Postgres pula o que já existe). Ordem: tabelas-base
(User/Server/ServerMember/Channel/Message/DirectMessage/DMConversation) já vêm
do migration 0000; aqui só garantimos o que veio depois. DDL espelha o estilo
do Drizzle (nomes de constraint/index) pra um futuro db:push não ver drift.

### `apps/api/src/db/schema.ts`

**`avatarUrl:    text('avatarUrl'),`**

A VERSÃO DE EXIBIÇÃO, e não a que a pessoa enviou. Ver `persistAvatar`: o cliente
salva 1024px, e este campo guarda 256 — o suficiente para o maior lugar em que um
avatar aparece (96dp em tela densa). Tudo que projeta usuário lê daqui, então o
ganho vale para os três clientes sem nenhum deles mudar.

**`iconFullUrl: text('iconFullUrl'),`**

A original, guardada para poder reprocessar. Não desenha nada hoje.

ESTA APARECE NO WIRE, ao contrário da irmã em `User`: as rotas de constelação usam
`db.select()` sem projeção, então o campo viaja. É inofensivo — os três clientes
decodificam com `ignoreUnknownKeys`, e uma URL de imagem pública não é segredo —, mas
vale saber antes de guardar aqui qualquer coisa que não deva sair.

**`export const serverSounds = pgTable('ServerSound',`**

Efeitos sonoros da constelacao. Mesma forma do ServerEmoji de proposito: os dois
sao "colecao de midia curta que pertence a constelacao", e divergir a estrutura
so criaria dois jeitos de fazer a mesma coisa.

A duracao fica GRAVADA aqui em vez de ser lida do arquivo na hora de tocar: quem
mostra a lista precisa dela pra desenhar, e abrir um WAV do bucket so pra saber
quanto ele dura seria uma requisicao por som a cada abertura do painel.

**`export const serverStickers = pgTable('ServerSticker',`**

Figurinhas da constelacao. Mesma forma do ServerSound de proposito — sao a
mesma ideia ("colecao de midia curta que pertence a constelacao"), e divergir a
estrutura so criaria dois jeitos de fazer a mesma coisa.

width/height ficam GRAVADOS: a conversa reserva o espaco da figurinha ANTES de
a imagem chegar. Sem eles a linha nasce com altura zero e empurra tudo pra
baixo quando a figurinha carrega — quem estava lendo perde a linha.

**`hiddenByA:    timestamp('hiddenByA', { precision: 3 }),`**

"Fechar conversa": timestamp de quando cada lado escondeu. NAO apaga nada e
nao afeta o outro lado. A conversa VOLTA sozinha quando chega mensagem nova,
porque a regra e "escondida se updatedAt <= hiddenBy" e mensagem nova bumpa o
updatedAt — mesma semantica do Discord, sem precisar de flag pra desfazer.

**`call:           text('call'),`**

Registro de CHAMADA (JSON), no mesmo espirito do `poll` das mensagens de
canal: a linha e uma mensagem de verdade, mas desenha diferente. Guardar a
chamada como uma linha do historico e o unico jeito de a pessoa descobrir
que ligaram enquanto o app estava fechado — e essa e justamente a chamada
que importa.

**`export const userBlocks = pgTable('UserBlock',`**

Bloqueio de pessoa. DIRECIONAL de proposito (quem bloqueou -> quem foi
bloqueado): saber quem partiu do bloqueio decide o que cada lado enxerga. Quem
bloqueia ve "Desbloquear"; quem foi bloqueado nao ve nada (o Discord tambem
nao avisa, e avisar so renderia briga).
O EFEITO, porem, vale nos dois sentidos: bloqueou, ninguem manda sussurro pro
outro. Bloqueio de mao unica seria uma porta trancada com a chave do lado de fora.

**`export const userMissions = pgTable('UserMission',`**

Progressao do usuario. UMA linha por pessoa, e so dois numeros que importam.

O NIVEL NAO E GUARDADO: e derivado do xp (progressoDoXp em lib/xp.ts). Guardar
os dois seria manter duas verdades sobre a mesma coisa, e a hora em que elas
discordassem — um crash entre os dois UPDATEs, um ajuste manual da curva — nao
haveria como saber qual esta certa.
Progresso de missao. UMA linha por (pessoa, missao, periodo).

O periodo faz parte da chave de proposito: e o que permite a mesma missao ser
jogada de novo amanha sem apagar nada. '2026-08-03' pra diaria, '2026-W32' pra
semanal, 'sempre' pra conquista.

QUAIS missoes cairam hoje NAO fica guardado aqui: o sorteio e deterministico a
partir de (userId + periodo) — ver lib/missoes.ts. Guardar o sorteio seria uma
segunda verdade sobre a mesma coisa, e uma escrita a mais no primeiro acesso do
dia de cada pessoa.

**`export const botPersonas = pgTable('BotPersona',`**

Aparencia das personas da bot, sobrepondo o que esta no codigo (lib/bot.ts).

A chave e a PERSONA e nao o id do usuario: as duas irmas dividem a mesma conta e
trocam de rosto na virada do turno, entao guardar por usuario daria uma unica
configuracao pras duas. Coluna nula = "usa o que esta no codigo".

### `apps/api/src/index.ts`

**`if (process.env.SOCKET_ADAPTER === 'redis')`**

UMA instancia so alcanca os sockets DELA. `io.emit` num processo nao chega em
quem esta conectado no outro — e o dia em que a API rodar em 2 processos,
METADE dos avisos some sem erro nenhum no log. O adapter do Redis resolve isso
repassando os eventos por pub/sub.

Fica atras de um interruptor (SOCKET_ADAPTER=redis) de proposito, e nao ligado
sempre, por dois motivos concretos: o plano free do Upstash conta comando e o
adapter publica um por broadcast, e nem todo Redis gerenciado libera pub/sub.
Hoje o Render roda 1 instancia — ligar so gastaria cota sem ganhar nada.
No dia que escalar: uma variavel de ambiente, sem tocar em codigo.

**`app.use('/api/profile', express.json({ limit: '16mb' }))`**

AS DUAS ROTAS QUE RECEBEM IMAGEM NO CORPO (avatar/banner de perfil e
icone/banner de constelacao) andam em data-uri, nao em multipart. Com o cliente
salvando em 2560/1024 pra nao borrar em tela 4K, um banner PNG com
transparencia passa folgado dos 8mb antigos — e passar do limite aqui vira 413
no meio do "salvar perfil", sem dizer o motivo. 16mb cobre o teto de 10MB
binario do cliente (ImageCrop.HARD_MAX) mais o inchaco de 33% do base64.

**`app.use('/api/translate', translateRouter)`**

Marcadores e as rotas HTTP de lembrete SAIRAM (2026-08-16, decisao do dono): nao
tinham porta no desktop, e a do web foi removida junto. O WORKER de lembrete
continua (lib/reminders.ts, importado acima) porque a Sparkle marca lembrete por
comando e a entrega funciona -- isso nao era superficie morta.

TRADUCAO FICOU. Ela parecia morta olhando desktop e web, mas o :app ANDROID usa em
producao (Translator -> ChannelChatViewModel e DmChatViewModel). Auditoria de
superficie morta tem de olhar os TRES clientes, nao dois.

**`app.use('/static', express.static(resolve(__dirname, '../public'),`**

Arquivos que VIAJAM COM O CODIGO — hoje, as fotos das duas personas do bot.

Diferente de /uploads em tudo que importa: /uploads e disco efemero do Render e
morre no proximo deploy (por isso o R2 existe); isto esta no repositorio e sobe
junto com o build, entao nunca some e pode ficar muito tempo em cache.

POR QUE ARQUIVO E NAO data-URI embutido: avatarUrl viaja no `author` de CADA
mensagem. Em base64 a foto do bot seriam ~125KB repetidos por mensagem; aqui e
uma URL de 24 caracteres, baixada uma vez e guardada no cache de disco do
cliente. __dirname resolve pro mesmo lugar em dev (src/) e em producao (dist/).

**`logger.error('UnhandledRejection', String(r), r)`**

NÃO derruba o processo. Uma promise rejeitada sem catch em qualquer canto
(ex.: um comando de cache) não deve tirar a API inteira do ar — foi isso que
causou a queda do Upstash capado (um refreshPresence fire-and-forget). Loga +
reporta e segue; o request que a originou falha isolado, o servidor sobrevive.

### `apps/api/src/lib/apagarConta.ts`

**`export async function constelacoesQueImpedem(userId: string)`**

APAGAR CONTA — por LÁPIDE, e não por exclusão.

A linha do usuário sobrevive vazia. Some tudo que é dele (e-mail, foto, nome,
bio, senha, vínculo com o Google) e o que fica é uma casca chamada "conta
apagada", à qual as mensagens antigas continuam presas.

POR QUE NÃO EXCLUIR DE VERDADE: `Message.authorId` e `DirectMessage.senderId`
têm `onDelete: 'cascade'`. Apagar a linha levaria junto TODA mensagem que a
pessoa escreveu — inclusive dentro das conversas de outras pessoas. A conversa
é de dois, e um dos dois indo embora não deveria abrir buracos no que o outro
leu e respondeu: sobraria a resposta sem a pergunta.

O que se apaga de verdade é o que só diz respeito a ela: sessões, presença nas
constelações, amizades, bloqueios, marcadores, lembretes, notificações e
aparelhos de push. Disso nada é conteúdo de conversa alheia.
Constelações de que a pessoa é DONA. Enquanto houver uma, apagar é recusado.

Não é só zelo: `Server.ownerId` referencia o usuário SEM cascade, então o banco
recusaria de qualquer jeito — mas recusaria com um erro de chave estrangeira,
que não diz nada a ninguém. Aqui a recusa vira uma lista do que resolver.

E o zelo também vale: constelação com gente dentro não pode evaporar porque uma
pessoa clicou em apagar conta às 3 da manhã.

**`const marca = userId.slice(-12).toLowerCase()`**

Sufixo do próprio id: e-mail e nome de usuário são ÚNICOS no banco, então
"apagada@..." fixo quebraria na segunda conta apagada. O domínio .invalid é
reservado por norma (RFC 2606) justamente pra não existir de verdade — nada
de e-mail sai desta caixa por acidente.

### `apps/api/src/lib/avatar.test.ts`

**`const LADO_DE_EXIBICAO = 256`**

O AVATAR EM DUAS VERSÕES — a conta que justifica a fatia, feita com pixel de verdade.

O que se quer provar não é que o `sharp` sabe redimensionar (sabe), e sim que a ECONOMIA
existe na ordem de grandeza que motivou a mudança. O cliente salva avatar com 1024
pixels de lado; o maior lugar em que ele é desenhado são 96dp, que numa tela de
densidade dupla dá 192 pixels. Se a versão de 256 não encolher o arquivo de forma
clara, a coluna nova e o processamento no upload não se pagam.

A imagem de teste é RUIDO, e é a escolha honesta: um degradê liso comprimiria a quase
nada nos dois tamanhos e faria a economia parecer maior do que é. Ruído é o pior caso
para um compressor, então o número que sai daqui é um piso.

### `apps/api/src/lib/blocks.ts`

**`export async function haBloqueio(a: string, b: string): Promise<boolean>`**

Bloqueio entre pessoas.

A regra que importa: o EFEITO vale nos dois sentidos. Se A bloqueou B, nem A
manda pra B nem B manda pra A. Bloqueio de mao unica seria uma porta trancada
com a chave do lado de fora — quem bloqueou continuaria recebendo.

O registro, por outro lado, e direcional (quem bloqueou / quem foi bloqueado),
porque so quem bloqueou pode desfazer, e so ele ve isso na interface.

### `apps/api/src/lib/bot.ts`

**`export interface Persona`**

============ PERSONA POR DIA ============

Mesma conta, dois turnos: a Sparxie pega SEXTA e SABADO; o resto da semana
(domingo a quinta) e da Sparkle. A troca em si e anunciada no canal — ver
botAvisos.ts.

UMA conta de proposito. Duas contas separariam o historico de mensagens em
dois autores diferentes, e uma conversa de quinta ficaria com o nome errado pra
sempre na sexta — sem contar dois cadastros pra manter. Aqui o que muda e o
nome exibido e o tom; a memoria, os comandos e o id continuam os mesmos.

**`banner:  string`**

Banner animado do perfil (GIF), e a cor que fica ATRÁS dele enquanto carrega.
As duas irmãs têm paleta própria (escolha do dono): a Sparxie em rosa com
branco, a Sparkle em vermelho com roxo escuro. A cor não é enfeite redundante
— um GIF de alguns MB demora, e sem ela o topo do cartão é um buraco preto até
o primeiro quadro chegar.

**`bannerZoom: number`**

ZOOM QUE FAZ O BANNER COBRIR A FAIXA, em porcento.

O cartao desenha o banner com "cabe inteiro", e a faixa e 3,5:1 enquanto os
dois GIFs sao quase 16:9. Caber inteiro numa faixa MUITO mais larga que a
imagem quer dizer encolher ate a ALTURA caber — e a arte chegava no meio, com
tarja preta dos dois lados. E o mesmo calculo do AvatarPicker.zoomQueCobre no
desktop (3,5 dividido pela proporcao da imagem), so que aqui as medidas sao
conhecidas: sparxie 480x270 -> 197%, sparkle 498x307 -> 216%.

Fica na persona e nao numa conta em tempo de execucao porque o servidor
nunca abre o GIF: ele so guarda a URL. Trocar a arte pede recalcular isto.

**`const raizPublica = env.API_URL?.replace(/\/+$/, '') ?? ''`**

As fotos vivem em apps/api/public/bot/ e sao servidas em /static (ver index.ts).

ABSOLUTA quando a API sabe o proprio endereco, RELATIVA quando nao sabe. Os dois
clientes nativos aceitam relativa (o desktop prefixa a BASE_URL sozinho, no
RelativeUrlMapper), mas o web joga avatarUrl cru dentro de <img src> — e ali uma
URL relativa aponta pro dominio da Vercel, onde /static nao existe. Com API_URL
preenchida no Render, os tres acertam.

### `apps/api/src/lib/botAvisos.test.ts`

**`const emUtc = (iso: string) => new Date(iso)`**

A CHAVE DO TURNO é o que impede a passagem de ser anunciada duas vezes.

O turno da Sparxie tem DOIS dias (sexta e sábado). Se a chave fosse só
"persona + data de hoje", ela se apresentaria na sexta e de novo no sábado —
duas chegadas para uma entrada só. Por isso ela recua até o primeiro dia do
turno: sexta e sábado precisam produzir a MESMA string.

Datas: 2026-08-06 quinta · 07 sexta · 08 sábado · 09 domingo · 10 segunda.

### `apps/api/src/lib/botAvisos.ts`

**`let io: SocketServer | null = null`**

TUDO QUE A BOT DIZ SEM SER CHAMADA.

Ela morava numa constelação com cargo próprio no painel de membros e nunca
abria a boca sozinha — só respondia a `/sparkle`. Isto aqui é o outro lado:
chegada de gente, marco de nível e a troca de turno entre as irmãs.

Três regras valem pros três casos, e são o que separa "ela é viva" de "ela é
spam":
  1. Uma vez só. Toda fala espontânea passa por uma trava no Redis; nada se
     repete por reboot, corrida entre requisições ou clique duplo.
  2. Onde ela pode falar. Respeita o interruptor por órbita/categoria que já
     existe (botNaOrbita) — quem pediu silêncio continua em silêncio.
  3. Curto. Uma linha. Aviso espontâneo que ocupa parágrafo vira ruído, e
     ruído ensina a ignorar.

**`async function canalDeAvisos(serverId: string): Promise<string | null>`**

Onde ela fala numa constelação.

PRIMEIRO a escolha do dono (Server.botNoticeChannelId). Ela vale pra tudo que a
bot diz sem ser chamada — chegada de gente e troca de turno. Subir de nível não
passa por aqui de propósito: aquele aviso é sobre a conversa em que a pessoa
estava, não sobre a constelação.

A escolha é RECONFERIDA a cada aviso, e não confiada. O id pode ter virado uma
órbita apagada, privada, de voz, ou uma em que a bot foi silenciada depois —
nada disso avisa esta tabela quando acontece. Se não passar, cai no automático:
a primeira órbita de texto em que ela tem voz, por posição, que é a mesma ordem
que a pessoa vê na barra lateral.

Cair no automático (e não calar) é decisão do dono: sair no lugar errado é um
aviso fora de lugar; não sair é um recurso que morre em silêncio.

### `apps/api/src/lib/botDiversao.test.ts`

**`describe('dado', () =>`**

Trava a parte dos comandos que tem LOGICA de verdade.

Sortear nao da pra testar (e aleatorio de proposito), mas o que cerca o sorteio
da: interpretar "2d6", nao deixar alguem pedir 9999d9999, e recusar entrada torta
com uma frase util em vez de um NaN na cara da pessoa.

### `apps/api/src/lib/botDiversao.ts`

**— sobre o arquivo inteiro —**

OS COMANDOS QUE NAO PRECISAM DE IA.

Existe porque a conversa livre depende de uma chave de API que o dono nao
consegue ter — mas a bot que ele queria desde o comeco era estilo Loritta, e a
Loritta e isto: sortear, apostar, rankear, zoar. Nada disso precisa de modelo
nenhum, e tudo isso funciona pra sempre, de graca, sem cadastro.

Todo comando aqui e deterministico ou usa dado que o Astra JA TEM. E a vantagem
que uma IA generica nunca teria: a bot fala do SEU servidor, das SUAS mensagens,
do XP das pessoas que estao ali.

### `apps/api/src/lib/botMembership.ts`

**`const NOME_CARGO = 'BOT'`**

A BOT COMO MEMBRO DE VERDADE.

Ate agora ela so existia como AUTOR de mensagem: postava, respondia comando, e
nunca aparecia no painel de membros — porque nunca esteve na tabela. Quem olhava
a lista via uma constelacao onde a Sparkle falava mas nao morava.

Alem do vinculo, um cargo "BOT" com hoist: hoist e o que separa o grupo no painel
em vez de misturar a bot no meio das pessoas. Sem ele o vinculo existiria e
continuaria invisivel na pratica.

Idempotente de ponta a ponta: pode rodar na criacao da constelacao E no boot pra
alcancar as que ja existiam, sem duplicar nada.

**`export async function garantirBotEmTodas(): Promise<void>`**

Alcanca as constelacoes que ja existiam antes desta feature. Roda uma vez no
boot; com o numero de constelacoes deste app, e uma passada barata. Falha de uma
nao derruba as outras — pior cenario e uma constelacao sem a bot no painel, que
e exatamente onde ja estavamos.

### `apps/api/src/lib/botPersona.test.ts`

**`const emUtc = (iso: string) => new Date(iso)`**

Trava a regra de QUEM esta de plantao.

O turno da Sparxie e SEXTA e SABADO (escolha do dono): sexta a noite e quando o
fim de semana comeca de verdade, e domingo ja e vespera de semana.

O fuso e a parte perigosa: o servidor roda em UTC (Render) e o publico e do
Brasil. Com `getDay()` cru, a Sparxie entraria as 21h de QUINTA e sairia as 21h
de SABADO — errado nas duas pontas, e do tipo que so aparece no fim de semana,
quando ninguem esta olhando o log. Por isso as duas bordas tem teste proprio.

Datas de referencia: 2026-08-06 e quinta; 07 sexta; 08 sabado; 09 domingo.

**`it('comando com argumento mostra a forma e um exemplo', () =>`**

O que a caixinha do "/" mostra tem que ENSINAR a escrever. Antes ela dizia
"/sparxie desejo — joga um desejo na estrela": quem lia mandava exatamente
isso, sem desejo nenhum, e o comando reclamava. O formato e justamente a
parte que nao da pra adivinhar.

### `apps/api/src/lib/botScope.ts`

**`export type RegraDaBot = { fala: boolean; guarda: boolean }`**

Onde a bot pode falar.

A regra (decisao do dono): LIGADA em tudo por padrao; quem quiser silencio
DESLIGA. O contrario — nascer desligada — parece mais discreto e na pratica faz
a bot "sumir": ninguem acha, e todo mundo acha que quebrou.

Heranca em tres niveis, do mais especifico pro mais geral:
  orbita.botEnabled   (nulo = nao decidi)
    -> categoria.botEnabled (nulo = nao decidi)
      -> ligada

Por isso as duas colunas sao NULAVEIS: sem o "nao decidi" nao daria pra
desligar uma categoria inteira e ainda assim reativar UMA orbita dentro dela.

### `apps/api/src/lib/botSemCodigo.test.ts`

**`describe('semMarcaDeCodigo', () =>`**

A bot conversa, ela não documenta. Caixa de código no meio de uma conversa faz a
resposta parecer saída de terminal — o oposto da persona.

Estes testes existem porque a instrução no prompt NÃO é garantia: modelo de
linguagem trata regra de formato como preferência forte, não como contrato. A
instrução reduz a frequência; esta função decide o resultado — e o que decide o
resultado é o que precisa de teste.

### `apps/api/src/lib/botSussurro.ts`

**`export async function responderNoSussurro(…`**

RESPOSTA DA BOT DENTRO DO SUSSURRO.

Irmã do `bot_command` do socket, com duas diferenças que vêm do lugar:

1. Sem prefixo. No canal ele separa "falo com a bot" de "falo com a sala"; numa
   conversa de duas pessoas onde a outra é ela, não há o que separar.
2. Sem constelação. O `serverId` vai nulo, e as ferramentas já sabiam lidar com
   isso desde sempre ("Você está em uma DM, não em servidor") — era só o caminho
   até elas que não existia.

A memória usa o id da CONVERSA no lugar do canal: o histórico do papo com ela
fica por conversa, que é exatamente o recorte certo aqui.

### `apps/api/src/lib/contagemDeMembros.ts`

**`export const NAO_E_BOT = sql${serverMembers.userId} <> (select "id" from "User" where "username" = ${BOT_USER`**

A BOT NÃO É GENTE, E A CONTAGEM DEVE DIZER ISSO (pedido do dono).

Ela entra em toda constelação com cargo próprio — precisa disso pra falar, pra
aparecer no painel e pra ter permissão. O efeito colateral era que toda
constelação nascia dizendo "1 membro" com ninguém dentro, e uma de duas pessoas
aparecia como três.

FILTRAR POR USERNAME e não por id resolvido em código, e a razão é a quantidade
de lugares: são seis consultas de contagem espalhadas (descoberta, prévia de
convite, entrar por convite, lista de constelações, constelação única). Buscar o
id da bot antes de cada uma seria seis `await` a mais e seis chances de alguém
esquecer o filtro na sétima consulta. Aqui é um pedaço de SQL só, importado.

O subselect não custa por linha: o Postgres o resolve UMA vez (InitPlan), porque
não depende da linha sendo avaliada.

### `apps/api/src/lib/dmCalls.ts`

**`const TOQUE_MS = 45_000`**

CHAMADA DE VOZ/VIDEO NO SUSSURRO.

A sala do LiveKit (`dm:<conversationId>`) e o token dela ja existiam em
routes/voice.ts, e o WEB ja falava um protocolo de toque (invite/accept/reject)
direto no socket.ts. Estes MESMOS eventos continuam aqui, com o mesmo formato,
justamente pra web e desktop conseguirem se ligar um pro outro — um protocolo
novo em paralelo daria dois jeitos de fazer a mesma coisa, e um deles ficaria
velho.

O que o relay antigo nao tinha, e e o motivo deste arquivo existir:

  1. ESTADO. Ninguem sabia que havia chamada em curso, entao nao havia como
     desistir depois de um tempo nem como gravar o que aconteceu.
  2. CRONOMETRO no servidor. Se ele morasse no app de quem ligou, fechar o app
     durante o toque deixaria o outro tocando pra sempre e nenhuma chamada
     perdida seria gravada — que e exatamente a chamada que importa.
  3. DESTINATARIO derivado da conversa. O relay confiava no `toUserId` que o
     cliente mandava: bastava estar em UMA conversa qualquer pra tocar o
     telefone de qualquer pessoa do app. Agora o outro lado sai da conversa.
  4. BLOQUEIO. Quem foi bloqueado nao toca o telefone de ninguem.

### `apps/api/src/lib/donoDoAstra.ts`

**`const DONOS: ReadonlySet<string> = new Set(…`**

QUEM PODE MEXER NA APARENCIA DAS BOTS.

Isto NAO e permissao de constelacao: a Sparkle e a Sparxie sao uma conta so,
compartilhada por todas as constelacoes, e a cara delas e a mesma em qualquer
lugar. Delegar isso a quem administra UMA constelacao deixaria essa pessoa
mudando a bot pra todo mundo. Por isso a lista mora fora do banco (env), e nao
ha rota que conceda: quem edita e quem tem acesso a hospedagem.

Comparacao por @ e nao por id porque id de usuario e gerado no cadastro — pra
preencher a variavel seria preciso ir ao banco buscar. O @ e unico, o dono sabe
o dele de cabeca, e trocar de @ e coisa que se faz uma vez na vida.

### `apps/api/src/lib/env.ts`

**`GROQ_API_KEY:      z.string().min(1).optional(),`**

Cerebro da bot e do tradutor — ver lib/ia.ts. Groq e o provedor padrao (da
chave sem cartao e sem projeto de cloud); Gemini fica de reserva. A
ANTHROPIC_API_KEY continua declarada so pra quem ja tinha uma no ambiente nao
ver erro de schema no boot; nada mais a le.

**`ASTRA_OWNER_USERNAMES: z.string().optional(),`**

QUEM MANDA NAS BOTS. O @ de quem pode editar a aparencia da Sparkle e da
Sparxie (uma so pessoa; a lista com virgula existe pra o dia em que houver
duas maquinas ou uma conta de teste).

Variavel de ambiente e NAO coluna no banco, de proposito: coluna e algo que
uma falha de permissao em qualquer rota pode ligar pra outra pessoa. Aqui a
unica forma de virar dono e ter acesso ao painel da hospedagem — que ja e o
nivel de acesso que isto concede. Vazio = ninguem edita, e as bots ficam com
a aparencia de fabrica.

### `apps/api/src/lib/ia.ts`

**`export type Provedor = 'groq' | 'gemini' | 'off'`**

O CEREBRO DA ASTRA — so a porta de entrada. Cada provedor mora no seu arquivo.

O FORMATO DE FORA E O DA ANTHROPIC (blocos `text` / `tool_use` / `tool_result`),
de proposito. O laco de ferramentas do bot.ts foi escrito nesse formato, funciona,
e reescrever ele pra falar dialeto de provedor seria mexer na parte que ja esta
certa pra acomodar a parte que muda. Cada adaptador traduz por dentro.

Ter dois provedores nao e flexibilidade especulativa: o do Gemini ja estava
escrito e testado quando o AI Studio recusou a conta do dono. Jogar codigo bom
fora pra depois reescrever quando a chave sair seria pior que os 10 minutos que
custou transformar a escolha numa variavel de ambiente.

### `apps/api/src/lib/iaGemini.ts`

**`const BASE = 'https://generativelanguage.googleapis.com/v1beta/models'`**

Adaptador do Gemini. Traduz o nosso formato (blocos estilo Anthropic) pro
`generateContent` e volta.

Fica de reserva: hoje o provedor padrao e o Groq, porque o AI Studio recusa a
conta do dono. Se um dia a chave sair, e so preencher GEMINI_API_KEY — zero
mudanca de codigo.

O PRECO DESTE AQUI, que precisa estar escrito em algum lugar: na camada gratuita
o Google usa as conversas pra melhorar os modelos deles, e as mensagens do canal
que a bot le vao junto. Quem paga tem opt-out; quem nao paga, nao.

### `apps/api/src/lib/iaGroq.test.ts`

**`const FERRAMENTA =`**

A traducao de historico e o unico lugar do adaptador onde da pra errar em
silencio: se a ordem sair torta ou um tool_call ficar sem resposta, o Groq
devolve 400 e a bot responde "problema tecnico" pra sempre — sem pista de que a
culpa foi de uma conversao, nao do modelo.

### `apps/api/src/lib/iaGroq.ts`

**`const BASE = 'https://api.groq.com/openai/v1/chat/completions'`**

Adaptador do Groq — o provedor PADRAO da Astra.

Escolhido por eliminacao honesta: a Anthropic e paga, o AI Studio do Google
recusa a conta do dono, e uma bot que responde "estou offline" pra sempre nao e
uma bot. O Groq da chave sem cartao, sem projeto de cloud, sem burocracia — e
roda em LPU, o que na pratica significa resposta quase instantanea.

O QUE SE PERDE: o teto gratis e por minuto e por dia (algo como 30 pedidos/min).
Num servidor movimentado isso ESTOURA, e por isso o 429 vira uma mensagem
propria la embaixo em vez de "erro tecnico" generico — a pessoa precisa saber
que e pra tentar de novo em um minuto, nao que a bot quebrou.

A API e compativel com a da OpenAI. Toda a traducao do nosso formato (blocos
estilo Anthropic) acontece aqui dentro; o laco de ferramentas do bot.ts nao sabe
que provedor esta atendendo.

**`export function paraMensagens(system: string, mensagens: any[]): MensagemOpenAi[]`**

A conversao mais delicada do arquivo.

No nosso formato o resultado de uma ferramenta vem DENTRO de uma mensagem de
usuario (`{ role:'user', content:[{type:'tool_result'}] }`). No formato da OpenAI
ele e uma mensagem separada, de papel 'tool', e precisa vir logo depois da
mensagem do assistente que pediu. Entao um item da nossa lista pode virar varios
aqui — e a ordem tem que ser preservada, senao a API recusa com 400.

### `apps/api/src/lib/iconeDeCriacao.test.ts`

**`describe('CreateServerSchema não é trava suficiente para o ícone', () =>`**

POR QUE A ROTA DE CRIAÇÃO PRECISA DAS MESMAS TRAVAS DO PATCH.

Este arquivo não testa a rota (isso exigiria banco e bucket): testa a PREMISSA que fez o
buraco existir, e que é justamente a parte contraintuitiva.

A rota de criação confiava no `CreateServerSchema` para barrar coisa estranha em
`iconUrl`, e o campo é `z.string().url()`. Parece suficiente. Não é — `url()` aceita
data-URI, porque `new URL('data:image/png;base64,…')` é um endereço válido. Sem limite
de tamanho no schema, o teto virava o corpo da requisição (16mb), e uma constelação
podia nascer com megabytes de base64 dentro de uma coluna que é lida com `select()` sem
projeção — arrastados em toda listagem, para sempre.

Se algum dia o schema passar a barrar isso sozinho, estes testes falham e o comentário
da rota pode encolher junto. Enquanto falharem, a trava tem de ficar onde está.

### `apps/api/src/lib/livekit.ts`

**`export function getRoomService(): RoomServiceClient | null`**

Cliente de administracao do LiveKit, num lugar so.

Nasceu privado dentro de routes/voice.ts e agora tem um segundo consumidor: o
XP de call (lib/xp.ts) pergunta ao LiveKit quem ESTA de fato numa sala. Rota
exportando funcao pra dentro de lib/ seria a seta apontando pro lado errado.

Devolve null quando a voz nao esta configurada — quem chama decide o que fazer
(a rota responde 503, o tick de XP simplesmente nao roda).

### `apps/api/src/lib/missoes.test.ts`

**`const ALGUEM = 'user_abc123'`**

O sorteio e a parte que MENTIRIA em silencio se quebrasse.

Ele nao grava nada: as missoes de hoje sao recalculadas a cada request a partir de
(userId + dia). Se deixar de ser estavel, a pessoa ve tres missoes, atualiza a tela
e ve outras tres — com o progresso da anterior preso numa linha que ninguem mais
consulta. Nao daria erro em lugar nenhum.

### `apps/api/src/lib/missoes.ts`

**— sobre o arquivo inteiro —**

MISSOES — o motivo pra voltar amanha.

XP sozinho recompensa quem ja ia usar o app de qualquer jeito. Missao recompensa
quem VOLTA, e e por isso que ela existe: tres camadas com ritmos diferentes, pra
cobrir tres pessoas diferentes.

  diaria    — quem entra hoje                     (some amanha)
  semanal   — quem entra varios dias              (some no domingo)
  conquista — quem esta aqui ha meses             (nunca some)

O XP de missao NAO passa pelo teto diario do lib/xp.ts. O teto existe pra que
ninguem farme conversa fiada; missao e o oposto de farm — ela pede exatamente o
comportamento que o app quer. Fazer a missao e depois descobrir que o XP nao veio
porque o dia acabou seria a pior surpresa possivel.

### `apps/api/src/lib/notifications.ts`

**`case 'friend_request': return true`**

SEM INTERRUPTOR PRÓPRIO, e de propósito. Pedido de amizade é raro, é
dirigido a você pessoalmente e é acionável — não tem como virar ruído do
jeito que menção e reação têm. Um toggle aqui seria uma linha a mais na
tela de configurações protegendo contra um incômodo que não existe.
Silêncio e horário de descanso continuam valendo (são checados fora daqui).

**`export function resumoDaMensagem(content: string, quantosAnexos: number): string`**

O TRECHO que vai no aviso. Num lugar só porque o desktop, o push do celular e o
sino leem o mesmo campo, e três truncagens diferentes dariam três avisos diferentes
para a mesma mensagem.

Mensagem só de anexo vira "enviou uma imagem" e não fica vazia: aspas em volta de
nada lê como falha do app, e o aviso perderia justamente a única coisa que tinha a
dizer. O limite de 140 é o que cabe num balão do Windows sem ser cortado pelo SO no
meio de uma palavra.

### `apps/api/src/lib/permissions.ts`

**`export async function membrosQueVeemCanal(…`**

O INVERSO do `userCanSeeChannel`: dado UM canal, quais destes membros o enxergam.

Existe porque o aviso de mensagem nova é um leque — um canal, N destinatários — e
perguntar "esta pessoa pode ver?" uma vez por membro daria N idas ao banco no
caminho mais quente que existe. Aqui são no máximo duas, e zero no caso comum.

CANAL PÚBLICO NÃO CONSULTA NADA. É a esmagadora maioria, e o curto-circuito é o que
permite esta checagem existir sem custo perceptível: só canal privado paga.

### `apps/api/src/lib/privacidadeDm.test.ts`

**`describe('nivelDeSussurro', () =>`**

Esta funcao e o portao de TODO usuario que existia antes da coluna nascer: pra
eles o banco devolve o default, e qualquer valor que ela nao reconheca vira
'all'. Se ela um dia cair pro lado errado (retornar 'friends' num valor
estranho), o efeito nao e um erro na tela -- e gente que para de conseguir
falar com voce sem nunca ter pedido isso.

### `apps/api/src/lib/privacidadeDm.ts`

**`export type NivelDeSussurro = 'all' | 'shared' | 'friends'`**

QUEM PODE TE MANDAR SUSSURRO.

Antes disto, qualquer pessoa com o seu ID (ou o seu nome de usuário) abria uma
conversa com você. Numa constelação pública isso é um convite aberto a quem
chegar — e o bloqueio, que era a única defesa, só serve DEPOIS que a mensagem
chegou.

Três níveis, e não um interruptor, porque o caso do meio é o mais comum de
todos: alguém da sua constelação que você ainda não adicionou querendo falar
com você. Um binário "amigo ou nada" mataria justamente esse.

**`export async function aceitaSussurroNovo(deId: string, paraId: string): Promise<boolean>`**

CONVERSA QUE JÁ EXISTE PASSA SEMPRE, e isso é regra e não descuido: apertar o
ajuste não pode calar quem você já estava respondendo. O filtro decide quem
consegue CHEGAR até você, não quem já chegou. (O bloqueio é que corta os dois
casos — ele continua sendo checado à parte, e antes deste.)

**`async function dividemConstelacao(x: string, y: string): Promise<boolean>`**

Duas consultas e não um JOIN de propósito: a primeira costuma devolver poucas
dezenas de linhas (as constelações de UMA pessoa), e a segunda vira uma busca
por índice dentro dessa lista curta. Um JOIN aqui faria o banco cruzar as duas
listas inteiras de participação pra descobrir a mesma coisa.

### `apps/api/src/lib/realtime.test.ts`

**`function fakeIo()`**

Trava o CONTRATO dos avisos de tempo real: nome da sala, nome do evento e o
que vai no payload.

Por que este teste existe: canal novo so aparecia pros outros quando eles
reabriam o app, porque simplesmente NAO HAVIA evento. Erros dessa familia
(sala errada, evento renomeado num lado so, payload sem o id) nao quebram
nada — o app segue rodando, apenas mudo. Sao os mais caros de achar, porque
so aparecem com DUAS pontas e ninguem testa assim por acidente.

### `apps/api/src/lib/realtime.ts`

**`let io: SocketServer | null = null`**

Eventos de socket disparados por rotas HTTP.

O realtime so tinha salas de canal e de DM — nada que valesse pra constelacao
inteira tinha por onde ser transmitido. Resultado: criar um canal so aparecia
pros OUTROS quando eles reabriam o app. A sala `server:<id>` nasce em
config/socket.ts; aqui ficam os disparos.

Estas rotas sao routers `const` (nao factories `create...Router(io)` como
messages/dm), entao o io chega por setter no boot em vez de por parametro.
Tudo aqui e opcional: sem io, nada quebra — so nao ha aviso ao vivo.

**`export function servidorDeSocket(): SocketServer | null { return io }`**

Pro `notify()`, que precisa do servidor de socket e nao de um helper pronto.

As rotas que notificam hoje recebem o `io` por parametro porque nasceram como
fabrica (createReactionsRouter(io)). A de amizades nasceu como Router simples, e
transforma-la em fabrica so pra isso mexeria na montagem do index — muito
estrago pra uma dependencia que ja mora aqui neste arquivo.

**`export function channelsChanged(serverId: string)`**

A lista de canais/categorias mudou -> todo mundo da constelacao refaz o
GET /servers. Refetch em vez de delta de proposito: posicao, visibilidade e
permissao sao decididas no backend, e um merge no cliente erraria em canal
privado (cada um ve uma lista diferente).

### `apps/api/src/lib/resumoDaMensagem.test.ts`

**`describe('resumoDaMensagem', () =>`**

O trecho que aparece no aviso do desktop e no push do celular. Vale testar porque
é a única parte da notificação que a pessoa lê ANTES de decidir se vale interromper
o que está fazendo — e porque os casos que quebram (só anexo, texto enorme) são
justamente os que ninguém repara em teste manual.

### `apps/api/src/lib/silencioDeCanal.test.ts`

**`describe('avisoPassa', () =>`**

Esta função decide se um aviso SAI. Errar pra um lado enche a bandeja de quem
pediu silêncio; errar pro outro cala quem não pediu nada — e o segundo caso é
invisível: ninguém percebe que parou de receber aviso, só para de receber.

Ela também é METADE de uma regra escrita duas vezes: a outra vive no cliente
(ShellUiState.orbitaSilenciada). Se as duas divergirem, o sino fica quieto e a
bandeja avisa, ou o contrário. Estes testes prendem esta metade.

### `apps/api/src/lib/silencioDeCanal.ts`

**`export type ModoDeAviso = 'all' | 'mentions' | 'mute'`**

SILENCIAR UMA ÓRBITA (ou uma constelação inteira).

As tabelas `ChannelNotifPref` e `ServerNotifPref` existiam, tinham rota e até
cliente no `shared` — e **ninguém lia**. Dava pra silenciar um canal, o valor
ia pro banco, e a notificação chegava do mesmo jeito. Era um interruptor
desligado de fábrica: parecia funcionar e não fazia nada.

Este arquivo é o lado que faltava. A resolução é em CASCATA, e a ordem importa:

  pref do CANAL  >  pref da CONSTELAÇÃO  >  'all'

O canal vence o servidor porque é a escolha mais específica: quem silenciou a
constelação inteira mas reativou uma órbita disse exatamente isso, e devolver o
silêncio ali seria ignorar a segunda frase por causa da primeira.

### `apps/api/src/lib/spamDetector.ts`

**`const WINDOW_SECONDS = 10`**

Calibragem: o messageLimiter (middleware/rateLimiter) ja barra em 20 msg/10s
com um 429 passageiro que se cura sozinho. Este detector e a rede DEPOIS dela,
pra flood sustentado — entao o teto tem que ficar ABAIXO do limiter mas longe
da conversa normal. Estava em 5/10s, ou seja 4x mais rigido que o limiter:
mandar 6 mensagens rapidas num canal auto-silenciava por 5 minutos, e como
nenhuma rota chamava unmuteUser, so restava esperar.

### `apps/api/src/lib/storage.ts`

**`S3_ENDPOINT,`**

Escapes pra NAO depender da Cloudflare.

Isto aqui sempre foi um cliente S3 comum: a unica coisa presa ao R2 era a URL
do endpoint, montada a partir do account id. Com S3_ENDPOINT preenchido, o
mesmo codigo fala com Supabase Storage, Backblaze B2, MinIO — qualquer coisa
que entenda S3. Importa porque o R2 exige cartao cadastrado mesmo no plano
gratuito, e nem todo mundo tem cartao pra dar.

### `apps/api/src/lib/storageSemBucket.test.ts`

**`async function carregarStorageCom(env: Record<string, string | undefined>)`**

EM PRODUCAO, SEM BUCKET, O UPLOAD TEM DE FALHAR — e este arquivo existe porque a
alternativa falha em SILENCIO, meses depois, longe da causa.

A historia curta: o disco do Render e efemero. Enquanto `putAttachment` caia pro disco
local ao nao achar o bucket, uma variavel de ambiente faltando por dez minutos gravava
`/uploads/xxx` no BANCO. O arquivo morria no proximo deploy; o endereco ficava. O
sintoma aparecia semanas depois e so pra CONTA NOVA — quem ja tinha visto a imagem
continuava enxergando, porque o cliente guarda em disco. Nada nos logs, nenhum upload
falhado, nada pra investigar.

Testar o CAMINHO DE ERRO e nao o feliz e deliberado: o caminho feliz precisa de bucket
de verdade, e o que quebrou nunca foi ele.

`resetModules` + import dinamico porque a decisao e tomada na CARGA do modulo (o `s3` e
o `EXIGE_BUCKET` sao constantes de topo). Importar uma vez no topo do arquivo
congelaria o ambiente do primeiro teste pros dois.

**`for (const item of storageFalta)`**

NOMES, NUNCA VALORES — esta lista vai pro log e pro /health, e os dois sao lidos
por gente que nao deveria ver credencial.

A regra e "cada item e um NOME de variavel de ambiente", e nao "nao contem a
palavra secret": `R2_SECRET_ACCESS_KEY` e um nome legitimo que contem "SECRET" — foi
exatamente nisso que a primeira versao deste teste tropecou. Um valor vazado nao
casaria com esta forma (tem minuscula, digito colado, `=`, `/`, `+`).

### `apps/api/src/lib/xp.test.ts`

**`describe('curva de nivel', () =>`**

Trava a CURVA e a TRILHA.

Estas duas coisas sao as unicas do sistema de progressao que nao dao pra
corrigir depois: no dia em que alguem ja tem nivel 14, mudar o custo do nivel 7
significa que a conta dessa pessoa passa a valer outra coisa. Entao o teste nao
existe pra "cobrir a funcao" — existe pra que uma mudanca acidental na formula
apareca como teste vermelho, e nao como um amigo perguntando por que perdeu
dois niveis da noite pro dia.

### `apps/api/src/lib/xp.ts`

**`export const XP_POR_MENSAGEM     = 12`**

PROGRESSAO DA ASTRA — todas as regras moram aqui.

Um lugar so de proposito: taxa de XP e a coisa mais dolorosa de mudar depois que
alguem ja acumulou, entao ela tem que ser facil de LER antes de ser mexida.

Dois numeros, nao quatro: XP e o que se ganha, Brilho e o que se gasta. A trilha
destrava sozinha por nivel (nao se compra), e a loja gasta Brilho. Quatro moedas
fariam nada parecer valioso.

**`const TETO_NIVEL = 500`**

Tetos diarios. Sao a defesa que NAO depende de eu ter previsto o truque: por mais
criativo que seja o jeito de farmar, o dia acaba no mesmo lugar.
~40 min de conversa ativa
2h de call

**`const TRILHA: number[] = [`**

Recompensa por nivel, DE GRACA — chegou no 4, ganha a 4. Nao ha "resgatar" e nao
ha o que gastar: e o que separa uma trilha de uma loja.

A lista cobre o comeco, onde a curva e mais interessante; dali pra frente TODO
nivel da a mesma coisa, pra sempre. Essa cauda infinita e o que impede a trilha de
virar uma esteira de conteudo que eu teria que reabastecer toda temporada — e
tambem o que garante que o nivel 80 ainda valha alguma coisa.

### `apps/api/src/routes/blocks.ts`

**`router.get('/', requireAuth, asyncHandler(async (req: Request, res: Response) =>`**

Bloquear alguem no Astra.

Bloquear nao e so "some da minha lista": e um pedido de "essa pessoa nao me
alcanca mais". Por isso o POST faz TRES coisas de uma vez, e nao apenas grava
a linha — bloquear e continuar amigo, com a conversa aberta na barra lateral,
seria bloqueio de mentira:
  1. grava o bloqueio (o envio de sussurro passa a ser recusado nos dois lados);
  2. desfaz a amizade, se houver;
  3. esconde a conversa da barra lateral de quem bloqueou.

O que NAO faz, de proposito: avisar a outra pessoa. O Discord tambem nao avisa,
e avisar so renderia briga.

### `apps/api/src/routes/botCommands.ts`

**`const router = Router()`**

Catalogo de comandos pro cliente montar a caixinha que abre ao digitar "/".

Vem do MESMO array que o `ajuda` usa (lib/bot.ts). Duplicar a lista no app
seria mais rapido de escrever e ficaria velha no primeiro comando novo — e
ninguem perceberia, porque nada quebra: a caixinha so deixaria de mostrar.

A lista muda com o dia (prefixo do plantao + extras de fim de semana), por
isso e montada a cada chamada e nao pode ser cacheada no cliente.

**`router.get(…`**

O CATALOGO CRU, com a chave estavel de cada comando. Rota separada da de cima
de proposito: aquela e "o que digitar hoje" (muda com o plantao e traz prefixo e
exemplo), esta e "o que existe pra ligar e desligar" — a mesma lista serviria
mal as duas, porque a chave nao pode mudar com o dia da semana.

### `apps/api/src/routes/botPersona.ts`

**`const router = Router()`**

APARENCIA DAS BOTS — so o dono do Astra.

A Sparkle e a Sparxie sao UMA conta compartilhada por todas as constelacoes: a
cara delas e a mesma em qualquer lugar. Por isso isto nao e permissao de
constelacao (quem administra uma estaria mudando a bot pra todo mundo) e sim uma
lista fora do banco — ver lib/donoDoAstra.ts.

**`limpar: z.array(z.enum([`**

VOLTAR AO ORIGINAL, por campo.

Existe porque o cliente NAO consegue mandar null explicito: o Json do app usa
`encodeDefaults=false`, entao campo nulo nem entra no corpo — "nao mexi" e
"desfaz" chegariam iguais. Uma lista de nomes contorna isso sem inventar um
formato novo pro resto dos campos.

### `apps/api/src/routes/discover.ts`

**`const memberCount = sql<number>(select count(*)::int from "ServerMember" where "ServerMember"."serverId" = "S`**

"Server"."id" QUALIFICADO na mao (nao ${servers.id}): na projecao do select o
Drizzle renderiza a coluna SEM tabela ("id"), e dentro da subquery esse "id"
pelado casa com "ServerMember"."id" (a PK do membro) em vez do id do servidor
-> serverId = id-do-membro nunca bate -> count 0 pra todos. Qualificar corrige.

### `apps/api/src/routes/dm.ts`

**`const convs = all.filter((c) =>`**

Conversas FECHADAS por mim somem — mas so enquanto nada acontecer nelas.
Mensagem nova bumpa o updatedAt e a conversa volta sozinha, sem precisar de
uma acao pra "reabrir". Filtro no JS (nao no SQL) porque qual coluna vale
depende de eu ser o lado A ou o B desta conversa.

**`router.delete(…`**

"Fechar mensagem direta". NAO apaga nada e nao afeta o outro lado: so marca
que EU escondi. A conversa reaparece sozinha na proxima mensagem (ver o filtro
na listagem). Preserva o updatedAt de proposito — bumpar aqui faria a conversa
voltar pro topo de quem acabou de fecha-la.

### `apps/api/src/routes/friends.ts`

**`async function avisarPedidoDeAmizade(paraQuem: string, deQuem: string): Promise<void>`**

Aviso de pedido de amizade: sino + toast na bandeja.

Fora do fluxo da rota (`void ...`) porque avisar não pode atrasar nem derrubar o
pedido em si: se o sino falhar, a amizade continua pedida — o contrário seria
perder a ação por causa do enfeite dela.

Sem `serverId`/`channelId` no payload: clicar nesta notificação não leva a uma
órbita, leva à tela de Amigos. Quem desenha decide o destino; aqui só se conta o
que aconteceu.

### `apps/api/src/routes/messages.ts`

**`function mentionsArray(raw: unknown): string[]`**

`mentions` e uma COLUNA DE TEXTO com ids separados por virgula (schema.ts), e essa
coluna vinha sendo devolvida CRUA no histórico — enquanto o POST e o socket sempre
mandaram um array. Ou seja: o mesmo campo tinha dois formatos dependendo de por onde
a mensagem chegava.

O desktop, que declara `mentions: List<String>`, engasgava no primeiro item do
histórico e NENHUMA conversa carregava. Como o erro era de leitura de resposta, a
política de repetição o tratava como falha temporária e a tela dizia "o servidor está
acordando" — para sempre, com o servidor no ar e respondendo em 200 ms. Um defeito de
contrato disfarçado de problema de hospedagem.

A conversão fica AQUI e não no app: o cliente que se adaptasse ao formato duplo
deixaria a inconsistência viva pro próximo cliente encontrar de novo.

**`if (!row.isPrivate || row.ownerId === userId) return row`**

Canal publico + ja e membro (garantido acima): acesso liberado sem
re-consultar. userCanSeeChannel refazia este mesmo join channels+servers e
a query de serverMembers do zero — 2 round-trips ao Neon por mensagem
enviada E por pagina de historico. So o canal privado precisa da checagem
de cargo (caminho raro), e o dono ve tudo.

### `apps/api/src/routes/notifications.ts`

**`const faltando = [...new Set(…`**

CONSERTO DO HISTORICO: notificação antiga foi gravada sem o nome de quem
mandou (ou com o literal 'Alguém'), e payload gravado não se corrige
sozinho. Como o authorId esta la, da pra resolver o nome na leitura — UMA
consulta por pagina, so pelos ids que faltam. Sem isto o conserto so
valeria pra notificação nova e a lista seguiria cheia de "alguém".

**`const HoraOuVazio = z.number().int().min(-1).max(23).nullable().optional()`**

-1 É ACEITO E SIGNIFICA "SEM HORÁRIO DE DESCANSO".

O web manda nulo e continua mandando. O desktop não consegue: o Json do app
Kotlin roda com `explicitNulls = false`, que apaga nulo na serialização, então
"limpar o descanso" chegaria aqui como um corpo sem os campos — e um campo
ausente quer dizer "não mexe". A sentinela é o único jeito de a intenção
atravessar, e é o mesmo recurso que o `botNoticeChannelId` já usa (string vazia
= voltar ao automático). Traduzido logo abaixo, antes de virar estado.

### `apps/api/src/routes/sounds.ts`

**`export function createSoundsRouter(io: SocketServer)`**

SOUNDBOARD DA CONSTELACAO.

Tocar um som NAO mistura audio nenhum na chamada: o servidor so avisa "fulano
tocou o som X" e cada cliente toca o arquivo localmente. Duas razoes, e as duas
pesam:

  1. Misturar no microfone faria o som passar pelo Opus da VOZ — codec afinado
     pra fala, que destroi musica e efeito. Chegaria chapado do outro lado, e o
     dono pediu explicitamente que arquivo nao perca qualidade.
  2. Exigiria mixagem no cliente que fala, e quem esta mudo nao teria por onde
     tocar nada.

Com aviso + arquivo local, todo mundo ouve o som ORIGINAL. O custo e cada um
baixar o arquivo uma vez; o cache resolve o resto.

Sem freio de tempo entre sons: decisao explicita do dono, ciente de que
soundboard sem limite convida spam.

### `apps/api/src/routes/stickers.ts`

**`export const stickersRouter = Router()`**

FIGURINHAS DA CONSTELACAO.

Mesmo desenho do soundboard (routes/sounds.ts): esta rota NAO recebe bytes. O
arquivo sobe pelo /api/upload, que ja sabe guardar no bucket, medir e gerar
blurhash — aqui so registramos a URL. Um lugar so pra upload continua sendo um
lugar so.

Nao ha rota de "enviar figurinha": mandar figurinha e mandar MENSAGEM, com um
anexo marcado `sticker: true`. Criar um caminho proprio duplicaria resposta,
reacao, exclusao e notificacao — tudo que uma mensagem ja tem.

### `apps/api/src/routes/upload.ts`

**`const THUMB_PX = 1280`**

Largura da miniatura — o que a BOLHA do chat baixa (o original so vem quando
alguem abre em tela cheia).

Eram 720px em qualidade 74, e era isso que se via como "foto pixelada na
conversa": a bolha tem 320dp, que num Windows a 200% de escala sao 640 pixeis
FISICOS, entao os 720 chegavam sem folga nenhuma — e a 74 o WebP ja deixa bloco
visivel em degrade e em texto de print. 1280/88 tira as duas coisas e continua
pesando uma fracao do original.

**`const maiorLado = Math.max(meta.width ?? 0, meta.height ?? 0)`**

O ORIGINAL VAI INTEIRO, byte por byte (pedido do dono: arquivo nao perde
qualidade). Antes ele era reduzido a 2048px e re-encodado em WebP 82 —
resolucao perdida e artefato somado, de forma IRREVERSIVEL: o que sobe e o
que fica, nao existe volta pro original depois.

Isso nao briga com "carregar instantaneamente" por causa da miniatura: a
bolha do chat baixa o thumb de 720px, e o original so e buscado quando
alguem abre a imagem em tela cheia. Quem paga o tamanho e quem pediu pra
ver de perto.

O custo real e espaco no bucket (1 GB). Se apertar, o caminho e apagar
anexo velho — nao estragar o novo.

### `apps/api/src/routes/xp.ts`

**`const NIVEIS_MOSTRADOS = 30`**

As REGRAS, servidas pelo servidor.

A tela que explica "como ganhar XP" tem que dizer os numeros de verdade. Cravar
12 e 8 no cliente daria uma tela que mente no dia em que eu ajustar a taxa — e
mentira sobre progressao e a que mais irrita.

**`router.get('/:userId', requireAuth, asyncHandler(async (req: Request, res: Response) =>`**

O progresso de OUTRA pessoa, pro cartão de perfil completo.

Mesma função do /me — não há cálculo paralelo pra divergir. Nada aqui é privado:
nível e XP são vaidade pública, do mesmo naipe de "membro desde". O que NÃO sai
(e por isso a rota devolve o progresso e nada mais) é de ONDE o XP veio: quantas
mensagens, em quais órbitas, quanto tempo em call. Isso desenharia a rotina da
pessoa pra quem abrisse o perfil dela.

POR ÚLTIMO, obrigatoriamente: `/:userId` casa com qualquer coisa, inclusive com
as palavras "me" e "regras". Declarada antes delas, engoliria as duas.

---

## packages — tipos compartilhados

### `packages/types/src/index.ts`

**`bannerScale:     z.number().int().min(50).max(300).optional(),`**

50..300, a MESMA faixa do banner de constelação (servers.ts) e a mesma que o
slider do desktop oferece. Eram três números diferentes pra mesma ideia — aqui
50..200, lá 100..300, e o slider indo de 0 a 300 —, então dar zoom acima de 200
fazia o servidor recusar o PATCH INTEIRO: sumia o salvamento do nome, da bio e
de tudo mais junto, com um "Dados inválidos" que não dizia de qual campo.
Abaixo de 50 a imagem vira um ponto no meio do cartão; esse piso fica.

**`blurhash: z.string().max(200).optional(),`**

PRECISA estar aqui. O Zod descarta chave que o schema nao declara — o upload
calculava o blurhash, devolvia pro cliente, o cliente reenviava na mensagem e
ele morria NA VALIDACAO, antes de chegar no banco. O trabalho era feito e
jogado fora em silencio, toda vez.

**`sticker: z.boolean().optional(),`**

Marca de FIGURINHA. Pela mesma razao do blurhash acima: sem declarar aqui, o
Zod apaga a chave e a figurinha chega no banco como imagem comum — grande,
clicavel, indistinguivel de um print. O bug seria invisivel no envio e so
apareceria depois de recarregar a conversa.
