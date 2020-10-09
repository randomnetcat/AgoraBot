package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.requests.RestAction
import org.randomcat.agorabot.digest.*
import org.randomcat.agorabot.util.CompletedRestAction

private const val JDA_HISTORY_MAX_RETRIEVE_LIMIT = 100

private fun retrieveMessagesExclusiveRange(beginExclusive: Message, endExclusive: Message): RestAction<List<Message>> {
    require(beginExclusive.jda == endExclusive.jda)
    require(beginExclusive.channel == endExclusive.channel)

    val jda = beginExclusive.jda
    val channel = beginExclusive.channel

    if (beginExclusive.id == endExclusive.id) return CompletedRestAction.ofSuccess(jda, emptyList())

    return channel
        .getHistoryAfter(beginExclusive, JDA_HISTORY_MAX_RETRIEVE_LIMIT)
        .map { it.retrievedHistory }
        .map { it.reversed() } // retrievedHistory goes newest -> oldest, we want oldest -> newest
        .map { messages -> messages.filter { msg -> msg.timeCreated < endExclusive.timeCreated } }
        .flatMap { messages ->
            if (messages.isEmpty())
                CompletedRestAction.ofSuccess(jda, emptyList())
            else
                retrieveMessagesExclusiveRange(
                    beginExclusive = messages.last(),
                    endExclusive = endExclusive,
                ).map { rest -> messages + rest }
        }
}

private fun retrieveMessagesBetween(beginInclusive: Message, endInclusive: Message): RestAction<List<Message>> {
    require(beginInclusive.jda == endInclusive.jda)
    require(beginInclusive.channel == endInclusive.channel)

    val jda = beginInclusive.jda

    if (beginInclusive.id == endInclusive.id) {
        return CompletedRestAction.ofSuccess(jda, listOf(beginInclusive))
    }

    return retrieveMessagesExclusiveRange(
        beginExclusive = beginInclusive,
        endExclusive = endInclusive
    ).map {
        // retrieveMessagesExclusiveRange excludes begin and end, so add them back in here
        listOf(beginInclusive) + it + endInclusive
    }
}

class DigestCommand(
    strategy: BaseCommandStrategy,
    private val digestMap: GuildDigestMap,
    private val sendStrategy: DigestSendStrategy?,
    private val digestFormat: DigestFormat,
) : BaseCommand(strategy) {
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

            if (sendStrategy != null) {
                subcommand("send") {
                    args(StringArg("destination")) { (destination) ->
                        sendStrategy.sendDigest(currentDigest(), destination)

                        respond("Sent digest to $destination.")
                    }
                }
            }

            subcommand("add") {
                matchFirst {
                    args(StringArg("message_id")) { (messageId) ->
                        val message = getMessageOrError(messageId) ?: return@args

                        message.retrieveDigestMessage().queue { digestMessage ->
                            currentDigest().add(digestMessage)
                            respond("Added one message to digest.")
                        }
                    }

                    args(StringArg("range_begin"), StringArg("range_end")) { (rangeBeginId, rangeEndId) ->
                        val rangeBegin = getMessageOrError(rangeBeginId) ?: return@args
                        val rangeEnd = getMessageOrError(rangeEndId) ?: return@args

                        val rangeBeginTime = rangeBegin.timeCreated
                        val rangeEndTime = rangeEnd.timeCreated

                        if (rangeBeginTime > rangeEndTime) {
                            respond("Range start cannot be before range end.")
                            return@args
                        }

                        retrieveMessagesBetween(rangeBegin, rangeEnd)
                            .mapToDigestMessages()
                            .queue { messages ->
                                currentDigest().add(messages)
                                respond("Added ${messages.size} messages to digest.")
                            }
                    }
                }
            }
        }
    }

    private fun ExecutionReceiverImpl.currentDigest() = digestMap.digestForGuild(currentGuildId())
}
