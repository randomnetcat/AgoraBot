package org.randomcat.agorabot.commands

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.MessageChannel
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.permissions.BotScope
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.util.DiscordPermission
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val ARCHIVE_PERMISSION = GuildScope.command("archive")

private val LOGGER = LoggerFactory.getLogger("AgoraBotArchiveCommand")

interface DiscordArchiver {
    suspend fun createArchiveFrom(channels: List<MessageChannel>): Path
    val archiveExtension: String
}

private fun formatCurrentDate(): String {
    return DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.now())
}

class ArchiveCommand(
    strategy: BaseCommandStrategy,
    private val archiver: DiscordArchiver,
    private val localStorageDir: Path,
) : BaseCommand(strategy) {
    init {
        Files.createDirectories(localStorageDir)
    }

    override fun BaseCommandImplReceiver.impl() {
        matchFirst {
            args(LiteralArg("store_locally"), RemainingStringArgs("channel_id"))
                .requiresGuild()
                .permissions(ARCHIVE_PERMISSION, BotScope.admin()) { (_, channelIds) ->
                    doArchive(channelIds = channelIds, storeArchiveResult = { path ->
                        val fileName = "archive_${formatCurrentDate()}.${archiver.archiveExtension}"

                        Files.copy(path, localStorageDir.resolve(fileName))

                        respond("Stored file locally at $fileName")
                    })
                }

            args(RemainingStringArgs("channel_ids"))
                .requiresGuild()
                .permissions(ARCHIVE_PERMISSION) { (channelIds) ->
                    doArchive(channelIds = channelIds, storeArchiveResult = { path ->
                        currentChannel()
                            .sendMessage("Archive for channels $channelIds")
                            .addFile(
                                path.toFile(),
                                "archive_${formatCurrentDate()}.${archiver.archiveExtension}",
                            )
                            .queue()
                    })
                }
        }
    }

    private fun BaseCommandExecutionReceiverGuilded.doArchive(
        channelIds: List<String>,
        storeArchiveResult: BaseCommandExecutionReceiverGuilded.(Path) -> Unit,
    ) {
        val member = currentMessageEvent().member ?: error("Member should exist because this is in a Guild")

        val targetChannels = channelIds.toSet().map { id ->
            val channel = currentGuildInfo().guild.getTextChannelById(id)

            if (channel == null) {
                respond("The channel id $id does not exist.")
                return
            }

            if (
                !member.hasPermission(channel, DiscordPermission.MESSAGE_READ) ||
                !member.hasPermission(channel, DiscordPermission.MESSAGE_HISTORY)
            ) {
                respond("You do not have permission to read in the channel $id.")
                return
            }

            channel
        }

        currentChannel().sendMessage("Running archive job...").queue { statusMessage ->
            fun markFailed() {
                statusMessage.editMessage("Archive job failed!").queue()
            }

            fun markFailedWith(e: Throwable) {
                markFailed()
                LOGGER.error("Error while archiving channels $channelIds", e)
            }

            try {
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        storeArchiveResult(archiver.createArchiveFrom(targetChannels))

                        statusMessage
                            .editMessage("Archive done for channel ids $channelIds.")
                            .queue()
                    } catch (t: Throwable) {
                        markFailedWith(t)
                    }
                }
            } catch (e: Exception) {
                markFailedWith(e)
            }
        }

        return
    }
}
