package org.randomcat.agorabot.commands.base.requirements.discord_ext

import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.requirements.discord.BaseCommandGuildRequirement
import org.randomcat.agorabot.commands.base.requirements.discord.InGuildSimple
import org.randomcat.agorabot.commands.base.requirements.discord.currentGuildId
import org.randomcat.agorabot.guild_state.GuildState

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
            GuildStateRequirement.Companion::create,
        ) {
            object :
                ExtendedGuildRequirement,
                ExtendedDiscordRequirement by (it[1] as ExtendedDiscordRequirement),
                GuildStateRequirement by (it[2] as GuildStateRequirement) {}
        }
    }
}
