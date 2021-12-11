package org.randomcat.agorabot.permissions

private const val BOT_PERMISSION_SCOPE = "bot"

data class BotScopeActionPermission(
    private val commandName: String,
    private val actionName: String,
) : BotPermission {
    private val actionPath = PermissionPath(listOf(commandName, actionName))
    private val commandPath = PermissionPath(listOf(commandName))

    override fun readable(): String {
        return formatScopedPermission(scope = BOT_PERMISSION_SCOPE, path = actionPath)
    }

    override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
        return when (userContext) {
            is UserPermissionContext.Unauthenticated -> false

            is UserPermissionContext.Authenticated -> {
                val user = userContext.user

                return botContext.isBotAdmin(userId = user.id) ||
                        botContext.checkGlobalPath(userId = user.id, actionPath)
                            .mapDeferred { botContext.checkGlobalPath(userId = user.id, commandPath) }
                            .isAllowed()
            }
        }
    }
}

data class BotScopeCommandPermission(private val commandName: String) : BotPermission {
    private val commandPath = PermissionPath(listOf(commandName))

    override fun readable(): String {
        return formatScopedPermission(scope = BOT_PERMISSION_SCOPE, path = commandPath)
    }

    fun action(actionName: String) = BotScopeActionPermission(
        commandName = commandName,
        actionName = actionName,
    )

    override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
        return when (userContext) {
            is UserPermissionContext.Unauthenticated -> false

            is UserPermissionContext.Authenticated -> {
                val user = userContext.user

                return botContext.isBotAdmin(userId = user.id) ||
                        botContext.checkGlobalPath(userId = user.id, commandPath).isAllowed()
            }
        }
    }
}

object BotScope {
    fun command(commandName: String) = BotScopeCommandPermission(commandName)

    fun admin() = object : BotPermission {
        override fun readable(): String {
            return "bot admin"
        }

        override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
            return when (userContext) {
                is UserPermissionContext.Unauthenticated -> false
                is UserPermissionContext.Authenticated -> botContext.isBotAdmin(userId = userContext.user.id)
            }
        }
    }
}
