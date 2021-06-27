package org.randomcat.agorabot.secrethitler.handlers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

internal object SecretHitlerJoinLeaveMessageQueue {
    private val logger = LoggerFactory.getLogger(javaClass)

    sealed class UpdateAction {
        abstract val updateNumber: BigInteger

        data class JoinMessageUpdate(
            override val updateNumber: BigInteger,
            val message: Message,
            val context: SecretHitlerNameContext,
            val state: SecretHitlerGameState.Joining,
        ) : UpdateAction()
    }

    private suspend fun doHandleMessageUpdates(channel: Channel<UpdateAction>) {
        var largestUpdateNumber: BigInteger? = null

        val futureMap = mutableMapOf<String /* ChannelId */, CompletableFuture<Unit>>()

        var pruneCount = 0

        for (updateAction in channel) {
            // Every 100 actions, remove the entries with futures that are completed. This means that any new updates
            // would execute immediately anyway, so the future is unnecessary.
            // 100 chosen arbitrarily.
            if (pruneCount == 100) {
                pruneCount = 0

                futureMap.entries.removeAll {
                    it.value.isDone
                }
            }

            if (largestUpdateNumber == null || updateAction.updateNumber > largestUpdateNumber) {
                try {
                    largestUpdateNumber = updateAction.updateNumber

                    @Suppress("UNUSED_VARIABLE")
                    val ensureExhaustive = when (updateAction) {
                        is UpdateAction.JoinMessageUpdate -> {
                            val newMessage =
                                MessageBuilder(updateAction.message)
                                    .setEmbed(
                                        formatSecretHitlerJoinMessageEmbed(
                                            context = updateAction.context,
                                            state = updateAction.state,
                                        ),
                                    )
                                    .build()

                            val restAction = updateAction.message.editMessage(newMessage).map { Unit }

                            // Ensure that the message edit action is queued only after any previous update in that
                            // channel has completed.
                            futureMap.compute(updateAction.message.channel.id) { _, old ->
                                if (old != null) {
                                    old.thenCompose { _ ->
                                        restAction.submit()
                                    }
                                } else {
                                    restAction.submit()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error ignored while attempting to handle update task $updateAction", e)
                }
            }
        }
    }

    private fun setupMessageUpdateChannel(): SendChannel<UpdateAction> {
        val channel = Channel<UpdateAction>(capacity = 100)

        CoroutineScope(Dispatchers.Default).launch {
            doHandleMessageUpdates(channel)
        }

        return channel
    }

    private val updateNumber = AtomicReference<BigInteger>(BigInteger.ZERO)

    fun nextUpdateNumber(): BigInteger {
        return updateNumber.getAndUpdate { it + BigInteger.ONE }
    }

    private val messageUpdateChannel: SendChannel<UpdateAction> by lazy {
        setupMessageUpdateChannel()
    }

    fun sendUpdateAction(action: UpdateAction) {
        messageUpdateChannel.trySendBlocking(action)
    }
}
