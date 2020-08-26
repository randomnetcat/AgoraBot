package org.randomcat.agorabot.commands

sealed class CommandArgumentParseResult<out T, out E> {
    data class Failure<out E>(
        val error: E,
    ) : CommandArgumentParseResult<Nothing, E>()

    data class Success<out T>(
        val value: T,
        val remaining: UnparsedCommandArgs,
    ) : CommandArgumentParseResult<T, Nothing>()
}

interface CommandArgumentParser<out T, out E> {
    fun parse(arguments: UnparsedCommandArgs): CommandArgumentParseResult<T, E>
}

private typealias CAP<T, E> = CommandArgumentParser<T, E>

interface ArgumentDescriptionReceiver {
    fun <T, E> argsRaw(vararg parsers: CommandArgumentParser<T, E>, exec: CommandExecutionReceiverBlock<List<T>>)
}

private typealias CDR = ArgumentDescriptionReceiver

inline fun <A, AE> CDR.args(
    a: CAP<A, AE>,
    crossinline exec: CERB<CommandArgs1<A>>,
) = argsRaw(a) {
    val args = args
    execWithNewArgs(exec, CommandArgs1(args[0] as A))
}

inline fun <A, AE, B, BE> CDR.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    crossinline exec: CERB<CommandArgs2<A, B>>,
) = argsRaw(a, b) {
    val args = args
    execWithNewArgs(exec, CommandArgs2(args[0] as A, args[1] as B))
}

inline fun <A, AE, B, BE, C, CE> CDR.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
    crossinline exec: CERB<CommandArgs3<A, B, C>>,
) = argsRaw(a, b, c) {
    val args = args
    execWithNewArgs(exec, CommandArgs3(args[0] as A, args[1] as B, args[2] as C))
}

inline fun <A, AE, B, BE, C, CE, D, DE> CDR.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
    d: CAP<D, DE>,
    crossinline exec: CERB<CommandArgs4<A, B, C, D>>,
) = argsRaw(a, b, c, d) {
    val args = args
    execWithNewArgs(exec, CommandArgs4(args[0] as A, args[1] as B, args[2] as C, args[3] as D))
}

interface CommandExecutionReceiver<out Args> {
    val args: Args
}

typealias CommandExecutionReceiverBlock<Args> = CommandExecutionReceiver<Args>.() -> Unit
private typealias CERB<Args> = CommandExecutionReceiverBlock<Args>

@PublishedApi
internal inline fun <OrigArgs, NewArgs> CommandExecutionReceiver<OrigArgs>.execWithNewArgs(
    exec: CERB<NewArgs>,
    newArgs: NewArgs
) {
    exec(__SetArgsCER(this, newArgs))
}

@PublishedApi
internal class __SetArgsCER<OrigArgs, NewArgs>(
    @Suppress("Unused") // Reserved for future use
    private val impl: CommandExecutionReceiver<OrigArgs>,
    override val args: NewArgs
) : CommandExecutionReceiver<NewArgs>
