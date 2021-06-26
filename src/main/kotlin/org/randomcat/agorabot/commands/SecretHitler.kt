package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.permissions.BotScope
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.secrethitler.SecretHitlerMutableImpersonationMap
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerHandlers.handleStart
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerHandlers.sendJoinLeaveMessage
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState

private val MANAGE_PERMISSION = GuildScope.command("secret_hitler").action("manage")
private val IMPERSONATE_PERMISSION = BotScope.command("secret_hitler").action("impersonate")

class SecretHitlerCommand(
    strategy: BaseCommandStrategy,
    private val repository: SecretHitlerRepository,
    private val impersonationMap: SecretHitlerMutableImpersonationMap?,
) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            if (impersonationMap != null) {
                subcommand("impersonate") {
                    subcommand("set_name") {
                        args(StringArg("name"))
                            .requiresGuild()
                            .permissions(IMPERSONATE_PERMISSION) { (name) ->
                                impersonationMap.setNameForId(userId = currentMessageEvent().author.id, newName = name)
                                respond("Done.")
                            }
                    }

                    subcommand("restore_name") {
                        noArgs()
                            .requiresGuild()
                            .permissions(IMPERSONATE_PERMISSION) {
                                impersonationMap.clearNameForId(currentMessageEvent().author.id)
                                respond("Done.")
                            }
                    }

                    subcommand("receive_dms") {
                        args(RemainingStringArgs("names"))
                            .requiresGuild()
                            .permissions(IMPERSONATE_PERMISSION) { (names) ->
                                val userId = currentMessageEvent().author.id

                                for (name in names) {
                                    impersonationMap.addDmUserIdForName(name = name, userId = userId)
                                }

                                respond("Done.")
                            }
                    }

                    subcommand("restore_dms") {
                        args(StringArg("name"))
                            .requiresGuild()
                            .permissions(IMPERSONATE_PERMISSION) { (name) ->
                                impersonationMap.clearDmUsersForName(name)
                            }
                    }
                }
            }

            subcommand("create") {
                noArgs().requiresGuild().permissions(MANAGE_PERMISSION) {
                    val state = SecretHitlerGameState.Joining()
                    val gameId = repository.gameList.createGame(state)
                    val assignSucceeded = repository.channelGameMap.tryPutGameForChannelId(currentChannel().id, gameId)

                    if (assignSucceeded) {
                        sendJoinLeaveMessage(gameId, state)
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

            subcommand("resend") {
                noArgs().requiresGuild() {
                    val gameId = repository.channelGameMap.gameByChannelId(currentChannelId())
                    if (gameId == null) {
                        respond("No game is running in this channel.")
                        return@requiresGuild
                    }

                    val gameState = repository.gameList.gameById(gameId)
                    if (gameState == null) {
                        respond("That game no longer exists.")
                        return@requiresGuild
                    }

                    when (gameState) {
                        is SecretHitlerGameState.Joining -> {
                            sendJoinLeaveMessage(gameId, gameState)
                        }

                        else -> {
                            respond("Don't know how to resend a message for that state.")
                        }
                    }
                }
            }
        }
    }
}
