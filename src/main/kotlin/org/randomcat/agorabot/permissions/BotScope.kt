package org.randomcat.agorabot.permissions

import kotlinx.collections.immutable.persistentListOf

private const val BOT_PERMISSION_SCOPE = "bot"

data class BotScopeActionPermission(
    private val commandName: String,
    private val actionName: String,
) : BotPermission {
    override val scope: String get() = BOT_PERMISSION_SCOPE
    override val path get() = persistentListOf(commandName, actionName)
    private val commandPath get() = listOf(commandName)

    override fun isSatisfied(context: BotPermissionContext, userContext: UserPermissionContext): Boolean {
        val user = userContext.user

        return context.isBotAdmin(userId = user.id) ||
                context.checkGlobalPath(userId = user.id, path)
                    .mapDeferred { context.checkGlobalPath(userId = user.id, commandPath) }
                    .isAllowed()
    }
}

data class BotScopeCommandPermission(private val commandName: String) : BotPermission {
    override val scope: String get() = BOT_PERMISSION_SCOPE
    override val path get() = persistentListOf(commandName)

    fun action(actionName: String) = BotScopeActionPermission(
        commandName = commandName,
        actionName = actionName,
    )

    override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
        val user = userContext.user

        return botContext.isBotAdmin(userId = user.id) ||
                botContext.checkGlobalPath(userId = user.id, path).isAllowed()
    }
}

object BotScope {
    fun command(commandName: String) = BotScopeCommandPermission(commandName)
}
