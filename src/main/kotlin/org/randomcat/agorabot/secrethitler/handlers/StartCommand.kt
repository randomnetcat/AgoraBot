package org.randomcat.agorabot.secrethitler.handlers

import org.randomcat.agorabot.secrethitler.SecretHitlerGameList
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.updateGameTypedWithValidExtract

@OptIn(ExperimentalStdlibApi::class)
private fun randomAssignRoles(
    players: Set<SecretHitlerPlayerNumber>,
    roleConfiguration: SecretHitlerRoleConfiguration,
): SecretHitlerRoleMap {
    val roleList: List<SecretHitlerRole> = buildList {
        repeat(roleConfiguration.liberalCount) { add(SecretHitlerRole.Liberal) }
        repeat(roleConfiguration.plainFascistCount) { add(SecretHitlerRole.PlainFascist) }
        add(SecretHitlerRole.Hitler)

        shuffle()
    }

    check(players.size == roleList.size)

    return SecretHitlerRoleMap((players zip roleList).toMap())
}

internal fun doHandleSecretHitlerStart(
    context: SecretHitlerCommandContext,
    gameList: SecretHitlerGameList,
    gameId: SecretHitlerGameId,
) {
    gameList.updateGameTypedWithValidExtract(
        gameId,
        onNoSuchGame = {
            context.respond("The game that was running in this channel has since been deleted.")
        },
        onInvalidType = { invalidGame ->
            context.respond("That game can no longer be started.")
        },
        validMapper = { currentState: SecretHitlerGameState.Joining ->
            val startResult = SecretHitlerGameState.Running.tryStart(
                currentState = currentState,
                assignRoles = ::randomAssignRoles,
                shuffleProvider = SecretHitlerGlobals.shuffleProvider(),
            )

            when (startResult) {
                is SecretHitlerGameState.Running.StartResult.Failure -> {
                    currentState to startResult
                }

                is SecretHitlerGameState.Running.StartResult.Success -> {
                    startResult.newState to startResult
                }
            }
        },
        afterValid = { underlyingResult ->
            when (underlyingResult) {
                is SecretHitlerGameState.Running.StartResult.Success -> {
                    context.respond("Starting game...")

                    secretHitlerSendChancellorSelectionMessage(
                        context = context,
                        gameId = gameId,
                        state = underlyingResult.newState,
                    )
                }

                is SecretHitlerGameState.Running.StartResult.InsufficientPlayers -> {
                    context.respond(
                        "The game can only be started with at least $SECRET_HITLER_MIN_START_PLAYERS players.",
                    )
                }
            }
        }
    )
}
