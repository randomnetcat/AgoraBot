package org.randomcat.agorabot.setup.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.config.parsing.features.readRuleCommandsConfig
import org.randomcat.agorabot.features.rulesCommandsFeature
import org.randomcat.agorabot.setup.BotDataPaths

fun setupRuleCommandsFeature(paths: BotDataPaths): Feature? {
    return readRuleCommandsConfig(
        paths.featureConfigDir.resolve("rule_commands.json"),
    )?.let {
        rulesCommandsFeature(
            ruleIndexUri = it.ruleIndexUri,
        )
    }
}
