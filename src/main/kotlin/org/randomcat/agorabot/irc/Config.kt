package org.randomcat.agorabot.irc

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import java.security.interfaces.ECPrivateKey

sealed class IrcServerAuthentication {
    data class EcdsaPrivateKey(val key: ECPrivateKey) : IrcServerAuthentication()
}

data class IrcServerConfig(
    val host: String,
    val port: Int,
    val serverIsSecure: Boolean,
    val userNickname: String,
    val authentication: IrcServerAuthentication?,
)

data class IrcServerListConfig(private val serversByName: ImmutableMap<IrcServerName, IrcServerConfig>) {
    constructor(serversByName: Map<IrcServerName, IrcServerConfig>) : this(serversByName.toImmutableMap())

    val names: Set<IrcServerName>
        get() = serversByName.keys

    fun getByName(name: IrcServerName): IrcServerConfig {
        return serversByName.getValue(name)
    }
}

sealed class RelayEndpointConfig {
    data class Discord(val channelId: String) : RelayEndpointConfig()

    data class Irc(
        val serverName: IrcServerName,
        val channelName: String,
        val commandPrefix: String?,
    ) : RelayEndpointConfig()
}

@JvmInline
value class RelayEndpointName(val raw: String)

data class RelayEndpointListConfig(val endpointsByName: ImmutableMap<RelayEndpointName, RelayEndpointConfig>) {
    constructor(endpointsByName: Map<RelayEndpointName, RelayEndpointConfig>) : this(endpointsByName.toImmutableMap())

    val names: Set<RelayEndpointName>
        get() = endpointsByName.keys

    val endpoints: Iterable<RelayEndpointConfig>
        get() = endpointsByName.values

    fun getByName(name: RelayEndpointName): RelayEndpointConfig {
        return endpointsByName.getValue(name)
    }
}

data class IrcRelayEntry(val endpointNames: ImmutableList<RelayEndpointName>) {
    constructor(endpointNames: List<RelayEndpointName>) : this(endpointNames.toImmutableList())
}

data class IrcRelayEntriesConfig(val entries: ImmutableList<IrcRelayEntry>) {
    constructor(entries: List<IrcRelayEntry>) : this(entries.toImmutableList())
}

data class IrcRelayConfig(
    val endpointsConfig: RelayEndpointListConfig,
    val relayEntriesConfig: IrcRelayEntriesConfig,
) {
    init {
        require(endpointsConfig.names.containsAll(relayEntriesConfig.entries.flatMap { it.endpointNames })) {
            "Unknown relay endpoint name requested."
        }
    }
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

        require(
            knownServerNames.containsAll(
                relayConfig
                    .endpointsConfig
                    .endpoints
                    .filterIsInstance<RelayEndpointConfig.Irc>()
                    .map { it.serverName },
            ),
        )
    }
}
