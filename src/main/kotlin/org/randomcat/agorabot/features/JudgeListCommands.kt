package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.JudgeListCommand

fun judgeListCommandsFeature(): Feature {
    return Feature.ofCommands { context ->
        mapOf(
            "judge_list" to JudgeListCommand(
                strategy = context.defaultCommandStrategy,
            ),
        )
    }
}
