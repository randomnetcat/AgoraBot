package org.randomcat.agorabot.features

import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import net.dv8tion.jda.api.utils.FileUpload
import org.randomcat.agorabot.*
import org.randomcat.agorabot.setup.features.featureConfigDir
import org.randomcat.agorabot.util.await
import org.slf4j.LoggerFactory
import java.net.URI
import kotlin.io.path.readText

private val FULL_RULE_PREFIXES = listOf("R", "FR", "FLR")
private val SHORT_RULE_PREFIXES = listOf("SR", "SLR")
private const val RULE_URL_NUMBER_REPLACEMENT = "{bot_rule_num}"
private const val CFJ_URL_NUMBER_REPLACEMENT = "{bot_case_num}"

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

private suspend fun sendResponse(message: Message, name: String, citationUri: URI) {
    withContext(Dispatchers.IO) {
        citationUri.toURL().openStream().use { fileStream ->
            val bytes = fileStream.readBytes()
            message.channel.sendFiles(FileUpload.fromData(bytes, "$name.txt")).await()
        }
    }
}

private val logger = LoggerFactory.getLogger("AgoraBotCitations")

private data class CitationsConfig(
    val fullRuleUrlPattern: String?,
    val shortRuleUrlPattern: String?,
    val cfjUrlPattern: String?,
)

@Serializable
private data class CitationsConfigDto(
    @SerialName("rule_url_format")
    val fullRuleUrlPattern: String? = null,
    @SerialName("short_rule_url_format")
    val shortRuleUrlPattern: String? = null,
    @SerialName("cfj_url_format")
    val cfjUrlPattern: String? = null,
) {
    companion object {
        private fun checkPatternReplacement(description: String, pattern: String?, replacement: String) {
            if (pattern == null) return

            require(pattern.contains(replacement)) {
                "Citation config $description pattern should contain \"$replacement\""
            }
        }
    }

    init {
        checkPatternReplacement("full rule", fullRuleUrlPattern, RULE_URL_NUMBER_REPLACEMENT)
        checkPatternReplacement("short rule", shortRuleUrlPattern, RULE_URL_NUMBER_REPLACEMENT)
        checkPatternReplacement("CFJ", cfjUrlPattern, CFJ_URL_NUMBER_REPLACEMENT)
    }

    fun toConfig() = CitationsConfig(
        fullRuleUrlPattern = fullRuleUrlPattern,
        shortRuleUrlPattern = shortRuleUrlPattern,
        cfjUrlPattern = cfjUrlPattern,
    )
}

private fun makeListener(
    coroutineScope: CoroutineScope,
    config: CitationsConfig,
): Any? {
    if (
        config.shortRuleUrlPattern == null &&
        config.fullRuleUrlPattern == null &&
        config.cfjUrlPattern == null
    ) {
        return null
    }

    return object {
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
                        coroutineScope.launch {
                            try {
                                sendResponse(
                                    message = message,
                                    name = bracedText,
                                    citationUri = URI(urlPattern.replace(replacement, number.toString())),
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                logger.error("Error while attempting to send citation", e)
                            }
                        }
                    }
                }
            }

            for (bracedSection in bracedSections) {
                if (config.fullRuleUrlPattern != null) {
                    for (prefix in FULL_RULE_PREFIXES) {
                        handleParsedNumber(
                            bracedText = bracedSection,
                            prefix = prefix,
                            urlPattern = config.fullRuleUrlPattern,
                            replacement = RULE_URL_NUMBER_REPLACEMENT,
                        )
                    }
                }

                if (config.shortRuleUrlPattern != null) {
                    for (prefix in SHORT_RULE_PREFIXES) {
                        handleParsedNumber(
                            bracedText = bracedSection,
                            prefix = prefix,
                            urlPattern = config.shortRuleUrlPattern,
                            replacement = RULE_URL_NUMBER_REPLACEMENT,
                        )
                    }
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
    }
}

private val coroutineScopeDep = FeatureDependency.Single(CoroutineScopeTag)

@FeatureSourceFactory
fun citationsFactory(): FeatureSource<*> = object : FeatureSource<CitationsConfig> {
    override val featureName: String
        get() = "citations"

    override fun readConfig(context: FeatureSetupContext): CitationsConfig {
        return Json.decodeFromString<CitationsConfigDto>(
            context.paths.featureConfigDir.resolve("citations.json").readText()
        ).toConfig()
    }

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(coroutineScopeDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(JdaListenerTag)

    override fun createFeature(config: CitationsConfig, context: FeatureSourceContext): Feature {
        val coroutineScope = context[coroutineScopeDep]

        val listener = makeListener(coroutineScope, config)

        return if (listener != null) {
            Feature.singleTag(JdaListenerTag, listener)
        } else {
            Feature.singleTag(JdaListenerTag) // No elements
        }
    }
}
