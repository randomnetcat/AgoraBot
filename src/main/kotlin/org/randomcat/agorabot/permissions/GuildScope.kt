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
    override val path = PermissionScopedPath(
        scope = GUILD_PERMISSION_SCOPE,
        basePath = PermissionPath(listOf(commandName, actionName))
    )

    private val commandPath = PermissionPath(listOf(commandName))

    override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
        return checkGuildPermission(botContext, userContext, path.basePath, commandPath)
    }
}

data class GuildScopeCommandPermission(
    private val commandName: String,
) : BotPermission {
    override val path = PermissionScopedPath(
        scope = GUILD_PERMISSION_SCOPE,
        basePath = PermissionPath(listOf(commandName))
    )

    override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
        return checkGuildPermission(botContext, userContext, path.basePath)
    }

    fun action(actionName: String) = GuildScopeActionPermission(
        commandName = commandName,
        actionName = actionName,
    )
}

object GuildScope {
    fun command(commandName: String) = GuildScopeCommandPermission(commandName)

    fun byPath(path: PermissionPath) = object : BotPermission {
        override val path: PermissionScopedPath
            get() = PermissionScopedPath(scope = GUILD_PERMISSION_SCOPE, basePath = path)

        override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
            return checkGuildPermission(botContext, userContext, path)
        }
    }
}
