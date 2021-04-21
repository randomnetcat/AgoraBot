package org.randomcat.agorabot.commands.impl

import net.dv8tion.jda.api.events.message.MessageReceivedEvent

interface GuildExtensionMarker<NextExecutionReceiver>

private const val NEED_GUILD_ERROR_MSG = "This command can only be run in a Guild."

class GuildExtensionExecutionMixin(private val event: MessageReceivedEvent?) : PendingExecutionReceiverMixin {
    override fun executeMixin(): PendingExecutionReceiverMixinResult {
        if (event == null) error("Violation of promise to never execute")

        if (event.isFromGuild) {
            return PendingExecutionReceiverMixinResult.ContinueExecution
        }

        event.channel.sendMessage(NEED_GUILD_ERROR_MSG).queue()
        return PendingExecutionReceiverMixinResult.StopExecution
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
