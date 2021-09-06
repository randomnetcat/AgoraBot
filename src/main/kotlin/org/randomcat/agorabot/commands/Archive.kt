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
            args(StringArg("channel_id"))
                .requiresGuild()
                .permissions(ARCHIVE_PERMISSION) { (channelId) ->
                    doArchive(channelId = channelId, storeArchiveResult = { path ->
                        currentChannel()
                            .sendMessage("Archive for channel $channelId")
                            .addFile(
                                path.toFile(),
                                "archive_${channelId}_${formatCurrentDate()}.${archiver.archiveExtension}",
                            )
                            .queue()
                    })
                }

            args(StringArg("channel_id"), LiteralArg("store_locally"))
                .requiresGuild()
                .permissions(ARCHIVE_PERMISSION, BotScope.admin()) { (channelId) ->
                    doArchive(channelId = channelId, storeArchiveResult = { path ->
                        val fileName = "archive_${channelId}_${formatCurrentDate()}.${archiver.archiveExtension}"

                        Files.copy(path, localStorageDir.resolve(fileName))

                        respond("Stored file locally at $fileName")
                    })
                }
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

        currentChannel().sendMessage("Running archive job on channel ${channelId}...").queue { statusMessage ->
            fun markFailed() {
                statusMessage.editMessage("Archive job failed for channel ${channelId}!").queue()
            }

            fun markFailedWith(e: Throwable) {
                markFailed()
                LOGGER.error("Error while archiving channel $channelId", e)
            }

            try {
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        storeArchiveResult(archiver.createArchiveFrom(listOf(targetChannel)))

                        statusMessage
                            .editMessage("Archive done for channel ${channelId}.")
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
