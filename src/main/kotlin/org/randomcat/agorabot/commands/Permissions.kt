package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.permissions.*

private val MANAGE_GUILD_PERMISSIONS_PERMISSION = GuildScope.command("permissions").action("manage")

class PermissionsCommand(
    strategy: BaseCommandStrategy,
    private val botMap: MutablePermissionMap,
    private val guildMap: MutableGuildPermissionMap,
) : BaseCommand(strategy) {
    private fun ExecutionReceiverImpl.handleGuildSetState(
        id: PermissionMapId,
        stringPath: String,
        newState: BotPermissionState,
    ) {
        val guildId = currentGuildInfo()?.guildId ?: run {
            respondNeedGuild()
            return
        }

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

    private fun ExecutionReceiverImpl.handleBotSetState(
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

    private fun SubcommandsArgumentDescriptionReceiver<ExecutionReceiverImpl, PermissionsExtensionMarker>.guildSubcommand(
        name: String,
        state: BotPermissionState,
    ) {
        subcommand(name) {
            subcommand("user") {
                args(
                    StringArg("user_id"),
                    StringArg("permission_path")
                ).permissions(
                    MANAGE_GUILD_PERMISSIONS_PERMISSION,
                ) { (userId, stringPath) ->
                    handleGuildSetState(
                        id = PermissionMap.idForUser(userId),
                        stringPath = stringPath.toLowerCase(),
                        newState = state,
                    )
                }
            }

            subcommand("role") {
                args(
                    StringArg("role_descriptor"),
                    StringArg("permission_path")
                ).permissions(
                    MANAGE_GUILD_PERMISSIONS_PERMISSION,
                ) { (roleString, stringPath) ->
                    val guildInfo = currentGuildInfo() ?: run {
                        respondNeedGuild()
                        return@permissions
                    }

                    val role = guildInfo.resolveRole(roleString)
                    if (role == null) {
                        respond("Unable to locate single role with name/id \"$roleString\".")
                        return@permissions
                    }

                    handleGuildSetState(
                        id = PermissionMap.idForRole(role),
                        stringPath = stringPath.toLowerCase(),
                        newState = state,
                    )
                }
            }
        }
    }

    private fun SubcommandsArgumentDescriptionReceiver<ExecutionReceiverImpl, PermissionsExtensionMarker>.botSubcommand(
        name: String,
        state: BotPermissionState,
    ) {
        subcommand(name) {
            args(
                StringArg("user_id"),
                StringArg("permission_path")
            ).permissions(
                BotScope.admin(),
            ) { (userId, stringPath) ->
                handleBotSetState(
                    id = PermissionMap.idForUser(userId = userId),
                    stringPath = stringPath.toLowerCase(),
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
