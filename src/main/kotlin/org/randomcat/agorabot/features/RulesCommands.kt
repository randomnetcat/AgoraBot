package org.randomcat.agorabot.features

import org.randomcat.agorabot.FeatureSetupContext
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.RuleCommand
import org.randomcat.agorabot.config.parsing.features.RuleCommandsConfig
import org.randomcat.agorabot.config.parsing.features.readRuleCommandsConfig
import org.randomcat.agorabot.ofBaseCommandsConfig
import org.randomcat.agorabot.setup.features.featureConfigDir

private fun readConfig(context: FeatureSetupContext): RuleCommandsConfig {
    return readRuleCommandsConfig(context.paths.featureConfigDir.resolve("rule_commands.json"))
}

@FeatureSourceFactory
fun rulesCommandsFactory() = FeatureSource.ofBaseCommandsConfig(
    name = "rules_commands",
    readConfig = ::readConfig,
) { strategy, config, _ ->
    mapOf(
        "rule" to RuleCommand(
            strategy,
            ruleIndexUri = config.ruleIndexUri,
        )
    )
}
