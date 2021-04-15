package org.randomcat.agorabot.features

import kotlinx.collections.immutable.toImmutableList
import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.HelpCommand

fun helpCommandsFeature(
    suppressedCommands: List<String>,
): Feature {
    val safeSuppressedList = suppressedCommands.toImmutableList()

    return Feature.ofCommands { context ->
        mapOf(
            "help" to HelpCommand(
                strategy = context.defaultCommandStrategy,
                registryFun = { context.commandRegistry() },
                suppressedCommands = safeSuppressedList,
            ),
        )
    }
}
