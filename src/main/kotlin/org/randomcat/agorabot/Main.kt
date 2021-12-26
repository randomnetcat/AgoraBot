package org.randomcat.agorabot

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.github.classgraph.ClassGraph
import kotlinx.collections.immutable.toPersistentList
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.buttons.*
import org.randomcat.agorabot.commands.HelpCommand
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.config.*
import org.randomcat.agorabot.features.adminCommandsFeature
import org.randomcat.agorabot.features.permissionsCommandsFeature
import org.randomcat.agorabot.irc.*
import org.randomcat.agorabot.listener.*
import org.randomcat.agorabot.permissions.makePermissionsStrategy
import org.randomcat.agorabot.setup.*
import org.randomcat.agorabot.util.AtomicLoadOnceMap
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.reflect.jvm.kotlinFunction
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

private fun makeGuildStateStrategy(guildStateMap: GuildStateMap): GuildStateStrategy {
    return object : GuildStateStrategy {
        override fun guildStateFor(guildId: String): GuildState {
            return guildStateMap.stateForGuild(guildId)
        }
    }
}

private fun makeButtonStrategy(buttonMap: ButtonRequestDataMap): ButtonsStrategy {
    return object : ButtonsStrategy {
        override fun storeButtonRequestAndGetId(
            descriptor: ButtonRequestDescriptor,
            expiry: Instant,
        ): ButtonRequestId {
            return buttonMap.putRequest(
                ButtonRequestData(
                    descriptor = descriptor,
                    expiry = expiry,
                ),
            )
        }
    }
}

private fun makeBaseCommandStrategy(
    outputStrategy: BaseCommandOutputStrategy,
    dependencyStrategy: BaseCommandDependencyStrategy,
): BaseCommandStrategy {
    return object :
        BaseCommandStrategy,
        BaseCommandArgumentStrategy by BaseCommandDefaultArgumentStrategy,
        BaseCommandOutputStrategy by outputStrategy,
        BaseCommandDependencyStrategy by dependencyStrategy {}
}

private fun createDirectories(paths: BotDataPaths) {
    paths.configPath.createDirectories()
    paths.storagePath.createDirectories()
    paths.tempPath.createDirectories()
}

private fun buildFeaturesMap(
    featureSources: Iterable<FeatureSource>,
    featureSetupContext: FeatureSetupContext,
): Map<String, Feature> {
    return buildMap {
        for (source in featureSources) {
            val name = source.featureName

            if (containsKey(name)) {
                logger.warn("Found multiple features with name {}. Skipping subsequent feature...", name)
                continue
            }

            logger.info("Configuring feature {}...", name)

            val featureConfig = try {
                source.readConfig(featureSetupContext)
            } catch (e: Exception) {
                logger.error("Error while configuring feature $name", e)
                continue
            }

            logger.info("Configuration for feature {}: {}", name, featureConfig)
            logger.info("Creating feature {}...", name)

            val feature = try {
                source.createFeature(featureConfig)
            } catch (e: Exception) {
                logger.error("Error while setting up feature $name", e)
                continue
            }

            put(name, feature)
        }
    }
}

object JdaListenerTag : FeatureElementTag<List<Any>>
object ButtonDataTag : FeatureElementTag<FeatureButtonData>
object BotCommandListTag : FeatureElementTag<Map<String, Command>>

private fun runBot(config: BotRunConfig) {
    val token = config.token
    val persistService: ConfigPersistService = DefaultConfigPersistService

    createDirectories(config.paths)

    val permissionsSetupResult = setupPermissions(paths = config.paths, persistService = persistService)

    val permissionsConfig = permissionsSetupResult.config
    val botPermissionMap = permissionsSetupResult.botMap
    val guildPermissionMap = permissionsSetupResult.guildMap

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

    val startupMessageStrategy = setupStartupMessageStrategy(config.paths)

    logger.info("Setting up JDA")

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

    logger.info("Waiting for JDA to be ready...")

    jda.awaitReady()

    logger.info("JDA ready")

    try {
        val relayConnectedEndpointMap: RelayConnectedEndpointMap?
        val commandOutputMapping: CommandOutputMapping

        when (ircSetupResult) {
            is IrcSetupResult.Connected -> {
                logger.info("Connecting to relay endpoints...")

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

                logger.info("Relay endpoints initialized")
            }

            else -> {
                logger.info("IRC endpoints not available, not connecting")

                relayConnectedEndpointMap = null
                commandOutputMapping = CommandOutputMapping.empty()
            }
        }

        val delayedRegistryReference = AtomicReference<QueryableCommandRegistry>(null)

        val commandRegistry = MutableMapCommandRegistry(emptyMap())

        val foundFeatureSources = ClassGraph().enableAllInfo().scan().use { scanResult ->
            scanResult.getClassesWithMethodAnnotation(FeatureSourceFactory::class.java).flatMap { classInfo ->
                classInfo
                    .methodInfo
                    .asSequence()
                    .filter { it.hasAnnotation(FeatureSourceFactory::class.java) }
                    .mapNotNull { it.loadClassAndGetMethod().kotlinFunction }
                    .onEach { logger.info("Reflectively found feature function: $it") }
                    .map { it.call() as FeatureSource }
            }
        }

        lateinit var commandStrategy: BaseCommandStrategy

        val extraFeatureSources = listOf(
            "bot_admin_commands" to adminCommandsFeature(
                writeHammertimeChannelFun = { startupMessageStrategy.writeChannel(channelId = it) },
            ),
            "permissions_commands" to permissionsCommandsFeature(
                botPermissionMap = botPermissionMap,
                guildPermissionMap = guildPermissionMap,
            ),
            "command_strategy_provider" to object : Feature {
                override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
                    if (tag is BaseCommandStrategyTag) return tag.result(commandStrategy)
                    return FeatureQueryResult.NotFound
                }
            },
            "config_persist_provider" to object : Feature {
                override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
                    if (tag is ConfigPersistServiceTag) return tag.result(persistService)
                    return FeatureQueryResult.NotFound
                }
            },
            "guild_state_storage_provider" to object : Feature {
                override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
                    if (tag is GuildStateStorageTag) return tag.result(guildStateMap)
                    return FeatureQueryResult.NotFound
                }
            },
        ).map { FeatureSource.ofConstant(it.first, it.second) }

        val featureMap = buildFeaturesMap(
            featureSources = foundFeatureSources + extraFeatureSources,
            featureSetupContext = FeatureSetupContext(paths = config.paths),
        )

        val featureContext = object : FeatureContext {
            private val cacheMap = AtomicLoadOnceMap<Any, Any?>()

            override fun <T> cache(cacheKey: Any, producer: () -> T): T {
                // Caller guarantees type safety.
                @Suppress("UNCHECKED_CAST")
                return cacheMap.getOrPut(cacheKey, producer) as T
            }

            override fun <T> queryAll(tag: FeatureElementTag<T>): Map<String, T> {
                return featureMap
                    .mapValues { (_, v) ->
                        v.query(this, tag)
                    }
                    .filterValues { v -> v is FeatureQueryResult.Found }
                    .mapValues { (_, v) -> (v as FeatureQueryResult.Found).value }
            }

            override fun <T> tryQueryAll(tag: FeatureElementTag<T>): Map<String, Result<FeatureQueryResult<T>>> {
                return featureMap.mapValues { (_, v) ->
                    runCatching {
                        v.query(this, tag)
                    }
                }
            }
        }

        val buttonHandlerMap = ButtonHandlerMap.mergeDisjointHandlers(
            featureMap.values
                .map { it.query(featureContext, ButtonDataTag) }
                .filterIsInstance<FeatureQueryResult.Found<FeatureButtonData>>()
                .mapNotNull {
                    when (it.value) {
                        is FeatureButtonData.NoButtons -> null
                        is FeatureButtonData.RegisterHandlers -> it.value.handlerMap
                    }
                },
        )

        val buttonRequestDataMap = setupButtonDataMap(
            paths = config.paths,
            buttonRequestTypes = buttonHandlerMap.handledClasses,
            persistService = persistService,
        )

        val guildStateStrategy = makeGuildStateStrategy(guildStateMap)

        val permissionsStrategy = makePermissionsStrategy(
            permissionsConfig = permissionsConfig,
            botMap = botPermissionMap,
            guildMap = guildPermissionMap
        )

        val buttonsStrategy = makeButtonStrategy(buttonRequestDataMap)

        commandStrategy = makeBaseCommandStrategy(
            BaseCommandOutputStrategyByOutputMapping(commandOutputMapping),
            object : BaseCommandDependencyStrategy {
                override fun tryFindDependency(tag: Any): Any? {
                    return when (tag) {
                        is PermissionsStrategyTag -> permissionsStrategy
                        is ButtonsStrategyTag -> buttonsStrategy
                        is GuildStateStrategyTag -> guildStateStrategy

                        else -> null
                    }
                }
            },
        )

        for ((name, feature) in featureMap) {
            logger.info("Registering feature $name")

            @Suppress("UNUSED_VARIABLE")
            val ensureExhaustive = when (val commandQueryResult = feature.query(featureContext, BotCommandListTag)) {
                is FeatureQueryResult.Found -> {
                    commandRegistry.addCommands(commandQueryResult.value)
                }

                FeatureQueryResult.NotFound -> {}
            }

            val requestedListeners = feature.query(featureContext, JdaListenerTag)
            if (requestedListeners is FeatureQueryResult.Found) {
                jda.addEventListener(*requestedListeners.value.toTypedArray())
            }
        }

        logger.info("Registering help command...")

        commandRegistry.addCommand("help", HelpCommand(
            strategy = featureContext.defaultCommandStrategy,
            registryFun = {
                checkNotNull(delayedRegistryReference.get()) {
                    "Attempt to access command registry that is not yet ready"
                }
            },
            suppressedCommands = listOf("permissions"),
        ))

        delayedRegistryReference.set(commandRegistry)

        logger.info("Adding JDA event listeners")

        logger.info("Adding command listener...")

        jda.addEventListener(
            BotListener(
                MentionPrefixCommandParser(GuildPrefixCommandParser(featureContext.prefixMap)),
                commandRegistry,
            ),
        )

        logger.info("Adding button listener..")

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
                    // Unambiguous names
                    val theEvent = event
                    val theDataMap = buttonRequestDataMap

                    handler(
                        object : ButtonHandlerContext {
                            override val event: ButtonClickEvent
                                get() = theEvent

                            override val buttonRequestDataMap: ButtonRequestDataMap
                                get() = theDataMap
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

        logger.info("Added JDA event listeners")

        if (ircSetupResult is IrcSetupResult.Connected) {
            logger.info("Initializing relay between relay endpoints...")

            val clientMap = ircSetupResult.clients

            try {
                if (relayConnectedEndpointMap != null) {
                    initializeIrcRelay(
                        config = ircSetupResult.config.relayConfig.relayEntriesConfig,
                        connectedEndpointMap = relayConnectedEndpointMap,
                        commandRegistry = commandRegistry,
                    )
                }

                logger.info("Relay initialized.")
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

private const val MIN_DATA_VERSION = 1

private class AgoraBotCommand : CliktCommand() {
    private val token by option("--token").required()

    private val dataVersion by option("--data-version").int().required()
    private val configPath by option("--config-path").path().required()
    private val storagePath by option("--storage-path").path().required()
    private val tempPath by option("--temp-path").path().required()

    override fun run() {
        if (dataVersion < MIN_DATA_VERSION) {
            throw UsageError("Invalid data version $dataVersion. The minimum data version is $MIN_DATA_VERSION.")
        }

        val config = BotRunConfig(
            paths = readBotDataPaths(),
            token = token,
        )

        runBot(config)
    }

    private fun readBotDataStandardPaths(): BotDataStandardPaths {
        return BotDataStandardPaths(
            configPath = configPath.toAbsolutePath(),
            storagePath = storagePath.toAbsolutePath(),
            tempPath = tempPath.toAbsolutePath(),
        )
    }

    private fun readBotDataPaths(): BotDataPaths {
        return when (dataVersion) {
            1 -> BotDataPaths.Version1(readBotDataStandardPaths())
            else -> throw PrintMessage("Invalid data version $dataVersion", error = true)
        }
    }
}

private fun javaWorkarounds() {
    // Workaround for JDK-8274349
    // https://bugs.openjdk.java.net/browse/JDK-8274349

    // Workaround code from https://github.com/DV8FromTheWorld/JDA/issues/1858#issuecomment-942066283

    val cores = Runtime.getRuntime().availableProcessors()

    if (cores <= 1) {
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "1")
    }
}

fun main(args: Array<String>) {
    javaWorkarounds()

    return AgoraBotCommand().main(args)
}
