package org.randomcat.agorabot.commands.base.requirements.discord

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.commands.base.BaseCommandContext
import org.randomcat.agorabot.commands.base.RequirementResult
import org.randomcat.agorabot.commands.base.RequirementSet
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.tryRespondWithText

private const val NEED_GUILD_ERROR_MSG = "This command can only be run in a Guild."

interface BaseCommandGuildRequirement : BaseCommandDiscordRequirement

object InGuildSimple : RequirementSet<BaseCommandContext, BaseCommandGuildRequirement> {
    override fun create(context: BaseCommandContext): RequirementResult<BaseCommandGuildRequirement> {
        val source = context.source

        if (source !is CommandEventSource.Discord || !source.event.isFromGuild) {
            source.tryRespondWithText(NEED_GUILD_ERROR_MSG)
            return RequirementResult.Failure
        }

        return RequirementResult.Success(object : BaseCommandGuildRequirement {
            override val currentMessageEvent: MessageReceivedEvent = source.event
        })
    }
}
