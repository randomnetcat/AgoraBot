package org.randomcat.agorabot.features

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.*
import org.slf4j.LoggerFactory

object JdaTokenTag : FeatureElementTag<String>

object JdaTag : FeatureElementTag<JDA>

private val tokenDep = FeatureDependency.Single(JdaTokenTag)

private class RawJdaFeatureSource : FeatureSource.NoConfig {
    companion object {
        private val logger = LoggerFactory.getLogger(RawJdaFeatureSource::class.java)
    }

    override val featureName: String
        get() = "jda_raw"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(tokenDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(JdaTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val token = context[tokenDep]

        logger.info("Initializing JDA...")

        val jda =
            JDABuilder
                .createDefault(
                    token,
                    listOf(
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_MESSAGE_REACTIONS,
                        GatewayIntent.DIRECT_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGE_REACTIONS,
                    ),
                )
                .setEventManager(AnnotatedEventManager())
                .build()

        logger.info("Waiting for JDA...")

        jda.awaitReady()

        logger.info("JDA ready")

        return object : Feature {
            override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                if (tag is JdaTag) return tag.values(jda)

                invalidTag(tag)
            }

            override fun close() {
                jda.shutdown()
            }
        }
    }
}

@FeatureSourceFactory
fun rawJdaFeatureSource(): FeatureSource<*> = RawJdaFeatureSource()
