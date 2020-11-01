@file:Suppress("UNCHECKED_CAST", "USELESS_CAST")

package org.randomcat.agorabot.commands.impl

private typealias CAP<T, E> = CommandArgumentParser<T, E>

@DslMarker
annotation class CommandDslMarker

/**
 * @param Extension a marker for use by extension functions
 */
@CommandDslMarker
interface ExtendableArgumentPendingExecutionReceiver<out ExecutionReceiver, out Arg, out Extension> {
    operator fun invoke(block: ExecutionReceiver.(arg: Arg) -> Unit)
}

typealias ArgumentPendingExecutionReceiver<ExecutionReceiver, Arg> = ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Arg, Any?>

inline fun <ExecutionReceiver, Arg> simpleInvokingPendingExecutionReceiver(
    crossinline handler: (ExecutionReceiver.(Arg) -> Unit) -> Unit,
): ArgumentPendingExecutionReceiver<ExecutionReceiver, Arg> {
    return object : ArgumentPendingExecutionReceiver<ExecutionReceiver, Arg> {
        override fun invoke(block: ExecutionReceiver.(arg: Arg) -> Unit) {
            handler(block)
        }
    }
}

object NullPendingExecutionReceiver : ArgumentPendingExecutionReceiver<Nothing, Nothing> {
    override operator fun invoke(block: Nothing.(arg: Nothing) -> Unit) {}
}

/**
 * @param ArgsExtend a type marker for extension functions
 */
@CommandDslMarker
interface ArgumentDescriptionReceiver<ExecutionReceiver, out ArgsExtend> {
    /**
     * Specifies an argument set with arguments from [parsers] and that can optionally be executed by [exec].
     *
     * Can only be called once on any given instance unless otherwise specified.
     */
    fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, R, ArgsExtend>

    /**
     * Indicates that the first [argsRaw] call in [block] that has parameters that match the input should be invoked.
     */
    fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver, ArgsExtend>.() -> Unit)
}

/**
 * Syntactically equivalent to [ArgumentMultiDescriptionReceiver], but indicates that `args` or [argsRaw] can be
 * called multiple times.
 */
@CommandDslMarker
interface ArgumentMultiDescriptionReceiver<ExecutionReceiver, out ArgsExtend> :
    ArgumentDescriptionReceiver<ExecutionReceiver, ArgsExtend>

@CommandDslMarker
interface SubcommandsArgumentDescriptionReceiver<ExecutionReceiver, out ArgsExtend> :
    ArgumentDescriptionReceiver<ExecutionReceiver, ArgsExtend> {
    fun subcommand(
        name: String,
        block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver, ArgsExtend>.() -> Unit,
    )
}

@CommandDslMarker
interface TopLevelArgumentDescriptionReceiver<ExecutionReceiver, ArgsExtend> :
    ArgumentDescriptionReceiver<ExecutionReceiver, ArgsExtend> {
    fun subcommands(block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver, ArgsExtend>.() -> Unit)
}

// In order to avoid repeating the full name a lot
private typealias ADR<Exec, ArgsExtend> = ArgumentDescriptionReceiver<Exec, ArgsExtend>

private typealias CmdExecBlock<ExecutionReceiver, Args> = ExecutionReceiver.(Args) -> Unit

fun <Rec, Ext> ADR<Rec, Ext>.noArgs(
) = argsRaw<Nothing, Nothing, CommandArgs0>(emptyList()) { CommandArgs0 }

fun <Rec, Ext> ADR<Rec, Ext>.noArgs(
    block: CmdExecBlock<Rec, CommandArgs0>,
) = noArgs().invoke(block)

fun <A, AE, Rec, Ext> ADR<Rec, Ext>.args(
    a: CAP<A, AE>,
) = argsRaw(listOf(a)) { CommandArgs1(it[0] as A) }

fun <A, AE, Rec, Ext> ADR<Rec, Ext>.args(
    a: CAP<A, AE>,
    block: CmdExecBlock<Rec, CommandArgs1<A>>,
) = args(a).invoke(block)

fun <A, AE, B, BE, Rec, Ext> ADR<Rec, Ext>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
) = argsRaw(listOf(a, b)) { CommandArgs2(it[0] as A, it[1] as B) }

fun <A, AE, B, BE, Rec, Ext> ADR<Rec, Ext>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    block: CmdExecBlock<Rec, CommandArgs2<A, B>>,
) = args(a, b).invoke(block)

fun <A, AE, B, BE, C, CE, Rec, Ext> ADR<Rec, Ext>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
) = argsRaw(listOf(a, b, c)) { CommandArgs3(it[0] as A, it[1] as B, it[2] as C) }

fun <A, AE, B, BE, C, CE, Rec, Ext> ADR<Rec, Ext>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
    block: CmdExecBlock<Rec, CommandArgs3<A, B, C>>,
) = args(a, b, c).invoke(block)

fun <A, AE, B, BE, C, CE, D, DE, Rec, Ext> ADR<Rec, Ext>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
    d: CAP<D, DE>,
) = argsRaw(listOf(a, b, c, d)) { CommandArgs4(it[0] as A, it[1] as B, it[2] as C, it[3] as D) }

fun <A, AE, B, BE, C, CE, D, DE, Rec, Ext> ADR<Rec, Ext>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
    d: CAP<D, DE>,
    block: CmdExecBlock<Rec, CommandArgs4<A, B, C, D>>,
) = args(a, b, c, d).invoke(block)
