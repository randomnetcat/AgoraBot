package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.base.requirements.permissions.PermissionsStrategy
import org.randomcat.agorabot.commands.base.requirements.permissions.PermissionsStrategyTag
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.listener.tryRespondWithText
import org.randomcat.agorabot.permissions.BotPermission
import org.randomcat.agorabot.permissions.BotPermissionContext
import org.randomcat.agorabot.permissions.feature.botPermissionContext

private object PermissionsStrategyCacheKey

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

@FeatureSourceFactory
fun baseCommandPermissionsSource() = FeatureSource.ofConstant("base_command_permissions_default", object : Feature {
    override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
        if (tag is BaseCommandDependencyTag && tag.baseTag is PermissionsStrategyTag) {
            return tag.result(context.cache(PermissionsStrategyCacheKey) {
                makePermissionsStrategy(
                    permissionsContext = context.botPermissionContext,
                )
            })
        }

        return FeatureQueryResult.NotFound
    }
})
