package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.permissions.BotScope
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.secrethitler.SecretHitlerImpersonationMap
import org.randomcat.agorabot.secrethitler.SecretHitlerMutableImpersonationMap
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerCommandContext
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerHandlers.handleStart
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerHandlers.sendJoinLeaveMessage
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.util.DiscordMessage
import java.time.Duration

private val MANAGE_PERMISSION = GuildScope.command("secret_hitler").action("manage")
private val IMPERSONATE_PERMISSION = BotScope.command("secret_hitler").action("impersonate")

private fun makeContext(
    commandReceiver: BaseCommandExecutionReceiverGuilded,
    impersonationMap: SecretHitlerImpersonationMap?,
): SecretHitlerCommandContext {
    return object : SecretHitlerCommandContext {
        override fun newButtonId(descriptor: ButtonRequestDescriptor, expiryDuration: Duration): String {
            return commandReceiver.newButtonId(descriptor, expiryDuration)
        }

        // Assume that commands are only sent in the same channel as the game.
        override fun sendGameMessage(message: String) {
            commandReceiver.currentChannel().sendMessage(message).queue()
        }

        // Assume that commands are only sent in the same channel as the game.
        override fun sendGameMessage(message: DiscordMessage) {
            commandReceiver.currentChannel().sendMessage(message).queue()
        }

        override fun sendPrivateMessage(recipient: SecretHitlerPlayerExternalName, message: String) {
            sendPrivateMessage(recipient, MessageBuilder(message).build())
        }

        override fun sendPrivateMessage(recipient: SecretHitlerPlayerExternalName, message: DiscordMessage) {
            val overrideRecipientIds = impersonationMap?.dmUserIdsForName(recipient.raw)

            if (overrideRecipientIds != null) {
                val adjustedMessage =
                    MessageBuilder(message)
                        .also {
                            it.stringBuilder.insert(0, "Redirected from ${recipient.raw}:\n")
                        }
                        .build()

                for (recipientId in overrideRecipientIds) {
                    commandReceiver.currentJda().openPrivateChannelById(recipientId).queue { channel ->
                        channel.sendMessage(adjustedMessage).queue()
                    }
                }
            } else {
                commandReceiver.currentJda().openPrivateChannelById(recipient.raw).queue { channel ->
                    channel.sendMessage(message)
                }
            }
        }

        override fun respond(message: DiscordMessage) {
            commandReceiver.respond(message)
        }

        override fun respond(message: String) {
            commandReceiver.respond(message)
        }
    }
}

class SecretHitlerCommand(
    strategy: BaseCommandStrategy,
    private val repository: SecretHitlerRepository,
    private val impersonationMap: SecretHitlerMutableImpersonationMap?,
) : BaseCommand(strategy) {
    private val BaseCommandExecutionReceiverGuilded.context: SecretHitlerCommandContext
        get() = makeContext(this, impersonationMap)

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
                        sendJoinLeaveMessage(
                            context = context,
                            gameId = gameId,
                            state = state,
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
                        context = context,
                        gameList = repository.gameList,
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
                            sendJoinLeaveMessage(
                                context = context,
                                gameId = gameId,
                                state = gameState,
                            )
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
