package org.randomcat.agorabot.commands.impl

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.commands.base.BaseCommandContext
import org.randomcat.agorabot.commands.base.RequirementResult
import org.randomcat.agorabot.commands.base.RequirementSet
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.tryRespondWithText

interface BaseCommandDiscordRequirement {
    val currentMessageEvent: MessageReceivedEvent
}

object InDiscordSimple : RequirementSet<BaseCommandContext, BaseCommandDiscordRequirement> {
    override fun create(context: BaseCommandContext): RequirementResult<BaseCommandDiscordRequirement> {
        val source = context.source

        if (source !is CommandEventSource.Discord) {
            source.tryRespondWithText("This command can only be run on Discord.")
            return RequirementResult.Failure
        }

        return RequirementResult.Success(object : BaseCommandDiscordRequirement {
            override val currentMessageEvent: MessageReceivedEvent = source.event
        })
    }
}
