package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.entities.Message
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.util.DiscordPermission
import org.randomcat.agorabot.util.JDA_HISTORY_MAX_RETRIEVE_LIMIT
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService

private val ARCHIVE_PERMISSION = GuildScope.command("archive")

private val LOGGER = LoggerFactory.getLogger("AgoraBotArchiveCommand")

interface DiscordArchiver {
    fun createArchiveFromAsync(executor: ExecutorService, messages: Iterable<Message>): CompletionStage<Result<Path>>
    val archiveExtension: String
}

class ArchiveCommand(
    strategy: BaseCommandStrategy,
    private val archiver: DiscordArchiver,
    private val executorFun: () -> ExecutorService,
) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        args(StringArg("channel_id")).requiresGuild().permissions(ARCHIVE_PERMISSION) { (channelId) ->
            doArchive(channelId = channelId, storeArchiveResult = { path ->
                val date =
                    DateTimeFormatter
                        .ISO_LOCAL_DATE_TIME
                        .withZone(ZoneOffset.UTC)
                        .format(Instant.now())

                currentChannel()
                    .sendMessage("Archive for channel $channelId")
                    .addFile(
                        path.toFile(),
                        "archive_${channelId}_${date}.${archiver.archiveExtension}",
                    )
                    .queue()
            })
        }
    }

    private fun BaseCommandExecutionReceiverGuilded.doArchive(
        channelId: String,
        storeArchiveResult: BaseCommandExecutionReceiverGuilded.(Path) -> Unit,
    ) {
        val targetChannel = currentGuildInfo().guild.getTextChannelById(channelId)

        if (targetChannel == null) {
            respond("No channel with that ID exists.")
            return
        }

        val member = currentMessageEvent().member ?: error("Member should exist because this is in a Guild")

        if (
            !member.hasPermission(targetChannel, DiscordPermission.MESSAGE_READ) ||
            !member.hasPermission(targetChannel, DiscordPermission.MESSAGE_HISTORY)
        ) {
            respond("You do not have permission to read within that channel.")
            return
        }

        val forwardHistory = sequence {
            val oldestMessageList = targetChannel.getHistoryFromBeginning(1).complete()

            check(oldestMessageList.size() <= 1)
            if (oldestMessageList.isEmpty) return@sequence

            var lastRetrievedMessage = oldestMessageList.retrievedHistory.single()
            yield(lastRetrievedMessage)

            while (true) {
                val nextHistory =
                    targetChannel.getHistoryAfter(lastRetrievedMessage, JDA_HISTORY_MAX_RETRIEVE_LIMIT).complete()

                if (nextHistory.isEmpty) {
                    return@sequence
                }

                // retrievedHistory is always newest -> oldest, we want oldest -> newest
                val nextMessages = nextHistory.retrievedHistory.asReversed()

                yieldAll(nextMessages)
                lastRetrievedMessage = nextMessages.last()
            }
        }

        currentChannel().sendMessage("Running archive job on channel ${channelId}...").queue { statusMessage ->
            fun markFailed() {
                statusMessage.editMessage("Archive job failed for channel ${channelId}!").queue()
            }

            fun markFailedWith(e: Throwable) {
                markFailed()
                LOGGER.error("Error while archiving channel $channelId", e)
            }

            try {
                archiver
                    .createArchiveFromAsync(executorFun(), forwardHistory.asIterable())
                    .thenApply { path ->
                        storeArchiveResult(path.getOrThrow())

                        statusMessage
                            .editMessage("Archive done for channel ${channelId}.")
                            .queue()
                    }
                    .exceptionally {
                        markFailedWith(it)
                    }
            } catch (e: Exception) {
                markFailedWith(e)
            }
        }

        return
    }
}
