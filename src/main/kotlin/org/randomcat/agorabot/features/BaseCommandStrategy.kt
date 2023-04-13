package org.randomcat.agorabot.features

import kotlinx.coroutines.launch
import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.impl.BaseCommandDefaultArgumentStrategy
import org.randomcat.agorabot.commands.impl.BaseCommandOutputStrategyByOutputMapping
import org.randomcat.agorabot.commands.impl.BaseCommandStrategyTag
import org.randomcat.agorabot.config.CommandOutputMappingTag
import org.slf4j.LoggerFactory

private fun makeBaseCommandStrategy(
    outputStrategy: BaseCommandOutputStrategy,
    dependencyStrategy: BaseCommandDependencyStrategy,
    executionStrategy: BaseCommandExecutionStrategy,
): BaseCommandStrategy {
    return object :
        BaseCommandStrategy,
        BaseCommandArgumentStrategy by BaseCommandDefaultArgumentStrategy,
        BaseCommandOutputStrategy by outputStrategy,
        BaseCommandDependencyStrategy by dependencyStrategy,
        BaseCommandExecutionStrategy by executionStrategy {}
}

private val logger = LoggerFactory.getLogger("BaseCommandStrategy")

private val outputDep = FeatureDependency.Single(CommandOutputMappingTag)
private val coroutineScopeDep = FeatureDependency.Single(CoroutineScopeTag)
private val baseCommandDependenciesDep = FeatureDependency.All(BaseCommandDependencyTag)

@FeatureSourceFactory
fun baseCommandStrategyFactory() = object : FeatureSource.NoConfig {
    override val featureName: String
        get() = "base_command_strategy_provider"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(outputDep, coroutineScopeDep, baseCommandDependenciesDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(BaseCommandStrategyTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val outputMapping = context[outputDep]
        val coroutineScope = context[coroutineScopeDep]
        val commandDependencies = context[baseCommandDependenciesDep]

        val strategy = makeBaseCommandStrategy(
            BaseCommandOutputStrategyByOutputMapping(outputMapping),
            object : BaseCommandDependencyStrategy {
                override fun tryFindDependency(tag: Any): Any? {
                    return commandDependencies.singleOrNull { it.baseTag == tag }?.value
                }
            },
            object : BaseCommandExecutionStrategy {
                override fun executeCommandBlock(block: suspend () -> Unit) {
                    try {
                        coroutineScope.launch {
                            try {
                                block()
                            } catch (e: Exception) {
                                logger.error("Exception during command execution", e)
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to schedule command execution", e)
                    }
                }
            }
        )

        return Feature.singleTag(BaseCommandStrategyTag, strategy)
    }
}
