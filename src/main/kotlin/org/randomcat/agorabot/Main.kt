package org.randomcat.agorabot

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.config.DefaultConfigPersistService
import org.randomcat.agorabot.features.*
import org.randomcat.agorabot.irc.BaseCommandIrcOutputSink
import org.randomcat.agorabot.irc.GuildStateIrcUserListMessageMap
import org.randomcat.agorabot.irc.initializeIrcRelay
import org.randomcat.agorabot.irc.updateIrcPersistentWho
import org.randomcat.agorabot.listener.*
import org.randomcat.agorabot.permissions.makePermissionsStrategy
import org.randomcat.agorabot.reactionroles.GuildStateReactionRolesMap
import org.randomcat.agorabot.setup.*
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("AgoraBot")

private fun ircAndDiscordMapping(jda: JDA, ircSetupResult: IrcSetupResult): CommandOutputMapping {
    return when (ircSetupResult) {
        is IrcSetupResult.Connected -> {
            val config = ircSetupResult.config
            val client = ircSetupResult.client

            val relayEntries = config.relayConfig.entries

            val discordToIrcMap = relayEntries.associate {
                it.discordChannelId to { client.getChannel(it.ircChannelName).orElse(null) }
            }

            val ircToDiscordMap = relayEntries.associate {
                it.ircChannelName to { jda.getTextChannelById(it.discordChannelId) }
            }

            CommandOutputMapping(
                discordToIrcMap = discordToIrcMap,
                ircToDiscordMap = ircToDiscordMap,
            )
        }

        else -> CommandOutputMapping.empty()
    }
}

private fun ircAndDiscordSink(mapping: CommandOutputMapping): BaseCommandOutputSink {
    return BaseCommandMultiOutputSink(
        listOf(
            BaseCommandDiscordOutputSink(mapping),
            BaseCommandIrcOutputSink(mapping),
        ),
    )
}

private fun makeBaseCommandStrategy(
    outputSink: BaseCommandOutputSink,
    guildStateStrategy: BaseCommandGuildStateStrategy,
    permissionsStrategy: BaseCommandPermissionsStrategy,
): BaseCommandStrategy {
    return object :
        BaseCommandStrategy,
        BaseCommandArgumentStrategy by BaseCommandDefaultArgumentStrategy,
        BaseCommandOutputSink by outputSink,
        BaseCommandPermissionsStrategy by permissionsStrategy,
        BaseCommandGuildStateStrategy by guildStateStrategy {}
}

private fun runBot(config: BotRunConfig) {
    val token = config.token
    val persistService: ConfigPersistService = DefaultConfigPersistService

    val versioningStorage = setupStorageVersioning(paths = config.paths)

    val prefixMap = setupPrefixStorage(
        paths = config.paths,
        versioningStorage = versioningStorage,
        persistService = persistService,
    )

    val permissionsSetupResult = setupPermissions(paths = config.paths, persistService = persistService)

    val permissionsConfig = permissionsSetupResult.config
    val botPermissionMap = permissionsSetupResult.botMap
    val guildPermissionMap = permissionsSetupResult.guildMap

    val digestSetupResult = setupDigest(
        paths = config.paths,
        persistService = persistService,
    )

    if (digestSetupResult.digestSendStrategy == null) {
        logger.warn("Unable to setup digest sending! Check for errors above.")
    }

    val guildStateMap = setupGuildStateMap(config.paths, persistService)

    val ircSetupResult = setupIrcClient(config.paths)

    run {
        @Suppress("UNUSED_VARIABLE")
        val ensureExhaustive = when (ircSetupResult) {
            is IrcSetupResult.Connected -> {

            }

            is IrcSetupResult.ConfigUnavailable -> {
                logger.warn("Unable to setup IRC! Check for errors above.")
            }

            is IrcSetupResult.NoRelayRequested -> {
                logger.info("No IRC connections requested.")
            }

            is IrcSetupResult.ErrorWhileConnecting -> {
                logger.error("Exception while setting up IRC!", ircSetupResult.error)
            }
        }
    }

    val reactionRolesMap = GuildStateReactionRolesMap { guildId -> guildStateMap.stateForGuild(guildId) }

    val citationsConfig = setupCitationsConfig(config.paths)

    val startupMessageStrategy = setupStartupMessageStrategy(config.paths)

    val jda =
        JDABuilder
            .createDefault(
                token,
                listOf(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.GUILD_MESSAGE_REACTIONS,
                ),
            )
            .setEventManager(AnnotatedEventManager())
            .build()

    jda.awaitReady()

    val executor = Executors.newSingleThreadScheduledExecutor()

    try {
        val delayedRegistryReference = AtomicReference<QueryableCommandRegistry>(null)

        val commandStrategy = makeBaseCommandStrategy(
            ircAndDiscordSink(ircAndDiscordMapping(jda, ircSetupResult)),
            BaseCommandGuildStateStrategy.fromMap(guildStateMap),
            makePermissionsStrategy(
                permissionsConfig = permissionsConfig,
                botMap = botPermissionMap,
                guildMap = guildPermissionMap
            ),
        )

        val persistentWhoMessageMap = GuildStateIrcUserListMessageMap { guildStateMap.stateForGuild(it) }

        val commandRegistry = MutableMapCommandRegistry(emptyMap())

        val features = listOfNotNull(
            "bot_admin_commands" to adminCommandsFeature(
                writeHammertimeChannelFun = { startupMessageStrategy.writeChannel(channelId = it) },
            ),
            "archive" to setupArchiveFeature(config.paths),
            "copyright_commands" to copyrightCommandsFeature(),
            "digest" to digestFeature(
                digestMap = digestSetupResult.digestMap,
                sendStrategy = digestSetupResult.digestSendStrategy,
                format = digestSetupResult.digestFormat,
            ),
            "duck" to duckFeature(),
            "help" to helpCommandsFeature(suppressedCommands = listOf("permissions")),
            "irc_commands" to (ircSetupResult as? IrcSetupResult.Connected)?.let {
                ircCommandsFeature(it.config, persistentWhoMessageMap)
            },
            "permissions_commands" to permissionsCommandsFeature(
                botPermissionMap = botPermissionMap,
                guildPermissionMap = guildPermissionMap,
            ),
            "prefix_commands" to prefixCommandsFeature(prefixMap),
            "random_commands" to randomCommandsFeature(),
            "reaction_roles" to reactionRolesFeature(reactionRolesMap),
            "self_assign_roles" to selfAssignCommandsFeature(),
            "citations" to if (citationsConfig != null) citationsFeature(citationsConfig) else null,
        )

        val featureContext = object : FeatureContext {
            override val defaultCommandStrategy: BaseCommandStrategy
                get() = commandStrategy

            override fun commandRegistry(): QueryableCommandRegistry {
                return checkNotNull(delayedRegistryReference.get()) {
                    "Attempt to access command registry that is not yet ready"
                }
            }
        }

        for ((name, feature) in features) {
            if (feature != null) {
                logger.info("Registering feature $name")

                commandRegistry.addCommands(feature.commandsInContext(featureContext))
                feature.registerListenersTo(jda)
            } else {
                logger.info("Not registering feature $name because it is not available.")
            }
        }

        delayedRegistryReference.set(commandRegistry)

        jda.addEventListener(
            BotListener(
                MentionPrefixCommandParser(GuildPrefixCommandParser(prefixMap)),
                commandRegistry,
            ),
        )

        if (ircSetupResult is IrcSetupResult.Connected) {
            initializeIrcRelay(
                ircClient = ircSetupResult.client,
                ircRelayConfig = ircSetupResult.config.relayConfig,
                jda = jda,
                commandRegistry = commandRegistry,
            )

            executor.scheduleAtFixedRate(
                {
                    updateIrcPersistentWho(jda, ircSetupResult.client, persistentWhoMessageMap)
                },
                0,
                1,
                TimeUnit.MINUTES,
            )
        }

        try {
            startupMessageStrategy.sendMessageAndClearChannel(jda = jda)
        } catch (e: Exception) {
            // Log and ignore. This failing should not bring down the whole bot
            logger.error("Exception while handling startup message.", e)
        }
    } catch (e: Exception) {
        logger.error("Exception while setting up JDA listeners!", e)
        jda.shutdownNow()
        exitProcess(1)
    }
}

private data class BotRunConfig(
    val paths: BotDataPaths,
    val token: String,
)

private class AgoraBotCommand : CliktCommand() {
    private val token by option("--token").required()

    override fun run() {
        val config = BotRunConfig(
            paths = BotDataPaths.Version0(basePath = Path.of(".").toAbsolutePath()),
            token = token,
        )

        runBot(config)
    }
}

fun main(args: Array<String>) {
    return when (args.size) {
        1 -> AgoraBotCommand().main(listOf("--token", args.single()))
        else -> AgoraBotCommand().main(args)
    }
}
