package org.randomcat.agorabot.permissions

import org.randomcat.agorabot.util.DiscordPermission

private const val GUILD_PERMISSION_SCOPE = "guild"

private fun checkGuildPermission(
    botContext: BotPermissionContext,
    userContext: UserPermissionContext,
    vararg paths: PermissionPath,
): Boolean {
    return when (userContext) {
        is UserPermissionContext.Unauthenticated, is UserPermissionContext.Authenticated.Guildless -> false

        is UserPermissionContext.Authenticated.InGuild -> {
            fun stateToBool(state: BotPermissionState): Boolean? {
                return when (state) {
                    BotPermissionState.ALLOW -> true
                    BotPermissionState.DENY -> false
                    BotPermissionState.DEFER -> null
                }
            }

            val member = userContext.member
            if (member.hasPermission(DiscordPermission.ADMINISTRATOR)) return true

            // Member returns roles from highest->lowest, which is the same order as we should check.
            // Member also does not include the @everyone role, so include that here.
            val roles = member.roles + userContext.guild.publicRole

            for (path in paths) {
                stateToBool(
                    botContext.checkUserGuildPath(guildId = userContext.guild.id, userId = userContext.user.id, path)
                )?.let { return it }

                for (role in roles) {
                    stateToBool(
                        botContext.checkRoleGuildPath(guildId = userContext.guild.id, roleId = role.id, path)
                    )?.let { return it }
                }
            }

            return false
        }
    }
}

data class GuildScopeActionPermission(
    private val commandName: String,
    private val actionName: String,
) : BotPermission {
    private val commandPath = PermissionPath(listOf(commandName))
    private val actionPath = PermissionPath(listOf(commandName, actionName))

    override fun readable(): String {
        return formatScopedPermission(scope = GUILD_PERMISSION_SCOPE, path = actionPath)
    }

    override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
        return checkGuildPermission(botContext, userContext, actionPath, commandPath)
    }
}

data class GuildScopeCommandPermission(
    private val commandName: String,
) : BotPermission {
    private val commandPath = PermissionPath(listOf(commandName))

    override fun readable(): String {
        return formatScopedPermission(scope = GUILD_PERMISSION_SCOPE, path = commandPath)
    }

    override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
        return checkGuildPermission(botContext, userContext, commandPath)
    }

    fun action(actionName: String) = GuildScopeActionPermission(
        commandName = commandName,
        actionName = actionName,
    )
}

object GuildScope {
    fun command(commandName: String) = GuildScopeCommandPermission(commandName)

    fun byPath(path: PermissionPath) = object : BotPermission {
        override fun readable(): String {
            return formatScopedPermission(scope = GUILD_PERMISSION_SCOPE, path = path)
        }

        override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
            return checkGuildPermission(botContext, userContext, path)
        }
    }
}
