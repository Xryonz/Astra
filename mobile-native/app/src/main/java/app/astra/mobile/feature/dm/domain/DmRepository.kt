package app.astra.mobile.feature.dm.domain

import app.astra.mobile.feature.dm.domain.model.Conversation

interface DmRepository {
    suspend fun conversations(): Result<List<Conversation>>
}
