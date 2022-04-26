package org.randomcat.agorabot.commands.impl

import org.randomcat.agorabot.commands.base.BaseCommandArgumentStrategy
import org.randomcat.agorabot.commands.base.NO_ARGUMENTS
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.listener.tryRespondWithText

object BaseCommandDefaultArgumentStrategy : BaseCommandArgumentStrategy {
    override fun sendArgumentErrorResponse(
        source: CommandEventSource,
        invocation: CommandInvocation,
        errorMessage: String,
        usage: String,
    ) {
        source.tryRespondWithText("$errorMessage. Usage: ${usage.ifBlank { NO_ARGUMENTS }}")
    }
}
