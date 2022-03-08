package org.randomcat.agorabot.commands.base.requirements.discord_ext

import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.buttons.ButtonRequestId
import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.requirements.discord.BaseCommandDiscordRequirement
import org.randomcat.agorabot.commands.base.requirements.discord.InDiscordSimple
import org.randomcat.agorabot.commands.base.requirements.permissions.PermissionsAccessRequirement
import java.time.Duration
import java.time.Instant

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
            ButtonsRequirement.Companion::create,
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
