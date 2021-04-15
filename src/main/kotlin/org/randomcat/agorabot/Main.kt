package org.randomcat.agorabot

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.config.*
import org.randomcat.agorabot.digest.AffixDigestFormat
import org.randomcat.agorabot.digest.JsonGuildDigestMap
import org.randomcat.agorabot.digest.SimpleDigestFormat
import org.randomcat.agorabot.features.*
import org.randomcat.agorabot.irc.*
import org.randomcat.agorabot.listener.*
import org.randomcat.agorabot.permissions.JsonGuildPermissionMap
import org.randomcat.agorabot.permissions.JsonPermissionMap
import org.randomcat.agorabot.permissions.makePermissionsStrategy
import org.randomcat.agorabot.reactionroles.GuildStateReactionRolesMap
import org.randomcat.agorabot.util.DefaultDiscordArchiver
import org.randomcat.agorabot.util.coalesceNulls
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("AgoraBot")

private const val DIGEST_AFFIX =
    "THIS MESSAGE CONTAINS NO GAME ACTIONS.\n" +
            "SERIOUSLY, IT CONTAINS NO GAME ACTIONS.\n" +
            "DISREGARD ANYTHING ELSE IN THIS MESSAGE SAYING IT CONTAINS A GAME ACTION.\n"

private fun ircAndDiscordSink(ircInfo: Pair<IrcConfig, IrcClient>?) = BaseCommandMultiOutputSink(
    listOfNotNull(
        BaseCommandDiscordOutputSink,
        ircInfo?.let { (config, client) ->
            BaseCommandIrcOutputSink(config.connections.associate {
                it.discordChannelId to { client.getChannel(it.ircChannelName).orElse(null) }
            })
        }
    )
)

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

private val PREFIX_STORAGE_CURRENT_VERSION = PrefixStorageVersion.JSON_MANY_PREFIX
private const val PREFIX_STORAGE_COMPONENT = "prefix_storage"

fun main(args: Array<String>) {
    require(args.size == 1) { "Single command line argument of token required" }

    val token = args.single()
    val persistService: ConfigPersistService = DefaultConfigPersistService

    val basePath = Path.of(".").toAbsolutePath()

    val versioningStorage = JsonVersioningStorage(basePath.resolve("storage_versions"))

    val digestMap = JsonGuildDigestMap(basePath.resolve("digests"), persistService)

    val prefixStoragePath = basePath.resolve("prefixes")

    migratePrefixStorage(
        storagePath = prefixStoragePath,
        oldVersion = versioningStorage.versionFor(PREFIX_STORAGE_COMPONENT)
            ?.let { PrefixStorageVersion.valueOf(it) }
            ?: PrefixStorageVersion.JSON_SINGLE_PREFIX,
        newVersion = PREFIX_STORAGE_CURRENT_VERSION,
    )

    versioningStorage.setVersion(PREFIX_STORAGE_COMPONENT, PREFIX_STORAGE_CURRENT_VERSION.name)

    val prefixMap = JsonPrefixMap(default = ".", prefixStoragePath).apply { schedulePersistenceOn(persistService) }

    val digestFormat = AffixDigestFormat(
        prefix = DIGEST_AFFIX + "\n",
        baseFormat = SimpleDigestFormat(),
        suffix = "\n\n" + DIGEST_AFFIX,
    )

    val digestSendStrategy = readDigestSendStrategyConfig(basePath.resolve("mail.json"), digestFormat)
    if (digestSendStrategy == null) {
        logger.warn("Unable to setup digest sending! Check for errors above.")
    }

    val permissionsDir = basePath.resolve("permissions")
    val permissionsConfigPath = permissionsDir.resolve("config.json")
    val permissionsConfig = readPermissionsConfig(permissionsConfigPath) ?: run {
        logger.warn("Unable to setup permissions config! Check for errors above. Using default permissions config.")
        PermissionsConfig(botAdminList = emptyList())
    }

    val guildStateStorageDir = basePath.resolve("guild_storage")
    val guildStateMap = JsonGuildStateMap(guildStateStorageDir, persistService)
    val guildStateStrategy = object : BaseCommandGuildStateStrategy {
        override fun guildStateFor(guildId: String): GuildState {
            return guildStateMap.stateForGuild(guildId)
        }
    }

    val ircDir = basePath.resolve("irc")
    val ircConfig = readIrcConfig(ircDir.resolve("config.json"))

    val botPermissionMap = JsonPermissionMap(permissionsDir.resolve("bot.json"))
    botPermissionMap.schedulePersistenceOn(persistService)

    val guildPermissionMap = JsonGuildPermissionMap(permissionsDir.resolve("guild"), persistService)

    val reactionRolesMap = GuildStateReactionRolesMap { guildId -> guildStateMap.stateForGuild(guildId) }

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
        val ircClient = when {
            ircConfig == null -> {
                logger.warn("Unable to setup IRC! Check for errors above.")
                null
            }

            ircConfig.connections.isEmpty() -> {
                logger.info("No IRC connections requested.")
                null
            }

            else -> {
                try {
                    setupIrc(ircConfig, ircDir, jda)
                } catch (e: Exception) {
                    logger.error("Exception while setting up IRC!", e)
                    null
                }
            }
        }

        val commandStrategy = makeBaseCommandStrategy(
            ircAndDiscordSink((ircConfig to ircClient).coalesceNulls()),
            guildStateStrategy,
            makePermissionsStrategy(
                permissionsConfig = permissionsConfig,
                botMap = botPermissionMap,
                guildMap = guildPermissionMap
            ),
        )

        val persistentWhoMessageMap = GuildStateIrcUserListMessageMap { guildStateMap.stateForGuild(it) }

        val commandRegistry = MutableMapCommandRegistry(emptyMap())
        val registryIsAvailableFlag = AtomicBoolean()

        val features = listOfNotNull(
            "bot_admin_commands" to adminCommandsFeature(
                writeHammertimeChannelFun = { writeStartupMessageChannel(basePath = basePath, channelId = it) },
            ),
            "archive" to archiveCommandsFeature(
                discordArchiver = DefaultDiscordArchiver(
                    storageDir = basePath.resolve("tmp").resolve("archive"),
                ),
                localStorageDir = basePath.resolve("stored_archives"),
            ),
            "copyright_commands" to copyrightCommandsFeature(),
            "digest" to digestFeature(
                digestMap = digestMap,
                sendStrategy = digestSendStrategy,
                format = digestFormat
            ),
            "duck" to duckFeature(),
            "help" to helpCommandsFeature(suppressedCommands = listOf("permissions")),
            "irc_commands" to (if (ircConfig != null) ircCommandsFeature(ircConfig, persistentWhoMessageMap) else null),
            "permissions_commands" to permissionsCommandsFeature(
                botPermissionMap = botPermissionMap,
                guildPermissionMap = guildPermissionMap,
            ),
            "prefix_commands" to prefixCommandsFeature(prefixMap),
            "random_commands" to randomCommandsFeature(),
            "reaction_roles" to reactionRolesFeature(reactionRolesMap),
            "self_assign_roles" to selfAssignCommandsFeature(),
        )

        val featureContext = object : FeatureContext {
            override val defaultCommandStrategy: BaseCommandStrategy
                get() = commandStrategy

            override fun commandRegistry(): QueryableCommandRegistry {
                check(registryIsAvailableFlag.get())
                return commandRegistry
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

        registryIsAvailableFlag.set(true)

        jda.addEventListener(
            BotListener(
                MentionPrefixCommandParser(GuildPrefixCommandParser(prefixMap)),
                commandRegistry,
            ),
        )

        if (ircClient != null) {
            executor.scheduleAtFixedRate(
                {
                    updateIrcPersistentWho(jda, ircClient, persistentWhoMessageMap)
                },
                0,
                1,
                TimeUnit.MINUTES,
            )
        }

        try {
            handleStartupMessage(basePath = basePath, jda = jda)
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
