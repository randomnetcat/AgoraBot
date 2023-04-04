package org.randomcat.agorabot

import org.randomcat.agorabot.commands.HelpCommand
import org.randomcat.agorabot.commands.impl.BaseCommandStrategyTag
import org.randomcat.agorabot.listener.MutableMapCommandRegistry
import org.randomcat.agorabot.listener.QueryableCommandRegistry
import java.util.concurrent.atomic.AtomicReference

object BotCommandRegistryTag : FeatureElementTag<QueryableCommandRegistry>

private val commandListsDep = FeatureDependency.All(BotCommandListTag)
private val strategyDep = FeatureDependency.Single(BaseCommandStrategyTag)

@FeatureSourceFactory
fun commandRegistrySource() = object : FeatureSource.NoConfig {
    override val featureName: String
        get() = "bot_command_registry"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(commandListsDep, strategyDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(BotCommandRegistryTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val commandLists = context[commandListsDep]
        val commandStrategy = context[strategyDep]

        val registry = MutableMapCommandRegistry(emptyMap())

        for (commandList in commandLists) {
            registry.addCommands(commandList)
        }

        // HelpCommand could attempt to concurrently access the registry.
        // Don't expose the registry to another thread until it is fully initialized (and thus cannot have races).

        val delayedRegistryRef = AtomicReference<QueryableCommandRegistry>(null)

        registry.addCommand(
            "help",
            HelpCommand(
                strategy = commandStrategy,
                registryFun = { delayedRegistryRef.get() },
                suppressedCommands = listOf("permissions"),
            )
        )

        delayedRegistryRef.set(registry)

        return Feature.singleTag(BotCommandRegistryTag, registry)
    }
}
