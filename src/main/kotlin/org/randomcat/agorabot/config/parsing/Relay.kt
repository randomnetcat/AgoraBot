package org.randomcat.agorabot.config.parsing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.readConfigFromFile
import org.randomcat.agorabot.irc.*
import org.randomcat.util.isDistinct
import java.nio.file.Path

@Serializable
private data class RelayIrcServerConfigDto(
    @SerialName("host") val host: String,
    @SerialName("port") val port: UInt,
    @SerialName("secure") val isSecure: Boolean,
    @SerialName("user_nickname") val userNickname: String,
)

private fun RelayIrcServerConfigDto.toIrcServerConfig(): IrcServerConfig {
    return IrcServerConfig(
        host = host,
        port = port.toInt(),
        serverIsSecure = isSecure,
        userNickname = userNickname,
    )
}

@Serializable
private data class RelaySetupConfigDto(
    @SerialName("irc_servers") val servers: Map<String, RelayIrcServerConfigDto> = emptyMap(),
)

@Serializable
private data class RelayIrcEndpointConfigDto(
    @SerialName("server_name") val serverName: String,
    @SerialName("channel_name") val channelName: String,
    @SerialName("command_prefix") val commandPrefix: String? = null,
)

@Serializable
private data class RelayDiscordEndpointConfigDto(
    @SerialName("channel_id") val channelId: String,
)

@Serializable
private data class RelayEndpointsConfigDto(
    @SerialName("irc") val ircEndpoints: Map<String, RelayIrcEndpointConfigDto> = emptyMap(),
    @SerialName("discord") val discordEndpoints: Map<String, RelayDiscordEndpointConfigDto> = emptyMap(),
) {
    init {
        require((ircEndpoints.keys + discordEndpoints.keys).isDistinct())
    }
}

private fun RelayEndpointsConfigDto.toEndpointListConfig(): RelayEndpointListConfig {
    val ircMap = ircEndpoints.mapKeys { (k, _) -> RelayEndpointName(k) }.mapValues { (_, v) ->
        RelayEndpointConfig.Irc(
            serverName = IrcServerName(v.serverName),
            channelName = v.channelName,
            commandPrefix = v.commandPrefix,
        )
    }

    val discordMap = discordEndpoints.mapKeys { (k, _) -> RelayEndpointName(k) }.mapValues { (_, v) ->
        RelayEndpointConfig.Discord(
            channelId = v.channelId,
        )
    }

    return RelayEndpointListConfig(ircMap + discordMap)
}

@Serializable
private data class RelayBridgeConfigDto(
    @SerialName("endpoints") val endpointNames: List<String>,
)

@Serializable
private data class RelayConfigDto(
    @SerialName("setup") val setupConfig: RelaySetupConfigDto = RelaySetupConfigDto(),
    @SerialName("endpoints") val endpointsConfig: RelayEndpointsConfigDto,
    @SerialName("bridges") val bridges: List<RelayBridgeConfigDto>,
)

private fun RelayConfigDto.toIrcConfig(): IrcConfig {
    return IrcConfig(
        setupConfig = IrcSetupConfig(
            serverListConfig = IrcServerListConfig(
                setupConfig
                    .servers
                    .mapKeys { (k, _) -> IrcServerName(k) }
                    .mapValues { (_, v) -> v.toIrcServerConfig() },
            ),
        ),
        relayConfig = IrcRelayConfig(
            endpointsConfig = endpointsConfig.toEndpointListConfig(),
            relayEntriesConfig = IrcRelayEntriesConfig(
                bridges.map { bridgeConfig ->
                    IrcRelayEntry(
                        endpointNames = bridgeConfig.endpointNames.map { name -> RelayEndpointName(name) },
                    )
                },
            ),
        ),
    )
}

fun readRelayConfig(path: Path) = readConfigFromFile(path, null) { text ->
    Json.decodeFromString<RelayConfigDto>(text).toIrcConfig()
}
