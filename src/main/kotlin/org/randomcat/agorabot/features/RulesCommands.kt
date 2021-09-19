package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.RuleCommand
import java.net.URI

fun rulesCommandsFeature(ruleIndexUri: URI): Feature {
    return Feature.ofCommands { context ->
        mapOf(
            "rule" to RuleCommand(
                context.defaultCommandStrategy,
                ruleIndexUri = ruleIndexUri,
            )
        )
    }
}
