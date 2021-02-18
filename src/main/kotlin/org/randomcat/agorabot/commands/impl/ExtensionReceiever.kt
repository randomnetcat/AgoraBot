package org.randomcat.agorabot.commands.impl

sealed class PendingExecutionReceiverMixinResult {
    object StopExecution : PendingExecutionReceiverMixinResult()
    object ContinueExecution : PendingExecutionReceiverMixinResult()
}

interface PendingExecutionReceiverMixin {
    fun executeMixin(): PendingExecutionReceiverMixinResult
}

abstract class MixinPendingExecutionReceiver<ExecutionReceiver, Arg, Ext>(
    protected val baseReceiver: ArgumentPendingExecutionReceiver<ExecutionReceiver, Arg>,
) : ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, Ext> {
    protected abstract val mixins: Iterable<PendingExecutionReceiverMixin>

    override fun invoke(block: ExecutionReceiver.(arg: Arg) -> Unit) {
        // The mixin execution has to only be done via the baseReceiver in case it doesn't represent an actual execution
        // context, since the mixins might need that execution context.
        return baseReceiver { arg ->
            for (mixin in mixins) {
                @Suppress("UNUSED_VARIABLE")
                val ensureExhaustive = when (mixin.executeMixin()) {
                    PendingExecutionReceiverMixinResult.ContinueExecution -> Unit

                    PendingExecutionReceiverMixinResult.StopExecution -> {
                        return@baseReceiver
                    }
                }
            }

            block(arg)
        }
    }
}
