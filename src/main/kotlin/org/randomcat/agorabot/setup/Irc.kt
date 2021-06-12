package org.randomcat.agorabot.setup

import org.kitteh.irc.client.library.feature.auth.SaslEcdsaNist256PChallenge
import org.randomcat.agorabot.config.parsing.RelayIrcServerAuthenticationDto
import org.randomcat.agorabot.config.parsing.readIrcConfig
import org.randomcat.agorabot.config.parsing.readRelayConfig
import org.randomcat.agorabot.irc.IrcClientMap
import org.randomcat.agorabot.irc.IrcConfig
import org.randomcat.agorabot.irc.IrcServerAuthentication
import org.randomcat.agorabot.irc.createIrcClients
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.readText

private val logger = LoggerFactory.getLogger("AgoraBotIrcSetup")

private fun BotDataPaths.ircConfigPath(): Path {
    return configPath.resolve("irc.json")
}

private fun BotDataPaths.relayConfigPath(): Path? {
    return configPath.resolve("relay.json")
}

private fun BotDataPaths.ircStorageDir(): Path {
    return storagePath.resolve("irc")
}

sealed class IrcSetupResult {
    object ConfigUnavailable : IrcSetupResult()
    object NoRelayRequested : IrcSetupResult()
    class ErrorWhileConnecting(val error: Exception) : IrcSetupResult()
    class Connected(val clients: IrcClientMap, val config: IrcConfig) : IrcSetupResult()
}

private fun connectWithConfig(
    ircConfig: IrcConfig,
    paths: BotDataPaths,
) = try {
    val clients = createIrcClients(
        ircSetupConfig = ircConfig.setupConfig,
        ircDir = paths.ircStorageDir(),
    )

    IrcSetupResult.Connected(clients, ircConfig)
} catch (e: Exception) {
    IrcSetupResult.ErrorWhileConnecting(e)
}

fun setupIrcClient(paths: BotDataPaths): IrcSetupResult {
    run {
        val relayConfigPath = paths.relayConfigPath()

        if (relayConfigPath != null) {
            val relayConfig = readRelayConfig(
                path = relayConfigPath,
            ) { authenticationDto ->
                when (authenticationDto) {
                    is RelayIrcServerAuthenticationDto.EcdsaPrivateKeyPath -> {
                        val unresolvedPath = Path.of(authenticationDto.unresolvedPath)

                        val resolvedPath = if (unresolvedPath.isAbsolute) {
                            unresolvedPath
                        } else {
                            paths.configPath.resolve(unresolvedPath)
                        }

                        val key = SaslEcdsaNist256PChallenge.getPrivateKey(resolvedPath.readText().trim())
                        IrcServerAuthentication.EcdsaPrivateKey(key)
                    }
                }
            }

            if (relayConfig != null) {
                return connectWithConfig(ircConfig = relayConfig, paths = paths)
            } else {
                logger.info("Unable to read new, preferred relay config. Defaulting to old IRC config.")
            }
        }
    }

    val ircConfig = readIrcConfig(paths.ircConfigPath())

    return when {
        ircConfig == null -> {
            IrcSetupResult.ConfigUnavailable
        }

        ircConfig.relayConfig.relayEntriesConfig.entries.isEmpty() -> {
            IrcSetupResult.NoRelayRequested
        }

        else -> {
            connectWithConfig(ircConfig = ircConfig, paths = paths)
        }
    }
}
