package org.randomcat.agorabot

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.slf4j.LoggerFactory

object JdaTokenTag : FeatureElementTag<String>

// JDA without any event listeners attached
object RawJdaTag : FeatureElementTag<JDA>

// JDA fully initialized (with event listeners, etc.)
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
        get() = listOf(RawJdaTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val token = context[tokenDep]

        logger.info("Initializing JDA...")

        val jda =
            JDABuilder
                .createDefault(
                    token,
                    listOf(
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
                if (tag is RawJdaTag) return tag.values(jda)

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

private val rawJdaDep = FeatureDependency.Single(RawJdaTag)
private val listenersDep = FeatureDependency.All(JdaListenerTag)

private class JdaFeatureSource : FeatureSource.NoConfig {
    companion object {
        private val logger = LoggerFactory.getLogger(RawJdaFeatureSource::class.java)
    }

    override val featureName: String
        get() = "jda"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(rawJdaDep, listenersDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(RawJdaTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val jda = context[rawJdaDep]
        val listeners = context[listenersDep]

        jda.addEventListener(*listeners.toTypedArray())

        return object : Feature {
            override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                if (tag is JdaTag) return tag.values(jda)

                invalidTag(tag)
            }

            override fun close() {
                jda.removeEventListener(*listeners.toTypedArray())
            }
        }
    }
}

@FeatureSourceFactory
fun fullJdaFeatureSource(): FeatureSource<*> = JdaFeatureSource()
