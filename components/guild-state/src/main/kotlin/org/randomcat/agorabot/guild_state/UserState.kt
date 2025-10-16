package org.randomcat.agorabot.guild_state

interface UserState : StringState

interface UserStateMap {
    fun stateForUser(userId: String): UserState
}
