package org.randomcat.agorabot.irc

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The configuration for the connection to the server, i.e. the config that could be shared between multiple bots
 * without issues.
 */
data class IrcServerConfig(
    val server: String,
    val port: Int,
    val serverIsSecure: Boolean,
)

/**
 * The configuration for the user, i.e. the config that only this instance should have and that would cause issues if
 * shared.
 */
data class IrcUserConfig(
    val nickname: String,
)

/**
 * The configuration for a single bridge, i.e. an IRC channel and a Discord channel that will have messages relayed
 * between them.
 */
data class IrcRelayEntry(
    val ircChannelName: String,
    val discordChannelId: String,
    val relayJoinLeaveMessages: Boolean,
    val ircCommandPrefix: String?,
)

data class IrcRelayConfig(val entries: ImmutableList<IrcRelayEntry>) {
    constructor(entries: List<IrcRelayEntry>) : this(entries.toImmutableList())
}

data class IrcConfig(
    val server: IrcServerConfig,
    val user: IrcUserConfig,
    val relayConfig: IrcRelayConfig,
)
