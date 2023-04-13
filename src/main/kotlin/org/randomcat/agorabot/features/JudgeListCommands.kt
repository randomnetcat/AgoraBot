package org.randomcat.agorabot.features

import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.JudgeListCommand
import org.randomcat.agorabot.ofBaseCommands

@FeatureSourceFactory
fun judgeListFactory() = FeatureSource.ofBaseCommands(
    "judge_list",
    mapOf(
        "judge_list" to ::JudgeListCommand,
    ),
)
