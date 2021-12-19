@file:Suppress("UNCHECKED_CAST", "USELESS_CAST")

package org.randomcat.agorabot.commands.impl

import kotlin.reflect.KClass

private typealias CAP<T, E> = CommandArgumentParser<T, E>

@DslMarker
annotation class CommandDslMarker

interface PendingInvocation<out Arg> {
    fun execute(block: (Arg) -> Unit)
}

interface WithContext<out Context> {
    val context: Context
}

data class ContextAndArg<out Context, out Arg>(
    override val context: Context,
    val arg: Arg,
) : WithContext<Context>

interface CommandDependencyProvider {
    fun tryFindDependency(markerClass: KClass<*>): Any?
}

sealed class PrependResult {
    object ContinueExecution : PrependResult()
    object StopExecution : PrependResult()
}

inline fun <Arg> PendingInvocation<Arg>.prepend(crossinline prependBlock: (Arg) -> PrependResult): PendingInvocation<Arg> {
    val baseInvocation = this

    return object : PendingInvocation<Arg> {
        override fun execute(block: (Arg) -> Unit) {
            baseInvocation.execute { arg ->
                @Suppress("UNUSED_VARIABLE")
                val ensureExhaustive = when (val prependResult = prependBlock(arg)) {
                    is PrependResult.ContinueExecution -> {
                        block(arg)
                    }

                    is PrependResult.StopExecution -> {}
                }
            }
        }
    }
}

sealed class PrependTransformResult<out NewArg> {
    data class ContinueExecution<NewArg>(val newArg: NewArg) : PrependTransformResult<NewArg>()
    object StopExecution : PrependTransformResult<Nothing>()
}

inline fun <Arg, NewArg> PendingInvocation<Arg>.prependTransform(crossinline prependBlock: (Arg) -> PrependTransformResult<NewArg>): PendingInvocation<NewArg> {
    val baseInvocation = this

    return object : PendingInvocation<NewArg> {
        override fun execute(block: (NewArg) -> Unit) {
            baseInvocation.execute { arg ->
                @Suppress("UNUSED_VARIABLE")
                val ensureExhaustive = when (val prependResult = prependBlock(arg)) {
                    is PrependTransformResult.ContinueExecution -> {
                        block(prependResult.newArg)
                    }

                    is PrependTransformResult.StopExecution -> {}
                }
            }
        }
    }
}

inline fun <Arg, NewArg> PendingInvocation<Arg>.prependAlwaysTransform(crossinline transform: (Arg) -> NewArg): PendingInvocation<NewArg> {
    val baseInvocation = this

    return object : PendingInvocation<NewArg> {
        override fun execute(block: (NewArg) -> Unit) {
            return baseInvocation.execute { block(transform(it)) }
        }
    }
}

/**
 * @param ArgsExtend a type marker for extension functions
 */
@CommandDslMarker
interface ArgumentDescriptionReceiver<out Context> {
    /**
     * Specifies an argument set with arguments from [parsers] and that can optionally be executed by [exec].
     *
     * Can only be called once on any given instance unless otherwise specified.
     */
    fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): PendingInvocation<ContextAndArg<Context, R>>

    /**
     * Indicates that the first [argsRaw] call in [block] that has parameters that match the input should be invoked.
     */
    fun matchFirst(block: ArgumentMultiDescriptionReceiver<Context>.() -> Unit)
}

/**
 * Syntactically equivalent to [ArgumentMultiDescriptionReceiver], but indicates that `args` or [argsRaw] can be
 * called multiple times.
 */
@CommandDslMarker
interface ArgumentMultiDescriptionReceiver<out Context> : ArgumentDescriptionReceiver<Context>

@CommandDslMarker
interface SubcommandsArgumentDescriptionReceiver<out Context> : ArgumentDescriptionReceiver<Context> {
    fun subcommand(
        name: String,
        block: SubcommandsArgumentDescriptionReceiver<Context>.() -> Unit,
    )
}

@CommandDslMarker
interface TopLevelArgumentDescriptionReceiver<out Context> : ArgumentDescriptionReceiver<Context> {
    fun subcommands(block: SubcommandsArgumentDescriptionReceiver<Context>.() -> Unit)
}

// In order to avoid repeating the full name a lot
private typealias ADR<Ctx> = ArgumentDescriptionReceiver<Ctx>

fun <Ctx> ADR<Ctx>.noArgs(
) = argsRaw<Nothing, Nothing, CommandArgs0>(emptyList()) { CommandArgs0 }

fun <A, AE, Ctx> ADR<Ctx>.args(
    a: CAP<A, AE>,
) = argsRaw(listOf(a)) { CommandArgs1(it[0] as A) }

fun <A, AE, B, BE, Ctx> ADR<Ctx>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
) = argsRaw(listOf(a, b)) { CommandArgs2(it[0] as A, it[1] as B) }

fun <A, AE, B, BE, C, CE, Ctx> ADR<Ctx>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
) = argsRaw(listOf(a, b, c)) { CommandArgs3(it[0] as A, it[1] as B, it[2] as C) }

fun <A, AE, B, BE, C, CE, D, DE, Ctx> ADR<Ctx>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
    d: CAP<D, DE>,
) = argsRaw(listOf(a, b, c, d)) { CommandArgs4(it[0] as A, it[1] as B, it[2] as C, it[3] as D) }

fun <Ctx, R> ADR<ContextAndReceiver<Ctx, R>>.noArgs(
    block: R.(CommandArgs0) -> Unit,
) = noArgs().execute { block(it.context.receiver, it.arg) }

fun <A, AE, Ctx, R> ADR<ContextAndReceiver<Ctx, R>>.args(
    a: CAP<A, AE>,
    block: R.(CommandArgs1<A>) -> Unit,
) = args(a).execute { block(it.context.receiver, it.arg) }

fun <A, AE, B, BE, Ctx, R> ADR<ContextAndReceiver<Ctx, R>>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    block: R.(CommandArgs2<A, B>) -> Unit,
) = args(a, b).execute { block(it.context.receiver, it.arg) }

fun <A, AE, B, BE, C, CE, Ctx, R> ADR<ContextAndReceiver<Ctx, R>>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
    block: R.(CommandArgs3<A, B, C>) -> Unit,
) = args(a, b, c).execute { block(it.context.receiver, it.arg) }

fun <A, AE, B, BE, C, CE, D, DE, Ctx, R> ADR<ContextAndReceiver<Ctx, R>>.args(
    a: CAP<A, AE>,
    b: CAP<B, BE>,
    c: CAP<C, CE>,
    d: CAP<D, DE>,
    block: R.(CommandArgs4<A, B, C, D>) -> Unit,
) = args(a, b, c, d).execute { block(it.context.receiver, it.arg) }
