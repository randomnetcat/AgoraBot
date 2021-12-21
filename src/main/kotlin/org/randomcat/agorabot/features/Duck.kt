package org.randomcat.agorabot.features

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import org.randomcat.agorabot.AbstractFeature
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.DuckCommand
import org.randomcat.agorabot.listener.Command
import java.util.concurrent.atomic.AtomicReference

private const val END_DUCK_SESSION_CMD = "I fixed it"

private val RESPONSES = listOf(
    "Tell me more.",
    "Okay.",
    "How so?",
    "Really?",
    "Is that correct?",
    "Okay...",
    "I guess.",
    "Alright.",
)

private val duckFeature = object : AbstractFeature() {
    private val listening = AtomicReference<PersistentSet<DuckCommand.ListeningSpec>>(persistentSetOf())

    override fun jdaListeners(): List<Any> {
        return listOf(object {
            @SubscribeEvent
            fun onMessage(event: MessageReceivedEvent) {
                if (!event.message.isFromGuild) return

                val currentListening = listening.get()

                val listeningSpec = DuckCommand.ListeningSpec(
                    channelId = event.channel.id,
                    userId = event.author.id,
                )

                if (currentListening.contains(listeningSpec)) {
                    if (event.message.contentStripped.equals(END_DUCK_SESSION_CMD, ignoreCase = true)) {
                        event.channel.sendMessage("Good for you! I'll be quiet now.").queue()

                        listening.updateAndGet {
                            it.remove(listeningSpec)
                        }
                    } else {
                        event.channel.sendMessage(RESPONSES.random()).queue()
                    }
                }
            }
        })
    }

    override fun commandsInContext(context: FeatureContext): Map<String, Command> {
        return mapOf(
            "duck" to DuckCommand(
                strategy = context.defaultCommandStrategy,
                addListener = { listeningSpec -> listening.updateAndGet { it.add(listeningSpec) } },
            ),
        )
    }
}

@FeatureSourceFactory
fun duckFactory() = FeatureSource.ofConstant("duck", duckFeature)
