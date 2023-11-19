package org.randomcat.agorabot.irc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val RELAY_USER_STATE_KEY = "relay"

@Serializable
sealed class RelayUserState {
    companion object {
        val DEFAULT = Version0()
    }

    @Serializable
    @SerialName("RelayUserStateV0")
    data class Version0(
        val disabledGuilds: Set<String> = setOf(),
    ) : RelayUserState()
}
