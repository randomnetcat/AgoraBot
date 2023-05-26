package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.utils.SplitUtil
import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.help.help
import org.randomcat.agorabot.commands.base.requirements.discord.currentGuildId
import org.randomcat.agorabot.commands.base.requirements.discord.currentGuildInfo
import org.randomcat.agorabot.commands.base.requirements.discord_ext.ExtendedDiscordRequirement
import org.randomcat.agorabot.commands.base.requirements.discord_ext.ExtendedGuildRequirement
import org.randomcat.agorabot.commands.base.requirements.discord_ext.InDiscord
import org.randomcat.agorabot.commands.base.requirements.discord_ext.InGuild
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.commands.base.requirements.permissions.senderHasPermission
import org.randomcat.agorabot.permissions.*

private val MANAGE_GUILD_PERMISSIONS_PERMISSION = GuildScope.command("permissions").action("manage")

class PermissionsCommand(
    strategy: BaseCommandStrategy,
    private val botMap: MutablePermissionMap,
    private val guildMap: MutableGuildPermissionMap,
) : BaseCommand(strategy) {
    private suspend fun BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.handleGuildSetState(
        id: PermissionMapId,
        stringPath: String,
        newState: BotPermissionState,
    ) {
        val guildId = currentGuildInfo.guildId

        check(senderHasPermission(MANAGE_GUILD_PERMISSIONS_PERMISSION))

        val permissionPath = PermissionPath.fromSplitting(stringPath)

        if (permissionPath.parts.contains("discord")) {
            respond("This command cannot grant Discord permissions.")
            return
        }

        if (senderHasPermission(GuildScope.byPath(permissionPath))) {
            guildMap
                .mapForGuild(guildId)
                .setStateForId(permissionPath, id = id, newState)

            respond("Done.")
        } else {
            respond("Cannot grant permission `${stringPath}` because you do not have it.")
        }
    }

    private suspend fun BaseCommandExecutionReceiverRequiring<ExtendedDiscordRequirement>.handleBotSetState(
        id: PermissionMapId,
        stringPath: String,
        newState: BotPermissionState,
    ) {
        check(senderHasPermission(BotScope.admin()))

        val permissionPath = PermissionPath.fromSplitting(stringPath)

        if (senderHasPermission(GuildScope.byPath(permissionPath))) {
            if (stringPath.equals("admin", ignoreCase = true)) {
                respond("Bot admin status cannot be changed via a command, only in the config file.")
                return
            }

            botMap.setStateForId(permissionPath, id, newState)

            respond("Done.")
        } else {
            respond("Cannot grant permission `${stringPath}` because you do not have it.")
        }
    }

    private fun SubcommandsArgumentDescriptionReceiver<ContextAndReceiver<BaseCommandContext, BaseCommandExecutionReceiver>>.guildSubcommand(
        name: String,
        state: BotPermissionState,
    ) {
        subcommand(name) {
            subcommand("user") {
                args(
                    StringArg("user_id"),
                    StringArg("permission_path")
                ).requires(
                    InGuild
                ).permissions(
                    MANAGE_GUILD_PERMISSIONS_PERMISSION,
                ) { (userId, stringPath) ->
                    handleGuildSetState(
                        id = PermissionMap.idForUser(userId),
                        stringPath = stringPath.lowercase(),
                        newState = state,
                    )
                }
            }

            subcommand("role") {
                args(
                    StringArg("role_descriptor"),
                    StringArg("permission_path")
                ).requires(
                    InGuild
                ).permissions(
                    MANAGE_GUILD_PERMISSIONS_PERMISSION,
                ) cmd@{ (roleString, stringPath) ->
                    val guildInfo = currentGuildInfo

                    val role = guildInfo.resolveRole(roleString)
                    if (role == null) {
                        respond("Unable to locate single role with name/id \"$roleString\".")
                        return@cmd
                    }

                    handleGuildSetState(
                        id = PermissionMap.idForRole(role),
                        stringPath = stringPath.lowercase(),
                        newState = state,
                    )
                }
            }
        }
    }

    private fun SubcommandsArgumentDescriptionReceiver<ContextAndReceiver<BaseCommandContext, BaseCommandExecutionReceiver>>.botSubcommand(
        name: String,
        state: BotPermissionState,
    ) {
        subcommand(name) {
            args(
                StringArg("user_id"),
                StringArg("permission_path")
            ).requires(
                InDiscord,
            ).permissions(
                BotScope.admin(),
            ) { (userId, stringPath) ->
                handleBotSetState(
                    id = PermissionMap.idForUser(userId = userId),
                    stringPath = stringPath.lowercase(),
                    newState = state,
                )
            }
        }
    }

    private suspend fun BaseCommandExecutionReceiver.listPermissions(permissionMap: PermissionMap) {
        val entries = permissionMap.listEntries().filterValues { it.isNotEmpty() }

        if (entries.isEmpty()) {
            respond("No permission entries exist.")
            return
        }

        val fullString = entries.entries.joinToString("\n\n") { (path, subMap) ->
            path.joinToString() + ":\n" + subMap.entries.joinToString("\n") { (id, state) ->
                val rawId = id.raw

                when {
                    rawId.startsWith("user.") -> "User <@${rawId.removePrefix("user.")}>"
                    rawId.startsWith("role.") -> "Role <&${rawId.removePrefix("role.")}>"
                    else -> "Internal id [$rawId]"
                } + ": " + when (state) {
                    BotPermissionState.ALLOW -> "allow"
                    BotPermissionState.DENY -> "deny"
                    BotPermissionState.DEFER -> "defer"
                }
            }
        }

        val parts = SplitUtil.split(fullString, Message.MAX_CONTENT_LENGTH, SplitUtil.Strategy.NEWLINE)
        parts.forEach { respond(it) }
    }

    override fun BaseCommandImplReceiver.impl() {
        help("Manages bot-wide or guild-local permissions.")

        subcommands {
            subcommand("guild") {
                help("Manages permissions for this guild. Guild admins always have all permissions. Otherwise, the permission is checked from highest to lowest role, stopping when a grant/deny is reached. If none is reached, the permission is denied.")

                guildSubcommand("grant", BotPermissionState.ALLOW)
                guildSubcommand("clear", BotPermissionState.DEFER)
                guildSubcommand("deny", BotPermissionState.DENY)

                subcommand("list") {
                    noArgs().requires(InGuild) {
                        listPermissions(guildMap.mapForGuild(currentGuildId))
                    }
                }
            }

            subcommand("bot") {
                help("Manages permissions for the whole bot. Can only be used by bot admins.")

                botSubcommand("grant", BotPermissionState.ALLOW)
                botSubcommand("clear", BotPermissionState.DEFER)
                botSubcommand("deny", BotPermissionState.DENY)

                subcommand("list") {
                    noArgs().permissions(BotScope.admin()) {
                        listPermissions(botMap)
                    }
                }
            }
        }
    }
}
