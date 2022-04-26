package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.HaltCommand
import org.randomcat.agorabot.commands.StopCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy

object StartupMessageStrategyTag : FeatureElementTag<StartupMessageStrategy>

@FeatureSourceFactory
fun adminCommandsFactory() = FeatureSource.ofConstant("admin_commands", Feature.ofCommands { context ->
    val commandStrategy = context.defaultCommandStrategy

    val haltCommand = HaltCommand(commandStrategy)

    val stopCommand = when (val result = context.tryQueryExpectOne(StartupMessageStrategyTag)) {
        is FeatureQueryResult.Found -> StopCommand(commandStrategy, writeChannelFun = result.value::writeChannel)
        is FeatureQueryResult.NotFound -> null
    }

    mapOf(
        "halt" to haltCommand,
        "stop" to stopCommand,
    ).filterValues {
        it != null
    }.mapValues { (_, v) ->
        v!!
    }
})
