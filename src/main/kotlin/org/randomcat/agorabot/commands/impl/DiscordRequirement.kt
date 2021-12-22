package org.randomcat.agorabot.commands.impl

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.buttons.ButtonRequestId
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.tryRespondWithText
import java.time.Duration
import java.time.Instant

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

interface ButtonsRequirement {
    companion object {
        fun create(context: BaseCommandContext): RequirementResult<ButtonsRequirement> {
            val strategy = context.tryFindDependency(ButtonsStrategyTag) as ButtonsStrategy

            return RequirementResult.Success(object : ButtonsRequirement {
                override fun newButtonId(descriptor: ButtonRequestDescriptor, expiryDuration: Duration): String {
                    return strategy
                        .storeButtonRequestAndGetId(
                            descriptor = descriptor,
                            expiry = Instant.now().plusSeconds(expiryDuration.toSeconds()),
                        )
                        .raw
                }
            })
        }
    }

    fun newButtonId(descriptor: ButtonRequestDescriptor, expiryDuration: java.time.Duration): String
}

object ButtonsStrategyTag

interface ButtonsStrategy {
    fun storeButtonRequestAndGetId(descriptor: ButtonRequestDescriptor, expiry: Instant): ButtonRequestId
}

fun BaseCommandExecutionReceiverRequiring<ButtonsRequirement>.newButtonId(
    descriptor: ButtonRequestDescriptor,
    expiryDuration: java.time.Duration,
) = requirement().newButtonId(descriptor, expiryDuration)

interface DiscordExtensionPartsRequirement : ButtonsRequirement, PermissionsAccessRequirement
interface ExtendedDiscordRequirement : BaseCommandDiscordRequirement, DiscordExtensionPartsRequirement

object InDiscord : RequirementSet<BaseCommandContext, ExtendedDiscordRequirement> {
    override fun create(context: BaseCommandContext): RequirementResult<ExtendedDiscordRequirement> {
        return mergeRequirements(
            context,
            InDiscordSimple::create,
            ButtonsRequirement::create,
            PermissionsAccessRequirement::create,
        ) {
            object :
                ExtendedDiscordRequirement,
                BaseCommandDiscordRequirement by (it[0] as BaseCommandDiscordRequirement),
                ButtonsRequirement by (it[1] as ButtonsRequirement),
                PermissionsAccessRequirement by (it[2] as PermissionsAccessRequirement) {}
        }
    }
}
