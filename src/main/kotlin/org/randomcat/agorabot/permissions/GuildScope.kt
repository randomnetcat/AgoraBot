package org.randomcat.agorabot.permissions

import kotlinx.collections.immutable.persistentListOf
import org.randomcat.agorabot.util.DiscordPermission

private const val GUILD_PERMISSION_SCOPE = "guild"

data class GuildScopeActionPermission(
    private val commandName: String,
    private val actionName: String,
) : BotPermission {
    override val scope: String get() = GUILD_PERMISSION_SCOPE
    override val path = persistentListOf(commandName, actionName)
    private val commandPath = listOf(commandName)

    override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
        return when (userContext) {
            is UserPermissionContext.Guildless -> false

            is UserPermissionContext.InGuild -> {
                userContext.member.hasPermission(DiscordPermission.ADMINISTRATOR) ||
                        botContext
                            .checkGuildPath(
                                guildId = userContext.guild.id,
                                userId = userContext.member.id,
                                path = path,
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
    override val scope: String get() = GUILD_PERMISSION_SCOPE
    override val path get() = persistentListOf(commandName)

    override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
        return when (userContext) {
            is UserPermissionContext.Guildless -> false

            is UserPermissionContext.InGuild -> {
                userContext.member.hasPermission(DiscordPermission.ADMINISTRATOR) ||
                        botContext.checkGuildPath(
                            guildId = userContext.guild.id,
                            userId = userContext.member.id,
                            path = path,
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
