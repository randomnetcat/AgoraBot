package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.requirements.discord.InGuildSimple
import org.randomcat.agorabot.commands.base.requirements.discord.currentChannel
import org.randomcat.agorabot.commands.base.requirements.discord.currentChannelId
import org.randomcat.agorabot.commands.base.requirements.discord.currentMessageEvent
import org.randomcat.agorabot.commands.base.requirements.discord_ext.ExtendedDiscordRequirement
import org.randomcat.agorabot.commands.base.requirements.discord_ext.InGuild
import org.randomcat.agorabot.commands.base.requirements.discord_ext.invalidButtonId
import org.randomcat.agorabot.commands.base.requirements.discord_ext.newButtonId
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.permissions.BotScope
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.secrethitler.context.SecretHitlerCommandContext
import org.randomcat.agorabot.secrethitler.context.SecretHitlerMessageContext
import org.randomcat.agorabot.secrethitler.context.SecretHitlerNameContext
import org.randomcat.agorabot.secrethitler.handlers.*
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerHandlers.handleStart
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerHandlers.sendJoinLeaveMessage
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerMutableImpersonationMap
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerRepository
import java.time.Duration

private val MANAGE_PERMISSION = GuildScope.command("secret_hitler").action("manage")
private val IMPERSONATE_PERMISSION = BotScope.command("secret_hitler").action("impersonate")

private fun makeContext(
    commandReceiver: BaseCommandExecutionReceiverRequiring<ExtendedDiscordRequirement>,
    nameContext: SecretHitlerNameContext,
    messageContext: SecretHitlerMessageContext,
): SecretHitlerCommandContext {
    return object :
        SecretHitlerCommandContext,
        SecretHitlerNameContext by nameContext,
        SecretHitlerMessageContext by messageContext {
        override fun newButtonId(descriptor: ButtonRequestDescriptor, expiryDuration: Duration): String {
            return commandReceiver.newButtonId(descriptor, expiryDuration)
        }

        override fun invalidButtonId(): String {
            return commandReceiver.invalidButtonId()
        }

        override suspend fun respond(message: MessageCreateData) {
            commandReceiver.respond(message)
        }

        override suspend fun respond(message: String) {
            commandReceiver.respond(message)
        }
    }
}

class SecretHitlerCommand(
    strategy: BaseCommandStrategy,
    private val repository: SecretHitlerRepository,
    private val impersonationMap: SecretHitlerMutableImpersonationMap?,
    private val nameContext: SecretHitlerNameContext,
    private val makeMessageContext: (gameId: SecretHitlerGameId, currentChannel: MessageChannel) -> SecretHitlerMessageContext,
) : BaseCommand(strategy) {
    private fun BaseCommandExecutionReceiverRequiring<ExtendedDiscordRequirement>.currentContext(gameId: SecretHitlerGameId): SecretHitlerCommandContext =
        makeContext(
            commandReceiver = this,
            nameContext = nameContext,
            messageContext = makeMessageContext(gameId, currentChannel),
        )

    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            if (impersonationMap != null) {
                subcommand("impersonate") {
                    subcommand("set_name") {
                        args(StringArg("name"))
                            .requires(InGuildSimple)
                            .permissions(IMPERSONATE_PERMISSION) { (name) ->
                                impersonationMap.setNameForId(userId = currentMessageEvent.author.id, newName = name)
                                respond("Done.")
                            }
                    }

                    subcommand("restore_name") {
                        noArgs()
                            .requires(InGuildSimple)
                            .permissions(IMPERSONATE_PERMISSION) {
                                impersonationMap.clearNameForId(currentMessageEvent.author.id)
                                respond("Done.")
                            }
                    }

                    subcommand("receive_dms") {
                        args(RemainingStringArgs("names"))
                            .requires(InGuildSimple)
                            .permissions(IMPERSONATE_PERMISSION) { (names) ->
                                val userId = currentMessageEvent.author.id

                                for (name in names) {
                                    impersonationMap.addDmUserIdForName(name = name, userId = userId)
                                }

                                respond("Done.")
                            }
                    }

                    subcommand("restore_dms") {
                        args(StringArg("name"))
                            .requires(InGuildSimple)
                            .permissions(IMPERSONATE_PERMISSION) { (name) ->
                                impersonationMap.clearDmUsersForName(name)
                            }
                    }
                }
            }

            subcommand("create") {
                noArgs().requires(InGuild).permissions(MANAGE_PERMISSION) {
                    val state = SecretHitlerGameState.Joining()
                    val gameId = repository.gameList.createGame(state)
                    val assignSucceeded = repository.channelGameMap.tryPutGameForChannelId(currentChannel.id, gameId)

                    if (assignSucceeded) {
                        sendJoinLeaveMessage(
                            context = currentContext(gameId = gameId),
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
                noArgs().requires(InGuildSimple).permissions(MANAGE_PERMISSION) {
                    val abortedGame = repository.channelGameMap.removeGameForChannelId(currentChannel.id)

                    if (abortedGame != null) {
                        repository.gameList.removeGameIfExists(abortedGame)
                        respond("Aborted game.")
                    } else {
                        respond("No game is running in this channel.")
                    }
                }
            }

            subcommand("start") {
                noArgs().requires(InGuild).permissions(MANAGE_PERMISSION) {
                    val gameId = repository.channelGameMap.gameByChannelId(currentChannel.id)
                    if (gameId == null) {
                        respond("No game is running in this channel.")
                        return@permissions
                    }

                    handleStart(
                        context = currentContext(gameId = gameId),
                        gameList = repository.gameList,
                        gameId = gameId,
                        shuffleRoles = SecretHitlerGlobals::shuffleRoles,
                    )
                }
            }

            subcommand("resend") {
                noArgs().requires(InGuild) {
                    val gameId = repository.channelGameMap.gameByChannelId(currentChannelId)
                    if (gameId == null) {
                        respond("No game is running in this channel.")
                        return@requires
                    }

                    val gameState = repository.gameList.gameById(gameId)
                    if (gameState == null) {
                        respond("That game no longer exists.")
                        return@requires
                    }

                    val context = currentContext(gameId = gameId)

                    @Suppress("UNUSED_VARIABLE")
                    val ensureExhaustive = when (gameState) {
                        is SecretHitlerGameState.Joining -> {
                            sendJoinLeaveMessage(
                                context = context,
                                gameId = gameId,
                                state = gameState,
                            )
                        }

                        is SecretHitlerGameState.Running -> {
                            when (gameState.ephemeralState) {
                                is SecretHitlerEphemeralState.ChancellorSelectionPending -> {
                                    secretHitlerSendChancellorSelectionMessage(
                                        context = context,
                                        gameId = gameId,
                                        state = gameState.assumeWith<SecretHitlerEphemeralState.ChancellorSelectionPending>(),
                                    )
                                }

                                is SecretHitlerEphemeralState.VotingOngoing -> {
                                    sendSecretHitlerVotingMessage(
                                        context = context,
                                        gameId = gameId,
                                        gameState = gameState.assumeWith<SecretHitlerEphemeralState.VotingOngoing>(),
                                    )
                                }

                                is SecretHitlerEphemeralState.PresidentPolicyChoicePending -> {
                                    sendSecretHitlerPresidentPolicySelectionMessage(
                                        context = context,
                                        gameId = gameId,
                                        currentState = gameState.assumeWith<SecretHitlerEphemeralState.PresidentPolicyChoicePending>(),
                                    )
                                }

                                is SecretHitlerEphemeralState.ChancellorPolicyChoicePending -> {
                                    sendSecretHitlerChancellorPolicySelectionMessage(
                                        context = context,
                                        gameId = gameId,
                                        state = gameState.assumeWith<SecretHitlerEphemeralState.ChancellorPolicyChoicePending>(),
                                    )
                                }

                                is SecretHitlerEphemeralState.PolicyPending -> {
                                    sendSecretHitlerPowerActivatedMessages(
                                        context = context,
                                        gameId = gameId,
                                        currentState = gameState.assumeWith<SecretHitlerEphemeralState.PolicyPending>(),
                                    )
                                }
                            }
                        }

                        is SecretHitlerGameState.Completed -> {
                            respond("That game is complete.")
                        }
                    }
                }
            }
        }
    }
}
