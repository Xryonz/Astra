package app.astra.desktop.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

sealed interface CorDoNome {
    val solida: Color
    val pincel: Brush

    data class Lisa(override val solida: Color) : CorDoNome {
        override val pincel: Brush get() = SolidColor(solida)
    }

    data class Degrade(
        override val solida: Color,
        val fim: Color,
        val graus: Int,
    ) : CorDoNome {
        override val pincel: Brush get() = Brush.linearGradient(listOf(solida, fim))
    }

    sealed interface Animada : CorDoNome {
        data class ArcoIris(override val solida: Color) : Animada {
            override val pincel: Brush get() = SolidColor(solida)
        }

        data class Varredura(override val solida: Color, val fim: Color) : Animada {
            override val pincel: Brush get() = Brush.linearGradient(listOf(solida, fim))
        }

        data class Pulso(override val solida: Color) : Animada {
            override val pincel: Brush get() = SolidColor(solida)
        }
    }
}

fun lerCorDoNome(cru: String?): CorDoNome? {
    val texto = cru?.trim().orEmpty()
    if (texto.isEmpty()) return null
    val partes = texto.split(":")
    return when {
        texto.startsWith("gradient:") -> lerDegrade(partes)
        texto.startsWith("anim:") -> lerAnimada(partes)
        else -> lerHex6(texto)?.let { CorDoNome.Lisa(it) }
    }
}

fun escreverCorDoNome(cor: CorDoNome?): String? = when (cor) {
    null -> null
    is CorDoNome.Lisa -> emHex(cor.solida)
    is CorDoNome.Degrade -> "gradient:${cor.graus}:${emHex(cor.solida)}:${emHex(cor.fim)}"
    is CorDoNome.Animada.ArcoIris -> "anim:arcoiris:${emHex(cor.solida)}"
    is CorDoNome.Animada.Varredura -> "anim:varredura:${emHex(cor.solida)}:${emHex(cor.fim)}"
    is CorDoNome.Animada.Pulso -> "anim:pulso:${emHex(cor.solida)}"
}

fun emHex(cor: Color): String {
    val r = (cor.red * 255).toInt().coerceIn(0, 255)
    val g = (cor.green * 255).toInt().coerceIn(0, 255)
    val b = (cor.blue * 255).toInt().coerceIn(0, 255)
    return "#%02x%02x%02x".format(r, g, b)
}

data class Hsv(val matiz: Float, val saturacao: Float, val valor: Float)

fun emHsv(cor: Color): Hsv {
    val maior = maxOf(cor.red, cor.green, cor.blue)
    val menor = minOf(cor.red, cor.green, cor.blue)
    val faixa = maior - menor
    val matiz = when {
        faixa == 0f -> 0f
        maior == cor.red -> 60f * (((cor.green - cor.blue) / faixa) % 6f)
        maior == cor.green -> 60f * (((cor.blue - cor.red) / faixa) + 2f)
        else -> 60f * (((cor.red - cor.green) / faixa) + 4f)
    }
    return Hsv(
        matiz = if (matiz < 0f) matiz + 360f else matiz,
        saturacao = if (maior == 0f) 0f else faixa / maior,
        valor = maior,
    )
}

private fun lerDegrade(partes: List<String>): CorDoNome? {
    if (partes.size != 4) return null
    val inicio = lerHex6(partes[2]) ?: return null
    val fim = lerHex6(partes[3]) ?: return null
    return CorDoNome.Degrade(inicio, fim, partes[1].toIntOrNull() ?: 0)
}

private fun lerAnimada(partes: List<String>): CorDoNome? {
    val inicio = partes.getOrNull(2)?.let { lerHex6(it) } ?: return null
    return when {
        partes.size == 3 && partes[1] == "arcoiris" -> CorDoNome.Animada.ArcoIris(inicio)
        partes.size == 3 && partes[1] == "pulso" -> CorDoNome.Animada.Pulso(inicio)
        partes.size == 4 && partes[1] == "varredura" ->
            lerHex6(partes[3])?.let { CorDoNome.Animada.Varredura(inicio, it) }
        else -> null
    }
}

private fun lerHex6(cru: String): Color? {
    val h = cru.trim().removePrefix("#")
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}
