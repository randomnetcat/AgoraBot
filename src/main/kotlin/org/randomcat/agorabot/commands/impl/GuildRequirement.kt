package org.randomcat.agorabot.commands.impl

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.config.GuildState
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.tryRespondWithText

private const val NEED_GUILD_ERROR_MSG = "This command can only be run in a Guild."

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

object GuildStateStrategyTag

interface GuildStateStrategy {
    fun guildStateFor(guildId: String): GuildState
}

interface GuildStateRequirement {
    companion object {
        fun create(context: BaseCommandContext): RequirementResult<GuildStateRequirement> {
            val strategy = context.tryFindDependency(GuildStateStrategyTag) as GuildStateStrategy

            return RequirementResult.Success(
                object : GuildStateRequirement {
                    override fun guildStateFor(guildId: String): GuildState {
                        return strategy.guildStateFor(guildId)
                    }
                }
            )
        }
    }

    fun guildStateFor(guildId: String): GuildState
}

fun BaseCommandExecutionReceiverRequiring<GuildStateRequirement>.guildStateFor(guildId: String) =
    requirement().guildStateFor(guildId)

val <R> BaseCommandExecutionReceiverRequiring<R>.currentGuildState where R : GuildStateRequirement, R : BaseCommandGuildRequirement
    get() = guildStateFor(this.currentGuildId)

interface ExtendedGuildRequirement : BaseCommandGuildRequirement, ExtendedDiscordRequirement, GuildStateRequirement

object InGuild : RequirementSet<BaseCommandContext, ExtendedGuildRequirement> {
    override fun create(context: BaseCommandContext): RequirementResult<ExtendedGuildRequirement> {
        return mergeRequirements(
            context,
            InGuildSimple::create,
            InDiscord::create,
            GuildStateRequirement::create,
        ) {
            object :
                ExtendedGuildRequirement,
                ExtendedDiscordRequirement by (it[1] as ExtendedDiscordRequirement),
                GuildStateRequirement by (it[2] as GuildStateRequirement) {}
        }
    }
}
