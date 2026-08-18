package app.astra.desktop.voice

// O VOCABULÁRIO DA CALL, separado de quem a implementa.
//
// Estes tipos nasceram dentro do VoiceEngine, e ficar lá deixou de fazer sentido
// quando a voz mudou de casa: hoje quem preenche isto é a CallEmMalha, falando com
// o processo em Go. A tela não deve saber qual dos dois está do outro lado — ela lê
// "quem está na sala e quem está falando", e é só isso que estes tipos dizem.
//
// Ficar num arquivo próprio também deixa o corte limpo pra quando o motor antigo
// finalmente sair: nada da interface segue junto.

sealed interface VoiceStatus {
    data object Connecting : VoiceStatus

    // `audioLive` é a diferença entre ESTAR NA SALA e a voz de fato passar. São
    // duas coisas, e só a segunda faz alguém ouvir alguém — dizer "conectado" nas
    // duas escondia justamente a falha que a pessoa está sentindo.
    data class Connected(
        val others: List<VoiceParticipant>,
        val audioLive: Boolean = false,
        val mySpeaking: Boolean = false,
    ) : VoiceStatus

    data class Failed(val reason: String) : VoiceStatus
    data object Closed : VoiceStatus
}

// `label` e `avatarUrl` chegam vazios da malha e são preenchidos pela tela, com a
// lista de membros que ela já tem em mãos.
//
// Isso mudou com a malha e vale registrar: antes o nome vinha nos metadados do
// token do LiveKit, porque havia um servidor de mídia para carregá-los. Ponto a
// ponto não tem esse servidor, então quem circula é só o id — e traduzir id em
// gente é trabalho de quem tem a lista, não de quem carrega o som.
data class VoiceParticipant(
    val identity: String,
    val label: String,
    val speaking: Boolean,
    val avatarUrl: String? = null,
)
