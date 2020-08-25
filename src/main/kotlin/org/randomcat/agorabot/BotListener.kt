package org.randomcat.agorabot

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent

class BotListener(private val parser: CommandParser, private val registry: CommandRegistry) {
    @SubscribeEvent
    fun onMessage(event: MessageReceivedEvent) {
        return when (val parseResult = parser.parse(event)) {
            is CommandParseResult.Invocation -> registry.invokeCommand(event, parseResult.invocation)
            is CommandParseResult.Message -> respond(event, parseResult.message)
            is CommandParseResult.Ignore -> {
            }
        }
    }

    private fun respond(event: MessageReceivedEvent, response: String) {
        event.channel.sendMessage(response).queue()
    }
}
