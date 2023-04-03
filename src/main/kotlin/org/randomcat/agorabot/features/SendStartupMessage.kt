package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AgoraBotStartupMessage")

private val strategyDep = FeatureDependency.Single(StartupMessageStrategyTag)
private val jdaDep = FeatureDependency.Single(JdaTag)

@FeatureSourceFactory
fun startupMessageBlockFactory() = object : FeatureSource.NoConfig {
    override val featureName: String
        get() = "start_message_block_provider"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(strategyDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(StartupBlockTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val strategy = context[strategyDep]
        val jda = context[jdaDep]

        return Feature.singleTag(StartupBlockTag, {
            try {
                strategy.sendMessageAndClearChannel(jda = jda)
            } catch (e: Exception) {
                // Log and ignore.
                logger.error("Exception while handling startup message.", e)
            }
        })
    }
}
