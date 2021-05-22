package org.randomcat.agorabot.setup

import org.randomcat.agorabot.config.parsing.readIrcConfig
import org.randomcat.agorabot.irc.IrcClient
import org.randomcat.agorabot.irc.IrcConfig
import org.randomcat.agorabot.irc.createIrcClient
import java.nio.file.Path

private fun BotDataPaths.ircConfigPath(): Path {
    return when (this) {
        is BotDataPaths.Version0 -> basePath.resolve("irc").resolve("config.json")
        is BotDataPaths.WithStandardPaths -> configPath.resolve("irc.json")
    }
}

private fun BotDataPaths.ircStorageDir(): Path {
    return storageDir().resolve("irc")
}

sealed class IrcSetupResult {
    object ConfigUnavailable : IrcSetupResult()
    object NoRelayRequested : IrcSetupResult()
    class ErrorWhileConnecting(val error: Exception) : IrcSetupResult()
    class Connected(val client: IrcClient, val config: IrcConfig) : IrcSetupResult()
}

fun setupIrcClient(paths: BotDataPaths): IrcSetupResult {
    val ircConfig = readIrcConfig(paths.ircConfigPath())

    return when {
        ircConfig == null -> {
            IrcSetupResult.ConfigUnavailable
        }

        ircConfig.relayConfig.entries.isEmpty() -> {
            IrcSetupResult.NoRelayRequested
        }

        else -> {
            try {
                val client = createIrcClient(
                    ircSetupConfig = ircConfig.setupConfig,
                    ircDir = paths.ircStorageDir(),
                )


                IrcSetupResult.Connected(client, ircConfig)
            } catch (e: Exception) {
                IrcSetupResult.ErrorWhileConnecting(e)
            }
        }
    }
}
