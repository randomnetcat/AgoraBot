package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.commands.base.requirements.permissions.senderHasPermission
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.permissions.*

private val MANAGE_GUILD_PERMISSIONS_PERMISSION = GuildScope.command("permissions").action("manage")

class PermissionsCommand(
    strategy: BaseCommandStrategy,
    private val botMap: MutablePermissionMap,
    private val guildMap: MutableGuildPermissionMap,
) : BaseCommand(strategy) {
    private fun BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.handleGuildSetState(
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

    private fun BaseCommandExecutionReceiverRequiring<ExtendedDiscordRequirement>.handleBotSetState(
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
                ) { (roleString, stringPath) ->
                    val guildInfo = currentGuildInfo

                    val role = guildInfo.resolveRole(roleString)
                    if (role == null) {
                        respond("Unable to locate single role with name/id \"$roleString\".")
                        return@permissions
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

    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("guild") {
                guildSubcommand("grant", BotPermissionState.ALLOW)
                guildSubcommand("clear", BotPermissionState.DEFER)
                guildSubcommand("deny", BotPermissionState.DENY)
            }

            subcommand("bot") {
                botSubcommand("grant", BotPermissionState.ALLOW)
                botSubcommand("clear", BotPermissionState.DEFER)
                botSubcommand("deny", BotPermissionState.DENY)
            }
        }
    }
}
