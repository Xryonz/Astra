package app.astra.mobile.feature.profile.domain.model

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
