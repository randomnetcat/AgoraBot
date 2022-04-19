package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.requirements.discord.BaseCommandDiscordRequirement
import org.randomcat.agorabot.commands.base.requirements.discord.InDiscordSimple
import org.randomcat.agorabot.commands.base.requirements.discord.currentChannelId
import org.randomcat.agorabot.commands.base.requirements.haltable.BaseCommandHaltableRequirement
import org.randomcat.agorabot.commands.base.requirements.haltable.Haltable
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.permissions.BotScope

private interface InDiscordHaltableRequirement : BaseCommandDiscordRequirement, BaseCommandHaltableRequirement

private object InDiscordHaltable : RequirementSet<BaseCommandContext, InDiscordHaltableRequirement> {
    override fun create(context: BaseCommandContext): RequirementResult<InDiscordHaltableRequirement> {
        return mergeRequirements(context, InDiscordSimple::create, Haltable::create) {
            object : InDiscordHaltableRequirement,
                BaseCommandDiscordRequirement by it[0] as BaseCommandDiscordRequirement,
                BaseCommandHaltableRequirement by it[1] as BaseCommandHaltableRequirement {}
        }
    }
}

class StopCommand(
    strategy: BaseCommandStrategy,
    private val writeChannelFun: (channelId: String) -> Unit,
) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        noArgs().requires(InDiscordHaltable).permissions(BotScope.admin()) {
            try {
                respond("STOP")
                writeChannelFun(currentChannelId)
            } finally {
                requirement().scheduleHalt()
            }
        }
    }
}
