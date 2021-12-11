package org.randomcat.agorabot.permissions

import org.randomcat.agorabot.commands.impl.BaseCommandPermissionsStrategy
import org.randomcat.agorabot.config.PermissionsConfig
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.listener.tryRespondWithText

fun makePermissionsStrategy(
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
            source: CommandEventSource,
            invocation: CommandInvocation,
            permission: BotPermission,
        ) {
            source.tryRespondWithText(
                "Could not execute due to lack of permission `${permission.readable()}`"
            )
        }

        override val permissionContext: BotPermissionContext
            get() = botPermissionContext
    }
}
