package org.randomcat.agorabot.commands

import kotlinx.serialization.Serializable
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.buttons.ButtonRequestId
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.formatSecretHitlerJoinMessage
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerHandlers.handleStart
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import java.time.Duration

private val MANAGE_PERMISSION = GuildScope.command("secret_hitler").action("manage")

private val JOIN_LEAVE_DURATION = Duration.ofDays(1)

class SecretHitlerCommand(
    strategy: BaseCommandStrategy,
    private val repository: SecretHitlerRepository,
) : BaseCommand(strategy) {
    @Serializable
    data class JoinGameRequestDescriptor(val gameId: SecretHitlerGameId) : ButtonRequestDescriptor

    @Serializable
    data class LeaveGameRequestDescriptor(val gameId: SecretHitlerGameId) : ButtonRequestDescriptor

    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("create") {
                noArgs().requiresGuild().permissions(MANAGE_PERMISSION) {
                    val state = SecretHitlerGameState.Joining()
                    val gameId = repository.gameList.createGame(state)
                    val assignSucceeded = repository.channelGameMap.tryPutGameForChannelId(currentChannel().id, gameId)

                    if (assignSucceeded) {
                        respond(
                            formatSecretHitlerJoinMessage(
                                state = state,
                                joinButtonId = ButtonRequestId(
                                    newButtonId(
                                        descriptor = JoinGameRequestDescriptor(gameId = gameId),
                                        expiryDuration = JOIN_LEAVE_DURATION,
                                    ),
                                ),
                                leaveButtonId = ButtonRequestId(
                                    newButtonId(
                                        descriptor = LeaveGameRequestDescriptor(gameId = gameId),
                                        expiryDuration = JOIN_LEAVE_DURATION,
                                    ),
                                ),
                            ),
                        )
                    } else {
                        respond("There is already a game ongoing in this channel.")
                        repository.gameList.removeGameIfExists(gameId)
                    }
                }
            }

            subcommand("abort") {
                noArgs().requiresGuild().permissions(MANAGE_PERMISSION) {
                    val abortedGame = repository.channelGameMap.removeGameForChannelId(currentChannel().id)

                    if (abortedGame != null) {
                        repository.gameList.removeGameIfExists(abortedGame)
                        respond("Aborted game.")
                    } else {
                        respond("No game is running in this channel.")
                    }
                }
            }

            subcommand("start") {
                noArgs().requiresGuild().permissions(MANAGE_PERMISSION) {
                    val gameId = repository.channelGameMap.gameByChannelId(currentChannel().id)
                    if (gameId == null) {
                        respond("No game is running in this channel.")
                        return@permissions
                    }

                    handleStart(
                        repository = repository,
                        gameId = gameId,
                    )
                }
            }
        }
    }
}
