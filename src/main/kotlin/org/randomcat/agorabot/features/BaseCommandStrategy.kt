package org.randomcat.agorabot.features

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.config.commandOutputMapping
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

private object BaseCommandStrategyCacheKey

private val logger = LoggerFactory.getLogger("BaseCommandStrategy")

@FeatureSourceFactory
fun baseCommandStrategyFactory() = FeatureSource.ofConstant("base_command_strategy_provider", object : Feature {
    override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
        if (tag is BaseCommandStrategyTag) return tag.result(context.cache(BaseCommandStrategyCacheKey) {
            makeBaseCommandStrategy(
                BaseCommandOutputStrategyByOutputMapping(context.commandOutputMapping),
                object : BaseCommandDependencyStrategy {
                    override fun tryFindDependency(tag: Any): Any? {
                        return context.tryQueryExpectOne(BaseCommandDependencyTag(baseTag = tag)).valueOrNull()
                    }
                },
                object : BaseCommandExecutionStrategy {
                    override fun executeCommandBlock(block: suspend () -> Unit) {
                        try {
                            CoroutineScope(Dispatchers.Default).launch {
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
        })

        return FeatureQueryResult.NotFound
    }
})