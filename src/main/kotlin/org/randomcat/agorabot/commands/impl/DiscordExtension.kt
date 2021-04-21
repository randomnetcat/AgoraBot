package org.randomcat.agorabot.commands.impl

import net.dv8tion.jda.api.events.message.MessageReceivedEvent

interface DiscordExtensionMarker<NextExecutionReceiver>

class DiscordExtensionExecutionMixin(private val event: MessageReceivedEvent?) : PendingExecutionReceiverMixin {
    override fun executeMixin(): PendingExecutionReceiverMixinResult {
        if (event == null) error("Violation of promise to never execute")

        // TODO: check in Discord when IRC commands become possible
        return PendingExecutionReceiverMixinResult.ContinueExecution
    }
}

interface DiscordExtensionPendingExecutionReceiver<out ExecutionReceiver, out NextExecutionReceiver, out Arg, out Ext> :
    ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, Ext> {
    fun requiresDiscord(): ExtendableArgumentPendingExecutionReceiver<NextExecutionReceiver, Arg, Ext>
}

fun <ExecutionReceiver, NextExecutionReceiver, Arg, Ext : DiscordExtensionMarker<NextExecutionReceiver>> ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, Ext>.requiresDiscord() =
    (this as DiscordExtensionPendingExecutionReceiver<ExecutionReceiver, NextExecutionReceiver, Arg, Ext>).requiresDiscord()

fun <ExecutionReceiver, NextExecutionReceiver, Arg, Ext : DiscordExtensionMarker<NextExecutionReceiver>> ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, Ext>.requiresDiscord(
    block: NextExecutionReceiver.(Arg) -> Unit,
) = (requiresDiscord()).invoke(block)
