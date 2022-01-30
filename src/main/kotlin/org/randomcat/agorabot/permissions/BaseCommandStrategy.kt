package org.randomcat.agorabot.permissions

import org.randomcat.agorabot.commands.impl.PermissionsStrategy
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.listener.tryRespondWithText

fun makePermissionsStrategy(
    permissionsContext: BotPermissionContext,
): PermissionsStrategy {
    // Unambiguous name
    @Suppress("UnnecessaryVariable")
    val thePermissionsContext = permissionsContext

    return object : PermissionsStrategy {
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
            get() = thePermissionsContext
    }
}
