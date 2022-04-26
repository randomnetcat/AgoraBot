package org.randomcat.agorabot.commands.base

sealed class RequirementResult<out T> {
    data class Success<T>(val requirement: T) : RequirementResult<T>()
    object Failure : RequirementResult<Nothing>()
}

private inline fun <C, A, OutRequirement> PendingInvocation<ContextReceiverArg<C, BaseCommandExecutionReceiver, A>>.prependRequirement(
    crossinline factory: (C) -> RequirementResult<OutRequirement>,
): PendingInvocation<ContextReceiverArg<C, BaseCommandExecutionReceiverRequiring<OutRequirement>, A>> {
    return prependTransform { input ->
        when (val requirementResult = factory(input.context)) {
            is RequirementResult.Success -> PrependTransformResult.ContinueExecution(
                ContextReceiverArg(
                    context = input.context,
                    receiver = object : BaseCommandExecutionReceiverRequiring<OutRequirement>,
                        BaseCommandExecutionReceiver by input.receiver {
                        override fun requirement(): OutRequirement {
                            return requirementResult.requirement
                        }
                    },
                    arg = input.arg,
                )
            )

            is RequirementResult.Failure -> PrependTransformResult.StopExecution
        }

    }
}

inline fun <C, OutRequirement> mergeRequirements(
    context: C,
    vararg requirementFactories: (C) -> RequirementResult<Any>,
    doMerge: (List<Any>) -> OutRequirement,
): RequirementResult<OutRequirement> {
    val requirements = requirementFactories.map { factory ->
        when (val result = factory(context)) {
            is RequirementResult.Success -> result.requirement
            is RequirementResult.Failure -> return RequirementResult.Failure
        }
    }

    return RequirementResult.Success(doMerge(requirements))
}

fun <Context, Arg, OutRequirement> PendingInvocation<ContextReceiverArg<Context, BaseCommandExecutionReceiver, Arg>>.requires(
    set: RequirementSet<Context, OutRequirement>,
): PendingInvocation<ContextReceiverArg<Context, BaseCommandExecutionReceiverRequiring<OutRequirement>, Arg>> {
    return prependRequirement(set::create)
}

fun <Context, Arg, OutRequirement> PendingInvocation<ContextReceiverArg<Context, BaseCommandExecutionReceiver, Arg>>.requires(
    set: RequirementSet<Context, OutRequirement>,
    block: suspend BaseCommandExecutionReceiverRequiring<OutRequirement>.(Arg) -> Unit,
) {
    return requires(set).execute { block(it.receiver, it.arg) }
}

interface RequirementSet<in Context, out OutRequirement> {
    fun create(context: Context): RequirementResult<OutRequirement>
}
