package app.astra.mobile.ui

object AstraCopy {

    const val BRAND = "Astra"

    object Noun {
        const val app = "Astra"
        const val server = "constelação"
        const val serverCap = "Constelação"
        const val serverPl = "constelações"
        const val person = "estrela"
        const val personCap = "Estrela"
        const val personPl = "estrelas"
        const val group = "aglomerado"
        const val groupCap = "Aglomerado"
        const val channel = "orbita"
        const val channelCap = "Orbita"
        const val channelPl = "orbitas"
        const val voiceChannel = "órbita de voz"
        const val thread = "cometa"
        const val threadCap = "Cometa"
        const val threadPl = "cometas"
        const val dm = "sussurro"
        const val dmCap = "Sussurro"
        const val dmPl = "sussurros"
        const val coordinate = "coordenada"
    }

    object Action {
        const val createServer = "Forjar constelação"
        const val joinServer = "Orbitar constelação"
        const val leaveServer = "Desorbitar"
        const val inviteServer = "Convidar para constelação"
        const val createGroup = "Forjar aglomerado"
        const val createChannel = "Abrir órbita"
        const val startThread = "Soltar cometa"
        const val startDM = "Iniciar sussurro"
        const val addStar = "Adicionar estrela"
        const val findStar = "Procurar uma estrela"
        const val logout = "Sair do Astra"
    }

    object Desc {
        const val constelacao = "Servidor — espaço da sua comunidade"
        const val aglomerado = "Grupo privado — sem convite público"
        const val estrela = "Usuário"
        const val orbita = "Canal de texto"
        const val orbitaVoz = "Canal de voz/video"
        const val cometa = "Thread — conversa derivada de uma mensagem"
        const val sussurro = "Mensagem privada 1-a-1"
    }

    data class Empty(val title: String, val hint: String)
    object Empties {
        val noServers = Empty("Seu céu ainda está vazio", "Crie ou entre numa constelação.")
        val noDMs = Empty("Nenhuma estrela à vista", "Convide alguém para começar.")
        val noFriends = Empty("Sozinho no céu", "Adicione estrelas por username ou coordenada.")
        val noMessages = Empty("Silêncio cósmico", "Seja o primeiro a transmitir aqui.")
        val noChannelMsgs = Empty("Silêncio nesta órbita", "Envie a primeira transmissão.")
        val noThreads = Empty("Sem cometas por aqui", "Responda numa mensagem para abrir um.")
    }

    fun statusLabel(raw: String): String = when (raw.uppercase()) {
        "ONLINE" -> "Brilhando"
        "IDLE" -> "Distante"
        "DND" -> "Eclipse"
        "INVISIBLE" -> "Oculta"
        else -> "Apagada"
    }

    object Toast {
        const val serverCreated = "Constelação acesa."
        const val serverDeleted = "Constelação extinta."
        const val channelCreated = "Órbita aberta."
        const val channelDeleted = "Órbita eclipsada."
        const val threadCreated = "Cometa solto."
        const val friendAdded = "Estrela alinhada."
        const val networkLost = "Sinal perdido — tentando reconectar."
        const val copySuccess = "Coordenada copiada."
    }
}
