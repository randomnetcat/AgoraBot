package org.randomcat.agorabot.features

import net.dv8tion.jda.api.interactions.Interaction
import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureButtonData
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.buttons.withType
import org.randomcat.agorabot.commands.SecretHitlerCommand
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.secrethitler.SecretHitlerImpersonationMap
import org.randomcat.agorabot.secrethitler.SecretHitlerMutableImpersonationMap
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerChancellorCandidateSelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerJoinGameButtonDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerLeaveGameButtonDescriptor
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerButtons
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerInteractionContext
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerNameContext
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.util.asSnowflakeOrNull

private data class NameContextImpl(
    val impersonationMap: SecretHitlerImpersonationMap?,
) : SecretHitlerCommand.CommandNameContext {
    override fun renderExternalName(name: SecretHitlerPlayerExternalName): String {
        val raw = name.raw

        return if (raw.toLongOrNull() != null) {
            "<@$raw>"
        } else {
            raw
        }
    }

    override fun resolveDmUserIds(
        name: SecretHitlerPlayerExternalName,
    ): SecretHitlerCommand.CommandNameContext.DmIdsResult {
        val rawName = name.raw

        val impersonationIds = impersonationMap?.dmUserIdsForName(rawName)
        if (impersonationIds != null) {
            return SecretHitlerCommand.CommandNameContext.DmIdsResult.Impersonated(impersonationIds)
        }

        if (rawName.asSnowflakeOrNull() != null) {
            return SecretHitlerCommand.CommandNameContext.DmIdsResult.Direct(rawName)
        }

        return SecretHitlerCommand.CommandNameContext.DmIdsResult.Invalid
    }
}

fun secretHitlerFeature(
    repository: SecretHitlerRepository,
    impersonationMap: SecretHitlerMutableImpersonationMap?,
) = object : Feature {
    private val nameContext = NameContextImpl(impersonationMap)

    override fun commandsInContext(context: FeatureContext): Map<String, Command> {
        return mapOf(
            "secret_hitler" to SecretHitlerCommand(
                context.defaultCommandStrategy,
                repository = repository,
                impersonationMap = impersonationMap,
                nameContext = nameContext,
            ),
        )
    }

    override fun buttonData(): FeatureButtonData {
        val interactionContext = object : SecretHitlerInteractionContext, SecretHitlerNameContext by nameContext {
            override fun nameFromInteraction(interaction: Interaction): SecretHitlerPlayerExternalName {
                val userId = interaction.user.id
                val effectiveName = impersonationMap?.currentNameForId(userId) ?: userId

                return SecretHitlerPlayerExternalName(effectiveName)
            }
        }

        return FeatureButtonData.RegisterHandlers(
            ButtonHandlerMap {
                withType<SecretHitlerJoinGameButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handleJoin(
                        repository = repository,
                        context = interactionContext,
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerLeaveGameButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handleLeave(
                        repository = repository,
                        context = interactionContext,
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerChancellorCandidateSelectionButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handleChancellorSelection(
                        repository = repository,
                        context = interactionContext,
                        event = context.event,
                        request = request,
                    )
                }
            },
        )
    }
}
