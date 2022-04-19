package org.randomcat.agorabot.commands.base.requirements.haltable

import org.randomcat.agorabot.commands.base.BaseCommandContext
import org.randomcat.agorabot.commands.base.RequirementResult
import org.randomcat.agorabot.commands.base.RequirementSet

interface HaltProvider {
    fun scheduleHalt()
}

object HaltProviderTag

interface BaseCommandHaltableRequirement {
    fun scheduleHalt()
}

object Haltable : RequirementSet<BaseCommandContext, BaseCommandHaltableRequirement> {
    override fun create(context: BaseCommandContext): RequirementResult<BaseCommandHaltableRequirement> {
        val provider = context.tryFindDependency(HaltProviderTag) as HaltProvider

        return RequirementResult.Success(object : BaseCommandHaltableRequirement {
            override fun scheduleHalt() {
                provider.scheduleHalt()
            }
        })
    }
}
