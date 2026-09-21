package app.astra.mobile.feature.friends.domain

import app.astra.mobile.feature.friends.domain.model.Friend
import app.astra.mobile.feature.friends.domain.model.FriendRequest

interface FriendsRepository {
    suspend fun friends(): Result<List<Friend>>
    suspend fun incoming(): Result<List<FriendRequest>>
    suspend fun outgoing(): Result<List<FriendRequest>>
    suspend fun sendRequest(username: String): Result<Unit>
    suspend fun accept(friendshipId: String): Result<Unit>
    suspend fun remove(friendshipId: String): Result<Unit>
}
