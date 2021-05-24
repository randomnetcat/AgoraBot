package org.randomcat.agorabot.irc

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

data class IrcServerConfig(
    val host: String,
    val port: Int,
    val serverIsSecure: Boolean,
    val userNickname: String,
)

data class IrcServerListConfig(private val serversByName: ImmutableMap<String, IrcServerConfig>) {
    constructor(serversByName: Map<String, IrcServerConfig>) : this(serversByName.toImmutableMap())

    val names: Set<String>
        get() = serversByName.keys

    fun getByName(name: String): IrcServerConfig {
        return serversByName.getValue(name)
    }
}

/**
 * The configuration for a single bridge, i.e. an IRC channel and a Discord channel that will have messages relayed
 * between them.
 */
data class IrcRelayEntry(
    val ircServerName: String,
    val ircChannelName: String,
    val discordChannelId: String,
    val relayJoinLeaveMessages: Boolean,
    val ircCommandPrefix: String?,
)

data class IrcRelayConfig(val entries: ImmutableList<IrcRelayEntry>) {
    constructor(entries: List<IrcRelayEntry>) : this(entries.toImmutableList())
}

data class IrcSetupConfig(
    val serverListConfig: IrcServerListConfig,
)

data class IrcConfig(
    val setupConfig: IrcSetupConfig,
    val relayConfig: IrcRelayConfig,
) {
    init {
        val knownServerNames = setupConfig.serverListConfig.names
        require(knownServerNames.containsAll(relayConfig.entries.map { it.ircServerName }))
    }
}
