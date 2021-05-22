package org.randomcat.agorabot.irc

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

private fun doCreateIrcClient(ircSetupConfig: IrcSetupConfig, stsStorageManager: StsStorageManager): IrcClient {
    return Client
        .builder()
        .server()
        .host(ircSetupConfig.serverConfig.host)
        .port(
            ircSetupConfig.serverConfig.port,
            if (ircSetupConfig.serverConfig.serverIsSecure) Client.Builder.Server.SecurityType.SECURE else Client.Builder.Server.SecurityType.INSECURE
        )
        .then()
        .nick(ircSetupConfig.serverConfig.userNickname)
        .management()
        .stsStorageManager(stsStorageManager)
        .then()
        .buildAndConnect()
}

fun createIrcClient(ircSetupConfig: IrcSetupConfig, ircDir: Path): IrcClient {
    return doCreateIrcClient(ircSetupConfig, StsPropertiesStorageManager(ircDir.resolve("kicl_sts_storage")))
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
