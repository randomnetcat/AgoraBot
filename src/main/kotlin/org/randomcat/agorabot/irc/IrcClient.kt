package org.randomcat.agorabot.irc

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.element.Channel
import org.kitteh.irc.client.library.element.User
import org.kitteh.irc.client.library.event.helper.ActorEvent
import org.kitteh.irc.client.library.feature.sts.StsPropertiesStorageManager
import org.kitteh.irc.client.library.feature.sts.StsStorageManager
import java.nio.file.Path


/**
 * In order to provide more information in the name than just "Client".
 */
typealias IrcClient = Client

private fun doCreateIrcClient(serverConfig: IrcServerConfig, stsStorageManager: StsStorageManager): IrcClient {
    return Client
        .builder()
        .server()
        .host(serverConfig.host)
        .port(
            serverConfig.port,
            if (serverConfig.serverIsSecure) Client.Builder.Server.SecurityType.SECURE else Client.Builder.Server.SecurityType.INSECURE
        )
        .then()
        .nick(serverConfig.userNickname)
        .management()
        .stsStorageManager(stsStorageManager)
        .then()
        .buildAndConnect()
}

@JvmInline
value class IrcServerName(val raw: String)

data class IrcClientMap(private val clientsByName: ImmutableMap<IrcServerName, IrcClient>) {
    constructor(clientsByName: Map<IrcServerName, IrcClient>) : this(clientsByName.toImmutableMap())

    val clients: Iterable<IrcClient>
        get() = clientsByName.values

    fun getByName(name: IrcServerName): IrcClient {
        return clientsByName.getValue(name)
    }
}

fun createIrcClients(ircSetupConfig: IrcSetupConfig, ircDir: Path): IrcClientMap {
    val serverListConfig = ircSetupConfig.serverListConfig

    val storageManager = StsPropertiesStorageManager(ircDir.resolve("kicl_sts_storage"))

    return IrcClientMap(
        serverListConfig.names.associateWith { name ->
            doCreateIrcClient(serverListConfig.getByName(name), storageManager)
        },
    )
}

typealias IrcChannel = Channel
typealias IrcUser = User

fun ActorEvent<User>.isSelfEvent() = actor.nick == client.nick

/**
 * Sends [message], which may contain multiple lines, splitting at both the length limit and every line in message.
 *
 * This differs from [org.kitteh.irc.client.library.element.Channel.sendMultiLineMessage] by allowing newlines
 * in the message.
 */
fun IrcChannel.sendSplitMultiLineMessage(message: String) {
    message.lineSequence().forEach { sendMultiLineMessage(it) }
}
