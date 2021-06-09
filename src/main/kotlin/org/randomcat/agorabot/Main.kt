package org.randomcat.agorabot

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.collections.immutable.toPersistentList
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.buttons.*
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.config.DefaultConfigPersistService
import org.randomcat.agorabot.features.*
import org.randomcat.agorabot.irc.*
import org.randomcat.agorabot.listener.*
import org.randomcat.agorabot.permissions.makePermissionsStrategy
import org.randomcat.agorabot.reactionroles.GuildStateReactionRolesMap
import org.randomcat.agorabot.setup.*
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("AgoraBot")

private fun ircAndDiscordMapping(
    jda: JDA,
    relayConnectedEndpointMap: RelayConnectedEndpointMap,
    relayEntriesConfig: IrcRelayEntriesConfig,
): CommandOutputMapping {
    data class IrcChannelLookupKey(val client: IrcClient, val channelName: String)

    val discordToIrcMap: MutableMap<String, MutableList<() -> List<CommandOutputSink>>> = mutableMapOf()
    val ircToDiscordMap: MutableMap<IrcChannelLookupKey, MutableList<() -> List<CommandOutputSink>>> = mutableMapOf()

    for (entry in relayEntriesConfig.entries) {
        val endpoints = entry.endpointNames.map { relayConnectedEndpointMap.getByName(it) }.toPersistentList()

        endpoints.mapIndexed { index, relayConnectedEndpoint ->
            val otherEndpoints = endpoints.removeAt(index)

            @Suppress(
                "UNUSED_VARIABLE",
                "MoveLambdaOutsideParentheses", // Lambda is the value, so it should be in parentheses
            )
            val ensureExhaustive = when (relayConnectedEndpoint) {
                is RelayConnectedDiscordEndpoint -> {
                    require(jda == relayConnectedEndpoint.jda) {
                        "Multiple JDAs are not supported here"
                    }

                    val list = discordToIrcMap.getOrPut(relayConnectedEndpoint.channelId) { mutableListOf() }

                    list.add({
                        otherEndpoints.mapNotNull { it.commandOutputSink() }
                    })
                }

                is RelayConnectedIrcEndpoint -> {
                    val list = ircToDiscordMap.getOrPut(
                        IrcChannelLookupKey(
                            client = relayConnectedEndpoint.client,
                            channelName = relayConnectedEndpoint.channelName,
                        ),
                    ) { mutableListOf() }

                    list.add({
                        otherEndpoints.mapNotNull { it.commandOutputSink() }
                    })
                }
            }
        }
    }

    return CommandOutputMapping(
        sinksForDiscordFun = { source ->
            discordToIrcMap[source.event.channel.id]?.flatMap { it() } ?: emptyList()
        },
        sinksForIrcFun = { source ->
            val key = IrcChannelLookupKey(client = source.event.client, channelName = source.event.channel.name)
            ircToDiscordMap[key]?.flatMap { it() } ?: emptyList()
        },
    )
}

private fun makeBaseCommandStrategy(
    outputStrategy: BaseCommandOutputStrategy,
    guildStateStrategy: BaseCommandGuildStateStrategy,
    permissionsStrategy: BaseCommandPermissionsStrategy,
    buttonStrategy: BaseCommandButtonStrategy,
): BaseCommandStrategy {
    return object :
        BaseCommandStrategy,
        BaseCommandArgumentStrategy by BaseCommandDefaultArgumentStrategy,
        BaseCommandOutputStrategy by outputStrategy,
        BaseCommandPermissionsStrategy by permissionsStrategy,
        BaseCommandGuildStateStrategy by guildStateStrategy,
        BaseCommandButtonStrategy by buttonStrategy {}
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

    try {
        val relayConnectedEndpointMap: RelayConnectedEndpointMap?
        val commandOutputMapping: CommandOutputMapping

        when (ircSetupResult) {
            is IrcSetupResult.Connected -> {
                relayConnectedEndpointMap = connectToRelayEndpoints(
                    endpointsConfig = ircSetupResult.config.relayConfig.endpointsConfig,
                    context = RelayConnectionContext(
                        ircClientMap = ircSetupResult.clients,
                        jda = jda,
                    ),
                )

                commandOutputMapping = ircAndDiscordMapping(
                    jda = jda,
                    relayConnectedEndpointMap = relayConnectedEndpointMap,
                    relayEntriesConfig = ircSetupResult.config.relayConfig.relayEntriesConfig,
                )
            }

            else -> {
                relayConnectedEndpointMap = null
                commandOutputMapping = CommandOutputMapping.empty()
            }
        }

        val delayedRegistryReference = AtomicReference<QueryableCommandRegistry>(null)

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
            "permissions_commands" to permissionsCommandsFeature(
                botPermissionMap = botPermissionMap,
                guildPermissionMap = guildPermissionMap,
            ),
            "prefix_commands" to prefixCommandsFeature(prefixMap),
            "random_commands" to randomCommandsFeature(),
            "judge_list_commands" to judgeListCommandsFeature(),
            "reaction_roles" to reactionRolesFeature(reactionRolesMap),
            "self_assign_roles" to selfAssignCommandsFeature(),
            "citations" to if (citationsConfig != null) citationsFeature(citationsConfig) else null,
            "button_test" to buttonTestFeature(),
        )

        val buttonHandlerMap = ButtonHandlerMap.mergeDisjointHandlers(
            features.mapNotNull { it.second?.buttonData() }.mapNotNull {
                when (it) {
                    is FeatureButtonData.NoButtons -> null
                    is FeatureButtonData.RegisterHandlers -> it.handlerMap
                }
            },
        )

        val buttonRequestDataMap = setupButtonDataMap(
            paths = config.paths,
            buttonRequestTypes = buttonHandlerMap.handledClasses,
            persistService = persistService,
        )

        val commandStrategy = makeBaseCommandStrategy(
            BaseCommandOutputStrategyByOutputMapping(commandOutputMapping),
            BaseCommandGuildStateStrategy.fromMap(guildStateMap),
            makePermissionsStrategy(
                permissionsConfig = permissionsConfig,
                botMap = botPermissionMap,
                guildMap = guildPermissionMap
            ),
            BaseCommandButtonStrategy.fromMap(buttonRequestDataMap = buttonRequestDataMap),
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

        jda.addEventListener(BotButtonListener { event ->
            val id = ButtonRequestId(event.componentId)

            val requestDescriptor = buttonRequestDataMap.tryGetRequestById(
                id = id,
                timeForExpirationCheck = Instant.now(),
            )

            if (requestDescriptor != null) {
                @Suppress("UNCHECKED_CAST")
                val handler =
                    buttonHandlerMap.tryGetHandler(requestDescriptor::class) as ButtonHandler<ButtonRequestDescriptor>?

                if (handler != null) {
                    // Unambiguous name for context
                    val theEvent = event

                    handler(
                        object : ButtonHandlerContext {
                            override val event: ButtonClickEvent
                                get() = theEvent
                        },
                        requestDescriptor,
                    )
                } else {
                    event.reply("Unknown button type. That feature may be disabled.").setEphemeral(true).queue()
                }
            } else {
                event.reply("Unknown button request. That button may have expired.").setEphemeral(true).queue()
            }
        })


        if (ircSetupResult is IrcSetupResult.Connected) {
            val clientMap = ircSetupResult.clients

            try {
                if (relayConnectedEndpointMap != null) {
                    initializeIrcRelay(
                        config = ircSetupResult.config.relayConfig.relayEntriesConfig,
                        connectedEndpointMap = relayConnectedEndpointMap,
                        commandRegistry = commandRegistry,
                    )
                }
            } catch (e: Exception) {
                for (client in clientMap.clients) {
                    client.shutdown("Exception during connection setup")
                }

                logger.error("Exception during IRC relay setup", e)
            }
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

    private val dataVersion by option("--data-version").int().default(0)
    private val configPath by option("--config-path")
    private val storagePath by option("--storage-path")
    private val tempPath by option("--temp-path")

    override fun run() {
        val config = BotRunConfig(
            paths = readBotDataPaths(),
            token = token,
        )

        runBot(config)
    }

    private fun parseRequiredDataPath(name: String, value: String?): Path {
        if (value == null) {
            throw PrintMessage("Data version $dataVersion requires $name path, but none was provided", error = true)
        }

        return Path.of(value).toAbsolutePath()
    }

    private fun readBotDataStandardPaths(): BotDataStandardPaths {
        return BotDataStandardPaths(
            configPath = parseRequiredDataPath(name = "config", value = configPath),
            storagePath = parseRequiredDataPath(name = "storage", value = storagePath),
            tempPath = parseRequiredDataPath(name = "temp", value = tempPath),
        )
    }

    private fun readBotDataPaths(): BotDataPaths {
        return when (dataVersion) {
            0 -> BotDataPaths.Version0(basePath = Path.of(".").toAbsolutePath())
            1 -> BotDataPaths.Version1(readBotDataStandardPaths())
            else -> throw PrintMessage("Invalid data version $dataVersion", error = true)
        }
    }
}

fun main(args: Array<String>) {
    return when (args.size) {
        1 -> AgoraBotCommand().main(listOf("--token", args.single()))
        else -> AgoraBotCommand().main(args)
    }
}
