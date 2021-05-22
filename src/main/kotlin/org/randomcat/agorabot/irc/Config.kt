package org.randomcat.agorabot.irc

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class IrcServerConfig(
    val host: String,
    val port: Int,
    val serverIsSecure: Boolean,
    val userNickname: String,
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

data class IrcSetupConfig(
    val serverConfig: IrcServerConfig,
)

data class IrcConfig(
    val setupConfig: IrcSetupConfig,
    val relayConfig: IrcRelayConfig,
)
