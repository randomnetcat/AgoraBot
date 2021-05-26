package org.randomcat.agorabot.features

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.config.parsing.features.CitationsConfig
import org.randomcat.agorabot.listener.Command
import java.net.URI

const val RULE_URL_NUMBER_REPLACMENT = "{bot_rule_num}"
const val CFJ_URL_NUMBER_REPLACEMENT = "{bot_case_num}"

private fun String.bracedSections(): List<String> {
    return withIndex()
        .filter { it.value == '[' }
        .map {
            it.index to indexOf(']', startIndex = it.index)
        }
        .filter {
            it.second != -1
        }
        .map {
            substring(it.first + 1, it.second)
        }
}

private fun enqueueResponse(message: Message, name: String, citationUri: URI) {
    CoroutineScope(Dispatchers.IO).launch {
        citationUri.toURL().openStream().use { fileStream ->
            val bytes = fileStream.readBytes()
            message.channel.sendFile(bytes, "$name.txt").queue()
        }
    }
}

fun citationsFeature(config: CitationsConfig): Feature {
    return object : Feature {
        override fun commandsInContext(context: FeatureContext): Map<String, Command> {
            return emptyMap()
        }

        override fun registerListenersTo(jda: JDA) {
            if (config.ruleUrlPattern == null && config.cfjUrlPattern == null) return // Nothing to do

            jda.addEventListener(object {
                @SubscribeEvent
                fun onMessage(event: GuildMessageReceivedEvent) {
                    val message = event.message
                    val content = message.contentRaw

                    val bracedSections = content.bracedSections()

                    fun handleParsedNumber(
                        bracedText: String,
                        prefix: String,
                        urlPattern: String,
                        replacement: String,
                    ) {
                        if (bracedText.startsWith(prefix)) {
                            bracedText.removePrefix(prefix).toBigIntegerOrNull()?.let { number ->
                                enqueueResponse(
                                    message = message,
                                    name = bracedText,
                                    citationUri = URI(urlPattern.replace(replacement, number.toString())),
                                )
                            }
                        }
                    }

                    for (bracedSection in bracedSections) {
                        if (config.ruleUrlPattern != null) {
                            handleParsedNumber(
                                bracedText = bracedSection,
                                prefix = "R",
                                urlPattern = config.ruleUrlPattern,
                                replacement = RULE_URL_NUMBER_REPLACMENT,
                            )
                        }

                        if (config.cfjUrlPattern != null) {
                            handleParsedNumber(
                                bracedText = bracedSection,
                                prefix = "CFJ",
                                urlPattern = config.cfjUrlPattern,
                                replacement = CFJ_URL_NUMBER_REPLACEMENT,
                            )
                        }
                    }
                }
            })
        }
    }
}
