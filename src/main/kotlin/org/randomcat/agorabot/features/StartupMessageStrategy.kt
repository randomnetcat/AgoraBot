package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import java.nio.file.Path

private data class StartupMessageConfig(val storagePath: Path)

@FeatureSourceFactory
fun startupMessageStrategyFactory(): FeatureSource<*> = object : FeatureSource<StartupMessageConfig> {
    override val featureName: String
        get() = "startup_message_strategy_provider"

    override val dependencies: List<FeatureDependency<*>>
        get() = emptyList()

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(StartupMessageStrategyTag)

    override fun readConfig(context: FeatureSetupContext): StartupMessageConfig {
        return StartupMessageConfig(
            storagePath = context.paths.storagePath.resolve("hammertime_channel")
        )
    }

    override fun createFeature(config: StartupMessageConfig, context: FeatureSourceContext): Feature {
        return Feature.singleTag(
            StartupMessageStrategyTag,
            DefaultStartupMessageStrategy(storagePath = config.storagePath)
        )
    }
}
