package org.randomcat.agorabot.commands.impl

import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.tryRespondWithText

/**
 * A marker that allows using [requiresDiscord]. By contract, a [ExtendableArgumentPendingExecutionReceiver] that has an
 * extension marker that implements this interface shall implement [DiscordExtensionPendingExecutionReceiver] with
 * proper type arguments (NextExecutionReceiver being the same as this, and the other type arguments being mandated by
 * [ExtendableArgumentPendingExecutionReceiver]).
 */
interface DiscordExtensionMarker<NextExecutionReceiver>

class DiscordExtensionExecutionMixin(private val source: CommandEventSource?) : PendingExecutionReceiverMixin {
    override fun executeMixin(): PendingExecutionReceiverMixinResult {
        if (source == null) error("Violation of promise to never execute")

        if (source !is CommandEventSource.Discord) {
            source.tryRespondWithText("This command can only be run on Discord.")
            return PendingExecutionReceiverMixinResult.StopExecution
        }

        return PendingExecutionReceiverMixinResult.ContinueExecution
    }
}

interface DiscordExtensionPendingExecutionReceiver<out ExecutionReceiver, out NextExecutionReceiver, out Arg, out Ext> :
    ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, Ext> {
    fun requiresDiscord(): ExtendableArgumentPendingExecutionReceiver<NextExecutionReceiver, Arg, Ext>
}

@Suppress("UNCHECKED_CAST") // This cast is guaranteed to be safe by the contract of DiscordExtensionMarker
fun <ExecutionReceiver, NextExecutionReceiver, Arg, Ext : DiscordExtensionMarker<NextExecutionReceiver>> ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, Ext>.requiresDiscord() =
    (this as DiscordExtensionPendingExecutionReceiver<ExecutionReceiver, NextExecutionReceiver, Arg, Ext>).requiresDiscord()

fun <ExecutionReceiver, NextExecutionReceiver, Arg, Ext : DiscordExtensionMarker<NextExecutionReceiver>> ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, Ext>.requiresDiscord(
    block: NextExecutionReceiver.(Arg) -> Unit,
) = (requiresDiscord()).invoke(block)
