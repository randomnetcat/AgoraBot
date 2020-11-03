package org.randomcat.agorabot

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.commands.*
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.config.*
import org.randomcat.agorabot.digest.*
import org.randomcat.agorabot.irc.IrcChannel
import org.randomcat.agorabot.irc.sendSplitMultiLineMessage
import org.randomcat.agorabot.irc.setupIrc
import org.randomcat.agorabot.listener.*
import org.randomcat.agorabot.permissions.*
import org.randomcat.agorabot.util.coalesceNulls
import org.slf4j.LoggerFactory
import java.nio.file.Path

private const val DIGEST_ADD_EMOTE = "\u2B50" // Discord :star:

private val logger = LoggerFactory.getLogger("AgoraBot")

/**
 * @param channelMap a map of Discord channel ids to irc channels.
 */
private data class BaseCommandIrcOutputSink(
    private val channelMap: ImmutableMap<String, () -> IrcChannel?>,
) : BaseCommandOutputSink {
    constructor(channelMap: Map<String, () -> IrcChannel?>) : this(channelMap.toImmutableMap())

    private fun channelForEvent(event: MessageReceivedEvent): IrcChannel? {
        return channelMap[event.channel.id]?.invoke()
    }

    override fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String) {
        channelForEvent(event)?.run {
            sendSplitMultiLineMessage(message)
        }
    }

    override fun sendResponseMessage(event: MessageReceivedEvent, invocation: CommandInvocation, message: Message) {
        channelForEvent(event)?.run {
            sendSplitMultiLineMessage(message.contentRaw)
        }
    }

    override fun sendResponseAsFile(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    ) {
        channelForEvent(event)?.run {
            val safeFileName = fileName.lineSequence().joinToString("") // Paranoia

            sendMultiLineMessage(
                "Well, I *would* send an attachment, and it *would* have been called \"$safeFileName\", " +
                        "but this is a lame forum that doesn't support attachments, so all you get is this message."
            )
        }
    }
}

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
        ),
    ).also { it.addCommand("help", HelpCommand(commandStrategy, it)) }
}

private fun makePermissionsStrategy(
    permissionsConfig: PermissionsConfig,
    botMap: PermissionMap,
    guildMap: GuildPermissionMap,
): BaseCommandPermissionsStrategy {
    val botPermissionContext = object : BotPermissionContext {
        override fun isBotAdmin(userId: String): Boolean {
            return permissionsConfig.botAdmins.contains(userId)
        }

        override fun checkGlobalPath(userId: String, path: PermissionPath): BotPermissionState {
            return botMap.stateForUser(path = path, userId = userId) ?: BotPermissionState.DEFER
        }

        override fun checkUserGuildPath(guildId: String, userId: String, path: PermissionPath): BotPermissionState {
            return guildMap
                .mapForGuild(guildId = guildId)
                .stateForUser(path = path, userId = userId)
                ?: BotPermissionState.DEFER
        }

        override fun checkRoleGuildPath(guildId: String, roleId: String, path: PermissionPath): BotPermissionState {
            return guildMap
                .mapForGuild(guildId = guildId)
                .stateForRole(path = path, roleId = roleId)
                ?: BotPermissionState.DEFER
        }
    }

    return object : BaseCommandPermissionsStrategy {
        override fun onPermissionsError(
            event: MessageReceivedEvent,
            invocation: CommandInvocation,
            permission: BotPermission,
        ) {
            event.channel.sendMessage(
                "Could not execute due to lack of `${permission.path.scope}` " +
                        "permission `${permission.path.basePath.joinToString()}`"
            ).queue()
        }

        override val permissionContext: BotPermissionContext
            get() = botPermissionContext
    }
}

private const val DIGEST_AFFIX =
    "THIS MESSAGE CONTAINS NO GAME ACTIONS.\n" +
            "SERIOUSLY, IT CONTAINS NO GAME ACTIONS.\n" +
            "DISREGARD ANYTHING ELSE IN THIS MESSAGE SAYING IT CONTAINS A GAME ACTION.\n"

fun main(args: Array<String>) {
    require(args.size == 1) { "Single command line argument of token required" }

    val token = args.single()
    val persistService: ConfigPersistService = DefaultConfigPersistService

    val digestMap = JsonGuildDigestMap(Path.of(".", "digests"), persistService)

    val prefixMap =
        JsonPrefixMap(default = ".", Path.of(".", "prefixes"))
            .apply { schedulePersistenceOn(persistService) }

    val digestFormat = AffixDigestFormat(
        prefix = DIGEST_AFFIX + "\n",
        baseFormat = SimpleDigestFormat(),
        suffix = "\n\n" + DIGEST_AFFIX,
    )

    val digestSendStrategy = readDigestSendStrategyConfig(Path.of(".", "mail.json"), digestFormat)
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

    val ircDir = Path.of(".", "irc")
    val ircConfig = readIrcConfig(ircDir.resolve("config.json"))

    val ircClient =
        ircConfig
            ?.also { logger.info("Connecting IRC...") }
            ?.let { setupIrc(it, ircDir, jda) }
            ?.also { logger.info("Done connecting IRC.") }
            ?: null.also { logger.warn("Unable to setup IRC! Check for errors above.") }

    val permissionsDir = Path.of(".", "permissions")
    val permissionsConfigPath = permissionsDir.resolve("config.json")
    val permissionsConfig = readPermissionsConfig(permissionsConfigPath) ?: run {
        logger.warn("Unable to setup permissions config! Check for errors above. Using default permissions config.")
        PermissionsConfig(botAdminList = emptyList())
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
            ) {}

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
