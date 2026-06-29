package app.astra.mobile.feature.invite.domain

import app.astra.mobile.feature.invite.domain.model.InvitePreview
import app.astra.mobile.feature.server.domain.model.Server

interface InvitesRepository {

    suspend fun preview(code: String): Result<InvitePreview>

    suspend fun join(code: String): Result<Server>
}
