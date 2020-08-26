@file:Suppress("UNCHECKED_CAST", "USELESS_CAST")

package org.randomcat.agorabot.commands

private typealias CAP<T, E> = CommandArgumentParser<T, E>

interface ArgumentDescriptionReceiver<ExecutionReceiver> {
    /**
     * Specifies an argument set with arguments from [parsers] and that can optionally be executed by [exec].
     *
     * Can only be called once on any given instance unless otherwise specified.
     */
    fun <T, E> argsRaw(vararg parsers: CommandArgumentParser<T, E>, exec: ExecutionReceiver.(args: List<T>) -> Unit)
}

/**
 * Syntactically equivalent to [ArgumentMultiDescriptionReceiver], but indicates that `args` or [argsRaw] can be
 * called multiple times.
 */
interface ArgumentMultiDescriptionReceiver<ExecutionReceiver> : ArgumentDescriptionReceiver<ExecutionReceiver>

private typealias CDR<Exec> = ArgumentDescriptionReceiver<Exec>

private typealias CmdExecBlock<ExecutionReceiver, Args> = ExecutionReceiver.(Args) -> Unit

inline fun <A, AE, Rec> CDR<Rec>.args(
    a: CAP<A, AE>,
    crossinline block: CmdExecBlock<Rec, CommandArgs1<A>>,
) = argsRaw(a) { args ->
    block(CommandArgs1(args[0] as A))
}

inline fun <A, AE, B, BE, Rec> CDR<Rec>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    crossinline block: CmdExecBlock<Rec, CommandArgs2<A, B>>,
) = argsRaw(a, b) { args ->
    block(CommandArgs2(args[0] as A, args[1] as B))
}

inline fun <A, AE, B, BE, C, CE, Rec> CDR<Rec>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
    crossinline block: CmdExecBlock<Rec, CommandArgs3<A, B, C>>,
) = argsRaw(a, b, c) { args ->
    block(CommandArgs3(args[0] as A, args[1] as B, args[2] as C))
}

inline fun <A, AE, B, BE, C, CE, D, DE, Rec> CDR<Rec>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
    d: CAP<D, DE>,
    crossinline block: CmdExecBlock<Rec, CommandArgs4<A, B, C, D>>,
) = argsRaw(a, b, c, d) { args ->
    block(CommandArgs4(args[0] as A, args[1] as B, args[2] as C, args[3] as D))
}

interface CommandExecutionReceiver<out Args> {
    val args: Args
}
