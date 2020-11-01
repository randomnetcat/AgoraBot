package org.randomcat.agorabot.permissions

import org.randomcat.agorabot.util.DiscordPermission

private const val GUILD_PERMISSION_SCOPE = "guild"

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
        return when (userContext) {
            is UserPermissionContext.Guildless -> false

            is UserPermissionContext.InGuild -> {
                userContext.member.hasPermission(DiscordPermission.ADMINISTRATOR) ||
                        botContext
                            .checkGuildPath(
                                guildId = userContext.guild.id,
                                userId = userContext.member.id,
                                path = path.basePath,
                            )
                            .mapDeferred {
                                botContext.checkGuildPath(
                                    guildId = userContext.guild.id,
                                    userId = userContext.member.id,
                                    path = commandPath
                                )
                            }
                            .isAllowed()
            }
        }
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
        return when (userContext) {
            is UserPermissionContext.Guildless -> false

            is UserPermissionContext.InGuild -> {
                userContext.member.hasPermission(DiscordPermission.ADMINISTRATOR) ||
                        botContext.checkGuildPath(
                            guildId = userContext.guild.id,
                            userId = userContext.member.id,
                            path = path.basePath,
                        ).isAllowed()
            }
        }
    }

    fun action(actionName: String) = GuildScopeActionPermission(
        commandName = commandName,
        actionName = actionName,
    )
}

object GuildScope {
    fun command(commandName: String) = GuildScopeCommandPermission(commandName)
}
