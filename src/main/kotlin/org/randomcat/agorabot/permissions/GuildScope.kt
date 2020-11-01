package org.randomcat.agorabot.permissions

import org.randomcat.agorabot.util.DiscordPermission

private const val GUILD_PERMISSION_SCOPE = "guild"

private fun checkGuildPermission(
    botContext: BotPermissionContext,
    userContext: UserPermissionContext,
    vararg paths: PermissionPath,
): Boolean {
    return when (userContext) {
        is UserPermissionContext.Guildless -> false

        is UserPermissionContext.InGuild -> {
            val member = userContext.member
            if (member.hasPermission(DiscordPermission.ADMINISTRATOR)) return true

            for (path in paths) {
                @Suppress("UNUSED_VARIABLE")
                val ensureExhaustive = when (
                    botContext.checkGuildPath(guildId = userContext.guild.id, userId = userContext.user.id, path)
                ) {
                    BotPermissionState.ALLOW -> return true
                    BotPermissionState.DENY -> return false
                    BotPermissionState.DEFER -> {
                        // continue around the loop
                    }
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
}
