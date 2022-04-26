package org.randomcat.agorabot.commands.base.help

import org.randomcat.agorabot.commands.base.*

interface HelpReceiver {
    fun acceptHelp(helpMessage: String)
}

fun <Ctx> TopLevelArgumentDescriptionReceiver<Ctx>.help(helpMessage: String) {
    if (this is HelpReceiver) acceptHelp(helpMessage)
}

fun <Ctx> SubcommandsArgumentDescriptionReceiver<Ctx>.help(helpMessage: String) {
    if (this is HelpReceiver) acceptHelp(helpMessage)
}

fun <Arg, Invocation : PendingInvocation<Arg>> Invocation.help(helpMessage: String): Invocation {
    if (this is HelpReceiver) acceptHelp(helpMessage)
    return this
}

inline fun <Context : BaseCommandContext, Receiver, Arg> PendingInvocation<ContextReceiverArg<Context, Receiver, Arg>>.help(
    helpMessage: String,
    crossinline block: suspend Receiver.(Arg) -> Unit,
) {
    return help(helpMessage).execute { block(it.receiver, it.arg) }
}
