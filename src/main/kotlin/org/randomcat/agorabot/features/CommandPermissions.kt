package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.base.requirements.permissions.PermissionsStrategy
import org.randomcat.agorabot.commands.base.requirements.permissions.PermissionsStrategyTag
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.listener.tryRespondWithText
import org.randomcat.agorabot.permissions.BotPermission
import org.randomcat.agorabot.permissions.BotPermissionContext
import org.randomcat.agorabot.permissions.feature.BotPermissionContextTag

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

private val permissionContextDep = FeatureDependency.Single(BotPermissionContextTag)

@FeatureSourceFactory
fun baseCommandPermissionsSource() = object : FeatureSource.NoConfig {
    override val featureName: String
        get() = "base_command_permissions_default"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(permissionContextDep)
    override val provides: List<FeatureElementTag<*>>
        get() = listOf(BaseCommandDependencyTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val permissionContext = context[permissionContextDep]

        return Feature.singleTag(
            BaseCommandDependencyTag,
            BaseCommandDependencyResult(
                baseTag = PermissionsStrategyTag,
                value = makePermissionsStrategy(permissionContext),
            ),
        )
    }
}
