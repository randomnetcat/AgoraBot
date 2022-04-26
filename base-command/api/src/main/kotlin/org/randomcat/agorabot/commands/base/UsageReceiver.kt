package org.randomcat.agorabot.commands.base

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import org.randomcat.agorabot.commands.base.help.BaseCommandArgumentSet
import org.randomcat.agorabot.commands.base.help.BaseCommandUsageModel
import org.randomcat.agorabot.commands.base.help.HelpReceiver


private fun <T, E> delayedArgumentSetWithHelp(parsers: List<CommandArgumentParser<T, E>>): Pair<PendingInvocation<Nothing>, () -> BaseCommandArgumentSet> {
    val argumentUsages = parsers.map { it.usage() }.toImmutableList()
    var argumentHelp: String? = null

    return object : PendingInvocation<Nothing> by PendingInvocation.neverExecute(), HelpReceiver {
        override fun acceptHelp(helpMessage: String) {
            check(argumentHelp == null)
            argumentHelp = helpMessage
        }

        override fun <NewArg> interpose(interposition: suspend (arg: Nothing, block: suspend (NewArg) -> Unit) -> Unit): PendingInvocation<NewArg> {
            return this
        }
    } to {
        BaseCommandArgumentSet(
            arguments = argumentUsages,
            help = argumentHelp,
        )
    }
}

private class MatchFirstUsageArgumentDescriptionReceiver : ArgumentMultiDescriptionReceiver<Nothing> {
    private val options = mutableListOf<() -> BaseCommandArgumentSet>()

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): PendingInvocation<Nothing> {
        val (invocation, argumentSetFunction) = delayedArgumentSetWithHelp(parsers)
        options.add(argumentSetFunction)
        return invocation
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<Nothing>.() -> Unit) {
        block()
    }

    fun usage(): BaseCommandUsageModel {
        return BaseCommandUsageModel.MatchArguments(options = options.map { it() }.toImmutableList())
    }
}

private class UsageSubcommandsArgumentDescriptionReceiver : SubcommandsArgumentDescriptionReceiver<Nothing>,
    HelpReceiver {
    private val checker = SubcommandsReceiverChecker()
    private var rawUsage: (() -> BaseCommandUsageModel)? = null
    private var overallHelp: String? = null

    override fun acceptHelp(helpMessage: String) {
        check(overallHelp == null)
        overallHelp = helpMessage
    }

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): PendingInvocation<Nothing> {
        checker.checkArgsRaw()
        val (invocation, argumentSetFunction) = delayedArgumentSetWithHelp(parsers)
        rawUsage = { BaseCommandUsageModel.MatchArguments(persistentListOf(argumentSetFunction())) }
        return invocation
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<Nothing>.() -> Unit) {
        checker.checkMatchFirst()
        rawUsage = { MatchFirstUsageArgumentDescriptionReceiver().apply(block).usage() }
    }

    override fun subcommand(
        name: String,
        block: SubcommandsArgumentDescriptionReceiver<Nothing>.() -> Unit,
    ) {
        checker.checkSubcommand(subcommand = name)

        val oldUsage = rawUsage ?: { BaseCommandUsageModel.Subcommands(subcommandsMap = persistentMapOf()) }
        val subOptions = UsageSubcommandsArgumentDescriptionReceiver().apply(block).usage()

        rawUsage = {
            (oldUsage() as BaseCommandUsageModel.Subcommands).let {
                it.copy(subcommandsMap = it.subcommandsMap.toPersistentMap().put(name, subOptions))
            }
        }
    }

    fun usage(): BaseCommandUsageModel {
        return when (val oldModel = checkNotNull(rawUsage)()) {
            is BaseCommandUsageModel.Subcommands -> oldModel.copy(overallHelp = overallHelp)
            is BaseCommandUsageModel.MatchArguments -> oldModel.copy(overallHelp = overallHelp)
        }
    }
}

class UsageTopLevelArgumentDescriptionReceiver : TopLevelArgumentDescriptionReceiver<Nothing>, HelpReceiver {
    private var overallHelp: String? = null
    private var rawUsage: (() -> BaseCommandUsageModel)? = null

    override fun acceptHelp(helpMessage: String) {
        check(overallHelp == null)
        overallHelp = helpMessage
    }

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): PendingInvocation<Nothing> {
        check(rawUsage == null)

        val (invocation, argumentSetFunction) = delayedArgumentSetWithHelp(parsers)
        rawUsage = { BaseCommandUsageModel.MatchArguments(persistentListOf(argumentSetFunction())) }
        return invocation
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<Nothing>.() -> Unit) {
        check(rawUsage == null)
        rawUsage = { MatchFirstUsageArgumentDescriptionReceiver().apply(block).usage() }
    }

    override fun subcommands(block: SubcommandsArgumentDescriptionReceiver<Nothing>.() -> Unit) {
        check(rawUsage == null)
        rawUsage = { UsageSubcommandsArgumentDescriptionReceiver().also(block).usage() }
    }

    fun usage(): BaseCommandUsageModel = when (val oldModel = checkNotNull(rawUsage)()) {
        is BaseCommandUsageModel.Subcommands -> oldModel.copy(overallHelp = overallHelp)
        is BaseCommandUsageModel.MatchArguments -> oldModel.copy(overallHelp = overallHelp)
    }
}
