package app.astra.mobile.feature.profile.domain.model

import app.astra.mobile.feature.friends.domain.model.Presence

// Servidor em comum com o usuario visto.
data class MutualServer(
    val id: String,
    val name: String,
    val iconUrl: String?,
    val isGroup: Boolean,
    val role: String,
)

// Resultado de ver o perfil de alguem: o perfil + presenca efetiva + mutuais.
data class ProfileView(
    val profile: Profile,
    val presence: Presence,
    val mutual: List<MutualServer>,
)
