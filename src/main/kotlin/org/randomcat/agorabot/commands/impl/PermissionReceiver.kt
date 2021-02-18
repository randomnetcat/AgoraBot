package org.randomcat.agorabot.commands.impl

import kotlinx.collections.immutable.PersistentList
import org.randomcat.agorabot.permissions.BotPermission
import org.randomcat.agorabot.permissions.BotPermissionContext
import org.randomcat.agorabot.permissions.UserPermissionContext

class PermissionsExtensionMarker

sealed class PermissionsReceiverData {
    data class AllowExecution(
        val userContext: UserPermissionContext,
        val permissionsContext: BotPermissionContext,
        val onError: (BotPermission) -> Unit,
    ) : PermissionsReceiverData()

    object NeverExecute : PermissionsReceiverData()
}

class PermissionsPendingExecutionReceiver<ExecutionReceiver, Arg>(
    private val baseReceiver: ArgumentPendingExecutionReceiver<ExecutionReceiver, Arg>,
    private val permissions: PersistentList<BotPermission>,
    private val data: PermissionsReceiverData,
) : ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, PermissionsExtensionMarker> {
    override fun invoke(block: ExecutionReceiver.(arg: Arg) -> Unit) {
        baseReceiver { arg ->
            @Suppress("UNUSED_VARIABLE")
            val ensureExhaustive = when (data) {
                is PermissionsReceiverData.AllowExecution -> {
                    for (permission in permissions) {
                        if (!permission.isSatisfied(data.permissionsContext, data.userContext)) {
                            data.onError(permission)
                            return@baseReceiver
                        }
                    }

                    block(arg)
                }

                is PermissionsReceiverData.NeverExecute -> {
                    error("Violation of promise to never execute permissions checking")
                }
            }
        }
    }

    fun permissions(vararg newPermissions: BotPermission) = PermissionsPendingExecutionReceiver(
        baseReceiver = baseReceiver,
        permissions = permissions.addAll(newPermissions.asList()),
        data = data,
    )
}

fun <ExecutionReceiver, Arg> ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, PermissionsExtensionMarker>.permissions(
    vararg newPermissions: BotPermission,
) = (this as PermissionsPendingExecutionReceiver).permissions(*newPermissions)

fun <ExecutionReceiver, Arg> ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, PermissionsExtensionMarker>.permissions(
    vararg newPermissions: BotPermission,
    block: ExecutionReceiver.(Arg) -> Unit,
) = permissions(*newPermissions).invoke(block)
