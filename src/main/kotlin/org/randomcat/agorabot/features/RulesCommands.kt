package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureSetupContext
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.RuleCommand
import org.randomcat.agorabot.config.parsing.features.RuleCommandsConfig
import org.randomcat.agorabot.config.parsing.features.readRuleCommandsConfig
import org.randomcat.agorabot.setup.features.featureConfigDir
import java.net.URI

private fun rulesCommandsFeature(ruleIndexUri: URI): Feature {
    return Feature.ofCommands { context ->
        mapOf(
            "rule" to RuleCommand(
                context.defaultCommandStrategy,
                ruleIndexUri = ruleIndexUri,
            )
        )
    }
}

@FeatureSourceFactory
fun rulesCommandsFactory() = object : FeatureSource {
    override val featureName: String
        get() = "rules_commands"

    override fun readConfig(context: FeatureSetupContext): RuleCommandsConfig {
        return readRuleCommandsConfig(context.paths.featureConfigDir.resolve("rule_commands.json"))
    }

    override fun createFeature(config: Any?): Feature {
        return rulesCommandsFeature((config as RuleCommandsConfig).ruleIndexUri)
    }
}
