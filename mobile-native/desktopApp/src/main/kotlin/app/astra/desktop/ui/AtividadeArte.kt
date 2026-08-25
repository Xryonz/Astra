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

data class ArteDeAtividade(val glifo: ImageVector, val cor: Color)

private val VERDE   = Color(0xFF6EC99B)
private val AZUL    = Color(0xFF6AAECA)
private val ROXO    = Color(0xFF9B7AC4)
private val LARANJA = Color(0xFFCA9A6E)
private val VERMELHO = Color(0xFFC46A6A)
private val AMARELO = Color(0xFFC9A96E)
private val CINZA   = Color(0xFF8A93A3)
private val ROSA    = Color(0xFFCA7A9B)

private val CATALOGO: List<Pair<List<String>, ArteDeAtividade>> = listOf(
    listOf("visual studio code", "vscode", "code - insiders") to ArteDeAtividade(Lucide.Code, AZUL),
    listOf("visual studio") to ArteDeAtividade(Lucide.Code, ROXO),
    listOf("intellij", "android studio", "pycharm", "webstorm", "rider", "clion") to
        ArteDeAtividade(Lucide.Braces, LARANJA),
    listOf("sublime", "notepad++", "neovim", "vim") to ArteDeAtividade(Lucide.Code, AMARELO),
    listOf("github desktop", "sourcetree", "gitkraken") to ArteDeAtividade(Lucide.FolderGit2, LARANJA),
    listOf("terminal", "powershell", "cmd", "prompt de comando", "bash", "wsl") to
        ArteDeAtividade(Lucide.Terminal, CINZA),

    listOf("chrome") to ArteDeAtividade(Lucide.Globe, AMARELO),
    listOf("firefox") to ArteDeAtividade(Lucide.Globe, LARANJA),
    listOf("edge") to ArteDeAtividade(Lucide.Globe, AZUL),
    listOf("opera") to ArteDeAtividade(Lucide.Globe, VERMELHO),
    listOf("brave", "vivaldi", "safari", "navegador") to ArteDeAtividade(Lucide.Globe, CINZA),

    listOf("spotify") to ArteDeAtividade(Lucide.Music, VERDE),
    listOf("youtube") to ArteDeAtividade(Lucide.Youtube, VERMELHO),
    listOf("twitch") to ArteDeAtividade(Lucide.Twitch, ROXO),
    listOf("vlc", "mpv", "media player", "netflix", "prime video", "disney") to
        ArteDeAtividade(Lucide.MonitorPlay, LARANJA),
    listOf("obs", "streamlabs") to ArteDeAtividade(Lucide.Video, CINZA),
    listOf("premiere", "davinci", "vegas", "after effects", "capcut") to
        ArteDeAtividade(Lucide.Clapperboard, ROXO),

    listOf("photoshop", "gimp", "krita", "paint", "clip studio", "aseprite") to
        ArteDeAtividade(Lucide.Brush, AZUL),
    listOf("figma") to ArteDeAtividade(Lucide.Figma, ROSA),
    listOf("blender", "unity", "unreal", "godot") to ArteDeAtividade(Lucide.Shapes, LARANJA),

    listOf("word", "docs", "notion", "obsidian", "onenote") to ArteDeAtividade(Lucide.Notebook, AZUL),
    listOf("excel", "sheets", "planilha") to ArteDeAtividade(Lucide.FileSpreadsheet, VERDE),
    listOf("powerpoint", "slides", "apresenta") to ArteDeAtividade(Lucide.Presentation, LARANJA),

    listOf("discord") to ArteDeAtividade(Lucide.MessageCircle, ROXO),
    listOf("telegram", "whatsapp", "signal") to ArteDeAtividade(Lucide.Send, AZUL),

    listOf("minecraft") to ArteDeAtividade(Lucide.Boxes, VERDE),
    listOf("roblox") to ArteDeAtividade(Lucide.Boxes, VERMELHO),
    listOf("valorant", "league of legends", "riot", "counter-strike", "cs2", "cs:go", "apex", "overwatch") to
        ArteDeAtividade(Lucide.Swords, VERMELHO),
    listOf("fortnite", "gta", "rocket league", "fall guys", "among us") to
        ArteDeAtividade(Lucide.Gamepad2, ROXO),
    listOf("steam", "epic games", "battle.net", "ubisoft", "ea app", "origin", "gog") to
        ArteDeAtividade(Lucide.Gamepad2, AZUL),

    listOf("explorer", "explorador de arquivos", "gerenciador de arquivos") to
        ArteDeAtividade(Lucide.Folder, AMARELO),
)

fun arteDaAtividade(texto: String?, accent: Color): ArteDeAtividade {
    val nome = texto?.lowercase()?.trim().orEmpty()
    if (nome.isNotEmpty()) {
        for ((chaves, arte) in CATALOGO) {
            if (chaves.any { it in nome }) return arte
        }
    }
    return ArteDeAtividade(Lucide.AppWindow, accent)
}

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
