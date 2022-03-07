@file:Suppress("unused")

package org.randomcat.agorabot.listener

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent

class BotListener(private val parser: CommandParser, private val registry: CommandRegistry) {
    @SubscribeEvent
    fun onMessage(event: MessageReceivedEvent) {
        if (event.author.id == event.jda.selfUser.id) return

        // Webhooks shouldn't be reacted to, and they cause problems later on because the event can't return a
        // Member object if the message is from a webhook.
        if (event.message.isWebhookMessage) return

        val source = CommandEventSource.Discord(event)

        return when (val parseResult = parser.parse(source)) {
            is CommandParseResult.Invocation -> {
                registry.invokeCommand(source, parseResult.invocation)
            }

            is CommandParseResult.Ignore -> {
            }
        }
    }

    private fun respond(event: MessageReceivedEvent, response: String) {
        event.channel.sendMessage(response).queue()
    }
}

class BotEmoteListener(private val handler: (MessageReactionAddEvent) -> Unit) {
    @SubscribeEvent
    fun onEmoteAdded(event: MessageReactionAddEvent) {
        handler(event)
    }
}

class BotButtonListener(private val handler: (event: ButtonInteractionEvent) -> Unit) {
    @SubscribeEvent
    fun onButtonClick(event: ButtonInteractionEvent) {
        handler(event)
    }
}
