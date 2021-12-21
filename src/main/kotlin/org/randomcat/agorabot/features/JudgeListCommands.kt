package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.JudgeListCommand

@FeatureSourceFactory
fun judgeListFactory() = FeatureSource.ofConstant("judge_list", Feature.ofCommands { context ->
    mapOf(
        "judge_list" to JudgeListCommand(
            strategy = context.defaultCommandStrategy,
        ),
    )
})
