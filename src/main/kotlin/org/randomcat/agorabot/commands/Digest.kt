package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.requests.RestAction
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.digest.*
import org.randomcat.agorabot.util.CompletedRestAction
import org.randomcat.agorabot.util.JDA_HISTORY_MAX_RETRIEVE_LIMIT
import org.randomcat.agorabot.util.tryAddReaction

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

private fun <Arg> PendingInvocation<ContextReceiverArg<BaseCommandContext, BaseCommandExecutionReceiver, Arg>>.digestAction() =
    requires(InGuildSimple)

private fun <Arg> PendingInvocation<ContextReceiverArg<BaseCommandContext, BaseCommandExecutionReceiver, Arg>>.digestAction(
    block: BaseCommandExecutionReceiverGuilded.(Arg) -> Unit,
) = digestAction().execute { block(it.receiver, it.arg) }

class DigestCommand(
    strategy: BaseCommandStrategy,
    private val digestMap: GuildMutableDigestMap,
    private val sendStrategy: DigestSendStrategy?,
    private val digestFormat: DigestFormat,
    private val digestAddedReaction: String?,
) : BaseCommand(strategy) {
    private fun BaseCommandExecutionReceiverGuilded.getMessageOrError(id: String): Message? {
        val msgResult = currentChannel.retrieveMessageById(id).mapToResult().complete()

        if (msgResult.isFailure) {
            respond("Unable to find message $id in *this* channel.")
            return null
        }

        return msgResult.get()
    }

    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("clear") {
                noArgs().digestAction { _ ->
                    val digest = currentDigest()

                    digest.clear()
                    respond("Successfully cleared digest.")
                }
            }

            subcommand("upload") {
                noArgs().digestAction { _ ->
                    val digest = currentDigest()

                    respondWithFile(
                        fileName = "digest.txt",
                        fileContent = digestFormat.format(digest)
                    )
                }
            }

            if (sendStrategy != null) {
                subcommand("send") {
                    args(StringArg("destination")).digestAction { (destination) ->
                        val digest = currentDigest()

                        sendStrategy.sendDigest(digest, destination)

                        respond("Sent digest to $destination.")
                    }
                }
            }

            subcommand("add") {
                matchFirst {
                    args(StringArg("message_id")).digestAction { (messageId) ->
                        val digest = currentDigest()

                        val message = getMessageOrError(messageId) ?: return@digestAction

                        message.retrieveDigestMessage().queue { digestMessage ->
                            digest.add(digestMessage)
                            respond("Added one message to digest.")

                            if (digestAddedReaction != null) {
                                message.tryAddReaction(digestAddedReaction).queue()
                            }
                        }
                    }

                    args(
                        StringArg("range_begin"),
                        StringArg("range_end"),
                    ).digestAction { (rangeBeginId, rangeEndId) ->
                        val digest = currentDigest()

                        val rangeBegin = getMessageOrError(rangeBeginId) ?: return@digestAction
                        val rangeEnd = getMessageOrError(rangeEndId) ?: return@digestAction

                        val rangeBeginTime = rangeBegin.timeCreated
                        val rangeEndTime = rangeEnd.timeCreated

                        if (rangeBeginTime > rangeEndTime) {
                            respond("Range start cannot be before range end.")
                            return@digestAction
                        }

                        retrieveMessagesBetween(rangeBegin, rangeEnd)
                            .map {
                                it to it.retrieveDigestMessages()
                            }
                            .queue { (messages, digestMessagesAction) ->
                                digestMessagesAction.queue { digestMessage ->
                                    digest.add(digestMessage)
                                    respond("Added ${messages.size} messages to digest.")

                                    if (digestAddedReaction != null) {
                                        messages.forEach { message ->
                                            message.tryAddReaction(digestAddedReaction).queue()
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }

    private fun BaseCommandExecutionReceiverGuilded.currentDigest() =
        digestMap.digestForGuild(currentGuildId)
}
