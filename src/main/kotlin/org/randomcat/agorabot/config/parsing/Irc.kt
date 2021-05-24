package org.randomcat.agorabot.config.parsing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.irc.*
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

private val logger = LoggerFactory.getLogger("AgoraBotIrcConfigParser")

@Serializable
private data class IrcConnectionConfigDto(
    @SerialName("irc_channel") val ircChannelName: String,
    @SerialName("discord_channel_id") val discordChannelId: String,
    @SerialName("irc_command_prefix") val ircCommandPrefix: String? = null,
    @SerialName("relay_join_leave") val relayJoinLeave: Boolean = false,
)

@Serializable
private data class IrcConfigDto(
    @SerialName("nickname") val nickname: String,
    @SerialName("server") val server: String,
    @SerialName("port") val port: UInt,
    @SerialName("server_is_secure") val serverIsSecure: Boolean,
    @SerialName("connections") val connections: List<IrcConnectionConfigDto>,
)

private val DEFAULT_IRC_SERVER_NAME = IrcServerName("lone-server")

private fun IrcConnectionConfigDto.toRelayEntry(): IrcRelayEntry {
    return IrcRelayEntry(
        ircServerName = DEFAULT_IRC_SERVER_NAME,
        ircChannelName = ircChannelName,
        discordChannelId = discordChannelId,
        ircCommandPrefix = ircCommandPrefix,
    )
}

fun decodeIrcConfig(configText: String): IrcConfig? {
    return try {
        val dto = Json.decodeFromString<IrcConfigDto>(configText)

        for (connection in dto.connections) {
            if (connection.relayJoinLeave) {
                logger.warn("A relay configuration requested relaying of join/leave messages, but that is no longer supported.")
            }
        }

        IrcConfig(
            setupConfig = IrcSetupConfig(
                serverListConfig = IrcServerListConfig(
                    mapOf(
                        DEFAULT_IRC_SERVER_NAME to IrcServerConfig(
                            host = dto.server,
                            port = dto.port.toInt(),
                            serverIsSecure = dto.serverIsSecure,
                            userNickname = dto.nickname,
                        ),
                    ),
                ),
            ),
            relayConfig = IrcRelayConfig(dto.connections.map { it.toRelayEntry() }),
        )
    } catch (e: SerializationException) {
        logger.error("Error while decoding IRC config", e)
        null
    }
}

fun readIrcConfig(configPath: Path): IrcConfig? {
    if (Files.notExists(configPath)) {
        logger.warn("IRC config path $configPath does not exist!")
        return null
    }

    return decodeIrcConfig(configPath.readText())
}
