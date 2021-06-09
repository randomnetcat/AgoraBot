package org.randomcat.agorabot.commands

import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.commands.impl.*
import java.time.Duration

class ButtonTestCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    @Serializable
    object SuccessRequest : ButtonRequestDescriptor

    @Serializable
    object FailureRequest : ButtonRequestDescriptor

    override fun BaseCommandImplReceiver.impl() {
        noArgs().requiresDiscord() {
            val successId = newButtonId(
                descriptor = SuccessRequest,
                expiryDuration = Duration.ofMinutes(1),
            )

            val failureId = newButtonId(
                descriptor = FailureRequest,
                expiryDuration = Duration.ofMinutes(1),
            )

            MessageBuilder("Testing").setActionRows(
                ActionRow.of(
                    Button.success(successId, "Success"),
                    Button.danger(failureId, "Error"),
                ),
            ).build().let { respond(it) }
        }
    }
}
