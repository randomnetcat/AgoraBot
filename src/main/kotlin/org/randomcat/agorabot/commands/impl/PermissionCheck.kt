package org.randomcat.agorabot.commands.impl

import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.permissions.BotPermission
import org.randomcat.agorabot.permissions.BotPermissionContext
import org.randomcat.agorabot.permissions.UserPermissionContext

fun userPermissionContextForSource(source: CommandEventSource): UserPermissionContext {
    return when (source) {
        is CommandEventSource.Discord -> {
            val event = source.event

            event.member?.let { UserPermissionContext.Authenticated.InGuild(it) }
                ?: UserPermissionContext.Authenticated.Guildless(event.author)
        }

        is CommandEventSource.Irc -> UserPermissionContext.Unauthenticated
    }
}

interface PermissionsStrategyDependency

interface PermissionsStrategy {
    fun onPermissionsError(source: CommandEventSource, invocation: CommandInvocation, permission: BotPermission)
    val permissionContext: BotPermissionContext
}

fun <Arg : WithContext<BaseCommandContext>> PendingInvocation<Arg>.permissions(
    vararg newPermissions: BotPermission,
) = prepend { arg ->
    val context = arg.context
    val strategy = context.tryFindDependency(PermissionsStrategyDependency::class) as PermissionsStrategy

    val source = context.source
    val botContext = strategy.permissionContext
    val userContext = userPermissionContextForSource(source)

    for (permission in newPermissions) {
        if (!permission.isSatisfied(botContext, userContext)) {
            strategy.onPermissionsError(source, context.invocation, permission)
            return@prepend PrependResult.StopExecution
        }
    }

    return@prepend PrependResult.ContinueExecution
}

@JvmName("permissions")
inline fun <Context : BaseCommandContext, Receiver, Arg> PendingInvocation<ContextReceiverArg<Context, Receiver, Arg>>.permissions(
    vararg newPermissions: BotPermission,
    crossinline block: Receiver.(Arg) -> Unit,
) {
    return permissions(*newPermissions).execute { block(it.receiver, it.arg) }
}

