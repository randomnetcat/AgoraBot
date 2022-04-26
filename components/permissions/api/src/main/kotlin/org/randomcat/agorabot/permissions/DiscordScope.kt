package org.randomcat.agorabot.permissions

import org.randomcat.agorabot.util.DiscordPermission

private const val DISCORD_PERMISSION_SCOPE = "discord"

object DiscordScope {
    fun permission(permission: DiscordPermission) = object : BotPermission {
        private val path = PermissionPath(listOf(permission.name))

        override fun readable(): String {
            return formatScopedPermission(scope = DISCORD_PERMISSION_SCOPE, path = path)
        }

        override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
            return when (userContext) {
                is UserPermissionContext.Authenticated.InGuild -> userContext.member.hasPermission(permission)
                is UserPermissionContext.Unauthenticated, is UserPermissionContext.Authenticated.Guildless -> false
            }
        }
    }

    fun admin() = permission(DiscordPermission.ADMINISTRATOR)
}
