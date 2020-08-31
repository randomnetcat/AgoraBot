package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.entities.Message
import org.randomcat.agorabot.digest.*

class DigestCommand(
    private val digestMap: GuildDigestMap,
    private val sendStrategy: DigestSendStrategy,
    private val digestFormat: DigestFormat,
) : ChatCommand() {
    companion object {
        private fun retrieveMessagesBetween(rangeBegin: Message, rangeEnd: Message): List<Message> {
            require(rangeBegin.channel == rangeEnd.channel)
            val channel = rangeBegin.channel

            val rangeBeginTime = rangeBegin.timeCreated
            val rangeEndTime = rangeEnd.timeCreated

            val result = mutableListOf<Message>()

            var currentBatch: List<Message> =
                channel
                    .getHistoryAround(rangeBegin, 100)
                    .complete()
                    .retrievedHistory
                    .reversed() // The given list is from newest -> oldest; we want oldest -> newest.

            var currentBegin = rangeBeginTime

            while (true) {
                var addedAny = false

                for (message in currentBatch) {
                    val messageTime = message.timeCreated

                    if (messageTime in currentBegin..rangeEndTime) {
                        result.add(message)
                        addedAny = true
                        currentBegin = messageTime
                    }
                }

                if (addedAny) {
                    val lastMessage = currentBatch.last()

                    currentBatch =
                        lastMessage
                            .channel
                            .getHistoryAfter(lastMessage, 100)
                            .complete()
                            .retrievedHistory
                } else {
                    break
                }
            }

            return result
        }
    }

    private fun ExecutionReceiverImpl.getMessageOrError(id: String): Message? {
        val msgResult = currentChannel().retrieveMessageById(id).mapToResult().complete()

        if (msgResult.isFailure) {
            respond("Unable to find message $id in *this* channel.")
            return null
        }

        return msgResult.get()
    }

    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        subcommands {
            subcommand("clear") {
                noArgs { _ ->
                    currentDigest().clear()
                    respond("Successfully cleared digest.")
                }
            }

            subcommand("upload") {
                noArgs { _ ->
                    respondWithFile(
                        fileName = "digest.txt",
                        fileContent = digestFormat.format(currentDigest())
                    )
                }
            }

            subcommand("send") {
                args(StringArg("destination")) { args ->
                    val destination = args.first
                    sendStrategy.sendDigest(currentDigest(), destination)

                    respond("Sent digest to $destination.")
                }
            }

            subcommand("add") {
                matchFirst {
                    args(StringArg("message_id")) { args ->
                        val messageId = args.first
                        val message = getMessageOrError(messageId) ?: return@args

                        currentDigest().add(message.digestMessageAction().complete())
                        respond("Added one message to digest.")
                    }

                    args(StringArg("range_begin"), StringArg("range_end")) { args ->
                        val rangeBeginId = args.first
                        val rangeBegin = getMessageOrError(rangeBeginId) ?: return@args

                        val rangeEndId = args.second
                        val rangeEnd = getMessageOrError(rangeEndId) ?: return@args

                        val rangeBeginTime = rangeBegin.timeCreated
                        val rangeEndTime = rangeEnd.timeCreated

                        if (rangeBeginTime > rangeEndTime) {
                            respond("Range start cannot be before range end.")
                            return@args
                        }

                        val messages =
                            retrieveMessagesBetween(rangeBegin, rangeEnd)
                                .digestMessageActions()
                                .complete()

                        currentDigest().add(messages)

                        respond("Added ${messages.size} messages to digest.")
                    }
                }
            }
        }
    }

    private fun ExecutionReceiverImpl.currentDigest() = digestMap.digestForGuild(currentGuildId())
}
