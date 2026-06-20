package app.astra.mobile.feature.profile.domain.model

// Status escolhido pelo usuario (espelha UserStatus do web). INVISIBLE = aparece
// offline pros outros. OFFLINE so existe como estado efetivo (nao da pra escolher).
enum class UserStatus { ONLINE, IDLE, DND, INVISIBLE, OFFLINE;
    companion object {
        fun from(raw: String?): UserStatus = when (raw?.uppercase()) {
            "ONLINE" -> ONLINE
            "IDLE" -> IDLE
            "DND" -> DND
            "INVISIBLE" -> INVISIBLE
            else -> OFFLINE
        }
    }
}
