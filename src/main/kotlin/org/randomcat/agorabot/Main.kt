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
import org.randomcat.agorabot.buttons.*
import org.randomcat.agorabot.commands.base.requirements.haltable.HaltProvider
import org.randomcat.agorabot.commands.base.requirements.haltable.HaltProviderTag
import org.randomcat.agorabot.config.CommandOutputMappingTag
import org.randomcat.agorabot.features.StartupMessageStrategyTag
import org.randomcat.agorabot.irc.*
import org.randomcat.agorabot.listener.*
import org.randomcat.agorabot.setup.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
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

private fun createDirectories(paths: BotDataPaths) {
    paths.configPath.createDirectories()
    paths.storagePath.createDirectories()
    paths.tempPath.createDirectories()
}

object JdaListenerTag : FeatureElementTag<Any>
object BotCommandListTag : FeatureElementTag<Map<String, Command>>
object StartupBlockTag : FeatureElementTag<() -> Unit>

data class BaseCommandDependencyResult(
    val baseTag: Any?,
    val value: Any?,
)

object BaseCommandDependencyTag : FeatureElementTag<BaseCommandDependencyResult>

private fun runBot(config: BotRunConfig) {
    val token = config.token

    createDirectories(config.paths)

    val ircSetupResult = setupIrcClient(config.paths)

    run {
        @Suppress("UNUSED_VARIABLE")
        val ensureExhaustive = when (ircSetupResult) {
            is IrcSetupResult.Connected -> {

            }

            is IrcSetupResult.ConfigUnavailable -> {
                logger.warn("Unable to setup IRC! Check for errors above.")
            }

            is IrcSetupResult.ErrorWhileConnecting -> {
                logger.error("Exception while setting up IRC!", ircSetupResult.error)
            }
        }
    }

    val startupMessageStrategy = setupStartupMessageStrategy(config.paths)

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

        val foundFeatureSources = ClassGraph().enableAllInfo().scan().use { scanResult ->
            scanResult.getClassesWithMethodAnnotation(FeatureSourceFactory::class.java).flatMap { classInfo ->
                classInfo
                    .methodInfo
                    .asSequence()
                    .filter { it.hasAnnotation(FeatureSourceFactory::class.java) }
                    .mapNotNull { it.loadClassAndGetMethod().kotlinFunction }
                    .onEach { logger.info("Reflectively found feature function: $it") }
                    .map { it.call() as FeatureSource<*> }
            }
        }

        val haltFunctionReference = AtomicReference<() -> Unit>(null)

        val extraFeatureSources = listOf(
            FeatureSource.ofConstant(
                "command_output_mapping_provider",
                CommandOutputMappingTag,
                commandOutputMapping,
            ),
            FeatureSource.ofConstant(
                "startup_message_provider",
                StartupMessageStrategyTag,
                startupMessageStrategy,
            ),
            FeatureSource.ofConstant(
                "halt_provider_provider",
                BaseCommandDependencyTag,
                BaseCommandDependencyResult(
                    baseTag = HaltProviderTag,
                    object : HaltProvider {
                        override fun scheduleHalt() {
                            haltFunctionReference.get()?.invoke()
                            exitProcess(0)
                        }
                    },
                ),
            ),
            FeatureSource.ofConstant(
                "jda_token_provider",
                JdaTokenTag,
                token,
            ),
        )

        val closeHandlerLock = ReentrantLock()
        val closeHandlers = mutableListOf<() -> Unit>()

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

                featureContext.onClose {
                    for (client in clientMap.clients) {
                        logger.info("Shutting down IRC...")
                        client.shutdown("Bot shutdown")
                        logger.info("IRC shutdown.")
                    }
                }

                logger.info("Relay initialized.")
            } catch (e: Exception) {
                for (client in clientMap.clients) {
                    client.shutdown("Exception during connection setup")
                }

                logger.error("Exception during IRC relay setup", e)
            }
        }

        val closeStartedFlag = AtomicBoolean(false)

        haltFunctionReference.set {
            if (closeStartedFlag.getAndSet(true)) return@set

            closeHandlerLock.withLock {
                val finalHandlers = closeHandlers.toList().asReversed()

                for (handler in finalHandlers) {
                    handler()
                }
            }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            (haltFunctionReference.get() ?: error("halt function should have been initialized")).invoke()
        })

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
