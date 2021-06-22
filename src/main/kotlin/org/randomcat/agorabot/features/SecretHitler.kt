package org.randomcat.agorabot.features

import net.dv8tion.jda.api.interactions.Interaction
import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureButtonData
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.buttons.withType
import org.randomcat.agorabot.commands.SecretHitlerCommand
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.secrethitler.SecretHitlerMutableImpersonationMap
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerButtons
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerNameContext
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName

fun secretHitlerFeature(
    repository: SecretHitlerRepository,
    impersonationMap: SecretHitlerMutableImpersonationMap?,
) = object : Feature {
    override fun commandsInContext(context: FeatureContext): Map<String, Command> {
        return mapOf(
            "secret_hitler" to SecretHitlerCommand(
                context.defaultCommandStrategy,
                repository = repository,
                impersonationMap = impersonationMap,
            ),
        )
    }

    override fun buttonData(): FeatureButtonData {
        val nameContext = object : SecretHitlerNameContext {
            override fun nameFromInteraction(interaction: Interaction): SecretHitlerPlayerExternalName {
                return SecretHitlerPlayerExternalName(interaction.user.id)
            }
        }

        return FeatureButtonData.RegisterHandlers(
            ButtonHandlerMap {
                withType<SecretHitlerCommand.JoinGameRequestDescriptor> { context, request ->
                    SecretHitlerButtons.handleJoin(
                        repository = repository,
                        nameContext = nameContext,
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerCommand.LeaveGameRequestDescriptor> { context, request ->
                    SecretHitlerButtons.handleLeave(
                        repository = repository,
                        nameContext = nameContext,
                        event = context.event,
                        request = request,
                    )
                }
            },
        )
    }
}
