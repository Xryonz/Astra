package app.astra.mobile.ui

object AstraCopy {

    object Noun {
        const val person = "estrela"
        const val personPl = "estrelas"
    }

    object Action {
        const val createServer = "Forjar constelação"
        const val joinServer = "Orbitar constelação"
        const val createGroup = "Forjar aglomerado"
        const val startDM = "Iniciar sussurro"
        const val addStar = "Adicionar estrela"
        const val logout = "Sair do Astra"
    }

    object Desc {
        const val constelacao = "Servidor: espaço da sua comunidade"
        const val aglomerado = "Grupo privado, sem convite público"
        const val sussurro = "Mensagem privada 1-a-1"
    }

    data class Empty(val title: String, val hint: String)
    object Empties {
        val noDMs = Empty("Nenhuma estrela à vista", "Convide alguém para começar.")
        val noFriends = Empty("Sozinho no céu", "Adicione estrelas por username ou coordenada.")
    }

    fun statusLabel(raw: String): String = when (raw.uppercase()) {
        "ONLINE" -> "Brilhando"
        "IDLE" -> "Distante"
        "DND" -> "Eclipse"
        "INVISIBLE" -> "Oculta"
        else -> "Apagada"
    }
}
