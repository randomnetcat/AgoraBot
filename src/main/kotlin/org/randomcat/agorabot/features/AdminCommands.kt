package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.HaltCommand
import org.randomcat.agorabot.commands.StopCommand

object StartupMessageStrategyTag : FeatureElementTag<StartupMessageStrategy>

private val startupStrategyDep = FeatureDependency.AtMostOne(StartupMessageStrategyTag)

@FeatureSourceFactory
fun adminCommandsFactory() = FeatureSource.ofBaseCommands(
    name = "admin_commands",
    extraDependencies = listOf(startupStrategyDep),
) { commandStrategy, context ->
    val startupStrategy = context[startupStrategyDep]
    val haltCommand = HaltCommand(commandStrategy)

    val stopCommand = if (startupStrategy != null) {
        StopCommand(commandStrategy, writeChannelFun = startupStrategy::writeChannel)
    } else {
        null
    }

    mapOf(
        "halt" to haltCommand,
        "stop" to stopCommand,
    ).filterValues {
        it != null
    }.mapValues { (_, v) ->
        v!!
    }
}
