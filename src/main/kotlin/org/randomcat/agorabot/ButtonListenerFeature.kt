package org.randomcat.agorabot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.randomcat.agorabot.buttons.*
import org.randomcat.agorabot.buttons.feature.ButtonHandlerMapTag
import org.randomcat.agorabot.buttons.feature.ButtonRequestDataMapTag
import org.randomcat.agorabot.features.CoroutineScopeTag
import org.randomcat.agorabot.listener.BotButtonListener
import java.time.Instant

private val coroutineScopeDep = FeatureDependency.Single(CoroutineScopeTag)
private val buttonRequestDataMapDep = FeatureDependency.Single(ButtonRequestDataMapTag)
private val buttonHandlerMapDep = FeatureDependency.Single(ButtonHandlerMapTag)

private fun makeListener(
    coroutineScope: CoroutineScope,
    buttonRequestDataMap: ButtonRequestDataMap,
    buttonHandlerMap: ButtonHandlerMap,
): BotButtonListener {
    return BotButtonListener { event ->
        val id = ButtonRequestId(event.componentId)

        val requestDescriptor = buttonRequestDataMap.tryGetRequestById(
            id = id,
            timeForExpirationCheck = Instant.now(),
        )

        if (requestDescriptor != null) {

            @Suppress("UNCHECKED_CAST")
            val handler =
                buttonHandlerMap.tryGetHandler(requestDescriptor::class) as ButtonHandler<ButtonRequestDescriptor>?

            if (handler != null) {
                // Unambiguous names
                val theEvent = event
                val theDataMap = buttonRequestDataMap

                coroutineScope.launch {
                    handler(
                        object : ButtonHandlerContext {
                            override val event: ButtonInteractionEvent
                                get() = theEvent

                            override val buttonRequestDataMap: ButtonRequestDataMap
                                get() = theDataMap
                        },
                        requestDescriptor,
                    )
                }
            } else {
                event.reply("Unknown button type. That feature may be disabled.").setEphemeral(true).queue()
            }
        } else {
            event.reply("Unknown button request. That button may have expired.").setEphemeral(true).queue()
        }
    }
}

@FeatureSourceFactory
fun botButtonListenerSource() = FeatureSource.NoConfig.ofCloseable(
    name = "bot_button_listener",
    element = JdaListenerTag,
    dependencies = listOf(coroutineScopeDep, buttonRequestDataMapDep, buttonHandlerMapDep),
    create = { context ->
        val coroutineScope = context[coroutineScopeDep]
        val buttonRequestDataMap = context[buttonRequestDataMapDep]
        val buttonHandlerMap = context[buttonHandlerMapDep]

        makeListener(
            coroutineScope = coroutineScope,
            buttonRequestDataMap = buttonRequestDataMap,
            buttonHandlerMap = buttonHandlerMap,
        )
    },
    close = { it.stop() },
)
