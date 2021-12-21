package org.randomcat.agorabot.features

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import org.randomcat.agorabot.*
import org.randomcat.agorabot.config.parsing.features.CitationsConfig
import org.randomcat.agorabot.config.parsing.features.readCitationsConfig
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.setup.features.featureConfigDir
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

private fun citationsFeature(config: CitationsConfig): Feature {
    return object : AbstractFeature() {
        override fun commandsInContext(context: FeatureContext): Map<String, Command> {
            return emptyMap()
        }

        override fun jdaListeners(): List<Any> {
            if (config.ruleUrlPattern == null && config.cfjUrlPattern == null) return emptyList()

            return listOf(object {
                @SubscribeEvent
                fun onMessage(event: MessageReceivedEvent) {
                    if (event.author == event.jda.selfUser) {
                        return
                    }

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

@FeatureSourceFactory
fun citationsFactory() = object : FeatureSource {
    override val featureName: String
        get() = "citations"

    override fun readConfig(context: FeatureSetupContext): CitationsConfig {
        return readCitationsConfig(context.paths.featureConfigDir.resolve("citations.json"))
    }

    override fun createFeature(config: Any?): Feature {
        return citationsFeature(config as CitationsConfig)
    }
}
