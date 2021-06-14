package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureButtonData
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.commands.SecretHitlerCommand
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository

fun secretHitlerFeature(repository: SecretHitlerRepository) = object : Feature {
    override fun commandsInContext(context: FeatureContext): Map<String, Command> {
        return mapOf(
            "secret_hitler" to SecretHitlerCommand(context.defaultCommandStrategy, repository = repository),
        )
    }

    override fun buttonData(): FeatureButtonData {
        return FeatureButtonData.RegisterHandlers(
            ButtonHandlerMap {
            },
        )
    }
}
