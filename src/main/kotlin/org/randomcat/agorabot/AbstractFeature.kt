package org.randomcat.agorabot

import kotlinx.collections.immutable.toImmutableMap
import org.randomcat.agorabot.buttons.feature.ButtonDataTag
import org.randomcat.agorabot.buttons.feature.FeatureButtonData
import org.randomcat.agorabot.commands.base.BaseCommandStrategy
import org.randomcat.agorabot.commands.impl.BaseCommandStrategyTag
import org.randomcat.agorabot.listener.Command

abstract class AbstractFeatureSource<Config> : FeatureSource<Config> {
    abstract class NoConfig : AbstractFeatureSource<Unit>() {
        override fun readConfig(context: FeatureSetupContext) = Unit
    }

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(JdaListenerTag, ButtonDataTag, BotCommandListTag)

    final override fun createFeature(config: Config, context: FeatureSourceContext): Feature {
        val commands = commandsInContext(context)
        val listeners = jdaListeners(context)
        val buttons = buttonData(context)

        return object : Feature {
            override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                if (tag is BotCommandListTag) return tag.values(commands)
                if (tag is JdaListenerTag) return tag.values(*listeners.toTypedArray())
                if (tag is ButtonDataTag) return tag.values(buttons)

                invalidTag(tag)
            }
        }
    }

    protected open fun commandsInContext(context: FeatureSourceContext): Map<String, Command> = emptyMap()

    protected open fun jdaListeners(context: FeatureSourceContext): List<Any> = emptyList()

    protected open fun buttonData(context: FeatureSourceContext): FeatureButtonData = FeatureButtonData.NoButtons
}

fun Feature.Companion.ofCommands(commands: Map<String, Command>): Feature {
    return Feature.singleTag(BotCommandListTag, commands.toImmutableMap())
}

fun <Config> FeatureSource.Companion.ofCommandsConfig(
    name: String,
    readConfig: (context: FeatureSetupContext) -> Config,
    dependencies: List<FeatureDependency<*>>,
    block: (config: Config, context: FeatureSourceContext) -> Map<String, Command>,
): FeatureSource<Config> {
    return object : FeatureSource<Config> {
        override val featureName: String
            get() = name

        override fun readConfig(context: FeatureSetupContext): Config {
            return readConfig(context)
        }

        override val dependencies: List<FeatureDependency<*>>
            get() = dependencies

        override val provides: List<FeatureElementTag<*>>
            get() = listOf(BotCommandListTag)

        override fun createFeature(config: Config, context: FeatureSourceContext): Feature {
            val commands = block(config, context)
            return Feature.ofCommands(commands)
        }
    }
}

private val strategyDep = FeatureDependency.Single(BaseCommandStrategyTag)

fun <Config> FeatureSource.Companion.ofBaseCommandsConfig(
    name: String,
    readConfig: (context: FeatureSetupContext) -> Config,
    extraDependencies: List<FeatureDependency<*>> = emptyList(),
    block: (strategy: BaseCommandStrategy, config: Config, context: FeatureSourceContext) -> Map<String, Command>,
): FeatureSource<Config> {
    return ofCommandsConfig(
        name = name,
        readConfig = readConfig,
        dependencies = extraDependencies + strategyDep,
    ) { config, context ->
        block(context[strategyDep], config, context)
    }
}

fun FeatureSource.Companion.ofBaseCommands(
    name: String,
    extraDependencies: List<FeatureDependency<*>> = emptyList(),
    block: (strategy: BaseCommandStrategy, context: FeatureSourceContext) -> Map<String, Command>,
): FeatureSource<Unit> {
    return ofBaseCommandsConfig(
        name = name,
        readConfig = { Unit },
        extraDependencies = extraDependencies,
    ) { strategy, _, context ->
        block(strategy, context)
    }
}

fun FeatureSource.Companion.ofBaseCommands(
    name: String,
    map: Map<String, (BaseCommandStrategy) -> Command>,
) = ofBaseCommands(name) { strategy, _ ->
    map.mapValues { (_, v) -> v(strategy) }
}
