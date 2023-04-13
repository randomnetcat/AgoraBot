package org.randomcat.agorabot.setup

import org.randomcat.agorabot.irc.IrcClientMap
import org.randomcat.agorabot.irc.IrcConfig
import org.randomcat.agorabot.irc.createIrcClients
import java.nio.file.Path

sealed class IrcSetupResult {
    class ErrorWhileConnecting(val error: Exception) : IrcSetupResult()
    class Connected(val clients: IrcClientMap, val config: IrcConfig) : IrcSetupResult()
}

fun connectIrc(
    ircConfig: IrcConfig,
    storageDir: Path,
) = try {
    val clients = createIrcClients(
        ircSetupConfig = ircConfig.setupConfig,
        ircDir = storageDir,
    )

    IrcSetupResult.Connected(clients, ircConfig)
} catch (e: Exception) {
    IrcSetupResult.ErrorWhileConnecting(e)
}
