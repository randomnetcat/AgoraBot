package org.randomcat.agorabot.commands

sealed class CommandArgumentParseResult<out T> {
    object Failure : CommandArgumentParseResult<Nothing>()
    data class FailureWithMessage<out T>(val message: String) : CommandArgumentParseResult<T>()
    data class Success<out T>(val value: T, val remaining: UnparsedCommandArgs) : CommandArgumentParseResult<T>()
}

interface CommandArgumentParser<out T> {
    fun parse(arguments: UnparsedCommandArgs): CommandArgumentParseResult<T>
}

private typealias CAP<T> = CommandArgumentParser<T>

interface CommandDescriptionReceiver {
    fun argsRaw(vararg parsers: CommandArgumentParser<Any?>, exec: CommandExecutionReceiverBlock<List<Any?>>)
}

private typealias CDR = CommandDescriptionReceiver

inline fun <A> CDR.args(
    a: CAP<A>,
    crossinline exec: CERB<CommandArgs1<A>>,
) = argsRaw(a) {
    val args = args
    execWithNewArgs(exec, CommandArgs1(args[0] as A))
}

inline fun <A, B> CDR.args(
    a: CAP<A>,
    b: CAP<B>,
    crossinline exec: CERB<CommandArgs2<A, B>>,
) = argsRaw(a, b) {
    val args = args
    execWithNewArgs(exec, CommandArgs2(args[0] as A, args[1] as B))
}

inline fun <A, B, C> CDR.args(
    a: CAP<A>,
    b: CAP<B>,
    c: CAP<C>,
    crossinline exec: CERB<CommandArgs3<A, B, C>>,
) = argsRaw(a, b, c) {
    val args = args
    execWithNewArgs(exec, CommandArgs3(args[0] as A, args[1] as B, args[2] as C))
}

inline fun <A, B, C, D> CDR.args(
    a: CAP<A>,
    b: CAP<B>,
    c: CAP<C>,
    d: CAP<D>,
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
