package org.randomcat.agorabot.commands.base.requirements.permissions

import org.randomcat.agorabot.commands.base.BaseCommandContext
import org.randomcat.agorabot.commands.base.BaseCommandExecutionReceiverRequiring
import org.randomcat.agorabot.commands.base.RequirementResult
import org.randomcat.agorabot.commands.base.RequirementSet
import org.randomcat.agorabot.permissions.BotPermission
import org.randomcat.agorabot.permissions.UserPermissionContext

interface PermissionsAccessRequirement {
    companion object {
        fun create(context: BaseCommandContext): RequirementResult<PermissionsAccessRequirement> {
            val strategy = context.tryFindDependency(PermissionsStrategyTag) as PermissionsStrategy

            return RequirementResult.Success(
                object : PermissionsAccessRequirement {
                    override val userPermissionContext: UserPermissionContext =
                        userPermissionContextForSource(context.source)

                    override fun senderHasPermission(permission: BotPermission): Boolean {
                        return permission.isSatisfied(strategy.permissionContext, userPermissionContext)
                    }
                }
            )
        }
    }

    val userPermissionContext: UserPermissionContext
    fun senderHasPermission(permission: BotPermission): Boolean
}

fun BaseCommandExecutionReceiverRequiring<PermissionsAccessRequirement>.senderHasPermission(permission: BotPermission) =
    requirement().senderHasPermission(permission)

object PermissionsAccess : RequirementSet<BaseCommandContext, PermissionsAccessRequirement> {
    override fun create(context: BaseCommandContext): RequirementResult<PermissionsAccessRequirement> {
        return PermissionsAccessRequirement.create(context)
    }
}
