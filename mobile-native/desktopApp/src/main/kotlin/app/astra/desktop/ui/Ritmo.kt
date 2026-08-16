package app.astra.desktop.ui

import kotlinx.coroutines.delay

// O TETO DE FPS DO CÉU (aurora e estrelas), num lugar só.
//
// Dormir é o que faz o teto valer: pedir quadro é o que custa (o `flush` do
// Direct3D esperando a GPU), e não atualizar o valor — segurar a emissão e seguir
// pedindo quadro não poupava nada. O comentário longo em Aurora.kt conta essa
// história inteira.
//
// O QUE ESTA FUNÇÃO CONSERTA: dormir `1000/cap` **depois** do quadro soma ao tempo
// do próprio quadro em vez de ser o período total. Num monitor de 165Hz o quadro
// leva ~6ms, então pedir 60 dava 6+16 = 22ms, ou seja **45fps**; pedir 30 dava
// ~25fps. O ajuste entregava sempre menos do que dizia — e "menos" aqui é a
// diferença entre 30fps fluido e 25fps visivelmente aos trancos.
//
// A divisão em NANOS, e não em milissegundos, mata o segundo erro: `1000L / 60`
// é 16 (não 16,666), e 0,66ms perdidos por quadro viram ~4% de desvio acumulado.
//
// Sem teto (`cap <= 0`, o padrão LIVRE) isto não faz nada e nem chama `delay` —
// quem escolheu seguir o monitor não paga por uma conta que não pediu.
suspend fun esperarPeloTeto(cap: Int, inicioDoQuadroNanos: Long) {
    if (cap <= 0) return
    val periodo = 1_000_000_000L / cap
    val gasto = System.nanoTime() - inicioDoQuadroNanos
    val resta = periodo - gasto
    // Quadro que já estourou o período não dorme: atrasar mais o próximo só
    // aumentaria o buraco. Vale pra máquina em que o desenho já é o gargalo — ali
    // o teto não tem o que limitar, e insistir viraria engasgo em cima de lentidão.
    if (resta > 0) delay(resta / 1_000_000)
}
