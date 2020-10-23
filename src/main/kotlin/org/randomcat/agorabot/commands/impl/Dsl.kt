@file:Suppress("UNCHECKED_CAST", "USELESS_CAST")

package org.randomcat.agorabot.commands.impl

private typealias CAP<T, E> = CommandArgumentParser<T, E>

@DslMarker
annotation class CommandDslMarker

@CommandDslMarker
interface ArgumentDescriptionReceiver<ExecutionReceiver> {
    /**
     * Specifies an argument set with arguments from [parsers] and that can optionally be executed by [exec].
     *
     * Can only be called once on any given instance unless otherwise specified.
     */
    fun <T, E> argsRaw(vararg parsers: CommandArgumentParser<T, E>, exec: ExecutionReceiver.(args: List<T>) -> Unit)

    /**
     * Indicates that the first [argsRaw] call in [block] that has parameters that match the input should be invoked.
     */
    fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit)
}

/**
 * Syntactically equivalent to [ArgumentMultiDescriptionReceiver], but indicates that `args` or [argsRaw] can be
 * called multiple times.
 */
@CommandDslMarker
interface ArgumentMultiDescriptionReceiver<ExecutionReceiver> : ArgumentDescriptionReceiver<ExecutionReceiver>

@CommandDslMarker
interface SubcommandsArgumentDescriptionReceiver<ExecutionReceiver> : ArgumentDescriptionReceiver<ExecutionReceiver> {
    fun subcommand(name: String, block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver>.() -> Unit)
}

@CommandDslMarker
interface TopLevelArgumentDescriptionReceiver<ExecutionReceiver> : ArgumentDescriptionReceiver<ExecutionReceiver> {
    fun subcommands(block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver>.() -> Unit)
}

// In order to avoid repeating the full name a lot
private typealias ADR<Exec> = ArgumentDescriptionReceiver<Exec>

private typealias CmdExecBlock<ExecutionReceiver, Args> = ExecutionReceiver.(Args) -> Unit

inline fun <Rec> ADR<Rec>.noArgs(
    crossinline block: CmdExecBlock<Rec, CommandArgs0>,
) = argsRaw<Nothing, Nothing>() {
    block(CommandArgs0)
}

inline fun <A, AE, Rec> ADR<Rec>.args(
    a: CAP<A, AE>,
    crossinline block: CmdExecBlock<Rec, CommandArgs1<A>>,
) = argsRaw(a) { args ->
    block(CommandArgs1(args[0] as A))
}

inline fun <A, AE, B, BE, Rec> ADR<Rec>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    crossinline block: CmdExecBlock<Rec, CommandArgs2<A, B>>,
) = argsRaw(a, b) { args ->
    block(CommandArgs2(args[0] as A, args[1] as B))
}

inline fun <A, AE, B, BE, C, CE, Rec> ADR<Rec>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
    crossinline block: CmdExecBlock<Rec, CommandArgs3<A, B, C>>,
) = argsRaw(a, b, c) { args ->
    block(CommandArgs3(args[0] as A, args[1] as B, args[2] as C))
}

inline fun <A, AE, B, BE, C, CE, D, DE, Rec> ADR<Rec>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
    d: CAP<D, DE>,
    crossinline block: CmdExecBlock<Rec, CommandArgs4<A, B, C, D>>,
) = argsRaw(a, b, c, d) { args ->
    block(CommandArgs4(args[0] as A, args[1] as B, args[2] as C, args[3] as D))
}
