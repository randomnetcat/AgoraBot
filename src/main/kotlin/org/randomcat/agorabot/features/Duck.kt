package org.randomcat.agorabot.features

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.DuckCommand
import org.randomcat.agorabot.commands.impl.BaseCommandStrategyTag
import org.randomcat.agorabot.util.userFacingRandom
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

private val strategyDep = FeatureDependency.Single(BaseCommandStrategyTag)

@FeatureSourceFactory
fun duckFactory() = object : FeatureSource.NoConfig {
    override val featureName: String
        get() = "duck"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(strategyDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(BotCommandListTag, JdaListenerTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val listening = AtomicReference<PersistentSet<DuckCommand.ListeningSpec>>(persistentSetOf())
        val strategy = context[strategyDep]

        val commands = mapOf(
            "duck" to DuckCommand(
                strategy = strategy,
                addListener = { listeningSpec -> listening.updateAndGet { it.add(listeningSpec) } },
            ),
        )

        val listener = object {
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
                        event.channel.sendMessage(RESPONSES.random(userFacingRandom())).queue()
                    }
                }
            }
        }

        return object : Feature {
            override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                if (tag is BotCommandListTag) return tag.values(commands)
                if (tag is JdaListenerTag) return tag.values(listener)

                invalidTag(tag)
            }
        }
    }
}
