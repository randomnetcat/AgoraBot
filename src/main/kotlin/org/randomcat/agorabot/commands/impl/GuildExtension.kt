package org.randomcat.agorabot.commands.impl

import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.tryRespondWithText

interface GuildExtensionMarker<NextExecutionReceiver>

private const val NEED_GUILD_ERROR_MSG = "This command can only be run in a Guild."

class GuildExtensionExecutionMixin(private val source: CommandEventSource?) : PendingExecutionReceiverMixin {
    override fun executeMixin(): PendingExecutionReceiverMixinResult {
        if (source == null) error("Violation of promise to never execute")

        if (source !is CommandEventSource.Discord) {
            source.tryRespondWithText(NEED_GUILD_ERROR_MSG)
            return PendingExecutionReceiverMixinResult.StopExecution
        }

        val event = source.event

        if (!event.isFromGuild) {
            event.channel.sendMessage(NEED_GUILD_ERROR_MSG).queue()
            return PendingExecutionReceiverMixinResult.StopExecution
        }

        return PendingExecutionReceiverMixinResult.ContinueExecution
    }
}

interface GuildExtensionPendingExecutionReceiver<out ExecutionReceiver, out NextExecutionReceiver, out Arg, out Ext> :
    ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, Ext> {
    fun requiresGuild(): ExtendableArgumentPendingExecutionReceiver<NextExecutionReceiver, Arg, Ext>
}

fun <ExecutionReceiver, NextExecutionReceiver, Arg, Ext : GuildExtensionMarker<NextExecutionReceiver>> ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, Ext>.requiresGuild() =
    (this as GuildExtensionPendingExecutionReceiver<ExecutionReceiver, NextExecutionReceiver, Arg, Ext>).requiresGuild()

fun <ExecutionReceiver, NextExecutionReceiver, Arg, Ext : GuildExtensionMarker<NextExecutionReceiver>> ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, Ext>.requiresGuild(
    block: NextExecutionReceiver.(Arg) -> Unit,
) = (requiresGuild()).invoke(block)
