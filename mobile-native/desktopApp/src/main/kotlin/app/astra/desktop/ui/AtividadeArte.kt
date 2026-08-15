package app.astra.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.AppWindow
import com.composables.icons.lucide.Boxes
import com.composables.icons.lucide.Braces
import com.composables.icons.lucide.Brush
import com.composables.icons.lucide.Clapperboard
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Figma
import com.composables.icons.lucide.FileSpreadsheet
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderGit2
import com.composables.icons.lucide.Gamepad2
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.MonitorPlay
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Notebook
import com.composables.icons.lucide.Presentation
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Shapes
import com.composables.icons.lucide.Swords
import com.composables.icons.lucide.Terminal
import com.composables.icons.lucide.Twitch
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.Youtube

// A MARCA DO PROGRAMA NA ATIVIDADE — catálogo curado, não ícone extraído.
//
// A alternativa era puxar o ícone do próprio .exe com JNA e mandar pela rede. Ela
// cobre qualquer jogo, inclusive obscuro, e foi descartada por escolha do dono: o
// que trafega hoje é uma string de 64 caracteres, e passar a mandar imagem por
// pessoa transforma um recurso de presença num canal de arquivos — com cache,
// rota de ícone e bytes de todo mundo no Redis de plano gratuito.
//
// Aqui não trafega NADA: a marca é resolvida no cliente, a partir do nome que já
// viajava. Custo de rede zero, custo de memória zero (os glifos são vetores que já
// estão no binário), e o mesmo programa fica igual pra todo mundo — coisa que
// ícone de máquina não garante (cada instalação tem a sua arte).
//
// O PREÇO, dito na cara: só existe marca pro que está nesta lista. Jogo fora dela
// cai no genérico. Esta lista cresce à mão, e é isso que "curado" quer dizer.
//
// Glifo e COR, e não logo de marca: o Astra não empacota arte de terceiro. A cor é
// o que faz reconhecer de longe (verde = Spotify, azul = VS Code) e o glifo diz a
// CATEGORIA quando a cor não basta. Trocar por PNG de verdade depois é mexer numa
// linha por entrada.
data class ArteDeAtividade(val glifo: ImageVector, val cor: Color)

// Cores puxadas pra baixo de propósito. A marca real do Spotify é #1DB954, que
// numa superfície escura vibra e rouba a tela inteira — e a norma do app é cor em
// pouca área, nunca competindo com o conteúdo. Cada uma aqui é a cor da marca
// dessaturada até assentar na paleta editorial.
private val VERDE   = Color(0xFF6EC99B)
private val AZUL    = Color(0xFF6AAECA)
private val ROXO    = Color(0xFF9B7AC4)
private val LARANJA = Color(0xFFCA9A6E)
private val VERMELHO = Color(0xFFC46A6A)
private val AMARELO = Color(0xFFC9A96E)
private val CINZA   = Color(0xFF8A93A3)
private val ROSA    = Color(0xFFCA7A9B)

// Ordem IMPORTA: o primeiro que casar vence, então o específico vem antes do
// genérico ("visual studio code" antes de "visual studio", "chrome" antes de nada
// que também contenha "ch"). A comparação é por trecho contido, minúsculo.
private val CATALOGO: List<Pair<List<String>, ArteDeAtividade>> = listOf(
    // --- editores e desenvolvimento ---
    listOf("visual studio code", "vscode", "code - insiders") to ArteDeAtividade(Lucide.Code, AZUL),
    listOf("visual studio") to ArteDeAtividade(Lucide.Code, ROXO),
    listOf("intellij", "android studio", "pycharm", "webstorm", "rider", "clion") to
        ArteDeAtividade(Lucide.Braces, LARANJA),
    listOf("sublime", "notepad++", "neovim", "vim") to ArteDeAtividade(Lucide.Code, AMARELO),
    listOf("github desktop", "sourcetree", "gitkraken") to ArteDeAtividade(Lucide.FolderGit2, LARANJA),
    listOf("terminal", "powershell", "cmd", "prompt de comando", "bash", "wsl") to
        ArteDeAtividade(Lucide.Terminal, CINZA),

    // --- navegadores ---
    listOf("chrome") to ArteDeAtividade(Lucide.Globe, AMARELO),
    listOf("firefox") to ArteDeAtividade(Lucide.Globe, LARANJA),
    listOf("edge") to ArteDeAtividade(Lucide.Globe, AZUL),
    listOf("opera") to ArteDeAtividade(Lucide.Globe, VERMELHO),
    listOf("brave", "vivaldi", "safari", "navegador") to ArteDeAtividade(Lucide.Globe, CINZA),

    // --- som e vídeo ---
    listOf("spotify") to ArteDeAtividade(Lucide.Music, VERDE),
    listOf("youtube") to ArteDeAtividade(Lucide.Youtube, VERMELHO),
    listOf("twitch") to ArteDeAtividade(Lucide.Twitch, ROXO),
    listOf("vlc", "mpv", "media player", "netflix", "prime video", "disney") to
        ArteDeAtividade(Lucide.MonitorPlay, LARANJA),
    listOf("obs", "streamlabs") to ArteDeAtividade(Lucide.Video, CINZA),
    listOf("premiere", "davinci", "vegas", "after effects", "capcut") to
        ArteDeAtividade(Lucide.Clapperboard, ROXO),

    // --- criação ---
    listOf("photoshop", "gimp", "krita", "paint", "clip studio", "aseprite") to
        ArteDeAtividade(Lucide.Brush, AZUL),
    listOf("figma") to ArteDeAtividade(Lucide.Figma, ROSA),
    listOf("blender", "unity", "unreal", "godot") to ArteDeAtividade(Lucide.Shapes, LARANJA),

    // --- escritório e estudo ---
    listOf("word", "docs", "notion", "obsidian", "onenote") to ArteDeAtividade(Lucide.Notebook, AZUL),
    listOf("excel", "sheets", "planilha") to ArteDeAtividade(Lucide.FileSpreadsheet, VERDE),
    listOf("powerpoint", "slides", "apresenta") to ArteDeAtividade(Lucide.Presentation, LARANJA),

    // --- conversa ---
    listOf("discord") to ArteDeAtividade(Lucide.MessageCircle, ROXO),
    listOf("telegram", "whatsapp", "signal") to ArteDeAtividade(Lucide.Send, AZUL),

    // --- lojas e jogos ---
    // A entrada de LOJA vem depois dos jogos de propósito: quem está com o Steam
    // aberto na biblioteca está no Steam, mas quem está no jogo aparece com o nome
    // do jogo — e aí é o jogo que tem que ganhar a marca.
    listOf("minecraft") to ArteDeAtividade(Lucide.Boxes, VERDE),
    listOf("roblox") to ArteDeAtividade(Lucide.Boxes, VERMELHO),
    listOf("valorant", "league of legends", "riot", "counter-strike", "cs2", "cs:go", "apex", "overwatch") to
        ArteDeAtividade(Lucide.Swords, VERMELHO),
    listOf("fortnite", "gta", "rocket league", "fall guys", "among us") to
        ArteDeAtividade(Lucide.Gamepad2, ROXO),
    listOf("steam", "epic games", "battle.net", "ubisoft", "ea app", "origin", "gog") to
        ArteDeAtividade(Lucide.Gamepad2, AZUL),

    // --- sistema ---
    listOf("explorer", "explorador de arquivos", "gerenciador de arquivos") to
        ArteDeAtividade(Lucide.Folder, AMARELO),
)

// Sem entrada no catálogo: janela genérica, na cor do tema. Neutro de propósito —
// inventar uma cor pro desconhecido faria "não sei o que é isto" parecer
// informação.
fun arteDaAtividade(texto: String?, accent: Color): ArteDeAtividade {
    val nome = texto?.lowercase()?.trim().orEmpty()
    if (nome.isNotEmpty()) {
        for ((chaves, arte) in CATALOGO) {
            if (chaves.any { it in nome }) return arte
        }
    }
    return ArteDeAtividade(Lucide.AppWindow, accent)
}

// HÁ QUANTO TEMPO, em texto adulto e sem enfeite.
//
// `desde` é epoch em ms e vem do SERVIDOR: ele só muda quando a atividade muda, e
// a renovação de 45s que segura o registro vivo reenvia o mesmo instante. Calcular
// isso no cliente zeraria o contador três vezes por minuto.
//
// Zero ou futuro (relógio da máquina adiantado, registro do formato antigo) devolve
// null em vez de "há -3min": é melhor não dizer nada do que dizer errado.
fun tempoDeAtividade(desde: Long, agora: Long = System.currentTimeMillis()): String? {
    if (desde <= 0L) return null
    val ms = agora - desde
    if (ms < 0L) return null
    val minutos = ms / 60_000L
    if (minutos < 1L) return "agora mesmo"
    if (minutos < 60L) return "há ${minutos}min"
    val horas = minutos / 60L
    if (horas < 24L) {
        val resto = minutos % 60L
        return if (resto == 0L) "há ${horas}h" else "há ${horas}h ${resto}min"
    }
    val dias = horas / 24L
    return if (dias == 1L) "há 1 dia" else "há $dias dias"
}
