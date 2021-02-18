package org.randomcat.agorabot.commands.impl

import kotlinx.collections.immutable.PersistentList
import org.randomcat.agorabot.permissions.BotPermission
import org.randomcat.agorabot.permissions.BotPermissionContext
import org.randomcat.agorabot.permissions.UserPermissionContext

interface PermissionsExtensionMarker

sealed class PermissionsReceiverData {
    data class AllowExecution(
        val userContext: UserPermissionContext,
        val permissionsContext: BotPermissionContext,
        val onError: (BotPermission) -> Unit,
    ) : PermissionsReceiverData()

    object NeverExecute : PermissionsReceiverData()
}

interface PermissionsPendingExecutionReceiver<ExecutionReceiver, Arg, Ext> :
    ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, Ext> {
    fun permissions(vararg newPermissions: BotPermission): PermissionsPendingExecutionReceiver<ExecutionReceiver, Arg, Ext>
}

data class PermissionsExecutionMixin(
    private val permissions: PersistentList<BotPermission>,
    private val data: PermissionsReceiverData,
) : PendingExecutionReceiverMixin {
    @Suppress("UNREACHABLE_CODE")
    override fun executeMixin(): PendingExecutionReceiverMixinResult {
        @Suppress("UNUSED_VARIABLE")
        val ensureExhaustive = when (data) {
            is PermissionsReceiverData.AllowExecution -> {
                for (permission in permissions) {
                    if (!permission.isSatisfied(data.permissionsContext, data.userContext)) {
                        data.onError(permission)
                        return PendingExecutionReceiverMixinResult.StopExecution
                    }
                }

                return PendingExecutionReceiverMixinResult.ContinueExecution
            }

            is PermissionsReceiverData.NeverExecute -> {
                error("Violation of promise to never execute permissions checking")
            }
        }
    }
}

fun <ExecutionReceiver, Arg> ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, PermissionsExtensionMarker>.permissions(
    vararg newPermissions: BotPermission,
) = (this as PermissionsPendingExecutionReceiver).permissions(*newPermissions)

fun <ExecutionReceiver, Arg> ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, PermissionsExtensionMarker>.permissions(
    vararg newPermissions: BotPermission,
    block: ExecutionReceiver.(Arg) -> Unit,
) = permissions(*newPermissions).invoke(block)
