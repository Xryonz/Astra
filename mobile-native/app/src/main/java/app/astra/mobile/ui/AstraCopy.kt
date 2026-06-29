package app.astra.mobile.ui

object AstraCopy {

    const val BRAND = "Astra"

    object Noun {
        const val app = "Astra"
        const val server = "constelacao"
        const val serverCap = "Constelacao"
        const val serverPl = "constelacoes"
        const val person = "estrela"
        const val personCap = "Estrela"
        const val personPl = "estrelas"
        const val group = "aglomerado"
        const val groupCap = "Aglomerado"
        const val channel = "orbita"
        const val channelCap = "Orbita"
        const val channelPl = "orbitas"
        const val voiceChannel = "orbita de voz"
        const val thread = "cometa"
        const val threadCap = "Cometa"
        const val threadPl = "cometas"
        const val dm = "sussurro"
        const val dmCap = "Sussurro"
        const val dmPl = "sussurros"
        const val coordinate = "coordenada"
    }

    object Action {
        const val createServer = "Forjar constelacao"
        const val joinServer = "Orbitar constelacao"
        const val leaveServer = "Desorbitar"
        const val inviteServer = "Convidar pra constelacao"
        const val createGroup = "Forjar aglomerado"
        const val createChannel = "Abrir orbita"
        const val startThread = "Soltar cometa"
        const val startDM = "Iniciar sussurro"
        const val addStar = "Adicionar estrela"
        const val findStar = "Procurar uma estrela"
        const val logout = "Sair do Astra"
    }

    object Desc {
        const val constelacao = "Servidor — espaco da sua comunidade"
        const val aglomerado = "Grupo privado — sem convite publico"
        const val estrela = "Usuario"
        const val orbita = "Canal de texto"
        const val orbitaVoz = "Canal de voz/video"
        const val cometa = "Thread — conversa derivada de uma mensagem"
        const val sussurro = "Mensagem privada 1-a-1"
    }

    data class Empty(val title: String, val hint: String)
    object Empties {
        val noServers = Empty("Seu ceu ainda esta vazio", "Crie ou entre numa constelacao.")
        val noDMs = Empty("Nenhuma estrela a vista", "Convide alguem pra comecar.")
        val noFriends = Empty("Sozinho no ceu", "Adicione estrelas por username ou coordenada.")
        val noMessages = Empty("Silencio cosmico", "Seja o primeiro a transmitir aqui.")
        val noChannelMsgs = Empty("Silencio nesta orbita", "Envie a primeira transmissao.")
        val noThreads = Empty("Sem cometas por aqui", "Responda numa mensagem pra abrir um.")
    }

    fun statusLabel(raw: String): String = when (raw.uppercase()) {
        "ONLINE" -> "Brilhando"
        "IDLE" -> "Distante"
        "DND" -> "Eclipse"
        "INVISIBLE" -> "Oculta"
        else -> "Apagada"
    }

    object Toast {
        const val serverCreated = "Constelacao acesa."
        const val serverDeleted = "Constelacao extinta."
        const val channelCreated = "Orbita aberta."
        const val channelDeleted = "Orbita eclipsada."
        const val threadCreated = "Cometa solto."
        const val friendAdded = "Estrela alinhada."
        const val networkLost = "Sinal perdido — tentando reconectar."
        const val copySuccess = "Coordenada copiada."
    }
}
