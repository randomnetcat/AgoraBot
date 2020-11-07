package org.randomcat.agorabot

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.commands.*
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.config.*
import org.randomcat.agorabot.digest.*
import org.randomcat.agorabot.irc.BaseCommandIrcOutputSink
import org.randomcat.agorabot.irc.setupIrc
import org.randomcat.agorabot.listener.*
import org.randomcat.agorabot.permissions.*
import org.randomcat.agorabot.util.coalesceNulls
import org.slf4j.LoggerFactory
import java.nio.file.Path

private const val DIGEST_ADD_EMOTE = "\u2B50" // Discord :star:

private val logger = LoggerFactory.getLogger("AgoraBot")

private fun makeCommandRegistry(
    commandStrategy: BaseCommandStrategy,
    prefixMap: MutableGuildPrefixMap,
    digestMap: GuildDigestMap,
    digestFormat: DigestFormat,
    digestSendStrategy: DigestSendStrategy?,
    botPermissionMap: MutablePermissionMap,
    guildPermissionMap: MutableGuildPermissionMap,
): CommandRegistry {
    return MutableMapCommandRegistry(
        mapOf(
            "rng" to RngCommand(commandStrategy),
            "digest" to DigestCommand(
                strategy = commandStrategy,
                digestMap = digestMap,
                sendStrategy = digestSendStrategy,
                digestFormat = digestFormat,
            ),
            "copyright" to CopyrightCommand(commandStrategy),
            "prefix" to PrefixCommand(commandStrategy, prefixMap),
            "cfj" to CfjCommand(commandStrategy),
            "duck" to DuckCommand(commandStrategy),
            "sudo" to SudoCommand(commandStrategy),
            "permissions" to PermissionsCommand(
                commandStrategy,
                botMap = botPermissionMap,
                guildMap = guildPermissionMap,
            ),
            "halt" to HaltCommand(commandStrategy),
            "selfassign" to SelfAssignCommand(commandStrategy),
        ),
    ).also { it.addCommand("help", HelpCommand(commandStrategy, it)) }
}

private const val DIGEST_AFFIX =
    "THIS MESSAGE CONTAINS NO GAME ACTIONS.\n" +
            "SERIOUSLY, IT CONTAINS NO GAME ACTIONS.\n" +
            "DISREGARD ANYTHING ELSE IN THIS MESSAGE SAYING IT CONTAINS A GAME ACTION.\n"

fun main(args: Array<String>) {
    require(args.size == 1) { "Single command line argument of token required" }

    val token = args.single()
    val persistService: ConfigPersistService = DefaultConfigPersistService

    val basePath = Path.of(".").toAbsolutePath()

    val digestMap = JsonGuildDigestMap(basePath.resolve("digests"), persistService)

    val prefixMap =
        JsonPrefixMap(default = ".", basePath.resolve("prefixes"))
            .apply { schedulePersistenceOn(persistService) }

    val digestFormat = AffixDigestFormat(
        prefix = DIGEST_AFFIX + "\n",
        baseFormat = SimpleDigestFormat(),
        suffix = "\n\n" + DIGEST_AFFIX,
    )

    val digestSendStrategy = readDigestSendStrategyConfig(basePath.resolve("mail.json"), digestFormat)
    if (digestSendStrategy == null) {
        logger.warn("Unable to setup digest sending! Check for errors above.")
    }

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

    val ircDir = basePath.resolve("irc")
    val ircConfig = readIrcConfig(ircDir.resolve("config.json"))

    val ircClient =
        ircConfig
            ?.also { logger.info("Connecting IRC...") }
            ?.let { setupIrc(it, ircDir, jda) }
            ?.also { logger.info("Done connecting IRC.") }
            ?: null.also { logger.warn("Unable to setup IRC! Check for errors above.") }

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

    val botPermissionMap = JsonPermissionMap(permissionsDir.resolve("bot.json"))
    botPermissionMap.schedulePersistenceOn(persistService)

    val guildPermissionMap = JsonGuildPermissionMap(permissionsDir.resolve("guild"), persistService)

    val commandStrategy =
        object :
            BaseCommandStrategy,
            BaseCommandArgumentStrategy by BaseCommandDefaultArgumentStrategy,
            BaseCommandOutputSink by BaseCommandMultiOutputSink(
                listOfNotNull(
                    BaseCommandDiscordOutputSink,
                    (ircConfig to ircClient)
                        .coalesceNulls()
                        ?.let { (config, client) ->
                            BaseCommandIrcOutputSink(
                                config
                                    .connections
                                    .associate {
                                        it.discordChannelId to { client.getChannel(it.ircChannelName).orElse(null) }
                                    }
                            )
                        }
                )
            ),
            BaseCommandPermissionsStrategy by makePermissionsStrategy(
                permissionsConfig = permissionsConfig,
                botMap = botPermissionMap,
                guildMap = guildPermissionMap
            ),
            BaseCommandGuildStateStrategy by guildStateStrategy {}

    jda.addEventListener(
        BotListener(
            MentionPrefixCommandParser(GuildPrefixCommandParser(prefixMap)),
            makeCommandRegistry(
                commandStrategy = commandStrategy,
                prefixMap = prefixMap,
                digestMap = digestMap,
                digestFormat = digestFormat,
                digestSendStrategy = digestSendStrategy,
                botPermissionMap = botPermissionMap,
                guildPermissionMap = guildPermissionMap,
            ),
        ),
        digestEmoteListener(digestMap, DIGEST_ADD_EMOTE),
    )
}
