package org.randomcat.agorabot.commands.impl

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
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

private fun <ExecutionReceiver, T, E, R> makePermissionsArgsRawReceiver(
    data: PermissionsReceiverData,
    baseReceiver: ArgumentDescriptionReceiver<ExecutionReceiver, Any?>,
    parsers: List<CommandArgumentParser<T, E>>,
    mapParsed: (List<T>) -> R,
) = PermissionsPendingExecutionReceiver(
    baseReceiver = baseReceiver.argsRaw(parsers, mapParsed),
    permissions = persistentListOf(),
    data = data,
)

private fun <ExecutionReceiver> makePermissionsMatchFirstReceiver(
    data: PermissionsReceiverData,
    baseMatchFirstReceiver: ArgumentMultiDescriptionReceiver<ExecutionReceiver, Any?>,
): ArgumentMultiDescriptionReceiver<ExecutionReceiver, PermissionsExtensionMarker> =
    object : ArgumentMultiDescriptionReceiver<ExecutionReceiver, PermissionsExtensionMarker> {
        override fun <T, E, R> argsRaw(
            parsers: List<CommandArgumentParser<T, E>>,
            mapParsed: (List<T>) -> R,
        ): ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, R, PermissionsExtensionMarker> {
            return PermissionsPendingExecutionReceiver(
                baseReceiver = baseMatchFirstReceiver.argsRaw(parsers, mapParsed),
                permissions = persistentListOf(),
                data = data,
            )
        }

        override fun matchFirst(
            block: ArgumentMultiDescriptionReceiver<ExecutionReceiver, PermissionsExtensionMarker>.() -> Unit,
        ) {
            baseMatchFirstReceiver.matchFirst {
                val nextReceiver = this
                block(makePermissionsMatchFirstReceiver(data, nextReceiver))
            }
        }
    }

private fun <ExecutionReceiver> makePermissionsSubcommmandReceiver(
    data: PermissionsReceiverData,
    baseSubcommandsReceiver: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver, Any?>,
): SubcommandsArgumentDescriptionReceiver<ExecutionReceiver, PermissionsExtensionMarker> =
    object : SubcommandsArgumentDescriptionReceiver<ExecutionReceiver, PermissionsExtensionMarker> {
        override fun <T, E, R> argsRaw(
            parsers: List<CommandArgumentParser<T, E>>,
            mapParsed: (List<T>) -> R,
        ): ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, R, PermissionsExtensionMarker> {
            return makePermissionsArgsRawReceiver(
                data,
                baseSubcommandsReceiver,
                parsers,
                mapParsed
            )
        }

        override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver, PermissionsExtensionMarker>.() -> Unit) {
            baseSubcommandsReceiver.matchFirst {
                block(makePermissionsMatchFirstReceiver(data, this))
            }
        }

        override fun subcommand(
            name: String,
            block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver, PermissionsExtensionMarker>.() -> Unit,
        ) {
            baseSubcommandsReceiver.subcommand(name) {
                block(makePermissionsSubcommmandReceiver(data, this))
            }
        }
    }

class MatchFirstPermissionsArgumentDescriptionReceiver<ExecutionReceiver>(
    private val baseReceiver: ArgumentMultiDescriptionReceiver<ExecutionReceiver, Any?>,
    private val data: PermissionsReceiverData,
) : ArgumentDescriptionReceiver<ExecutionReceiver, PermissionsExtensionMarker> {
    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, R, PermissionsExtensionMarker> {
        return makePermissionsArgsRawReceiver(data, baseReceiver, parsers, mapParsed)
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver, PermissionsExtensionMarker>.() -> Unit) {
        baseReceiver.matchFirst {
            block(
                makePermissionsMatchFirstReceiver(
                    this@MatchFirstPermissionsArgumentDescriptionReceiver.data,
                    this
                )
            )
        }
    }
}

class TopLevelPermissionsArgumentDescriptionReceiver
<ExecutionReceiver, Base : TopLevelArgumentDescriptionReceiver<ExecutionReceiver, Any?>>(
    private val baseReceiver: Base,
    private val data: PermissionsReceiverData,
) : TopLevelArgumentDescriptionReceiver<ExecutionReceiver, PermissionsExtensionMarker> {
    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): PermissionsPendingExecutionReceiver<ExecutionReceiver, R> {
        return makePermissionsArgsRawReceiver(data, baseReceiver, parsers, mapParsed)
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver, PermissionsExtensionMarker>.() -> Unit) {
        baseReceiver.matchFirst {
            block(
                makePermissionsMatchFirstReceiver(
                    this@TopLevelPermissionsArgumentDescriptionReceiver.data,
                    this,
                ),
            )
        }
    }

    override fun subcommands(block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver, PermissionsExtensionMarker>.() -> Unit) {
        baseReceiver.subcommands {
            block(
                makePermissionsSubcommmandReceiver(
                    this@TopLevelPermissionsArgumentDescriptionReceiver.data,
                    this,
                )
            )
        }
    }

    fun base() = baseReceiver
}
