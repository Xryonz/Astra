package app.astra.mobile.feature.invite.domain

import app.astra.mobile.feature.invite.domain.model.InvitePreview
import app.astra.mobile.feature.server.domain.model.Server

interface InvitesRepository {
    /** Preview publico do convite. 404 -> falha com mensagem amigavel. */
    suspend fun preview(code: String): Result<InvitePreview>

    /** Entra no servidor pelo codigo. Devolve o servidor (com canais). */
    suspend fun join(code: String): Result<Server>
}
